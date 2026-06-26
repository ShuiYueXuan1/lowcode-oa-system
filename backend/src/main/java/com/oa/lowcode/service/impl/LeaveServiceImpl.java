package com.oa.lowcode.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.chain.ApproveContext;
import com.oa.lowcode.chain.ChainBuilder;
import com.oa.lowcode.config.SchemaCacheManager;
import com.oa.lowcode.entity.*;
import com.oa.lowcode.handler.ApproveHandler;
import com.oa.lowcode.mapper.*;
import com.oa.lowcode.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 请假服务实现 —— 请假业务的核心服务
 *
 * <p><b>全生命周期：</b>
 * <ol>
 *   <li>提交 — 校验表单 → 创建 leave_instance → 加载流程 Schema → 构建责任链 → 创建首节点 PENDING</li>
 *   <li>审批通过 — 更新当前节点 → 从 snapshot 快照找下一节点 → 流转或完成</li>
 *   <li>审批驳回 — 标记 REJECTED，流程终止</li>
 *   <li>查询 — 用户维度的请假列表 + 详情（含审批链）</li>
 * </ol></p>
 *
 * <p><b>关键设计：</b>
 * <ul>
 *   <li>流程 Schema 优先从 Caffeine 缓存读取，未命中查 DB 并回写</li>
 *   <li>审批流转通过 process_instance.snapshot_json 中的 resolvedNodes 查找下一节点</li>
 *   <li>所有写操作标记 @Transactional，保证数据一致性</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private final FormSchemaMapper formSchemaMapper;
    private final FlowSchemaMapper flowSchemaMapper;
    private final SchemaCacheManager cacheManager;
    private final LeaveInstanceMapper leaveInstanceMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ChainBuilder chainBuilder;

    /**
     * 提交请假申请
     *
     * <p><b>流程（6 步）：</b>
     * <ol>
     *   <li>校验表单 Schema 存在且已发布（status=2）</li>
     *   <li>创建 leave_instance（status=PENDING）</li>
     *   <li>查找对应的 flow_schema（优先缓存，未命中查 DB 并回写）</li>
     *   <li>ChainBuilder.build() 解析节点 + 评估条件 → 构建责任链 + 生成快照</li>
     *   <li>创建 process_instance（status=IN_PROGRESS，含 snapshot_json）</li>
     *   <li>调用链头的 handler.doHandle() → 创建首节点 PENDING 审批记录</li>
     * </ol></p>
     *
     * <p>特殊处理：
     * <ul>
     *   <li>无流程配置 → 直接标记 APPROVED</li>
     *   <li>所有节点被条件跳过 → 自动通过</li>
     * </ul></p>
     *
     * @return { leaveInstance, processInstance, snapshot, message? }
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitLeave(Long formSchemaId, Map<String, Object> formData,
                                            Long applicantId, String applicantName) {
        // ===== 第1步：校验表单 Schema =====
        FormSchema formSchema = formSchemaMapper.selectById(formSchemaId);
        if (formSchema == null || formSchema.getStatus() != 2)
            throw new IllegalArgumentException("表单 Schema 不存在或未发布");

        // ===== 第2步：创建请假业务记录 =====
        // form_data 是 MySQL JSON 列，直接存用户提交的整个表单数据
        LeaveInstance leave = new LeaveInstance();
        leave.setFormSchemaId(formSchemaId);
        leave.setApplicantId(applicantId);
        leave.setApplicantName(applicantName);
        leave.setFormData(formData);
        leave.setStatus("PENDING");
        leaveInstanceMapper.insert(leave);

        // ===== 第3步：加载流程配置 （缓存优先，未命中查 DB） =====
        Long flowSchemaId = null;
        Map<String, Object> flowConfig = cacheManager.getFlowSchema(formSchema.getCode());
        if (flowConfig == null) {
            // 缓存未命中 → 查 flow_schema 表最新已发布版本
            FlowSchema flowSchema = flowSchemaMapper.selectOne(
                    new LambdaQueryWrapper<FlowSchema>()
                            .eq(FlowSchema::getCode, formSchema.getCode())
                            .eq(FlowSchema::getStatus, 2)
                            .orderByDesc(FlowSchema::getVersion).last("LIMIT 1"));
            if (flowSchema == null || flowSchema.getSchemaJson() == null) {
                // 没有配置审批流程 → 直接自动批准
                leave.setStatus("APPROVED");
                leaveInstanceMapper.updateById(leave);
                log.info("无流程配置，请假直接通过: leaveId={}", leave.getId());
                return Map.of("leaveInstance", leave, "message", "无审批流程配置，已自动通过");
            }
            flowSchemaId = flowSchema.getId();
            flowConfig = flowSchema.getSchemaJson();
            cacheManager.putFlowSchema(formSchema.getCode(), flowConfig);
        }

        // ===== 第4步：提取 nodes 数组，空则自动批准 =====
        List<Map<String, Object>> nodesConfig = (List<Map<String, Object>>) flowConfig.get("nodes");
        if (nodesConfig == null || nodesConfig.isEmpty()) {
            leave.setStatus("APPROVED");
            leaveInstanceMapper.updateById(leave);
            return Map.of("leaveInstance", leave, "message", "流程无审批节点，已自动通过");
        }

        // ===== 第5步：ChainBuilder 构建责任链 + 生成快照 =====
        // 核心：遍历 nodes → 评估条件跳过/保留 → 反射获取 Handler Bean → setNext 串链
        // 返回 chainHead（链头节点）和 snapshot（解析快照，记录每个节点 required/skipped）
        ChainBuilder.BuildResult buildResult = chainBuilder.build(nodesConfig, formData);

        // ===== 第6步：创建流程实例，存入 snapshot 快照 =====
        // snapshot_json 是流程的"冻结副本"，后续审批流转全部基于此快照，不重新读 flow_schema
        ProcessInstance process = new ProcessInstance();
        process.setFlowSchemaId(flowSchemaId != null ? flowSchemaId : 1L);
        process.setBusinessId(leave.getId());
        process.setBusinessType("LEAVE");
        process.setStatus("IN_PROGRESS");
        process.setSnapshotJson(buildResult.snapshot());
        processInstanceMapper.insert(process);

        // ===== 第7步：创建第一条待审批记录 =====
        ApproveHandler chainHead = buildResult.chainHead();
        if (chainHead != null) {
            // 有需要审批的节点 → 设置当前节点，创建 PENDING 记录
            process.setCurrentNodeId(chainHead.getNodeCode());
            processInstanceMapper.updateById(process);
            ApproveContext ctx = ApproveContext.builder()
                    .leaveInstance(leave).processInstance(process).formData(formData).build();
            // doHandle() 实时查询组织架构表，获取首节点审批人 ID
            createPendingRecord(process.getId(), chainHead, ctx);
        } else {
            // chainHead 为 null → 所有节点被条件跳过 → 自动批准
            leave.setStatus("APPROVED");
            leaveInstanceMapper.updateById(leave);
            process.setStatus("APPROVED");
            process.setFinishTime(LocalDateTime.now());
            processInstanceMapper.updateById(process);
            return Map.of("leaveInstance", leave, "processInstance", process,
                    "message", "所有审批节点条件不满足，已自动通过");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaveInstance", leave);
        result.put("processInstance", process);
        result.put("snapshot", buildResult.snapshot());
        return result;
    }

    /**
     * 审批通过
     *
     * <p><b>流转逻辑：</b>
     * <ol>
     *   <li>更新当前审批记录为 APPROVE</li>
     *   <li>从 snapshot.resolvedNodes 查找当前节点之后第一个 required=true 的节点</li>
     *   <li>有下一节点 → 通过 handler.doHandle() 获取审批人，创建新的 PENDING 记录</li>
     *   <li>无下一节点 → 流程完成，标记 leave/process 为 APPROVED</li>
     * </ol></p>
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> approve(Long recordId, Long approverId, String approverName, String comment) {
        // ===== 第1步：校验当前审批记录 =====
        ApprovalRecord record = approvalRecordMapper.selectById(recordId);
        if (record == null || !"PENDING".equals(record.getAction()))
            throw new IllegalArgumentException("审批记录不存在或已处理");

        // ===== 第2步：更新当前记录为"已批准" =====
        record.setAction("APPROVE");
        record.setApproverId(approverId);
        record.setApproverName(approverName);
        record.setComment(comment);
        record.setHandleTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);

        // ===== 第3步：从 snapshot 快照中查找下一个需要审批的节点 =====
        // 关键：读的是 process_instance.snapshot_json，不是重新读 flow_schema
        // 这保证了在途流程不受管理员后续修改流程配置的影响
        ProcessInstance process = processInstanceMapper.selectById(record.getProcessId());
        if (process == null) throw new IllegalArgumentException("流程实例不存在");

        Map<String, Object> snapshot = process.getSnapshotJson();
        List<Map<String, Object>> resolvedNodes =
                (List<Map<String, Object>>) snapshot.get("resolvedNodes");
        Map<String, Object> nextRequiredNode =
                findNextRequiredNode(resolvedNodes, process.getCurrentNodeId());

        if (nextRequiredNode == null) {
            // ===== 第4a步：没有下一个 required 节点 → 流程结束，全部批准 =====
            process.setStatus("APPROVED");
            process.setFinishTime(LocalDateTime.now());
            process.setCurrentNodeId(null);
            processInstanceMapper.updateById(process);
            LeaveInstance leave = leaveInstanceMapper.selectById(process.getBusinessId());
            if (leave != null) { leave.setStatus("APPROVED"); leaveInstanceMapper.updateById(leave); }
            log.info("审批流程完成: processId={}", process.getId());
            return Map.of("processInstance", process, "message", "审批流程已全部通过");
        }

        // ===== 第4b步：有下一个节点 → 流转 =====
        String nextNodeCode = (String) nextRequiredNode.get("nodeCode");
        String nextNodeName = (String) nextRequiredNode.get("nodeName");
        process.setCurrentNodeId(nextNodeCode);
        processInstanceMapper.updateById(process);

        // 创建下一节点的待审批记录
        ApprovalRecord nextRecord = new ApprovalRecord();
        nextRecord.setProcessId(process.getId());
        nextRecord.setNodeId((String) nextRequiredNode.get("nodeId"));
        nextRecord.setNodeName(nextNodeName);
        nextRecord.setAction("PENDING");

        // 通过反射获取 Handler，doHandle() 实时查组织架构确定审批人
        LeaveInstance leave = leaveInstanceMapper.selectById(process.getBusinessId());
        ApproveHandler handler = findHandlerForNode(nextNodeCode);
        if (handler != null && leave != null) {
            ApproveContext ctx = ApproveContext.builder()
                    .leaveInstance(leave).processInstance(process).formData(leave.getFormData()).build();
            nextRecord.setApproverId(handler.doHandle(ctx));
            nextRecord.setApproverName(handler.getApproverName());
        }
        approvalRecordMapper.insert(nextRecord);

        log.info("流转到下一节点: {} (handler={})", nextNodeName,
                handler != null ? handler.getClass().getSimpleName() : "null");
        return Map.of("processInstance", process, "nextApprovalRecord", nextRecord,
                "message", "已流转到: " + nextNodeName);
    }

    /**
     * 审批驳回
     *
     * <p>更新审批记录 → process/leave 标记 REJECTED → 流程终止</p>
     */
    @Override
    @Transactional
    public Map<String, Object> reject(Long recordId, Long approverId, String approverName, String comment) {
        ApprovalRecord record = approvalRecordMapper.selectById(recordId);
        if (record == null || !"PENDING".equals(record.getAction()))
            throw new IllegalArgumentException("审批记录不存在或已处理");

        record.setAction("REJECT");
        record.setApproverId(approverId);
        record.setApproverName(approverName);
        record.setComment(comment);
        record.setHandleTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);

        ProcessInstance process = processInstanceMapper.selectById(record.getProcessId());
        if (process != null) {
            process.setStatus("REJECTED");
            process.setFinishTime(LocalDateTime.now());
            processInstanceMapper.updateById(process);
            LeaveInstance leave = leaveInstanceMapper.selectById(process.getBusinessId());
            if (leave != null) { leave.setStatus("REJECTED"); leaveInstanceMapper.updateById(leave); }
        }
        log.info("审批驳回: recordId={}, processId={}", recordId,
                process != null ? process.getId() : null);
        return Map.of("approvalRecord", record, "processInstance", process, "message", "已驳回");
    }

    /** 查询某用户的所有请假记录（按时间倒序，含流程状态摘要） */
    @Override
    public List<Map<String, Object>> getUserRecords(Long userId) {
        List<LeaveInstance> leaves = leaveInstanceMapper.selectList(
                new LambdaQueryWrapper<LeaveInstance>()
                        .eq(LeaveInstance::getApplicantId, userId)
                        .orderByDesc(LeaveInstance::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LeaveInstance leave : leaves) {
            ProcessInstance process = processInstanceMapper.selectOne(
                    new LambdaQueryWrapper<ProcessInstance>()
                            .eq(ProcessInstance::getBusinessId, leave.getId())
                            .eq(ProcessInstance::getBusinessType, "LEAVE"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", leave.getId());
            item.put("applicantName", leave.getApplicantName());
            item.put("status", leave.getStatus());
            item.put("formData", leave.getFormData());
            item.put("createTime", leave.getCreateTime());
            if (process != null) {
                item.put("processId", process.getId());
                item.put("processStatus", process.getStatus());
                item.put("currentNodeId", process.getCurrentNodeId());
            }
            result.add(item);
        }
        return result;
    }

    /** 查询请假详情（含表单数据 + 流程状态 + 完整审批记录链） */
    @Override
    public Map<String, Object> getDetail(Long leaveId) {
        LeaveInstance leave = leaveInstanceMapper.selectById(leaveId);
        if (leave == null) throw new IllegalArgumentException("请假记录不存在");
        ProcessInstance process = processInstanceMapper.selectOne(
                new LambdaQueryWrapper<ProcessInstance>()
                        .eq(ProcessInstance::getBusinessId, leaveId)
                        .eq(ProcessInstance::getBusinessType, "LEAVE"));
        List<ApprovalRecord> records = null;
        if (process != null) {
            records = approvalRecordMapper.selectList(
                    new LambdaQueryWrapper<ApprovalRecord>()
                            .eq(ApprovalRecord::getProcessId, process.getId())
                            .orderByAsc(ApprovalRecord::getCreateTime));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaveInstance", leave);
        result.put("processInstance", process);
        result.put("approvalRecords", records);
        return result;
    }

    // ===== 私有方法 =====

    private void createPendingRecord(Long processId, ApproveHandler handler, ApproveContext ctx) {
        Long approverId = handler.doHandle(ctx);
        ApprovalRecord record = new ApprovalRecord();
        record.setProcessId(processId);
        record.setNodeId(handler.getNodeCode());
        record.setNodeName(handler.getNodeName());
        record.setApproverId(approverId);
        record.setApproverName(handler.getApproverName());
        record.setAction("PENDING");
        approvalRecordMapper.insert(record);
    }

    /** 从 resolvedNodes 中找到当前节点之后的下一个 required=true 的节点 */
    private Map<String, Object> findNextRequiredNode(List<Map<String, Object>> resolvedNodes,
                                                      String currentNodeCode) {
        if (resolvedNodes == null) return null;
        boolean foundCurrent = (currentNodeCode == null);
        for (Map<String, Object> node : resolvedNodes) {
            String code = (String) node.get("nodeCode");
            if (!foundCurrent) {
                if (code != null && code.equals(currentNodeCode)) foundCurrent = true;
                continue;
            }
            if (code != null && code.equals(currentNodeCode)) continue;
            if (Boolean.TRUE.equals(node.getOrDefault("required", true))) return node;
        }
        return null;
    }

    private ApproveHandler findHandlerForNode(String nodeCode) {
        if (StrUtil.isBlank(nodeCode)) return null;
        try {
            ChainBuilder.BuildResult br = chainBuilder.build(
                    List.of(Map.of("nodeCode", nodeCode, "nodeName", nodeCode,
                            "nodeId", "temp", "conditions", List.of())),
                    Map.of());
            return br.chainHead();
        } catch (Exception e) {
            log.warn("获取 Handler 失败: nodeCode={}", nodeCode, e);
            return null;
        }
    }
}
