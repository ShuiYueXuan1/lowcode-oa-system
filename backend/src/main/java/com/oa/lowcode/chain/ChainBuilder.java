package com.oa.lowcode.chain;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.entity.ApprovalNode;
import com.oa.lowcode.handler.ApproveHandler;
import com.oa.lowcode.mapper.ApprovalNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任链构建器 —— 流程引擎的核心组件
 *
 * <p><b>职责：</b>读取 flow_schema 的 JSON 配置，动态组装审批责任链。</p>
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>解析 schema_json.nodes 数组</li>
 *   <li>对每个节点调用 ConditionEvaluator 评估条件分支
 *       <ul>
 *         <li>无条件 → 加入责任链</li>
 *         <li>条件满足且 action=SKIP → 跳过该节点</li>
 *         <li>条件满足且 action=REQUIRE → 加入责任链</li>
 *       </ul></li>
 *   <li>根据 approval_node 表的 handler_type 字段，反射实例化对应的 Handler Bean</li>
 *   <li>将 Handler 按顺序串联（setNext），返回链头</li>
 *   <li>生成 resolvedNodes 快照存入 process_instance.snapshot_json
 *       （用于后续审批流转时查找下一个节点，且不受后续 schema 变更影响）</li>
 * </ol></p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 *   ChainBuilder.BuildResult result = chainBuilder.build(nodesConfig, formData);
 *   ApproveHandler head = result.chainHead();
 *   process.setSnapshotJson(result.snapshot());
 * }</pre></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainBuilder {

    private final ApplicationContext applicationContext;
    private final ApprovalNodeMapper approvalNodeMapper;

    /**
     * 构建责任链并生成流程快照
     *
     * @param nodesConfig flow_schema.schema_json.nodes 数组
     * @param formData    用户提交的表单数据（用于条件评估）
     * @return BuildResult 包含链头 Handler 和流程解析快照
     */
    @SuppressWarnings("unchecked")
    public BuildResult build(List<Map<String, Object>> nodesConfig,
                             Map<String, Object> formData) {
        List<Map<String, Object>> resolvedNodes = new ArrayList<>();
        ApproveHandler head = null;
        ApproveHandler tail = null;

        // 遍历流程 schema 的每个审批节点
        for (int i = 0; i < nodesConfig.size(); i++) {
            Map<String, Object> nodeConfig = nodesConfig.get(i);
            String nodeCode = (String) nodeConfig.get("nodeCode");
            String nodeName = (String) nodeConfig.get("nodeName");
            List<Map<String, Object>> conditions =
                (List<Map<String, Object>>) nodeConfig.getOrDefault("conditions", List.of());

            // ===== 步骤A: 评估条件，判断该节点是否需要跳过 =====
            // 空条件 → 不跳过（默认需要审批）
            // 有条件 → 逐条 Expression 求值，第一条命中即生效（短路）
            boolean skipped = ConditionEvaluator.shouldSkip(conditions, formData);

            // ===== 步骤B: 构建 resolvedNodes 快照记录 =====
            // 无论跳过与否都写入快照，前端可按 required 字段区分展示
            Map<String, Object> resolved = new LinkedHashMap<>();
            resolved.put("nodeId", nodeConfig.get("nodeId"));
            resolved.put("nodeCode", nodeCode);
            resolved.put("nodeName", nodeName);
            resolved.put("order", nodeConfig.getOrDefault("order", i + 1));
            resolved.put("required", !skipped);
            resolved.put("conditions", conditions);

            if (skipped) {
                resolved.put("skipReason", "条件不满足，跳过");
                resolvedNodes.add(resolved);
                log.info("节点 [{}] 条件不满足，跳过", nodeName);
                continue;  // 跳过此节点，不加入责任链
            }

            // ===== 步骤C: 反射获取 Handler Bean =====
            // findHandler 里面做 3 件事：查 approval_node 表拿全限定类名 → Class.forName 反射 → getBean 从 Spring 取实例
            ApproveHandler handler = findHandler(nodeCode);
            if (handler == null) {
                resolved.put("skipReason", "未找到 Handler: " + nodeCode);
                resolvedNodes.add(resolved);
                log.warn("节点 [{}] 未找到 Handler: {}", nodeName, nodeCode);
                continue;
            }

            // 注入当前节点的配置（nodeCode/nodeName/conditions）到 Handler
            handler.setNodeConfig(nodeConfig);
            resolvedNodes.add(resolved);

            // ===== 步骤D: 串联到责任链尾部 =====
            if (head == null) {
                head = handler;    // 第一个节点 → 既是链头也是链尾
                tail = handler;
            } else {
                tail.setNext(handler);  // 接到链尾后面
                tail = handler;         // 更新链尾指针
            }

            log.info("节点 [{}] → Handler: {}", nodeName, handler.getClass().getSimpleName());
        }

        // ===== 生成流程快照 =====
        // 快照在提交时冻结，存入 process_instance.snapshot_json
        // 后续审批流转（同意/驳回）全部从这里找下一节点，不重新读 flow_schema
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resolvedNodes", resolvedNodes);
        snapshot.put("buildTime", System.currentTimeMillis());

        return new BuildResult(head, snapshot);
    }

    /**
     * 根据 nodeCode 查找对应的 Spring Bean Handler
     *
     * <p>三步完成：查 approval_node 表拿 handler_type 全限定类名
     * → Class.forName 反射加载 Class → applicationContext.getBean 从容器取实例</p>
     */
    private ApproveHandler findHandler(String nodeCode) {
        // 第1步: 从数据库查 handler_type（如 "com.oa.lowcode.handler.DirectLeaderHandler"）
        ApprovalNode node = approvalNodeMapper.selectOne(
            new LambdaQueryWrapper<ApprovalNode>().eq(ApprovalNode::getNodeCode, nodeCode));
        if (node == null || StrUtil.isBlank(node.getHandlerType())) return null;
        try {
            // 第2步: 反射加载 Class（把字符串变成真正的 Class 对象）
            Class<?> clazz = Class.forName(node.getHandlerType());
            // 第3步: 从 Spring 容器获取 Handler 单例 Bean（每个 Handler 都有 @Component）
            return (ApproveHandler) applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.error("反射实例化 Handler 失败: {}", node.getHandlerType(), e);
            return null;
        }
    }

    /**
     * 责任链构建结果
     *
     * @param chainHead 责任链头节点（为 null 表示所有节点被跳过）
     * @param snapshot  流程解析快照，需存入 process_instance.snapshot_json
     */
    public record BuildResult(ApproveHandler chainHead, Map<String, Object> snapshot) {
    }
}