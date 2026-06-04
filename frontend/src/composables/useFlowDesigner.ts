/**
 * 流程设计器 —— 全局状态管理
 *
 * <p><b>管理的状态：</b>
 * <ul>
 *   <li>nodes — 审批链节点列表（响应式数组）</li>
 *   <li>nodeLibrary — 从后端加载的审批节点库</li>
 *   <li>activeNodeId — 当前选中的节点（用于条件编辑）</li>
 *   <li>schemaJson — 导出完整的流程 JSON Schema</li>
 * </ul></p>
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>addNode(item, index?) — 从节点库拖入新节点（每个 nodeCode 仅允许一次）</li>
 *   <li>removeNode(id) — 删除节点</li>
 *   <li>moveNode(from, to) — 拖拽排序</li>
 *   <li>addCondition / removeCondition / updateCondition — 条件分支编辑</li>
 * </ul></p>
 */
import { reactive, computed, toRefs, ref } from 'vue'
import type { FlowNode, FlowSchema, FlowCondition, ApprovalNodeItem } from '@/types/flow'
import { generateNodeId } from '@/types/flow'

interface FlowDesignerState {
  flowName: string
  flowCode: string
  nodes: FlowNode[]
  activeNodeId: string | null
}

const state = reactive<FlowDesignerState>({
  flowName: '请假审批流程', flowCode: 'leave_apply', nodes: [], activeNodeId: null,
})

const nodeLibrary = ref<ApprovalNodeItem[]>([])

export function useFlowDesigner() {
  /** 当前选中的节点对象 */
  const activeNode = computed<FlowNode | null>(() =>
    state.nodes.find(n => n.nodeId === state.activeNodeId) ?? null)

  /** 导出完整流程 JSON Schema */
  const schemaJson = computed<FlowSchema>(() => ({
    name: state.flowName, code: state.flowCode,
    nodes: state.nodes.map((n, i) => ({ ...n, order: i + 1 })),
  }))

  /** 设置节点库数据（从后端加载） */
  function setNodeLibrary(nodes: ApprovalNodeItem[]) { nodeLibrary.value = nodes }

  /**
   * 添加节点到审批链
   * @returns true=添加成功，false=该节点类型已使用
   */
  function addNode(nodeItem: ApprovalNodeItem, index?: number): boolean {
    if (state.nodes.some(n => n.nodeCode === nodeItem.nodeCode)) return false // 每种节点只能使用一次
    const node: FlowNode = { nodeId: generateNodeId(), nodeCode: nodeItem.nodeCode, nodeName: nodeItem.nodeName, order: 0, conditions: [] }
    if (index !== undefined && index >= 0 && index <= state.nodes.length) state.nodes.splice(index, 0, node)
    else state.nodes.push(node)
    state.activeNodeId = node.nodeId
    reorder()
    return true
  }

  function removeNode(nodeId: string) {
    const idx = state.nodes.findIndex(n => n.nodeId === nodeId)
    if (idx === -1) return
    state.nodes.splice(idx, 1)
    if (state.activeNodeId === nodeId)
      state.activeNodeId = state.nodes.length > 0 ? state.nodes[Math.min(idx, state.nodes.length - 1)].nodeId : null
    reorder()
  }

  function moveNode(from: number, to: number) {
    const [item] = state.nodes.splice(from, 1); state.nodes.splice(to, 0, item); reorder()
  }

  /** 重新编号节点顺序 */
  function reorder() { state.nodes.forEach((n, i) => n.order = i + 1) }

  function setActiveNode(nodeId: string | null) { state.activeNodeId = nodeId }

  /** 添加条件分支 */
  function addCondition(nodeId: string) {
    const n = state.nodes.find(n => n.nodeId === nodeId)
    if (n) n.conditions.push({ field: '', operator: '>', value: '', action: 'REQUIRE' })
  }

  function removeCondition(nodeId: string, ci: number) {
    const n = state.nodes.find(n => n.nodeId === nodeId); if (n) n.conditions.splice(ci, 1)
  }

  function updateCondition(nodeId: string, ci: number, patch: Partial<FlowCondition>) {
    const n = state.nodes.find(n => n.nodeId === nodeId); if (n) Object.assign(n.conditions[ci], patch)
  }

  /** 根据鼠标位置计算拖入时的插入索引 */
  function getInsertIndex(event: DragEvent): number {
    const list = (event.currentTarget as HTMLElement)?.closest('.flow-node-list')
    if (!list) return state.nodes.length
    const items = list.querySelectorAll('.flow-node-wrapper')
    for (let i = 0; i < items.length; i++) {
      if (event.clientY < items[i].getBoundingClientRect().top + items[i].getBoundingClientRect().height / 2) return i
    }
    return state.nodes.length
  }

  return {
    ...toRefs(state), activeNode, schemaJson, nodeLibrary,
    setNodeLibrary, addNode, removeNode, moveNode, setActiveNode,
    addCondition, removeCondition, updateCondition, getInsertIndex,
  }
}
