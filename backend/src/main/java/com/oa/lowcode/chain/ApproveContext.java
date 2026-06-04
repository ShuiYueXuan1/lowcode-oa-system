package com.oa.lowcode.chain;

import com.oa.lowcode.entity.LeaveInstance;
import com.oa.lowcode.entity.ProcessInstance;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 审批上下文 — 在责任链各节点间传递的数据载体
 */
@Data
@Builder
public class ApproveContext {
    /** 请假业务数据 */
    private LeaveInstance leaveInstance;
    /** 流程实例 */
    private ProcessInstance processInstance;
    /** 用户填写的表单数据（扁平化，如 days=3, leave_type="annual"） */
    private Map<String, Object> formData;
    /** 当前审批人 ID */
    private Long approverId;
    /** 当前审批人姓名 */
    private String approverName;
    /** 审批意见 */
    private String comment;
    /** 当前正在处理的节点 ID */
    private String currentNodeId;
}
