<script setup lang="ts">
/**
 * LeaveManagement.vue —— 请假管理（申请 + 记录）
 * 左侧：新建请假申请  右侧：历史请假记录
 */
import { ref, onMounted, computed } from 'vue'
import FormRenderer from '@/components/renderer/FormRenderer.vue'
import type { FormSchema } from '@/types/form'
import { formSchemaApi, leaveApi, api } from '@/api'
import { ElMessage } from 'element-plus'

const currentUser = { id: 1, name: '张三' }

// ===== 左侧：申请表单 =====
const formSchema = ref<FormSchema | null>(null)
const formLoading = ref(true)
const submitting = ref(false)
const showResult = ref(false)
const submitResult = ref<Record<string, unknown> | null>(null)

const resultLeave = computed(() => submitResult.value?.leaveInstance as Record<string, unknown> | undefined)
const resultProcess = computed(() => submitResult.value?.processInstance as Record<string, unknown> | undefined)
const resultNodes = computed(() => {
  const s = submitResult.value?.snapshot as Record<string, unknown> | undefined
  return (s?.resolvedNodes || []) as Array<Record<string, unknown>>
})

onMounted(async () => {
  try {
    const res = await formSchemaApi.getByCode('leave_apply')
    if (res.code === 200 && res.data) {
      const entity = res.data as unknown as Record<string, unknown>
      const inner = (entity.schemaJson || entity) as Record<string, unknown>
      formSchema.value = {
        name: (entity.name as string) || '请假申请表',
        code: (entity.code as string) || 'leave_apply',
        labelWidth: 100,
        fields: (inner.fields || []) as FormSchema['fields'],
      }
      formLoading.value = false
      return
    }
  } catch { /* mock */ }

  formSchema.value = {
    name: '请假申请表', code: 'leave_apply', labelWidth: 100,
    fields: [
      { key: 'leave_type', type: 'select', label: '请假类型', placeholder: '请选择', rules: [{ required: true, message: '请选择类型' }], options: [{ label: '年假', value: 'annual' }, { label: '事假', value: 'personal' }, { label: '病假', value: 'sick' }] },
      { key: 'start_date', type: 'date', label: '开始日期', rules: [{ required: true, message: '请选择' }] },
      { key: 'end_date', type: 'date', label: '结束日期', rules: [{ required: true, message: '请选择' }] },
      { key: 'days', type: 'number', label: '请假天数', rules: [{ required: true, message: '请输入' }] },
      { key: 'reason', type: 'textarea', label: '原因', placeholder: '请输入', rules: [] },
    ],
  }
  formLoading.value = false
})

async function handleSubmit(data: Record<string, unknown>) {
  submitting.value = true
  try {
    const res = await leaveApi.submit({ formSchemaId: 1, formData: data, applicantId: currentUser.id, applicantName: currentUser.name })
    if (res.code === 200) {
      submitResult.value = res.data as Record<string, unknown>
      showResult.value = true
      ElMessage.success('提交成功')
      loadRecords()
    }
  } catch {
    submitResult.value = { leaveInstance: { id: Date.now(), status: 'PENDING' }, processInstance: { status: 'IN_PROGRESS' }, snapshot: { resolvedNodes: [] }, message: '(Mock) 提交成功' }
    showResult.value = true
  } finally { submitting.value = false }
}

// ===== 右侧：请假记录 =====
const records = ref<Array<Record<string, unknown>>>([])
const recordsLoading = ref(false)
const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const dLeave = computed(() => (detail.value?.leaveInstance || {}) as Record<string, unknown>)
const dRecords = computed(() => (detail.value?.approvalRecords || []) as Array<Record<string, unknown>>)
const dFormData = computed(() => (dLeave.value.formData || {}) as Record<string, unknown>)

async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await api.get<unknown[]>('/api/leave/my-records?userId=' + currentUser.id)
    if (res.code === 200) records.value = res.data as Array<Record<string, unknown>>
  } catch { records.value = [] }
  finally { recordsLoading.value = false }
}

async function showDetail(leaveId: number) {
  try {
    const res = await api.get<Record<string, unknown>>('/api/leave/' + leaveId)
    if (res.code === 200) { detail.value = res.data; detailVisible.value = true }
  } catch { ElMessage.error('加载失败') }
}

function getSt(st: string) {
  const m: Record<string, string> = { APPROVED: 'success', REJECTED: 'danger', PENDING: 'warning' }
  return m[st] || 'info'
}
function getStText(st: string) {
  const m: Record<string, string> = { APPROVED: '已通过', REJECTED: '已驳回', PENDING: '审批中' }
  return m[st] || st
}
function fmt(v: unknown) { return v ? String(v).substring(0, 16) : '-' }

const fieldLabels: Record<string, string> = {
  leave_type: '请假类型', start_date: '开始日期', end_date: '结束日期',
  days: '请假天数', reason: '请假原因',
}

// 从表单 Schema 的 options 建立 value→label 映射
const valueLabelMap = computed(() => {
  const map: Record<string, string> = {}
  for (const f of (formSchema.value?.fields || [])) {
    if (f.options) {
      for (const opt of f.options) {
        map[String(opt.value)] = opt.label
      }
    }
  }
  // 兜底映射
  map.annual = '年假'; map.personal = '事假'; map.sick = '病假'
  map.PENDING = '审批中'; map.APPROVED = '已通过'; map.REJECTED = '已驳回'; map.IN_PROGRESS = '审批中'
  return map
})

function cnLabel(key: string) { return fieldLabels[key] || key }
function cnValue(key: string, val: unknown): string {
  if (val === null || val === undefined) return '-'
  return valueLabelMap.value[String(val)] || String(val)
}

onMounted(loadRecords)
</script>

<template>
  <div class="leave-mgmt">
    <header class="page-header">
      <h2><el-icon :size="20"><DocumentAdd /></el-icon> 请假管理</h2>
      <el-tag type="info">{{ currentUser.name }}</el-tag>
    </header>

    <div class="page-body">
      <!-- 左侧：新建申请 -->
      <div class="panel panel-left">
        <el-card shadow="never" class="panel-card" v-loading="formLoading">
          <template #header><span class="card-title">新建请假</span></template>

          <template v-if="!showResult && formSchema">
            <FormRenderer :key="formSchema.code" :form-schema="formSchema"
              @submit="handleSubmit" @cancel="() => {}" />
          </template>

          <div v-else-if="showResult" class="result-box">
            <el-result icon="success" title="已提交" :sub-title="'单号: ' + (resultLeave?.id || '-')">
              <template #extra>
                <div class="chain-mini" v-if="resultNodes.length">
                  <span v-for="(n, i) in resultNodes" :key="i">
                    <span v-if="i > 0" class="chain-arr">→</span>
                    <el-tag :type="n.required ? '' : 'info'" size="small" :effect="n.required ? 'light' : 'plain'">
                      {{ n.nodeName }}{{ n.required ? '' : '(跳过)' }}
                    </el-tag>
                  </span>
                </div>
                <el-button type="primary" size="small" style="margin-top:12px" @click="showResult = false; submitResult = null">
                  再请一张
                </el-button>
              </template>
            </el-result>
          </div>
        </el-card>
      </div>

      <!-- 右侧：历史记录 -->
      <div class="panel panel-right">
        <el-card shadow="never" class="panel-card" v-loading="recordsLoading">
          <template #header>
            <div class="card-header-row">
              <span class="card-title">请假记录</span>
              <el-button size="small" text @click="loadRecords"><el-icon><Refresh /></el-icon></el-button>
            </div>
          </template>

          <div v-if="records.length === 0" class="empty-hint">暂无记录</div>

          <el-table v-else :data="records" size="small" stripe>
            <el-table-column label="单号" prop="id" width="60" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="getSt(row.status as string)" size="small">{{ getStText(row.status as string) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="100">
              <template #default="{ row }">
                <span v-if="row.processStatus === 'APPROVED'" style="color:#67c23a">已通过</span>
                <span v-else-if="row.processStatus === 'REJECTED'" style="color:#f56c6c">已驳回</span>
                <span v-else-if="row.currentNodeId">{{ row.currentNodeId }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="130">
              <template #default="{ row }">{{ fmt(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="showDetail(row.id as number)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="请假详情" width="560px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="单号">{{ dLeave.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getSt(dLeave.status as string)" size="small">{{ getStText(dLeave.status as string) }}</el-tag>
        </el-descriptions-item>
        <template v-for="(v, k) in dFormData" :key="k">
          <el-descriptions-item :label="cnLabel(String(k))">{{ cnValue(String(k), v) }}</el-descriptions-item>
        </template>
      </el-descriptions>
      <div v-if="dRecords.length" class="chain-section">
        <div class="chain-title">审批记录</div>
        <el-timeline>
          <el-timeline-item v-for="rec in dRecords" :key="(rec.id as number)"
            :timestamp="fmt(rec.handleTime || rec.createTime)"
            :type="rec.action === 'APPROVE' ? 'success' : rec.action === 'REJECT' ? 'danger' : 'primary'">
            <strong>{{ rec.nodeName }}</strong>
            <el-tag :type="rec.action === 'APPROVE' ? 'success' : rec.action === 'REJECT' ? 'danger' : 'warning'" size="small" style="margin-left:8px">
              {{ rec.action === 'APPROVE' ? '同意' : rec.action === 'REJECT' ? '驳回' : rec.action === 'SKIP' ? '跳过' : '待审批' }}
            </el-tag>
            <div v-if="rec.approverName" class="rec-sub">{{ rec.approverName }}</div>
            <div v-if="rec.comment" class="rec-sub">{{ rec.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.leave-mgmt {
  display: flex; flex-direction: column; height: 100%; background: #f0f2f5;
}
.page-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 24px; background: #fff; border-bottom: 1px solid #e4e7ed; flex-shrink: 0;
  h2 { margin: 0; font-size: 17px; display: flex; align-items: center; gap: 8px; color: #303133; }
}
.page-body {
  flex: 1; display: flex; gap: 16px; padding: 16px; overflow: hidden;
}
.panel {
  flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden;
}
.panel-left { max-width: 520px; }
.panel-right { min-width: 420px; }

.panel-card {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
  :deep(.el-card__header) { padding: 12px 16px; }
  :deep(.el-card__body) { flex: 1; overflow-y: auto; padding: 12px 16px; }
}

.card-title { font-size: 15px; font-weight: 600; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; }

.result-box { padding: 16px 0; }
.chain-mini { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; justify-content: center; }
.chain-arr { color: #c0c4cc; margin: 0 4px; }

.empty-hint { text-align: center; color: #c0c4cc; padding: 40px; font-size: 14px; }

.chain-section { margin-top: 16px; }
.chain-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.rec-sub { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
