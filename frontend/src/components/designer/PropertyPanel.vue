<script setup lang="ts">
/**
 * PropertyPanel —— 右侧属性配置面板
 * 编辑选中字段的属性：基础信息、类型专属校验规则、选项配置
 */
import { computed } from 'vue'
import { useFormDesigner } from '@/composables/useFormDesigner'
import { FIELD_TYPE_LABELS, hasOptions } from '@/types/form'
import type { FieldType, ValidationRule } from '@/types/form'

const designer = useFormDesigner()
const field = computed(() => designer.activeField.value)

const fieldTypeOptions = computed(() =>
  Object.entries(FIELD_TYPE_LABELS).map(([value, label]) => ({ value, label })),
)

// ===== 规则辅助 =====

/** 获取或创建当前字段的非 required 规则（每种类型最多一条复合规则） */
function getRule(): ValidationRule {
  if (!field.value) return { message: '' }
  const rules = field.value.rules
  let rule = rules.find(r => !r.required)
  if (!rule) {
    rule = { message: '' }
    rules.push(rule)
  }
  return rule
}

function updateRule(patch: Partial<ValidationRule>) {
  if (!field.value) return
  const rule = getRule()
  Object.assign(rule, patch)
  // 触发响应式
  designer.updateField(field.value.key, { rules: [...field.value.rules] })
}

function onRequiredChange(val: boolean) {
  if (!field.value) return
  const rules = [...field.value.rules]
  if (val) {
    if (!rules.some(r => r.required)) {
      rules.unshift({ required: true, message: `请填写${field.value.label || '内容'}` })
    }
  } else {
    const idx = rules.findIndex(r => r.required)
    if (idx !== -1) rules.splice(idx, 1)
  }
  designer.updateField(field.value.key, { rules })
}

const isRequired = computed(() => field.value?.rules.some(r => r.required) ?? false)

// ===== 类型分组 =====

function isType(...types: FieldType[]) {
  return field.value && types.includes(field.value.type)
}

// ===== 基础属性编辑 =====

function onLabelChange(val: string) {
  if (field.value) designer.updateField(field.value.key, { label: val })
}

function onPlaceholderChange(val: string) {
  if (field.value) designer.updateField(field.value.key, { placeholder: val })
}

function onTypeChange(val: FieldType) {
  if (!field.value) return
  // 切换类型时清除不兼容的规则
  designer.updateField(field.value.key, { type: val, rules: [] })
  if (hasOptions(val)) {
    designer.updateField(field.value.key, { options: field.value.options ?? [] })
  }
}

// ===== 选项编辑 =====

function onOptionLabelChange(index: number, val: string) {
  if (!field.value || !field.value.options) return
  const options = [...field.value.options]
  options[index] = { ...options[index], label: val }
  designer.updateField(field.value.key, { options })
}

function onOptionValueChange(index: number, val: string) {
  if (!field.value || !field.value.options) return
  const options = [...field.value.options]
  options[index] = { ...options[index], value: val }
  designer.updateField(field.value.key, { options })
}

function onRemoveOption(index: number) {
  if (!field.value) return
  designer.removeOption(field.value.key, index)
}

function onAddOption() {
  if (!field.value) return
  designer.addOption(field.value.key)
}

const showOptions = computed(() => field.value && hasOptions(field.value.type))
</script>

<template>
  <aside class="property-panel">
    <div class="panel-header">
      <span class="panel-title">属性配置</span>
    </div>

    <div v-if="!field" class="panel-empty">
      <el-icon :size="36"><Setting /></el-icon>
      <p>请在画布中选择一个字段进行编辑</p>
    </div>

    <div v-else class="panel-form">
      <!-- ========== 基础信息 ========== -->
      <div class="form-section">
        <div class="section-title">基础信息</div>
        <div class="section-body">
          <div class="prop-row">
            <label class="prop-label">标签名</label>
            <el-input :model-value="field.label" size="small" placeholder="如：请假类型" @input="onLabelChange" />
          </div>
          <div class="prop-row">
            <label class="prop-label">字段 key</label>
            <el-input :model-value="field.key" size="small" disabled />
            <span class="prop-hint">自动生成，不可编辑</span>
          </div>
          <div class="prop-row">
            <label class="prop-label">字段类型</label>
            <el-select :model-value="field.type" size="small" style="width: 100%" @change="onTypeChange">
              <el-option v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </div>
          <div v-if="!isType('switch', 'radio', 'checkbox')" class="prop-row">
            <label class="prop-label">占位提示</label>
            <el-input :model-value="field.placeholder || ''" size="small" placeholder="输入占位文字" @input="onPlaceholderChange" />
          </div>
        </div>
      </div>

      <!-- ========== 校验规则 ========== -->
      <div v-if="!isType('switch')" class="form-section">
        <div class="section-title">校验规则</div>
        <div class="section-body">

          <!-- 通用：必填 -->
          <div class="prop-row">
            <el-checkbox :model-value="isRequired" @change="onRequiredChange">必填</el-checkbox>
          </div>

          <!-- input / textarea: 文本规则 -->
          <template v-if="isType('input', 'textarea')">
            <div class="rule-grid">
              <div class="rule-cell">
                <label class="rule-label">最小长度</label>
                <el-input-number
                  :model-value="getRule().minLength"
                  :min="0" :max="500" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ minLength: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">最大长度</label>
                <el-input-number
                  :model-value="getRule().maxLength"
                  :min="0" :max="5000" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ maxLength: v })"
                />
              </div>
            </div>
            <div class="prop-row">
              <label class="prop-label">正则表达式</label>
              <el-input
                :model-value="getRule().pattern || ''"
                size="small" placeholder="如：^1[3-9]\\d{9}$"
                @input="(v: string) => updateRule({ pattern: v || undefined })"
              />
            </div>
          </template>

          <!-- number: 数字规则 -->
          <template v-if="isType('number')">
            <div class="rule-grid">
              <div class="rule-cell">
                <label class="rule-label">最小值</label>
                <el-input-number
                  :model-value="getRule().min"
                  size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ min: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">最大值</label>
                <el-input-number
                  :model-value="getRule().max"
                  size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ max: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">倍数</label>
                <el-input-number
                  :model-value="getRule().multipleOf"
                  :min="0" :step="0.5" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ multipleOf: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">小数位</label>
                <el-input-number
                  :model-value="getRule().decimalPlaces"
                  :min="0" :max="10" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ decimalPlaces: v })"
                />
              </div>
            </div>
          </template>

          <!-- date: 日期规则 -->
          <template v-if="isType('date')">
            <div class="prop-row">
              <label class="prop-label">最早日期</label>
              <el-input
                :model-value="getRule().minDate || ''"
                size="small" placeholder="today / today+7 / 2025-01-01"
                @input="(v: string) => updateRule({ minDate: v || undefined })"
              />
              <span class="prop-hint">支持相对值：today, today+7, today-3</span>
            </div>
            <div class="prop-row">
              <label class="prop-label">最晚日期</label>
              <el-input
                :model-value="getRule().maxDate || ''"
                size="small" placeholder="today+30 / 2025-12-31"
                @input="(v: string) => updateRule({ maxDate: v || undefined })"
              />
            </div>
            <div class="prop-row">
              <el-checkbox
                :model-value="getRule().disablePast"
                @change="(v: boolean) => updateRule({ disablePast: v || undefined })"
              >
                禁止选择过去日期
              </el-checkbox>
            </div>
          </template>

          <!-- select: 选择规则 -->
          <template v-if="isType('select')">
            <div class="prop-row">
              <el-checkbox
                :model-value="field.multiple"
                @change="(v: boolean) => designer.updateField(field!.key, { multiple: v })"
              >
                多选
              </el-checkbox>
            </div>
            <div v-if="field.multiple" class="rule-grid">
              <div class="rule-cell">
                <label class="rule-label">最少选几项</label>
                <el-input-number
                  :model-value="getRule().minSelect"
                  :min="0" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ minSelect: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">最多选几项</label>
                <el-input-number
                  :model-value="getRule().maxSelect"
                  :min="0" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ maxSelect: v })"
                />
              </div>
            </div>
          </template>

          <!-- checkbox: 勾选规则 -->
          <template v-if="isType('checkbox')">
            <div class="rule-grid">
              <div class="rule-cell">
                <label class="rule-label">最少勾选</label>
                <el-input-number
                  :model-value="getRule().minSelect"
                  :min="0" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ minSelect: v })"
                />
              </div>
              <div class="rule-cell">
                <label class="rule-label">最多勾选</label>
                <el-input-number
                  :model-value="getRule().maxSelect"
                  :min="0" size="small" controls-position="right"
                  @update:model-value="(v: number | undefined) => updateRule({ maxSelect: v })"
                />
              </div>
            </div>
          </template>

          <!-- radio: 仅必填，无额外规则 --><!-- switch: 无规则区域 -->
        </div>
      </div>

      <!-- ========== 选项列表 ========== -->
      <div v-if="showOptions" class="form-section">
        <div class="section-title">
          <span>选项列表</span>
          <el-button size="small" text type="primary" @click="onAddOption()">+ 添加选项</el-button>
        </div>
        <div class="section-body">
          <div v-for="(opt, idx) in (field.options ?? [])" :key="idx" class="option-row">
            <el-input :model-value="opt.label" size="small" placeholder="显示文字" style="width: 100px"
              @input="(val: string) => onOptionLabelChange(idx, val)" />
            <el-input :model-value="opt.value" size="small" placeholder="值" style="width: 80px"
              @input="(val: string) => onOptionValueChange(idx, val)" />
            <el-button size="small" text type="danger" @click="onRemoveOption(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div v-if="!field.options?.length" class="option-empty">暂无选项，请点击"添加选项"</div>
        </div>
      </div>

      <!-- ========== 操作 ========== -->
      <div class="form-section">
        <div class="section-title">操作</div>
        <div class="section-body">
          <el-button type="danger" plain size="small" @click="designer.removeField(field.key)">
            <el-icon><Delete /></el-icon>
            删除此字段
          </el-button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.property-panel {
  width: 320px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header { padding: 16px; border-bottom: 1px solid #ebeef5; }
.panel-title { font-size: 14px; font-weight: 600; color: #303133; }

.panel-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  justify-content: center; color: #c0c4cc; gap: 12px; font-size: 13px;
}

.panel-form { flex: 1; overflow-y: auto; padding: 0 0 24px; }

.form-section {
  padding: 0 16px; margin-top: 16px;
  &:first-child { margin-top: 0; }
}

.section-title {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; font-weight: 600; color: #303133;
  padding: 8px 0; border-bottom: 1px solid #ebeef5; margin-bottom: 8px;
}

.section-body { display: flex; flex-direction: column; gap: 10px; }

.prop-row { display: flex; flex-direction: column; gap: 4px; }
.prop-label { font-size: 12px; color: #909399; }
.prop-hint { font-size: 11px; color: #c0c4cc; }

.rule-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.rule-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rule-label {
  font-size: 11px;
  color: #909399;
}

.option-row { display: flex; align-items: center; gap: 6px; }
.option-empty { font-size: 12px; color: #c0c4cc; padding: 8px 0; }
</style>
