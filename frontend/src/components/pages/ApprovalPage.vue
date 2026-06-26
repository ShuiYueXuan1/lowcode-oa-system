<script setup lang="ts">
/**
 * ApprovalPage.vue —— 审批工作台
 * 左侧：待审批列表（按审批人筛选）
 * 右侧：审批详情（表单数据 + 审批链 + 同意/驳回）
 */
import { ref, computed, onMounted, watch } from 'vue'
import { leaveApi, approvalApi } from '@/api'
import { ElMessage } from 'element-plus'

interface ApprovalItem {
  approvalRecord: { id: number; nodeName: string; action: string; createTime: string; processId: number }
  leaveInstance: { id: number; applicantName: string; formData: Record<string, unknown>; status: string }
  processInstance: { id: number; status: string; currentNodeId: string; snapshotJson: Record<string, unknown> }
}

interface ProcessRecord {
  id: number; nodeId: string; nodeName: string; approverName: string
  action: string; comment: string; handleTime: string; createTime: string
}

// 审批人选项对应数据库 sys_user 表中的真实用户 ID
// 组织架构: 王五(ID=2)=研发部leader/产品部leader, 赵六(ID=3)=总公司leader/ROLE_ADMIN, 许人事(ID=5)=ROLE_ADMIN
const approverId = ref(2)
const approverOptions = [
  { id: 2, name: '王五（研发部/产品部 leader，直属主管/部门经理）' },
  { id: 3, name: '赵六（总公司 leader，部门经理/总经理/人事）' },
  { id: 5, name: '许人事（行政人事部，人事）' },
]

const listTab = ref<'pending' | 'processed'>('pending')
const pendingList = ref<ApprovalItem[]>([])
const processedList = ref<ApprovalItem[]>([])
const currentList = computed(() => listTab.value === 'pending' ? pendingList.value : processedList.value)
const selectedItem = ref<ApprovalItem | null>(null)
const processRecords = ref<ProcessRecord[]>([])
const loading = ref(false)
const submitting = ref(false)
const comment = ref('')

function getStatusType(status: string) {
  const map: Record<string, string> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', IN_PROGRESS: '' }
  return map[status] || 'info'
}

function getStatusText(status: string) {
  const map: Record<string, string> = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', IN_PROGRESS: '审批中' }
  return map[status] || status
}

function getActionText(action: string) {
  const map: Record<string, string> = { APPROVE: '已同意', REJECT: '已驳回', PENDING: '待审批', SKIP: '已跳过' }
  return map[action] || action
}

function getActionType(action: string) {
  const map: Record<string, string> = { APPROVE: 'success', REJECT: 'danger', PENDING: 'warning', SKIP: 'info' }
  return map[action] || 'info'
}

async function loadPendingList() {
  loading.value = true
  try {
    const res = await approvalApi.getPending(approverId.value)
    if (res.code === 200) {
      pendingList.value = res.data as ApprovalItem[]
    }
  } catch {
    // Mock 数据
    if (approverId.value === 1001) {
      pendingList.value = [{
        approvalRecord: { id: 1, nodeName: '直属主管', action: 'PENDING', createTime: new Date().toISOString(), processId: 1 },
        leaveInstance: { id: 1, applicantName: '张三', formData: { leave_type: 'annual', start_date: '2025-03-10', end_date: '2025-03-12', days: 3, reason: '个人旅游' }, status: 'PENDING' },
        processInstance: { id: 1, status: 'IN_PROGRESS', currentNodeId: 'DIRECT_LEADER', snapshotJson: { resolvedNodes: [{ nodeCode: 'DIRECT_LEADER', nodeName: '直属主管', required: true, nodeId: 'n1', conditions: [] }, { nodeCode: 'DEPT_MANAGER', nodeName: '部门经理', required: false, nodeId: 'n2', conditions: [{ field: 'days', operator: '>', value: 3, action: 'REQUIRE' }] }, { nodeCode: 'HR', nodeName: '人事', required: true, nodeId: 'n3', conditions: [] }] } },
      }]
    } else {
      pendingList.value = []
    }
  } finally {
    loading.value = false
  }
}

async function loadProcessedList() {
  loading.value = true
  try {
    const res = await fetch(`/api/approval/processed?approverId=${approverId.value}`)
    const json = await res.json()
    if (json.code === 200) processedList.value = json.data as ApprovalItem[]
  } catch {
    processedList.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab: 'pending' | 'processed') {
  listTab.value = tab
  selectedItem.value = null
  processRecords.value = []
}

async function loadProcessRecords(processId: number) {
  try {
    const res = await approvalApi.getRecords(processId)
    if (res.code === 200) {
      processRecords.value = res.data as ProcessRecord[]
    }
  } catch {
    processRecords.value = []
  }
}

function selectItem(item: ApprovalItem) {
  selectedItem.value = item
  comment.value = ''
  loadProcessRecords(item.approvalRecord.processId)
}

async function handleApprove() {
  if (!selectedItem.value) return
  submitting.value = true
  const recordId = selectedItem.value.approvalRecord.id
  const approver = approverOptions.find(o => o.id === approverId.value)

  try {
    const res = await leaveApi.approve(recordId, {
      approverId: approverId.value,
      approverName: approver?.name || '审批人',
      comment: comment.value || '同意',
    })
    if (res.code === 200) {
      ElMessage.success((res.data as any)?.message || '审批通过')
      selectedItem.value = null
      processRecords.value = []
      loadAll()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.success('(Mock) 审批通过')
    selectedItem.value = null
    processRecords.value = []
    pendingList.value = []
  } finally {
    submitting.value = false
  }
}

async function handleReject() {
  if (!selectedItem.value) return
  submitting.value = true
  const recordId = selectedItem.value.approvalRecord.id
  const approver = approverOptions.find(o => o.id === approverId.value)

  try {
    const res = await leaveApi.reject(recordId, {
      approverId: approverId.value,
      approverName: approver?.name || '审批人',
      comment: comment.value || '驳回',
    })
    if (res.code === 200) {
      ElMessage.warning('已驳回')
      selectedItem.value = null
      processRecords.value = []
      loadAll()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.warning('(Mock) 已驳回')
    selectedItem.value = null
    processRecords.value = []
    pendingList.value = []
  } finally {
    submitting.value = false
  }
}

function getFormDataEntries(data: Record<string, unknown> | undefined) {
  if (!data) return []
  return Object.entries(data).map(([key, val]) => ({ key, value: String(val ?? '') }))
}

const resolvedNodes = computed(() => {
  const snapshot = selectedItem.value?.processInstance?.snapshotJson as Record<string, unknown> | undefined
  return (snapshot?.resolvedNodes || []) as Record<string, unknown>[]
})

watch(approverId, () => {
  selectedItem.value = null
  processRecords.value = []
  loadAll()
})

function loadAll() {
  loadPendingList()
  loadProcessedList()
}

onMounted(() => loadAll())
</script>

<template>
  <div class="approval-page">
    <header class="page-header">
      <h2><el-icon :size="20"><Checked /></el-icon> 审批工作台</h2>
      <div class="approver-switch">
        <span class="switch-label">当前审批人：</span>
        <el-select v-model="approverId" size="default" style="width: 200px">
          <el-option v-for="opt in approverOptions" :key="opt.id" :label="opt.name" :value="opt.id" />
        </el-select>
      </div>
    </header>

    <div class="page-body">
      <!-- 左侧：待审批列表 -->
      <div class="list-panel">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header-row">
              <div class="list-tabs">
                <button class="list-tab" :class="{ active: listTab === 'pending' }" @click="switchTab('pending')">
                  待审批 ({{ pendingList.length }})
                </button>
                <button class="list-tab" :class="{ active: listTab === 'processed' }" @click="switchTab('processed')">
                  已处理 ({{ processedList.length }})
                </button>
              </div>
              <el-button size="small" text @click="loadAll()">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-if="loading" class="list-empty">
            <el-icon :size="28"><Loading /></el-icon>
            <p>加载中...</p>
          </div>

          <div v-else-if="currentList.length === 0" class="list-empty">
            <el-icon :size="36"><FolderOpened /></el-icon>
            <p>{{ listTab === 'pending' ? '暂无待审批记录' : '暂无已处理记录' }}</p>
          </div>

          <div v-else class="list-items">
            <div
              v-for="item in currentList"
              :key="item.approvalRecord.id"
              class="list-item"
              :class="{ active: selectedItem?.approvalRecord.id === item.approvalRecord.id }"
              @click="selectItem(item)"
            >
              <div class="item-header">
                <span class="item-applicant">{{ item.leaveInstance?.applicantName }}</span>
                <el-tag :type="getStatusType(item.leaveInstance?.status)" size="small">
                  {{ getStatusText(item.leaveInstance?.status) }}
                </el-tag>
              </div>
              <div class="item-meta">
                <span>节点：{{ item.approvalRecord.nodeName }}</span>
              </div>
              <div class="item-time">{{ item.approvalRecord.createTime?.substring(0, 16) }}</div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧：审批详情 -->
      <div class="detail-panel">
        <template v-if="!selectedItem">
          <div class="detail-empty">
            <el-icon :size="48"><Document /></el-icon>
            <p>请从左侧选择一条待审批记录</p>
          </div>
        </template>

        <template v-else>
          <!-- 表单数据 -->
          <el-card shadow="never" class="detail-card">
            <template #header>
              <span class="panel-title">请假详情</span>
            </template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item
                v-for="entry in getFormDataEntries(selectedItem.leaveInstance?.formData)"
                :key="entry.key"
                :label="entry.key"
              >
                {{ entry.value }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 审批链 -->
          <el-card shadow="never" class="detail-card">
            <template #header>
              <span class="panel-title">审批链</span>
            </template>
            <div class="chain-viz">
              <template v-for="(node, idx) in resolvedNodes" :key="idx">
                <div v-if="idx > 0" class="chain-connector">
                  <div class="connector-line"></div>
                </div>
                <div
                  class="chain-node"
                  :class="{
                    required: node.required,
                    skipped: !node.required,
                  }"
                >
                  <div class="node-order">{{ idx + 1 }}</div>
                  <div class="node-info">
                    <div class="node-name">{{ node.nodeName }}</div>
                    <div class="node-status">
                      {{ node.required ? '待审批' : '条件不满足，已跳过' }}
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </el-card>

          <!-- 审批记录 -->
          <el-card v-if="processRecords.length > 0" shadow="never" class="detail-card">
            <template #header>
              <span class="panel-title">审批记录</span>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="rec in processRecords"
                :key="rec.id"
                :timestamp="rec.handleTime || rec.createTime"
                placement="top"
                :type="getActionType(rec.action) === 'success' ? 'success' : getActionType(rec.action) === 'danger' ? 'danger' : 'primary'"
              >
                <div class="timeline-content">
                  <strong>{{ rec.nodeName }}</strong>
                  <el-tag :type="getActionType(rec.action)" size="small" style="margin-left: 8px">
                    {{ getActionText(rec.action) }}
                  </el-tag>
                  <div v-if="rec.approverName" class="timeline-approver">
                    {{ rec.approverName }}
                  </div>
                  <div v-if="rec.comment" class="timeline-comment">{{ rec.comment }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>

          <!-- 操作区（仅待审批时显示） -->
          <el-card v-if="selectedItem.approvalRecord.action === 'PENDING'" shadow="never" class="detail-card">
            <template #header>
              <span class="panel-title">审批操作</span>
            </template>
            <div class="action-area">
              <el-input
                v-model="comment"
                type="textarea"
                :rows="3"
                placeholder="审批意见（可选）"
              />
              <div class="action-buttons">
                <el-button
                  type="success"
                  :loading="submitting"
                  @click="handleApprove"
                >
                  <el-icon><Select /></el-icon>
                  同意
                </el-button>
                <el-button
                  type="danger"
                  :loading="submitting"
                  @click="handleReject"
                >
                  <el-icon><CloseBold /></el-icon>
                  驳回
                </el-button>
              </div>
            </div>
          </el-card>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.approval-page {
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

  h2 {
    margin: 0;
    font-size: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #303133;
  }
}

.approver-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-label {
  font-size: 13px;
  color: #606266;
}

.page-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.list-panel {
  width: 340px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  overflow: hidden;
}

.panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    overflow-y: auto;
    padding: 0;
  }
}

.panel-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-tabs {
  display: flex; gap: 0;
}
.list-tab {
  padding: 4px 12px; border: 1px solid #dcdfe6; background: #fff;
  font-size: 13px; color: #606266; cursor: pointer; transition: all 0.2s;
  &:first-child { border-radius: 4px 0 0 4px; }
  &:last-child { border-radius: 0 4px 4px 0; }
  &:hover { color: #409eff; }
  &.active { background: #409eff; color: #fff; border-color: #409eff; }
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
}

.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 0;
  color: #c0c4cc;
  font-size: 13px;
}

.list-items {
  padding: 0;
}

.list-item {
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  transition: background 0.15s;

  &:hover { background: #f5f7fa; }
  &.active { background: #ecf5ff; border-left: 3px solid #409eff; }
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.item-applicant {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.item-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.item-time {
  font-size: 11px;
  color: #c0c4cc;
}

.detail-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  gap: 12px;
  font-size: 14px;
}

.detail-card {
  :deep(.el-card__body) {
    padding: 12px 20px 16px;
  }
}

.chain-viz {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.chain-node {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  width: 100%;

  &.required { background: #ecf5ff; border: 1px solid #d9ecff; }
  &.skipped { background: #fafafa; border: 1px solid #ebeef5; opacity: 0.55; }
}

.chain-connector {
  display: flex;
  justify-content: center;
  width: 40px;
  margin-left: 14px;
}

.connector-line {
  width: 2px;
  height: 20px;
  background: #c0c4cc;
}

.node-order {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;

  .skipped & { background: #c0c4cc; }
}

.node-info {
  flex: 1;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.node-status {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.timeline-content {
  font-size: 13px;
}

.timeline-approver {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.timeline-comment {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  font-style: italic;
}

.action-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
