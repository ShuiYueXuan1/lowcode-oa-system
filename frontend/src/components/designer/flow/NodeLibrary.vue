<script setup lang="ts">
/**
 * NodeLibrary —— 左侧审批节点库面板
 * 展示从后端加载的可用审批节点，拖拽到画布添加
 * 已使用的节点置灰不可拖拽
 */
import { computed } from 'vue'
import { useFlowDesigner } from '@/composables/useFlowDesigner'
import type { ApprovalNodeItem } from '@/types/flow'

const designer = useFlowDesigner()

const usedCodes = computed(() => new Set(designer.nodes.value.map(n => n.nodeCode)))

function isUsed(node: ApprovalNodeItem) {
  return usedCodes.value.has(node.nodeCode)
}

function onDragStart(event: DragEvent, node: ApprovalNodeItem) {
  if (isUsed(node)) {
    event.preventDefault()
    return
  }
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/flow-node', JSON.stringify(node))
    event.dataTransfer.effectAllowed = 'copy'
  }
}
</script>

<template>
  <aside class="node-library">
    <div class="panel-header">
      <span class="panel-title">审批节点库</span>
      <span class="panel-hint">每个节点仅可使用一次</span>
    </div>

    <div class="node-list">
      <div
        v-for="node in designer.nodeLibrary.value"
        :key="node.nodeCode"
        class="node-item"
        :class="{ 'is-used': isUsed(node) }"
        :draggable="!isUsed(node)"
        @dragstart="onDragStart($event, node)"
      >
        <div class="node-icon">
          <el-icon :size="20"><Avatar /></el-icon>
        </div>
        <div class="node-info">
          <div class="node-name">{{ node.nodeName }}</div>
          <div class="node-code">{{ node.nodeCode }}</div>
        </div>
        <el-icon :size="14" class="drag-handle"><Rank /></el-icon>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.node-library {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 16px 16px 12px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: block;
}

.panel-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: block;
}

.node-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #fafafa;
  cursor: grab;
  user-select: none;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
    background: #ecf5ff;
    color: #409eff;

    .drag-handle { opacity: 1; }
  }

  &:active {
    cursor: grabbing;
    transform: scale(0.97);
  }

  &.is-used {
    opacity: 0.4;
    cursor: not-allowed;
    background: #f5f5f5;

    &:hover {
      border-color: #ebeef5;
      background: #f5f5f5;
    }
  }
}

.node-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  flex-shrink: 0;
}

.node-info {
  flex: 1;
  min-width: 0;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.node-code {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.drag-handle {
  color: #c0c4cc;
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}
</style>
