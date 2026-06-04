package com.oa.lowcode.service;

import java.util.List;
import java.util.Map;

/**
 * 请假业务服务接口
 *
 * <p><b>全生命周期：</b>
 * <ul>
 *   <li>提交 → 校验表单 + 加载流程 Schema（优先缓存）→ 构建责任链 → 创建首节点 PENDING</li>
 *   <li>审批通过 → 从 snapshot 快照找下一节点 → 流转或完成</li>
 *   <li>审批驳回 → 标记 REJECTED → 流程终止</li>
 *   <li>查询 → 用户维度请假列表 + 详情（含审批链）</li>
 * </ul></p>
 */
public interface LeaveService {

    /**
     * 提交请假申请
     * @return { leaveInstance, processInstance, snapshot, message? }
     */
    Map<String, Object> submitLeave(Long formSchemaId, Map<String, Object> formData,
                                    Long applicantId, String applicantName);

    /** 审批通过 → 流转到下一节点或完成流程 */
    Map<String, Object> approve(Long recordId, Long approverId, String approverName, String comment);

    /** 审批驳回 → 终止流程 */
    Map<String, Object> reject(Long recordId, Long approverId, String approverName, String comment);

    /** 查询某用户的所有请假记录（含流程状态摘要） */
    List<Map<String, Object>> getUserRecords(Long userId);

    /** 查询请假详情（含表单数据 + 流程状态 + 审批记录链） */
    Map<String, Object> getDetail(Long leaveId);
}
