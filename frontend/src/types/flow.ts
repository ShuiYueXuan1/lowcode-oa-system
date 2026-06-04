/**
 * 流程设计器 —— 核心类型定义
 *
 * <p>定义了流程 JSON Schema 的完整结构，包括：
 * <ul>
 *   <li>FlowNode — 审批链中的单个节点（含条件分支）</li>
 *   <li>FlowCondition — 节点上的条件规则</li>
 *   <li>FlowSchema — 完整流程定义（设计器产出物）</li>
 *   <li>ApprovalNodeItem — 从后端审批节点库加载的节点元数据</li>
 * </ul></p>
 */

/** 条件运算符（6 种） */
export type ConditionOperator = '>' | '>=' | '<' | '<=' | '==' | '!='

/** 条件动作：满足条件时的处理方式 */
export type ConditionAction = 'REQUIRE' | 'SKIP'

/** 节点上的单个条件分支 */
export interface FlowCondition {
  field: string
  operator: ConditionOperator
  value: string | number
  action: ConditionAction
}

/** 审批链中的单个节点 */
export interface FlowNode {
  nodeId: string
  nodeCode: string
  nodeName: string
  order: number
  conditions: FlowCondition[]
}

/** 流程 Schema（设计器产出物，存入 flow_schema.schema_json） */
export interface FlowSchema {
  name: string
  code: string
  nodes: FlowNode[]
}

/** 从后端加载的审批节点库项（对应 approval_node 表） */
export interface ApprovalNodeItem {
  id: number
  nodeCode: string
  nodeName: string
  handlerType: string
  description: string
  sortOrder: number
}

/** 运算符中文映射（用于 UI 展示） */
export const OPERATOR_LABELS: Record<ConditionOperator, string> = {
  '>': '大于', '>=': '大于等于', '<': '小于', '<=': '小于等于', '==': '等于', '!=': '不等于',
}

/** 条件动作中文映射 */
export const ACTION_LABELS: Record<ConditionAction, string> = {
  REQUIRE: '必须审批', SKIP: '跳过此节点',
}

let _flowCounter = 0

/** 生成唯一节点 ID */
export function generateNodeId(): string {
  return `node_${Date.now()}_${++_flowCounter}`
}
