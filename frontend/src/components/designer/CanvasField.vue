<script setup lang="ts">
/**
 * CanvasField —— 画布中的单个字段预览卡片
 * 根据字段类型渲染对应的视觉预览
 */
import { computed } from 'vue'
import { FIELD_TYPE_LABELS } from '@/types/form'
import type { FormField } from '@/types/form'
import { useFormDesigner } from '@/composables/useFormDesigner'

const props = defineProps<{ field: FormField }>()
const designer = useFormDesigner()

const displayLabel = computed(() => props.field.label || '(未命名字段)')
const typeLabel = computed(() => FIELD_TYPE_LABELS[props.field.type] || props.field.type)
const rulesSummary = computed(() => {
  const parts: string[] = []
  if (props.field.rules.some((r) => r.required)) parts.push('必填')
  if (props.field.rules.some((r) => r.minLength)) parts.push('有长度限制')
  if (props.field.rules.some((r) => r.pattern)) parts.push('有正则校验')
  return parts.join(' / ')
})

function onDelete(event: Event) {
  event.stopPropagation()
  designer.removeField(props.field.key)
}
</script>

<template>
  <div class="canvas-field">
    <div class="field-preview">
      <!-- 模拟标签 + 输入区域 -->
      <div class="field-meta">
        <span class="field-label">{{ displayLabel }}</span>
        <span v-if="rulesSummary" class="field-rules">{{ rulesSummary }}</span>
      </div>
      <div class="field-control">
        <!-- 输入框预览 -->
        <template v-if="field.type === 'input'">
          <div class="mock-input">{{ field.placeholder || '请输入内容' }}</div>
        </template>

        <!-- 文本域预览 -->
        <template v-else-if="field.type === 'textarea'">
          <div class="mock-textarea">{{ field.placeholder || '请输入文本' }}</div>
        </template>

        <!-- 数字输入预览 -->
        <template v-else-if="field.type === 'number'">
          <div class="mock-input mock-number">{{ field.placeholder || '0' }}</div>
        </template>

        <!-- 下拉选择预览 -->
        <template v-else-if="field.type === 'select'">
          <div class="mock-select">
            <span class="mock-select-text">{{ field.placeholder || '请选择' }}</span>
            <el-icon :size="14"><ArrowDown /></el-icon>
          </div>
        </template>

        <!-- 日期选择预览 -->
        <template v-else-if="field.type === 'date'">
          <div class="mock-select">
            <span class="mock-select-text">{{ field.placeholder || '请选择日期' }}</span>
            <el-icon :size="14"><Calendar /></el-icon>
          </div>
        </template>

        <!-- 开关预览 -->
        <template v-else-if="field.type === 'switch'">
          <div class="mock-switch"></div>
        </template>

        <!-- 单选框预览 -->
        <template v-else-if="field.type === 'radio'">
          <div class="mock-options">
            <span
              v-for="(opt, i) in (field.options?.length ? field.options : [{ label: '选项1', value: 1 }])"
              :key="i"
              class="mock-radio"
            >
              <span class="radio-dot"></span>
              {{ opt.label }}
            </span>
          </div>
        </template>

        <!-- 复选框预览 -->
        <template v-else-if="field.type === 'checkbox'">
          <div class="mock-options">
            <span
              v-for="(opt, i) in (field.options?.length ? field.options : [{ label: '选项1', value: 1 }])"
              :key="i"
              class="mock-checkbox"
            >
              <span class="checkbox-box"></span>
              {{ opt.label }}
            </span>
          </div>
        </template>
      </div>
    </div>

    <div class="field-actions">
      <span class="field-type-badge">{{ typeLabel }}</span>
      <el-button
        :size="'small'"
        :icon="'Delete'"
        text
        type="danger"
        class="field-delete-btn"
        @click="onDelete"
        title="删除字段"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.canvas-field {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
}

.field-preview {
  flex: 1;
  min-width: 0;
}

.field-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.field-rules {
  font-size: 11px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 1px 6px;
  border-radius: 3px;
}

.field-control {
  max-width: 360px;
}

// ---- Mock UI elements ----
.mock-input {
  height: 30px;
  line-height: 30px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  color: #c0c4cc;
  background: #f5f7fa;
}

.mock-textarea {
  height: 52px;
  line-height: 1.5;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  color: #c0c4cc;
  background: #f5f7fa;
}

.mock-number {
  width: 140px;
}

.mock-select {
  height: 30px;
  line-height: 30px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 200px;
}

.mock-select-text {
  font-size: 12px;
  color: #c0c4cc;
}

.mock-switch {
  width: 40px;
  height: 20px;
  border-radius: 10px;
  background: #c0c4cc;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #fff;
  }
}

.mock-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.mock-radio,
.mock-checkbox {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.radio-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
}

.checkbox-box {
  width: 14px;
  height: 14px;
  border-radius: 2px;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
}

// ---- Actions ----
.field-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.field-type-badge {
  font-size: 11px;
  color: #909399;
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

.field-delete-btn {
  opacity: 0;
  transition: opacity 0.15s;

  .canvas-field-item:hover &,
  .canvas-field-item.is-active & {
    opacity: 1;
  }
}
</style>
