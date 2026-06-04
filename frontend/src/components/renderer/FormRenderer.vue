<script setup lang="ts">
/**
 * FormRenderer —— 动态表单渲染引擎
 * 接收 FormSchema JSON，动态渲染出可填写的表单
 * 支持全部 8 种字段类型，自动生成校验规则
 */
import { computed, reactive, watch, ref } from 'vue'
import type { FormSchema, FormField, ValidationRule, SelectOption } from '@/types/form'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  formSchema: FormSchema
  initialData?: Record<string, unknown>
  readonly?: boolean
}>()

const emit = defineEmits<{
  submit: [data: Record<string, unknown>]
  cancel: []
}>()

const formRef = ref()

// ----- 动态表单数据模型 -----
const formModel = reactive<Record<string, unknown>>({})

// 根据 schema.fields 初始化/重置 formModel
function buildModel() {
  for (const key of Object.keys(formModel)) {
    delete formModel[key]
  }
  for (const field of props.formSchema.fields) {
    if (props.initialData && props.initialData[field.key] !== undefined) {
      formModel[field.key] = props.initialData[field.key]
    } else if (field.defaultValue !== undefined) {
      formModel[field.key] = field.defaultValue
    } else if (field.type === 'select' && field.multiple) {
      formModel[field.key] = []
    } else {
      formModel[field.key] = getDefaultValue(field.type)
    }
  }
}

function getDefaultValue(type: string): unknown {
  switch (type) {
    case 'switch':   return false
    case 'checkbox': return []
    case 'number':   return undefined
    default:         return ''
  }
}

buildModel()

watch(() => props.formSchema.fields, buildModel, { deep: true })

// ----- 校验规则转换 -----
function convertRules(field: FormField): Record<string, unknown>[] {
  const result: Record<string, unknown>[] = []
  const trigger = getTrigger(field.type)

  for (const rule of field.rules) {
    const r: Record<string, unknown> = { trigger }

    if (rule.required) {
      r.required = true
    }
    if (rule.min !== undefined) {
      r.min = rule.min
    }
    if (rule.max !== undefined) {
      r.max = rule.max
    }
    if (rule.minLength !== undefined) {
      r.min = rule.minLength
    }
    if (rule.maxLength !== undefined) {
      r.max = rule.maxLength
    }
    if (rule.pattern) {
      try { r.pattern = new RegExp(rule.pattern) } catch { r.pattern = rule.pattern }
    }
    if (rule.message) {
      r.message = rule.message
    } else {
      r.message = buildDefaultMessage(rule, field.label)
    }

    // 数字：倍数校验
    if (rule.multipleOf && rule.multipleOf > 0) {
      const multipleOf = rule.multipleOf
      r.validator = (_r: unknown, value: number, cb: (e?: Error) => void) => {
        if (value !== undefined && value !== null && value !== 0 && value % multipleOf !== 0) {
          cb(new Error(`${field.label}必须是${multipleOf}的倍数`))
        } else { cb() }
      }
    }

    // 数字：小数位限制
    if (rule.decimalPlaces !== undefined && rule.decimalPlaces >= 0) {
      const dp = rule.decimalPlaces
      r.validator = (_r: unknown, value: number, cb: (e?: Error) => void) => {
        if (value !== undefined && value !== null) {
          const s = String(value)
          const dot = s.indexOf('.')
          if (dot !== -1 && s.length - dot - 1 > dp) {
            cb(new Error(`${field.label}最多${dp}位小数`))
            return
          }
        }
        cb()
      }
    }

    // 日期：最早/最晚（支持相对日期）
    if (rule.minDate || rule.maxDate || rule.disablePast) {
      r.validator = (_r: unknown, value: string, cb: (e?: Error) => void) => {
        if (!value) return cb()
        const d = new Date(value)
        if (rule.disablePast) {
          const today = new Date(); today.setHours(0, 0, 0, 0)
          if (d < today) { cb(new Error(`${field.label}不能选择过去日期`)); return }
        }
        if (rule.minDate) {
          const min = resolveDate(rule.minDate)
          if (min && d < min) { cb(new Error(`${field.label}不能早于${rule.minDate}`)); return }
        }
        if (rule.maxDate) {
          const max = resolveDate(rule.maxDate)
          if (max && d > max) { cb(new Error(`${field.label}不能晚于${rule.maxDate}`)); return }
        }
        cb()
      }
    }

    // 选择类：最少/最多选中
    if (rule.minSelect !== undefined || rule.maxSelect !== undefined) {
      const minS = rule.minSelect
      const maxS = rule.maxSelect
      r.validator = (_r: unknown, value: unknown[], cb: (e?: Error) => void) => {
        const len = Array.isArray(value) ? value.length : 0
        if (minS && len < minS) { cb(new Error(`至少选择${minS}项`)); return }
        if (maxS && len > maxS) { cb(new Error(`最多选择${maxS}项`)); return }
        cb()
      }
    }

    result.push(r)
  }
  return result
}

/** 解析相对日期 */
function resolveDate(expr: string): Date | null {
  if (!expr) return null
  if (/^\d{4}-\d{2}-\d{2}$/.test(expr)) return new Date(expr)
  const m = expr.match(/^today([+-]\d+)?$/)
  if (m) {
    const d = new Date(); d.setHours(0, 0, 0, 0)
    if (m[1]) d.setDate(d.getDate() + parseInt(m[1]))
    return d
  }
  return null
}

function getTrigger(type: string): string {
  switch (type) {
    case 'select':
    case 'date':
    case 'radio':
    case 'checkbox':
    case 'switch':
      return 'change'
    default:
      return 'blur'
  }
}

function buildDefaultMessage(rule: ValidationRule, label: string): string {
  if (rule.required) return `请选择${label}` === `请选择${label}` ? `请输入${label}` : `请输入${label}`
  if (rule.minLength) return `${label}不能少于${rule.minLength}个字符`
  if (rule.maxLength) return `${label}不能超过${rule.maxLength}个字符`
  if (rule.pattern)  return `${label}格式不正确`
  return ''
}

const formRules = computed(() => {
  const rules: Record<string, Record<string, unknown>[]> = {}
  for (const field of props.formSchema.fields) {
    const converted = convertRules(field)
    if (converted.length > 0) {
      rules[field.key] = converted
    }
  }
  return rules
})

// ----- 提交 -----
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit', { ...formModel })
  } catch {
    ElMessage.warning('请检查表单中的错误后再提交')
  }
}

function handleReset() {
  buildModel()
  formRef.value?.clearValidate()
}

// ----- 暴露给父组件的方法 -----
defineExpose({ validate: () => formRef.value?.validate(), reset: handleReset, getData: () => ({ ...formModel }) })

// ----- UI 辅助 -----
function getPlaceholder(field: FormField): string {
  if (field.placeholder) return field.placeholder
  const map: Record<string, string> = {
    input: '请输入', textarea: '请输入', number: '请输入',
    select: '请选择', date: '请选择日期',
  }
  return map[field.type] ?? ''
}
</script>

<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      :model="formModel"
      :rules="formRules"
      :label-width="formSchema.labelWidth + 'px'"
      label-position="right"
    >
      <template v-for="field in formSchema.fields" :key="field.key">
        <!-- ======== 输入框 ======== -->
        <el-form-item
          v-if="field.type === 'input'"
          :label="field.label"
          :prop="field.key"
        >
          <el-input
            v-model="formModel[field.key]"
            :placeholder="getPlaceholder(field)"
            :disabled="field.disabled"
            clearable
          />
        </el-form-item>

        <!-- ======== 文本域 ======== -->
        <el-form-item
          v-else-if="field.type === 'textarea'"
          :label="field.label"
          :prop="field.key"
        >
          <el-input
            v-model="formModel[field.key]"
            type="textarea"
            :placeholder="getPlaceholder(field)"
            :disabled="field.disabled"
            :rows="4"
          />
        </el-form-item>

        <!-- ======== 数字输入 ======== -->
        <el-form-item
          v-else-if="field.type === 'number'"
          :label="field.label"
          :prop="field.key"
        >
          <el-input-number
            v-model="formModel[field.key]"
            :placeholder="getPlaceholder(field)"
            :disabled="field.disabled"
            :min="0"
            :precision="0.5"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>

        <!-- ======== 下拉选择 ======== -->
        <el-form-item
          v-else-if="field.type === 'select'"
          :label="field.label"
          :prop="field.key"
        >
          <el-select
            v-model="formModel[field.key]"
            :placeholder="getPlaceholder(field)"
            :disabled="field.disabled"
            :multiple="field.multiple"
            :collapse-tags="field.multiple"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="opt in (field.options ?? [])"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- ======== 日期选择 ======== -->
        <el-form-item
          v-else-if="field.type === 'date'"
          :label="field.label"
          :prop="field.key"
        >
          <el-date-picker
            v-model="formModel[field.key]"
            type="date"
            :placeholder="getPlaceholder(field)"
            :disabled="field.disabled"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <!-- ======== 开关 ======== -->
        <el-form-item
          v-else-if="field.type === 'switch'"
          :label="field.label"
          :prop="field.key"
        >
          <el-switch
            v-model="formModel[field.key]"
            :disabled="field.disabled"
            active-text="是"
            inactive-text="否"
          />
        </el-form-item>

        <!-- ======== 单选框 ======== -->
        <el-form-item
          v-else-if="field.type === 'radio'"
          :label="field.label"
          :prop="field.key"
        >
          <el-radio-group
            v-model="formModel[field.key]"
            :disabled="field.disabled"
          >
            <el-radio
              v-for="opt in (field.options ?? [])"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- ======== 复选框 ======== -->
        <el-form-item
          v-else-if="field.type === 'checkbox'"
          :label="field.label"
          :prop="field.key"
        >
          <el-checkbox-group
            v-model="formModel[field.key]"
            :disabled="field.disabled"
          >
            <el-checkbox
              v-for="opt in (field.options ?? [])"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </template>
    </el-form>

    <!-- 操作按钮 -->
    <div v-if="!readonly" class="form-actions">
      <el-button @click="handleReset">重置</el-button>
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="handleSubmit">提交</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.form-renderer {
  padding: 0;
}

.el-form {
  max-width: 600px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
  margin-top: 20px;
}
</style>
