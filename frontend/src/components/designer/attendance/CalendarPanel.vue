<script setup lang="ts">
/**
 * CalendarPanel —— 考勤日历面板
 * 展示月份视图，标注节假日/调休日，点击切换特殊日状态
 */
import { computed, ref } from 'vue'
import { useAttendanceDesigner } from '@/composables/useAttendanceDesigner'
import { daysInMonth, firstDayOfWeek, dateKey, isWeekend } from '@/types/attendance'
import type { SpecialDay } from '@/types/attendance'

const props = defineProps<{
  readonly?: boolean
  externalSpecialDays?: SpecialDay[]
  externalYear?: number
  externalMonth?: number
}>()

const designer = useAttendanceDesigner()

/** 只读模式下的内部导航状态 */
const roYear = ref(props.externalYear || new Date().getFullYear())
const roMonth = ref(props.externalMonth || (new Date().getMonth() + 1))

const specialDaysSource = computed(() =>
  props.readonly ? (props.externalSpecialDays || []) : designer.specialDays.value
)
const calYear = computed(() =>
  props.readonly ? roYear.value : designer.calendarYear.value
)
const calMonth = computed(() =>
  props.readonly ? roMonth.value : designer.calendarMonth.value
)

const monthLabel = computed(() =>
  `${calYear.value}年 ${calMonth.value}月`,
)

function getSpecialDay(date: string): SpecialDay | undefined {
  return specialDaysSource.value.find(s => s.date === date)
}

// 构建日历网格
const calendarGrid = computed(() => {
  const year = calYear.value
  const month = calMonth.value
  const days = daysInMonth(year, month)
  const firstDow = firstDayOfWeek(year, month)

  const cells: Array<{
    day: number
    date: string
    isCurrentMonth: boolean
    isWeekend: boolean
    special: SpecialDay | undefined
  }> = []

  // 填充上月空白格
  const prevMonthDays = daysInMonth(year, month - 1 === 0 ? 12 : month - 1)
  for (let i = firstDow - 1; i >= 0; i--) {
    const d = prevMonthDays - i
    cells.push({
      day: d,
      date: '',
      isCurrentMonth: false,
      isWeekend: false,
      special: undefined,
    })
  }

  // 当月日期
  for (let d = 1; d <= days; d++) {
    const key = dateKey(year, month, d)
    cells.push({
      day: d,
      date: key,
      isCurrentMonth: true,
      isWeekend: isWeekend(year, month, d),
      special: getSpecialDay(key),
    })
  }

  // 填充下月空白格（凑满 6 行）
  const remaining = 42 - cells.length
  for (let d = 1; d <= remaining; d++) {
    cells.push({
      day: d,
      date: '',
      isCurrentMonth: false,
      isWeekend: false,
      special: undefined,
    })
  }

  return cells
})

function onCellClick(cell: typeof calendarGrid.value[0]) {
  if (props.readonly || !cell.isCurrentMonth || !cell.date) return
  const parts = cell.date.split('-')
  const y = parseInt(parts[0]), m = parseInt(parts[1]), d = parseInt(parts[2])
  designer.toggleSpecialDay(y, m, d)
}

function handlePrevMonth() {
  if (props.readonly) {
    if (roMonth.value === 1) { roYear.value--; roMonth.value = 12 }
    else { roMonth.value-- }
  } else {
    designer.prevMonth()
  }
}

function handleNextMonth() {
  if (props.readonly) {
    if (roMonth.value === 12) { roYear.value++; roMonth.value = 1 }
    else { roMonth.value++ }
  } else {
    designer.nextMonth()
  }
}

function getCellClass(cell: typeof calendarGrid.value[0]) {
  return {
    'cell-current': cell.isCurrentMonth,
    'cell-other': !cell.isCurrentMonth,
    'cell-weekend': cell.isWeekend && !cell.special,
    'cell-holiday': cell.special?.type === 'HOLIDAY',
    'cell-workday': cell.special?.type === 'WORKDAY',
    'cell-today': cell.date === dateKey(new Date().getFullYear(), new Date().getMonth() + 1, new Date().getDate()),
  }
}

const weekDays = ['日', '一', '二', '三', '四', '五', '六']
</script>

<template>
  <div class="calendar-panel">
    <!-- 月份导航 -->
    <div class="calendar-nav">
      <el-button size="small" text @click="handlePrevMonth()">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="month-label">{{ monthLabel }}</span>
      <el-button size="small" text @click="handleNextMonth()">
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>

    <!-- 星期头部 -->
    <div class="calendar-grid">
      <div v-for="wd in weekDays" :key="wd" class="weekday-header">
        {{ wd }}
      </div>

      <!-- 日期格子 -->
      <div
        v-for="(cell, idx) in calendarGrid"
        :key="idx"
        class="day-cell"
        :class="getCellClass(cell)"
        @click="onCellClick(cell)"
      >
        <span class="day-num">{{ cell.day }}</span>
        <span v-if="cell.special" class="day-badge">
          {{ cell.special.type === 'HOLIDAY' ? '休' : '班' }}
        </span>
        <span v-if="cell.special?.desc" class="day-desc">{{ cell.special.desc }}</span>
      </div>
    </div>

    <!-- 图例 -->
    <div class="calendar-legend">
      <span class="legend-item"><span class="dot holiday"></span> 节假日</span>
      <span class="legend-item"><span class="dot workday"></span> 调休上班</span>
      <span class="legend-item"><span class="dot weekend"></span> 周末</span>
      <span v-if="!readonly" class="legend-hint">点击日期切换状态</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.calendar-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 16px;
  overflow: hidden;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 12px;
}

.month-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  min-width: 130px;
  text-align: center;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}

.weekday-header {
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: #909399;
  padding: 6px 0;
}

.day-cell {
  aspect-ratio: 1;
  max-height: 80px;
  min-height: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: default;
  position: relative;
  transition: all 0.15s;
  font-size: 14px;
  gap: 2px;

  &.cell-other {
    color: #e0e0e0;
  }

  &.cell-current {
    cursor: pointer;
    color: #303133;

    &:hover {
      background: #ecf5ff;
    }
  }

  &.cell-weekend {
    color: #c0c4cc;
  }

  &.cell-holiday {
    background: #e1f3d8;
    color: #67c23a;

    &:hover { background: #c8e6c0; }
  }

  &.cell-workday {
    background: #d9ecff;
    color: #409eff;

    &:hover { background: #c6e2ff; }
  }

  &.cell-today {
    font-weight: 700;

    .day-num {
      border: 2px solid #409eff;
      border-radius: 50%;
      width: 28px;
      height: 28px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}

.day-num {
  font-size: 14px;
  line-height: 1;
}

.day-badge {
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.day-desc {
  font-size: 10px;
  color: inherit;
  opacity: 0.8;
  line-height: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.calendar-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
  font-size: 13px;
  color: #909399;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;

  &.holiday { background: #e1f3d8; border: 1px solid #b3e19d; }
  &.workday { background: #d9ecff; border: 1px solid #a0cfff; }
  &.weekend { background: #f5f5f5; border: 1px solid #e0e0e0; }
}

.legend-hint {
  margin-left: auto;
  color: #c0c4cc;
}
</style>
