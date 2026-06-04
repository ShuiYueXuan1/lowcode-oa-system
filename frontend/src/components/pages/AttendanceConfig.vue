<script setup lang="ts">
/**
 * AttendanceConfig —— 考勤规则配置主页面
 * 三栏布局：基础规则 | 日历面板 | 特殊日列表
 */
import { ref } from 'vue'
import { useAttendanceDesigner } from '@/composables/useAttendanceDesigner'
import RuleEditor from '@/components/designer/attendance/RuleEditor.vue'
import CalendarPanel from '@/components/designer/attendance/CalendarPanel.vue'
import VersionDialog from '@/components/designer/VersionDialog.vue'
import SchemaPreviewPage from '@/components/pages/SchemaPreviewPage.vue'
import { attendanceSchemaApi } from '@/api'
import { ElMessage } from 'element-plus'

const designer = useAttendanceDesigner()
const syncing = ref(false)
const saving = ref(false)
const syncResult = ref<{ success: boolean; count: number; error?: string } | null>(null)
const versionVisible = ref(false)
const versions = ref<Array<Record<string, unknown>>>([])
const previewOpen = ref(false)
const previewData = ref<Record<string, unknown> | null>(null)

function openVersionPreview(row: Record<string, unknown>) {
  previewData.value = row
  previewOpen.value = true
}

async function handleSyncHolidays() {
  syncing.value = true
  syncResult.value = null
  const result = await designer.syncHolidays()
  syncResult.value = result
  if (result.success) {
    ElMessage.success(`同步成功，更新了 ${result.count} 条节假日数据`)
  } else {
    ElMessage.warning(`同步失败: ${result.error || '未知错误'}`)
  }
  syncing.value = false
}

async function handleSave() {
  saving.value = true
  try {
    const schema = JSON.parse(JSON.stringify(designer.schemaJson.value))
    const res = await attendanceSchemaApi.save(schema)
    if (res.code === 200) ElMessage.success('保存成功！')
    else ElMessage.error(res.message || '保存失败')
  } catch {
    ElMessage.warning('后端未启动，Schema 已输出到控制台')
    console.log('Attendance Schema:', JSON.stringify(designer.schemaJson.value, null, 2))
  } finally { saving.value = false }
}

async function openVersions() {
  try {
    const res = await attendanceSchemaApi.getVersions()
    if (res.code === 200) versions.value = res.data as Array<Record<string, unknown>>
  } catch { versions.value = [] }
  versionVisible.value = true
}

async function handleSetCurrent(id: number) {
  try {
    await attendanceSchemaApi.setCurrent(id)
    ElMessage.success('已设为当前生效规则')
    openVersions()
  } catch { ElMessage.error('操作失败') }
}
</script>

<template>
  <div class="attendance-config">
    <header class="page-header">
      <div class="header-left">
        <h2><el-icon :size="20"><Clock /></el-icon> 考勤规则配置</h2>
      </div>
      <div class="header-right">
        <el-button
          type="success"
          :loading="syncing"
          @click="handleSyncHolidays"
        >
          <el-icon><Refresh /></el-icon>
          同步 {{ designer.calendarYear.value }} 年节假日
        </el-button>
        <el-button @click="openVersions">版本历史</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Document /></el-icon>
          保存
        </el-button>
      </div>
    </header>

    <div class="page-body">
      <div class="body-left">
        <RuleEditor />

        <!-- 同步结果 -->
        <div v-if="syncResult" class="sync-info">
          <el-alert
            :title="syncResult.success ? '同步成功' : '同步失败'"
            :type="syncResult.success ? 'success' : 'warning'"
            :description="syncResult.success
              ? `已同步 ${syncResult.count} 天节假日/调休数据，请在日历中查看`
              : (syncResult.error || '未知错误')"
            show-icon
            :closable="false"
          />
        </div>

        <!-- 特殊日快捷列表 -->
        <div v-if="designer.specialDays.value.length > 0" class="special-list">
          <div class="section-title">
            特殊日列表 ({{ designer.specialDays.value.length }})
          </div>
          <div class="special-items">
            <div
              v-for="sd in designer.specialDays.value"
              :key="sd.date"
              class="special-item"
            >
              <el-tag
                :type="sd.type === 'HOLIDAY' ? 'success' : ''"
                size="small"
              >
                {{ sd.type === 'HOLIDAY' ? '休' : '班' }}
              </el-tag>
              <span class="special-date">{{ sd.date }}</span>
              <el-input
                :model-value="sd.desc"
                size="small"
                placeholder="说明"
                style="width: 100px"
                @input="(v: string) => designer.updateSpecialDayDesc(sd.date, v)"
              />
              <el-button
                size="small"
                text
                type="danger"
                @click="designer.removeSpecialDay(sd.date)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="body-right">
        <CalendarPanel />
      </div>
    </div>

    <VersionDialog
      v-model:visible="versionVisible"
      title="考勤规则"
      :versions="versions"
      :show-set-current="true"
      @set-current="handleSetCurrent"
      @preview="openVersionPreview"
    />

    <SchemaPreviewPage
      v-model:visible="previewOpen"
      type="attendance"
      :data="previewData"
    />
  </div>
</template>

<style scoped lang="scss">
.attendance-config {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f0f2f5;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;

  h2 {
    margin: 0;
    font-size: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #303133;
  }
}

.header-right {
  display: flex;
  gap: 8px;
}

.page-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.body-left {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.body-right {
  flex: 1;
  min-width: 460px;
  overflow-y: auto;
}

.sync-info {
  :deep(.el-alert__description) {
    font-size: 12px;
  }
}

.special-list {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.special-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
}

.special-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
}

.special-date {
  font-size: 12px;
  color: #606266;
  font-family: monospace;
  width: 85px;
}
</style>
