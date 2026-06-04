package com.oa.lowcode.handler;

import com.oa.lowcode.chain.ApproveContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 审批处理器抽象基类 —— 责任链模式的核心抽象
 *
 * <p><b>设计思路：</b>每个审批节点（直属主管、部门经理、HR、总经理）都是一个独立的 Handler。
 * Handler 通过 {@link #setNext} 串联成链，运行时由 ChainBuilder 动态组装。</p>
 *
 * <p><b>子类需要实现：</b>
 * <ul>
 *   <li>{@link #doHandle(ApproveContext)} — 从组织架构中查询正确的审批人</li>
 *   <li>{@link #getApproverName()} — 返回审批人姓名</li>
 *   <li>{@link #getApproverId()} — 返回审批人 ID</li>
 * </ul></p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 *   handler1.setNext(handler2);
 *   handler1.doHandle(context);  // 从链头开始执行
 * }</pre></p>
 */
@Slf4j
@Getter
@Setter
public abstract class ApproveHandler {

    /** 责任链下一节点，为 null 表示链尾 */
    protected ApproveHandler next;

    /** 当前节点配置（来自 flow_schema.schema_json.nodes[i]） */
    protected Map<String, Object> nodeConfig;

    /**
     * 执行审批处理：从组织架构中查询正确的审批人
     * @param context 审批上下文（含申请人信息、表单数据等）
     * @return 审批人用户 ID
     */
    public abstract Long doHandle(ApproveContext context);

    /** @return 审批人姓名 */
    public abstract String getApproverName();

    /** @return 审批人用户 ID */
    public abstract Long getApproverId();

    /** @return 当前节点名称（来自 schema 配置） */
    public String getNodeName() {
        return nodeConfig != null ? (String) nodeConfig.getOrDefault("nodeName", "") : "";
    }

    /** @return 当前节点编码（来自 schema 配置） */
    public String getNodeCode() {
        return nodeConfig != null ? (String) nodeConfig.getOrDefault("nodeCode", "") : "";
    }
}
