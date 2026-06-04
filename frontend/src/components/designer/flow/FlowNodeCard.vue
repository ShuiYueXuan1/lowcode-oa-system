<script setup lang="ts">
/**
 * FlowNodeCard —— 审批链中的单个节点卡片
 * 显示节点信息、条件摘要，支持选中/删除/编辑条件
 */
import { computed } from 'vue'
import type { FlowNode } from '@/types/flow'
import { OPERATOR_LABELS, ACTION_LABELS } from '@/types/flow'

const props = defineProps<{
  node: FlowNode
  index: number
  isActive: boolean
}>()

const emit = defineEmits<{
  click: []
  delete: []
  editConditions: []
}>()

const conditionSummary = computed(() => {
  if (props.node.conditions.length === 0) return '无条件'
  return props.node.conditions
    .map((c) => {
      const op = OPERATOR_LABELS[c.operator] || c.operator
      const act = ACTION_LABELS[c.action]
      return `当 ${c.field || '?'} ${op} ${c.value} 时${act}`
    })
    .join('；')
})
</script>

<template>
  <div
    class="flow-node-card"
    :class="{ 'is-active': isActive }"
    @click="emit('click')"
  >
    <!-- 拖拽手柄 + 序号 -->
    <div class="card-left">
      <el-icon :size="16" class="node-drag-handle"><Rank /></el-icon>
      <span class="node-order">{{ index + 1 }}</span>
    </div>

    <!-- 节点主体 -->
    <div class="card-body">
      <div class="node-title-row">
        <span class="node-name">{{ node.nodeName }}</span>
        <el-tag size="small" type="info">{{ node.nodeCode }}</el-tag>
      </div>
      <div class="node-condition">
        <el-icon :size="13"><SetUp /></el-icon>
        <span>{{ conditionSummary }}</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="card-actions">
      <el-button
        size="small"
        text
        type="warning"
        @click.stop="emit('editConditions')"
        title="编辑条件"
      >
        <el-icon><SetUp /></el-icon>
      </el-button>
      <el-button
        size="small"
        text
        type="danger"
        @click.stop="emit('delete')"
        title="删除节点"
      >
        <el-icon><Delete /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.flow-node-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 380px;
  padding: 14px 16px 14px 10px;
  background: #fff;
  border-radius: 10px;
  border: 2px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  &:hover {
    border-color: #c6e2ff;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  &.is-active {
    border-color: #409eff;
    box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
  }
}

.card-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  width: 32px;
}

.node-drag-handle {
  color: #c0c4cc;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.node-order {
  font-size: 14px;
  font-weight: 700;
  color: #409eff;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.node-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.node-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.node-condition {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;

  .flow-node-card:hover &,
  .flow-node-card.is-active & {
    opacity: 1;
  }
}
</style>
