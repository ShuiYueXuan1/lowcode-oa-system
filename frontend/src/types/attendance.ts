/**
 * 考勤规则配置 —— 核心类型定义
 *
 * <p>定义了考勤规则 JSON Schema 的完整结构，包括：
 * <ul>
 *   <li>BaseRule — 基础上下班规则（时间、弹性、阈值）</li>
 *   <li>SpecialDay — 特殊日配置（节假日 / 调休上班）</li>
 *   <li>SyncConfig — 第三方 API 同步配置</li>
 *   <li>AttendanceSchema — 完整考勤规则定义</li>
 * </ul></p>
 *
 * <p>工具函数：daysInMonth、firstDayOfWeek、isWeekend、dateKey</p>
 */

export interface BaseRule {
  /** 上班时间，格式 "HH:mm"，如 "09:00" */
  workStart: string
  /** 下班时间 */
  workEnd: string
  /** 弹性分钟数，实际打卡时间在此范围内不判迟到/早退 */
  flexMinutes: number
  /** 迟到阈值（分钟），超过弹性但 ≤ 此值 → 迟到，> 此值 → 严重迟到 */
  lateThreshold: number
  /** 早退阈值（分钟），超过弹性但 ≤ 此值 → 早退，> 此值 → 严重早退 */
  earlyThreshold: number
}

export type SpecialDayType = 'HOLIDAY' | 'WORKDAY'

/** 特殊日（节假日 / 调休上班） */
export interface SpecialDay {
  /** 日期，格式 "YYYY-MM-DD" */
  date: string
  type: SpecialDayType
  /** 描述，如"元旦"、"春节调休" */
  desc: string
}

/** 第三方 API 同步配置 */
export interface SyncConfig {
  apiUrl: string
  lastSyncTime: string | null
  autoSync: boolean
}

/** 考勤规则完整 Schema（设计器产出物，存入 attendance_schema.schema_json） */
export interface AttendanceSchema {
  baseRule: BaseRule
  specialDays: SpecialDay[]
  syncConfig: SyncConfig
}

/** 默认基础规则（09:00-18:00，弹性 15 分钟，阈值 30 分钟） */
export function defaultBaseRule(): BaseRule {
  return {
    workStart: '09:00', workEnd: '18:00',
    flexMinutes: 15, lateThreshold: 30, earlyThreshold: 30,
  }
}

/** 默认同步配置（API 地址 + 不自动同步） */
export function defaultSyncConfig(): SyncConfig {
  return {
    apiUrl: 'https://timor.tech/api/holiday/year',
    lastSyncTime: null, autoSync: false,
  }
}

/** 格式化日期为 "YYYY-MM-DD" */
export function dateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** 获取指定年月的天数 */
export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

/** 获取指定年月第一天是星期几（0=周日） */
export function firstDayOfWeek(year: number, month: number): number {
  return new Date(year, month - 1, 1).getDay()
}

/** 判断是否为周末（周六或周日） */
export function isWeekend(year: number, month: number, day: number): boolean {
  return new Date(year, month - 1, day).getDay() % 6 === 0
}
