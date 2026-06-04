/**
 * 表单设计器 —— 全局状态管理
 *
 * <p><b>管理的状态：</b>
 * <ul>
 *   <li>fields — 画布中的字段列表（响应式数组）</li>
 *   <li>activeKey — 当前选中的字段 key（用于属性面板联动）</li>
 *   <li>schemaJson — 导出完整的表单 JSON Schema</li>
 * </ul></p>
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>addField(type, index?) — 从组件库拖入新字段</li>
 *   <li>removeField(key) — 删除字段</li>
 *   <li>moveField(from, to) — 拖拽排序</li>
 *   <li>updateField(key, patch) — 属性面板修改字段属性</li>
 *   <li>loadSchema(schema) — 从 JSON 恢复设计器状态</li>
 * </ul></p>
 */
import { reactive, computed, toRefs } from 'vue'
import type { FormField, FormSchema, FieldType } from '@/types/form'
import { generateFieldKey, hasOptions } from '@/types/form'

interface DesignerState {
  formName: string
  formCode: string
  labelWidth: number
  fields: FormField[]
  activeKey: string | null
}

/** 根据字段类型创建默认字段 */
function createDefaultField(type: FieldType): FormField {
  const field: FormField = { key: generateFieldKey(), type, label: '', placeholder: '', rules: [] }
  if (hasOptions(type)) field.options = []
  return field
}

const state = reactive<DesignerState>({
  formName: '请假申请表', formCode: 'leave_apply', labelWidth: 100, fields: [], activeKey: null,
})

export function useFormDesigner() {
  /** 当前选中的字段对象（用于属性面板编辑） */
  const activeField = computed<FormField | null>(() =>
    state.fields.find(f => f.key === state.activeKey) ?? null)

  /** 导出完整表单 JSON Schema */
  const schemaJson = computed<FormSchema>(() => ({
    name: state.formName, code: state.formCode, labelWidth: state.labelWidth,
    fields: state.fields.map(({ key, type, label, placeholder, defaultValue, rules, options, disabled, multiple }) => {
      const f: FormField = { key, type, label, placeholder, defaultValue, rules, disabled }
      if (options) f.options = options
      if (multiple) f.multiple = multiple
      return f
    }),
  }))

  /** 从组件库添加字段到画布 */
  function addField(type: FieldType, index?: number) {
    const field = createDefaultField(type)
    if (index !== undefined && index >= 0 && index <= state.fields.length)
      state.fields.splice(index, 0, field)
    else state.fields.push(field)
    state.activeKey = field.key
  }

  /** 从画布删除字段 */
  function removeField(key: string) {
    const idx = state.fields.findIndex(f => f.key === key)
    if (idx === -1) return
    state.fields.splice(idx, 1)
    if (state.activeKey === key)
      state.activeKey = state.fields.length > 0 ? state.fields[Math.min(idx, state.fields.length - 1)].key : null
  }

  /** 属性面板修改字段 */
  function updateField(key: string, patch: Partial<FormField>) {
    const f = state.fields.find(f => f.key === key)
    if (f) Object.assign(f, patch)
  }

  function addRule(fieldKey: string) {
    const f = state.fields.find(f => f.key === fieldKey)
    if (f) f.rules.push({ message: '' })
  }

  function removeRule(fieldKey: string, idx: number) {
    const f = state.fields.find(f => f.key === fieldKey)
    if (f) f.rules.splice(idx, 1)
  }

  function addOption(fieldKey: string) {
    const f = state.fields.find(f => f.key === fieldKey)
    if (f && f.options) f.options.push({ label: '', value: '' })
  }

  function removeOption(fieldKey: string, idx: number) {
    const f = state.fields.find(f => f.key === fieldKey)
    if (f && f.options) f.options.splice(idx, 1)
  }

  /** 拖拽排序 */
  function moveField(from: number, to: number) {
    const [item] = state.fields.splice(from, 1)
    state.fields.splice(to, 0, item)
  }

  function setActiveField(key: string | null) { state.activeKey = key }
  function clearActiveField() { state.activeKey = null }

  /** 从 JSON Schema 加载设计器状态（用于回显编辑） */
  function loadSchema(schema: FormSchema) {
    state.formName = schema.name; state.formCode = schema.code
    state.labelWidth = schema.labelWidth ?? 100
    state.fields = schema.fields.map(f => ({ ...f }))
    state.activeKey = null
  }

  /** 根据鼠标位置计算拖入时的插入索引 */
  function getInsertIndex(event: DragEvent): number {
    const canvas = (event.currentTarget as HTMLElement)?.closest('.canvas-field-list')
    if (!canvas) return state.fields.length
    const items = canvas.querySelectorAll('.canvas-field-item')
    const mouseY = event.clientY
    for (let i = 0; i < items.length; i++) {
      const rect = items[i].getBoundingClientRect()
      if (mouseY < rect.top + rect.height / 2) return i
    }
    return state.fields.length
  }

  return {
    ...toRefs(state), activeField, schemaJson,
    addField, removeField, updateField,
    addRule, removeRule, addOption, removeOption,
    moveField, setActiveField, clearActiveField, loadSchema, getInsertIndex,
  }
}
