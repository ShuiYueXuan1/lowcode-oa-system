<script setup lang="ts">
/**
 * ComponentPanel —— 左侧组件库面板
 * 展示可拖拽的字段类型列表，拖拽到中间画布即可添加字段
 */
import { COMPONENT_LIBRARY } from '@/types/form'
import type { ComponentItem, FieldType } from '@/types/form'

function onDragStart(event: DragEvent, item: ComponentItem) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/field-type', item.type)
    event.dataTransfer.effectAllowed = 'copy'
  }
}
</script>

<template>
  <aside class="component-panel">
    <div class="panel-header">
      <span class="panel-title">组件库</span>
      <span class="panel-hint">拖拽到画布添加</span>
    </div>

    <div class="component-list">
      <div
        v-for="item in COMPONENT_LIBRARY"
        :key="item.type"
        class="component-item"
        :draggable="true"
        @dragstart="onDragStart($event, item)"
      >
        <el-icon :size="18" class="item-icon">
          <component :is="item.icon" />
        </el-icon>
        <span class="item-label">{{ item.label }}</span>
        <el-icon :size="14" class="drag-handle"><Rank /></el-icon>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.component-panel {
  width: 220px;
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

.component-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
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

    .drag-handle {
      opacity: 1;
    }
  }

  &:active {
    cursor: grabbing;
    transform: scale(0.97);
  }
}

.item-icon {
  color: #606266;
  flex-shrink: 0;
}

.item-label {
  flex: 1;
  font-size: 13px;
  color: #606266;
}

.drag-handle {
  color: #c0c4cc;
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}
</style>
