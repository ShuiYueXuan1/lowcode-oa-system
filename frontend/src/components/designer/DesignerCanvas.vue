<script setup lang="ts">
/**
 * DesignerCanvas —— 中间画布
 * 接收从组件库拖入的字段，使用 vuedraggable 支持排序
 */
import { useFormDesigner } from '@/composables/useFormDesigner'
import CanvasField from './CanvasField.vue'
import draggable from 'vuedraggable'
import type { FieldType } from '@/types/form'

const designer = useFormDesigner()

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const type = event.dataTransfer?.getData('application/field-type') as FieldType | undefined
  if (!type) return

  const insertIndex = designer.getInsertIndex(event)
  designer.addField(type, insertIndex)
}

function onFieldClick(key: string) {
  designer.setActiveField(key)
}
</script>

<template>
  <main
    class="designer-canvas"
    @dragover="onDragOver"
    @drop="onDrop"
  >
    <div class="canvas-header">
      <span class="canvas-title">表单画布</span>
      <span class="canvas-info">{{ designer.fields.value.length }} 个字段</span>
    </div>

    <div class="canvas-body">
      <!-- 空状态 -->
      <div v-if="designer.fields.value.length === 0" class="canvas-empty">
        <el-icon :size="48" class="empty-icon"><Upload /></el-icon>
        <p class="empty-title">从左侧拖拽组件到此处</p>
        <p class="empty-desc">支持输入框、下拉框、日期选择等 8 种字段类型</p>
      </div>

      <!-- 字段列表（支持拖拽排序） -->
      <draggable
        v-model="designer.fields.value"
        item-key="key"
        class="canvas-field-list"
        :animation="200"
        ghost-class="field-ghost"
        handle=".field-drag-handle"
      >
        <template #item="{ element: field }">
          <div
            class="canvas-field-item"
            :class="{ 'is-active': designer.activeKey.value === field.key }"
            @click="onFieldClick(field.key)"
          >
            <el-icon :size="16" class="field-drag-handle"><Rank /></el-icon>
            <CanvasField :field="field" />
          </div>
        </template>
      </draggable>
    </div>
  </main>
</template>

<style scoped lang="scss">
.designer-canvas {
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
  padding: 0 24px 24px;
}

.canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fff;
}

.empty-icon {
  color: #c0c4cc;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 14px;
  color: #909399;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 12px;
  color: #c0c4cc;
  margin: 0;
}

.canvas-field-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 60px;
}

.canvas-field-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 8px;
  background: #fff;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: #c6e2ff;
  }

  &.is-active {
    border-color: #409eff;
    box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
  }
}

.field-drag-handle {
  color: #c0c4cc;
  cursor: grab;
  flex-shrink: 0;

  &:active {
    cursor: grabbing;
  }
}

.field-ghost {
  opacity: 0.4;
  background: #ecf5ff;
  border: 2px dashed #409eff;
  border-radius: 6px;
}
</style>
