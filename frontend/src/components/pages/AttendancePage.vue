<script setup lang="ts">
/**
 * AttendancePage —— 员工打卡页面
 * 实时时钟 + 上班打卡/下班打卡 + 月度考勤统计
 */
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { attendanceApi } from '@/api'
import { ElMessage } from 'element-plus'

// 当前用户（Mock）
const currentUser = { id: 1, name: '张三' }

// 实时时钟
const currentTime = ref('')
const currentDate = ref('')
let timer: ReturnType<typeof setInterval> | null = null

function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
  })
}

// 今日状态
interface TodayStatus {
  today: string
  needWork: boolean
  dayReason: string
  record: {
    id: number; signInTime: string; signOutTime: string
    status: string
  } | null
  currentTime: string
}

const todayStatus = ref<TodayStatus | null>(null)
const loading = ref(false)

async function loadTodayStatus() {
  try {
    const res = await attendanceApi.getToday(currentUser.id)
    if (res.code === 200) {
      todayStatus.value = res.data as TodayStatus
    }
  } catch {
    todayStatus.value = {
      today: new Date().toISOString().substring(0, 10),
      needWork: true,
      dayReason: '(Mock)',
      record: null,
      currentTime: '',
    }
  }
}

// 打卡操作
async function handleSignIn() {
  loading.value = true
  try {
    const res = await attendanceApi.signIn(currentUser.id, currentUser.name)
    if (res.code === 200) {
      const data = res.data as Record<string, unknown>
      if (data.needWork === false) {
        ElMessage.warning(data.message as string)
      } else {
        ElMessage.success(data.message as string || '打卡成功')
      }
      await loadTodayStatus()
    }
  } catch {
    ElMessage.success('(Mock) 上班打卡成功')
    todayStatus.value = {
      today: new Date().toISOString().substring(0, 10),
      needWork: true,
      dayReason: '(Mock)',
      record: {
        id: 1, signInTime: new Date().toISOString(), signOutTime: '',
        status: 'NORMAL',
      },
      currentTime: '',
    }
  } finally {
    loading.value = false
  }
}

async function handleSignOut() {
  loading.value = true
  try {
    const res = await attendanceApi.signOut(currentUser.id, currentUser.name)
    if (res.code === 200) {
      const data = res.data as Record<string, unknown>
      if (data.needWork === false) {
        ElMessage.warning(data.message as string)
      } else {
        ElMessage.success(data.message as string || '打卡成功')
      }
      await loadTodayStatus()
    }
  } catch {
    ElMessage.success('(Mock) 下班打卡成功')
    if (todayStatus.value?.record) {
      todayStatus.value.record.signOutTime = new Date().toISOString()
      todayStatus.value.record.status = 'NORMAL'
    }
  } finally {
    loading.value = false
  }
}

// 月度记录
const hireDate = new Date(2025, 0, 1) // 入职日期：2025-01-01
const today = new Date(); today.setHours(0, 0, 0, 0)
const monthlyYear = ref(today.getFullYear())
const monthlyMonth = ref(today.getMonth() + 1)
const monthlyRecords = ref<Array<{
  date: string; signInTime: string; signOutTime: string
  status: string; statusLabel: string
}>>([])

// 月份导航限制
const canGoPrev = computed(() => {
  // 不能早于入职月份
  const d = new Date(monthlyYear.value, monthlyMonth.value - 1, 1)
  const hireMonth = new Date(hireDate.getFullYear(), hireDate.getMonth(), 1)
  return d > hireMonth
})

const canGoNext = computed(() => {
  // 不能超过当前月份
  const d = new Date(monthlyYear.value, monthlyMonth.value - 1, 1)
  const thisMonth = new Date(today.getFullYear(), today.getMonth(), 1)
  return d < thisMonth
})

function goPrevMonth() {
  if (!canGoPrev.value) return
  if (monthlyMonth.value === 1) { monthlyYear.value--; monthlyMonth.value = 12 }
  else { monthlyMonth.value-- }
  loadMonthly()
}

function goNextMonth() {
  if (!canGoNext.value) return
  if (monthlyMonth.value === 12) { monthlyYear.value++; monthlyMonth.value = 1 }
  else { monthlyMonth.value++ }
  loadMonthly()
}

// 月度统计
const monthStats = computed(() => {
  const stats = { normal: 0, late: 0, seriousLate: 0, early: 0, seriousEarly: 0, missing: 0, rest: 0, leave: 0, pending: 0, total: 0 }
  for (const r of monthlyRecords.value) {
    stats.total++
    const s = r.status || ''
    if (s === 'NORMAL') stats.normal++
    else if (s === 'LATE') stats.late++
    else if (s === 'SERIOUS_LATE') stats.seriousLate++
    else if (s === 'EARLY') stats.early++
    else if (s === 'SERIOUS_EARLY') stats.seriousEarly++
    else if (s === 'MISSING') stats.missing++
    else if (s === 'REST') stats.rest++
    else if (s === 'LEAVE') stats.leave++
    else if (s === 'PENDING') stats.pending++
  }
  return stats
})

async function loadMonthly() {
  try {
    const res = await attendanceApi.getMonthly(currentUser.id, monthlyYear.value, monthlyMonth.value)
    if (res.code === 200) {
      monthlyRecords.value = res.data as Array<Record<string, unknown>> as any
    }
  } catch {
    monthlyRecords.value = []
  }
}

function getRowClass({ row }: { row: Record<string, unknown> }) {
  if (row.status === 'REST' || row.status === 'LEAVE') return 'row-rest'
  if (row.status === 'FUTURE') return 'row-future'
  return ''
}

function getStatusType(status: string) {
  if (!status) return 'info'
  switch (status) {
    case 'NORMAL': return 'success'
    case 'LATE':
    case 'EARLY': return 'warning'
    case 'SERIOUS_LATE':
    case 'SERIOUS_EARLY':
    case 'MISSING': return 'danger'
    case 'LEAVE': return ''
    case 'REST': return ''
    case 'PENDING': return 'info'
    default: return 'info'
  }
}

const canSignIn = computed(() => !todayStatus.value?.record?.signInTime)
const canSignOut = computed(() =>
  todayStatus.value?.record?.signInTime && !todayStatus.value?.record?.signOutTime
)

onMounted(() => {
  updateClock()
  timer = setInterval(updateClock, 1000)
  loadTodayStatus()
  loadMonthly()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="attendance-page">
    <header class="page-header">
      <h2><el-icon :size="20"><Clock /></el-icon> 员工打卡</h2>
      <el-tag type="info">{{ currentUser.name }}</el-tag>
    </header>

    <div class="page-body">
      <!-- 左侧：实时时钟 + 打卡按钮 -->
      <div class="body-left">
        <div class="clock-card">
          <div class="clock-date">{{ currentDate }}</div>
          <div class="clock-time">{{ currentTime }}</div>

          <div v-if="todayStatus && !todayStatus.needWork" class="rest-badge">
            <el-alert :title="todayStatus.dayReason" type="success" :closable="false" show-icon />
          </div>

          <div v-else class="punch-actions">
            <div class="punch-row">
              <div class="punch-item">
                <div class="punch-label">上班打卡</div>
                <div v-if="todayStatus?.record?.signInTime" class="punch-done">
                  <el-tag type="success" size="large">
                    {{ new Date(todayStatus.record.signInTime).toLocaleTimeString('zh-CN') }}
                  </el-tag>
                </div>
                <el-button
                  v-else
                  type="primary"
                  size="large"
                  :loading="loading"
                  :disabled="!canSignIn"
                  @click="handleSignIn"
                >
                  上班打卡
                </el-button>
              </div>

              <el-divider direction="vertical" style="height: 60px" />

              <div class="punch-item">
                <div class="punch-label">下班打卡</div>
                <div v-if="todayStatus?.record?.signOutTime" class="punch-done">
                  <el-tag type="success" size="large">
                    {{ new Date(todayStatus.record.signOutTime).toLocaleTimeString('zh-CN') }}
                  </el-tag>
                </div>
                <el-button
                  v-else
                  type="warning"
                  size="large"
                  :loading="loading"
                  :disabled="!canSignOut"
                  @click="handleSignOut"
                >
                  下班打卡
                </el-button>
              </div>
            </div>

            <div v-if="todayStatus?.record?.status" class="punch-status-line">
              今日状态：
              <el-tag :type="getStatusType(todayStatus.record.status)" size="default">
                {{ todayStatus.record.status }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 月度统计卡片 -->
        <div class="stats-card" v-if="monthlyRecords.length > 0">
          <div class="stats-title">{{ monthlyYear }}年{{ monthlyMonth }}月 统计</div>
          <div class="stats-grid">
            <div class="stat-item normal">
              <span class="stat-num">{{ monthStats.normal }}</span>
              <span class="stat-label">正常</span>
            </div>
            <div class="stat-item late">
              <span class="stat-num">{{ monthStats.late + monthStats.seriousLate }}</span>
              <span class="stat-label">迟到</span>
            </div>
            <div class="stat-item early">
              <span class="stat-num">{{ monthStats.early + monthStats.seriousEarly }}</span>
              <span class="stat-label">早退</span>
            </div>
            <div class="stat-item missing">
              <span class="stat-num">{{ monthStats.missing }}</span>
              <span class="stat-label">缺卡</span>
            </div>
            <div class="stat-item rest">
              <span class="stat-num">{{ monthStats.rest }}</span>
              <span class="stat-label">休息</span>
            </div>
            <div class="stat-item leave">
              <span class="stat-num">{{ monthStats.leave }}</span>
              <span class="stat-label">请假</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：月度考勤表 -->
      <div class="body-right">
        <div class="monthly-card">
          <div class="monthly-header">
            <span class="monthly-title">月度考勤</span>
            <div class="monthly-nav">
              <el-button size="small" text :disabled="!canGoPrev" @click="goPrevMonth">
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <span class="monthly-label">{{ monthlyYear }}年 {{ monthlyMonth }}月</span>
              <el-button size="small" text :disabled="!canGoNext" @click="goNextMonth">
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>

          <div v-if="monthlyRecords.length === 0" class="monthly-empty">
            暂无考勤记录
          </div>

          <div v-else class="table-wrapper">
            <el-table :data="monthlyRecords" size="default" stripe
              :row-class-name="getRowClass"
            >
            <el-table-column label="日期" width="110">
              <template #default="{ row }">
                <span :style="{ color: row.status === 'REST' ? '#909399' : '' }">{{ row.date }}</span>
              </template>
            </el-table-column>
            <el-table-column label="上班" width="80">
              <template #default="{ row }">
                <span v-if="row.signInTime">{{ row.signInTime?.substring(11, 16) }}</span>
                <span v-else style="color: #dcdfe6">-</span>
              </template>
            </el-table-column>
            <el-table-column label="下班" width="80">
              <template #default="{ row }">
                <span v-if="row.signOutTime">{{ row.signOutTime?.substring(11, 16) }}</span>
                <span v-else style="color: #dcdfe6">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="110">
              <template #default="{ row }">
                <template v-if="row.status === 'REST'">
                  <span class="status-rest">{{ row.statusLabel }}</span>
                </template>
                <template v-else-if="row.status === 'FUTURE'">
                  <span></span>
                </template>
                <template v-else>
                  <el-tag :type="getStatusType(row.status)" size="small">
                    {{ row.statusLabel }}
                  </el-tag>
                </template>
              </template>
            </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.attendance-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f0f2f5;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;

  h2 {
    margin: 0;
    font-size: 17px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #303133;
    font-weight: 600;
  }
}

.page-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 20px;
  overflow: hidden;
}

.body-left {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.body-right {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.clock-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  padding: 28px 24px 20px;
  text-align: center;
  flex-shrink: 0;
}

.clock-date {
  font-size: 15px;
  color: #909399;
  margin-bottom: 6px;
}

.clock-time {
  font-size: 52px;
  font-weight: 300;
  color: #303133;
  font-family: 'SF Mono', 'Consolas', monospace;
  letter-spacing: 2px;
  margin-bottom: 18px;
}

.rest-badge {
  margin-top: 12px;
}

.punch-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.punch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}

.punch-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.punch-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.punch-done {
  display: flex;
  align-items: center;
}

.punch-status-line {
  margin-top: 14px;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
}

// ---- 统计卡片 ----
.stats-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 16px 18px;
  flex-shrink: 0;
}

.stats-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 4px;
  border-radius: 8px;
  background: #fafafa;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.stat-item.normal .stat-num { color: #67c23a; }
.stat-item.late .stat-num { color: #e6a23c; }
.stat-item.early .stat-num { color: #e6a23c; }
.stat-item.missing .stat-num { color: #f56c6c; }
.stat-item.rest .stat-num { color: #909399; }
.stat-item.leave .stat-num { color: #409eff; }

.monthly-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.monthly-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.monthly-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.monthly-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.monthly-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  min-width: 100px;
  text-align: center;
}

.table-wrapper {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.monthly-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 15px;
}

.status-rest {
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
}
</style>

<style>
/* 全局样式：表格行底色 */
.row-rest {
  background: #fafafa !important;
}
.row-future {
  opacity: 0.4;
}
</style>
