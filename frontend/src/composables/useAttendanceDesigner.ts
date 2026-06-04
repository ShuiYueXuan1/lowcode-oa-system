/**
 * 考勤规则设计器 —— 全局状态管理
 *
 * <p><b>管理的状态：</b>
 * <ul>
 *   <li>baseRule — 上下班时间、弹性分钟、迟到/早退阈值</li>
 *   <li>specialDays — 特殊日列表（节假日、调休日）</li>
 *   <li>calendarYear / Month — 日历当前视图的年月</li>
 *   <li>syncConfig — 第三方 API 同步配置</li>
 * </ul></p>
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>toggleSpecialDay() — 点击日历格子循环切换：正常 → 节假日 → 调休 → 正常</li>
 *   <li>syncHolidays(year?) — 调用 timor.tech API 获取当年节假日数据</li>
 *   <li>schemaJson() — 导出完整考勤规则 JSON</li>
 * </ul></p>
 */
import { reactive, computed, toRefs } from 'vue'
import type { BaseRule, SpecialDay, AttendanceSchema, SyncConfig, SpecialDayType } from '@/types/attendance'
import { defaultBaseRule, defaultSyncConfig, dateKey } from '@/types/attendance'

interface AttendanceState {
  baseRule: BaseRule
  specialDays: SpecialDay[]
  syncConfig: SyncConfig
  calendarYear: number
  calendarMonth: number
}

const state = reactive<AttendanceState>({
  baseRule: defaultBaseRule(),
  specialDays: [],
  syncConfig: defaultSyncConfig(),
  calendarYear: new Date().getFullYear(),
  calendarMonth: new Date().getMonth() + 1,
})

export function useAttendanceDesigner() {
  /** 导出完整考勤规则 JSON Schema */
  const schemaJson = computed<AttendanceSchema>(() => ({
    baseRule: { ...state.baseRule },
    specialDays: state.specialDays.map(s => ({ ...s })),
    syncConfig: { ...state.syncConfig },
  }))

  /** 日历上个月 */
  function prevMonth() {
    if (state.calendarMonth === 1) { state.calendarYear--; state.calendarMonth = 12 }
    else { state.calendarMonth-- }
  }

  /** 日历下个月 */
  function nextMonth() {
    if (state.calendarMonth === 12) { state.calendarYear++; state.calendarMonth = 1 }
    else { state.calendarMonth++ }
  }

  /** 查询指定日期的特殊日配置 */
  function getSpecialDay(date: string): SpecialDay | undefined {
    return state.specialDays.find(s => s.date === date)
  }

  /**
   * 点击日历格子切换状态
   * 正常 → 节假日(HOLIDAY) → 调休上班(WORKDAY) → 正常(删除)
   */
  function toggleSpecialDay(year: number, month: number, day: number) {
    const key = dateKey(year, month, day)
    const idx = state.specialDays.findIndex(s => s.date === key)
    if (idx === -1) {
      state.specialDays.push({ date: key, type: 'HOLIDAY', desc: '' })
    } else if (state.specialDays[idx].type === 'HOLIDAY') {
      state.specialDays[idx].type = 'WORKDAY'
      state.specialDays[idx].desc = '调休上班'
    } else {
      state.specialDays.splice(idx, 1)
    }
  }

  function updateSpecialDayDesc(date: string, desc: string) {
    const sd = state.specialDays.find(s => s.date === date)
    if (sd) sd.desc = desc
  }

  function removeSpecialDay(date: string) {
    const idx = state.specialDays.findIndex(s => s.date === date)
    if (idx !== -1) state.specialDays.splice(idx, 1)
  }

  /**
   * 从第三方 API 同步节假日数据
   * @param year 年份，默认当前年
   * @returns { success, count, error? }
   */
  async function syncHolidays(year?: number) {
    const y = year || state.calendarYear
    const url = `${state.syncConfig.apiUrl}/${y}`
    try {
      const res = await fetch(url)
      const json = await res.json()
      if (json.code === 0 && json.holiday) {
        // 清除当前年份已存在的特殊日
        state.specialDays = state.specialDays.filter(s => parseInt(s.date.substring(0, 4)) !== y)
        // 解析 API 返回：holiday=true→节假日，holiday=false→调休上班
        for (const [datePart, info] of Object.entries(json.holiday)) {
          const holiday = info as { holiday?: boolean; name?: string }
          state.specialDays.push({
            date: `${y}-${datePart}`,
            type: holiday.holiday ? 'HOLIDAY' : 'WORKDAY',
            desc: holiday.name || (holiday.holiday ? '节假日' : '调休上班'),
          })
        }
        state.syncConfig.lastSyncTime = new Date().toISOString()
        return { success: true, count: Object.keys(json.holiday).length }
      }
      return { success: false, count: 0, error: 'API 返回格式异常' }
    } catch (e) {
      return { success: false, count: 0, error: String(e) }
    }
  }

  return {
    ...toRefs(state),
    schemaJson,
    prevMonth, nextMonth,
    getSpecialDay, toggleSpecialDay, updateSpecialDayDesc, removeSpecialDay,
    syncHolidays,
  }
}
