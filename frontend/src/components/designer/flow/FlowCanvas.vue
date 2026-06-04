<script setup lang="ts">
/**
 * FlowCanvas —— 流程链式画布
 * 垂直排列审批节点，支持拖拽排序，节点间用连线连接
 */
import { useFlowDesigner } from '@/composables/useFlowDesigner'
import FlowNodeCard from './FlowNodeCard.vue'
import draggable from 'vuedraggable'
import type { ApprovalNodeItem } from '@/types/flow'
import { ElMessage } from 'element-plus'

const designer = useFlowDesigner()

const emit = defineEmits<{
  'editConditions': []
}>()

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('application/flow-node')
  if (!raw) return
  const nodeItem: ApprovalNodeItem = JSON.parse(raw)
  const insertIndex = designer.getInsertIndex(event)
  const added = designer.addNode(nodeItem, insertIndex)
  if (!added) {
    ElMessage.warning(`节点"${nodeItem.nodeName}"已使用，每个节点仅可添加一次`)
  }
}

function onNodeClick(nodeId: string) {
  designer.setActiveNode(nodeId)
}

function onDeleteNode(nodeId: string) {
  designer.removeNode(nodeId)
}
</script>

<template>
  <main
    class="flow-canvas"
    @dragover="onDragOver"
    @drop="onDrop"
  >
    <div class="canvas-header">
      <span class="canvas-title">审批链</span>
      <span class="canvas-info">{{ designer.nodes.value.length }} 个节点</span>
    </div>

    <div class="canvas-body">
      <!-- 空状态 -->
      <div v-if="designer.nodes.value.length === 0" class="canvas-empty">
        <el-icon :size="56"><Connection /></el-icon>
        <p class="empty-title">从左侧拖拽审批节点到此处</p>
        <p class="empty-desc">构建审批链，支持条件分支配置</p>
      </div>

      <!-- 节点链 -->
      <div v-else class="flow-chain">
        <!-- 开始标记 -->
        <div class="chain-start">
          <div class="start-dot"></div>
          <span class="start-label">申请人提交</span>
        </div>

        <div class="chain-line"></div>

        <!-- 可拖拽排序的节点列表 -->
        <draggable
          v-model="designer.nodes.value"
          item-key="nodeId"
          class="flow-node-list"
          :animation="200"
          ghost-class="node-ghost"
          handle=".node-drag-handle"
        >
          <template #item="{ element: node, index }">
            <div class="flow-node-wrapper">
              <FlowNodeCard
                :node="node"
                :index="index"
                :is-active="designer.activeNodeId.value === node.nodeId"
                @click="onNodeClick(node.nodeId)"
                @delete="onDeleteNode(node.nodeId)"
                @edit-conditions="emit('editConditions')"
              />
              <!-- 节点之间的连线和条件区域 -->
              <div v-if="index < designer.nodes.value.length - 1" class="node-connector">
                <div class="connector-line"></div>
                <div class="connector-arrow">
                  <el-icon :size="14"><ArrowDown /></el-icon>
                </div>
              </div>
            </div>
          </template>
        </draggable>

        <!-- 连接最后一个节点到结束 -->
        <div class="chain-line"></div>

        <!-- 结束标记 -->
        <div class="chain-end">
          <div class="end-dot"></div>
          <span class="end-label">审批完成</span>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped lang="scss">
.flow-canvas {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  overflow: hidden;
}

.canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px 12px;
}

.canvas-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.canvas-info {
  font-size: 12px;
  color: #909399;
  background: #e4e7ed;
  padding: 2px 10px;
  border-radius: 10px;
}

.canvas-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 32px;
  display: flex;
  justify-content: center;
}

.canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 480px;
  margin-top: 60px;
  height: 260px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #c0c4cc;
}

.empty-title {
  font-size: 14px;
  color: #909399;
  margin: 12px 0 6px;
}

.empty-desc {
  font-size: 12px;
  color: #c0c4cc;
  margin: 0;
}

// ---- 链式布局 ----
.flow-chain {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.chain-start,
.chain-end {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.start-dot,
.end-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #67c23a;
}

.end-dot {
  background: #409eff;
}

.start-label,
.end-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.chain-line {
  width: 2px;
  height: 28px;
  background: #c0c4cc;
}

.flow-node-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.flow-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 4px 0;
}

.connector-line {
  width: 2px;
  height: 24px;
  background: #c0c4cc;
}

.connector-arrow {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #c0c4cc;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.node-ghost {
  opacity: 0.4;

  .flow-node-card {
    background: #ecf5ff;
    border: 2px dashed #409eff;
  }
}
</style>
