<script setup lang="ts">
/**
 * LeaveRecords.vue —— 请假记录页面
 * 员工查看自己的请假列表，点击查看详情（含审批链）
 */
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api'
import { ElMessage } from 'element-plus'

const apiBase = 'http://localhost:8080'

const currentUser = { id: 1, name: '张三' }
const records = ref<Array<Record<string, unknown>>>([])
const detailVisible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
// 将 detail 数据解包为响应式引用便于模板使用
const dLeave = computed(() => (detail.value?.leaveInstance || {}) as Record<string, unknown>)
const dRecords = computed(() => (detail.value?.approvalRecords || []) as Array<Record<string, unknown>>)
const dFormData = computed(() => (dLeave.value.formData || {}) as Record<string, unknown>)
const loading = ref(false)

async function loadRecords() {
  loading.value = true
  try {
    const res = await api.get<unknown[]>(`/api/leave/my-records?userId=${currentUser.id}`)
    if (res.code === 200) records.value = res.data as Array<Record<string, unknown>>
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

async function showDetail(leaveId: number) {
  try {
    const res = await api.get<Record<string, unknown>>(`/api/leave/${leaveId}`)
    if (res.code === 200) {
      detail.value = res.data
      detailVisible.value = true
    }
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function getStatusType(status: string) {
  const map: Record<string, string> = { APPROVED: 'success', REJECTED: 'danger', PENDING: 'warning', CANCELLED: 'info' }
  return map[status] || 'info'
}

function getStatusText(status: string) {
  const map: Record<string, string> = { APPROVED: '已通过', REJECTED: '已驳回', PENDING: '审批中', CANCELLED: '已取消' }
  return map[status] || status
}

function formatTime(v: unknown) {
  if (!v) return '-'
  return String(v).substring(0, 16)
}

onMounted(loadRecords)
</script>

<template>
  <div class="leave-records">
    <header class="page-header">
      <h2><el-icon :size="20"><List /></el-icon> 请假记录</h2>
      <el-button text @click="loadRecords"><el-icon><Refresh /></el-icon> 刷新</el-button>
    </header>

    <div class="page-body">
      <el-card shadow="never" v-loading="loading">
        <el-table :data="records" size="small" stripe v-if="records.length > 0">
          <el-table-column label="单号" prop="id" width="70" />
          <el-table-column label="申请人" prop="applicantName" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status as string)" size="small">
                {{ getStatusText(row.status as string) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批进度" min-width="140">
            <template #default="{ row }">
              <template v-if="row.processStatus">
                <span v-if="row.processStatus === 'APPROVED'" style="color: #67c23a">已通过</span>
                <span v-else-if="row.processStatus === 'REJECTED'" style="color: #f56c6c">已驳回</span>
                <span v-else>当前: {{ row.currentNodeId }}</span>
              </template>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="140">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="showDetail(row.id as number)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty">暂无请假记录</div>
      </el-card>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="请假详情" width="600px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="单号">{{ dLeave.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(dLeave.status as string)" size="small">
              {{ getStatusText(dLeave.status as string) }}
            </el-tag>
          </el-descriptions-item>
          <template v-for="(v, k) in dFormData" :key="k">
            <el-descriptions-item :label="String(k)">{{ v }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <!-- 审批链 -->
        <div v-if="dRecords.length > 0" class="detail-chain">
          <div class="chain-title">审批记录</div>
          <el-timeline>
            <el-timeline-item
              v-for="rec in dRecords"
              :key="rec.id as number"
              :timestamp="formatTime(rec.handleTime || rec.createTime)"
              :type="rec.action === 'APPROVE' ? 'success' : rec.action === 'REJECT' ? 'danger' : 'primary'"
            >
              <strong>{{ rec.nodeName }}</strong>
              <el-tag :type="rec.action === 'APPROVE' ? 'success' : rec.action === 'REJECT' ? 'danger' : 'warning'" size="small" style="margin-left: 8px">
                {{ rec.action === 'APPROVE' ? '同意' : rec.action === 'REJECT' ? '驳回' : rec.action === 'SKIP' ? '跳过' : '待审批' }}
              </el-tag>
              <div v-if="rec.approverName" style="font-size:12px;color:#909399">{{ rec.approverName }}</div>
              <div v-if="rec.comment" style="font-size:12px;color:#606266">{{ rec.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.leave-records { display: flex; flex-direction: column; height: 100%; background: #f0f2f5; }
.page-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 24px; background: #fff; border-bottom: 1px solid #e4e7ed; flex-shrink: 0;
  h2 { margin: 0; font-size: 16px; display: flex; align-items: center; gap: 8px; color: #303133; }
}
.page-body { flex: 1; overflow-y: auto; padding: 16px; }
.empty { text-align: center; color: #c0c4cc; padding: 48px; font-size: 14px; }
.detail-chain { margin-top: 16px; }
.chain-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
</style>
