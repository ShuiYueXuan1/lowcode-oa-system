/**
 * 表单设计器 —— 核心类型定义
 *
 * <p>定义了表单 JSON Schema 的完整结构，包括：
 * <ul>
 *   <li>FieldType — 8 种支持的字段类型</li>
 *   <li>ValidationRule — 校验规则（含各类型专属字段）</li>
 *   <li>FormField — 单个字段的完整定义</li>
 *   <li>FormSchema — 完整表单定义（设计器产出物）</li>
 * </ul></p>
 */

/** 支持的字段类型（共 8 种） */
export type FieldType =
    | 'input' | 'textarea' | 'number' | 'select'
    | 'date' | 'switch' | 'radio' | 'checkbox'

/**
 * 校验规则 —— 各类型字段有各自的专属规则字段
 *
 * <ul>
 *   <li><b>文本类</b>（input / textarea）：minLength、maxLength、pattern</li>
 *   <li><b>数字类</b>（number）：min、max、multipleOf、decimalPlaces</li>
 *   <li><b>日期类</b>（date）：minDate、maxDate、disablePast</li>
 *   <li><b>选择类</b>（select / checkbox）：minSelect、maxSelect</li>
 *   <li><b>所有类型</b>：required、message</li>
 * </ul>
 */
export interface ValidationRule {
    required?: boolean
    message: string
    minLength?: number
    maxLength?: number
    pattern?: string
    min?: number
    max?: number
    multipleOf?: number
    decimalPlaces?: number
    minDate?: string
    maxDate?: string
    disablePast?: boolean
    minSelect?: number
    maxSelect?: number
}

/** 选择类组件的选项项 */
export interface SelectOption {
    label: string
    value: string | number
}

/** 单个表单字段的完整定义 */
export interface FormField {
    key: string
    type: FieldType
    label: string
    placeholder?: string
    defaultValue?: unknown
    rules: ValidationRule[]
    options?: SelectOption[]
    disabled?: boolean
    /** select 是否多选 */
    multiple?: boolean
}

/** 表单 Schema（设计器产出物，存入 form_schema.schema_json） */
export interface FormSchema {
    name: string
    code: string
    labelWidth: number
    fields: FormField[]
}

/** 组件库面板中的组件项 */
export interface ComponentItem {
    type: FieldType
    label: string
    icon: string
}

/** 左侧组件库数据源（8 种可拖拽组件） */
export const COMPONENT_LIBRARY: ComponentItem[] = [
    {type: 'input', label: '输入框', icon: 'Edit'},
    {type: 'textarea', label: '文本域', icon: 'Document'},
    {type: 'number', label: '数字输入', icon: 'Sort'},
    {type: 'select', label: '下拉选择', icon: 'ArrowDown'},
    {type: 'date', label: '日期选择', icon: 'Calendar'},
    {type: 'switch', label: '开关', icon: 'Switch'},
    {type: 'radio', label: '单选框', icon: 'CircleCheck'},
    {type: 'checkbox', label: '复选框', icon: 'Select'},
]

/** 字段类型中文映射 */
export const FIELD_TYPE_LABELS: Record<FieldType, string> = {
    input: '输入框', textarea: '文本域', number: '数字输入',
    select: '下拉选择', date: '日期选择', switch: '开关',
    radio: '单选框', checkbox: '复选框',
}

/** 需要配置 options 的字段类型 */
export function hasOptions(type: FieldType): boolean {
    return type === 'select' || type === 'radio' || type === 'checkbox'
}

let _counter = 0

/** 生成唯一字段 key（用于标识画布中的每个字段） */
export function generateFieldKey(): string {
    return `field_${Date.now()}_${++_counter}`
}