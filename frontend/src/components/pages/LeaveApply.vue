<script setup lang="ts">
/**
 * LeaveApply.vue —— 请假申请页面
 * 加载表单 Schema → 动态渲染 → 提交 → 触发审批流
 */
import { ref, onMounted, computed } from 'vue'
import FormRenderer from '@/components/renderer/FormRenderer.vue'
import type { FormSchema } from '@/types/form'
import { formSchemaApi, leaveApi } from '@/api'
import { ElMessage } from 'element-plus'

const formSchema = ref<FormSchema | null>(null)
const loading = ref(true)
const submitting = ref(false)
const result = ref<Record<string, unknown> | null>(null)

// 提取结果中的嵌套数据（避免模板中使用 as any）
const resultLeave = computed(() => result.value?.leaveInstance as Record<string, unknown> | undefined)
const resultProcess = computed(() => result.value?.processInstance as Record<string, unknown> | undefined)
const resultSnapshot = computed(() => result.value?.snapshot as Record<string, unknown> | undefined)
const resultNodes = computed(() => (resultSnapshot.value?.resolvedNodes || []) as Record<string, unknown>[])
const resultMessage = computed(() => (result.value?.message as string) || '')

// 模拟当前用户
const currentUser = { id: 1, name: '张三' }

onMounted(async () => {
  try {
    const res = await formSchemaApi.getByCode('leave_apply')
    if (res.code === 200 && res.data) {
      // 后端返回 DB 实体: { id, name, code, schemaJson: { labelWidth, fields }, ... }
      // FormRenderer 需要扁平: { name, code, labelWidth, fields }
      const entity = res.data as unknown as Record<string, unknown>
      const inner = (entity.schemaJson || entity) as Record<string, unknown>
      formSchema.value = {
        name: (entity.name as string) || '请假申请表',
        code: (entity.code as string) || 'leave_apply',
        labelWidth: (inner.labelWidth as number) || 100,
        fields: (inner.fields || []) as FormSchema['fields'],
      }
      loading.value = false
      return
    }
  } catch { /* fallback to mock */ }

  // Mock 数据（后端未启动时使用）
  formSchema.value = {
    name: '请假申请表',
    code: 'leave_apply',
    labelWidth: 100,
    fields: [
      { key: 'leave_type', type: 'select', label: '请假类型', placeholder: '请选择', rules: [{ required: true, message: '请选择请假类型' }], options: [{ label: '年假', value: 'annual' }, { label: '事假', value: 'personal' }, { label: '病假', value: 'sick' }] },
      { key: 'start_date', type: 'date', label: '开始日期', rules: [{ required: true, message: '请选择开始日期' }] },
      { key: 'end_date', type: 'date', label: '结束日期', rules: [{ required: true, message: '请选择结束日期' }] },
      { key: 'days', type: 'number', label: '请假天数', rules: [{ required: true, message: '请输入天数' }] },
      { key: 'reason', type: 'textarea', label: '请假原因', placeholder: '请输入请假原因', rules: [{ required: true, message: '请输入原因' }] },
    ],
  }
  loading.value = false
})

async function handleSubmit(data: Record<string, unknown>) {
  submitting.value = true
  result.value = null

  try {
    const res = await leaveApi.submit({
      formSchemaId: 1,
      formData: data,
      applicantId: currentUser.id,
      applicantName: currentUser.name,
    })
    if (res.code === 200) {
      result.value = res.data as Record<string, unknown>
      ElMessage.success('请假申请已提交')
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch {
    // 后端不可用时，模拟成功
    result.value = {
      leaveInstance: { id: Date.now(), applicantName: currentUser.name, status: 'PENDING', formData: data },
      processInstance: { id: Date.now(), status: 'IN_PROGRESS', currentNodeId: 'DIRECT_LEADER' },
      snapshot: { resolvedNodes: [{ nodeCode: 'DIRECT_LEADER', nodeName: '直属主管', required: true, conditions: [] }, { nodeCode: 'DEPT_MANAGER', nodeName: '部门经理', required: (data.days as number) > 3, conditions: [{ field: 'days', operator: '>', value: 3, action: 'REQUIRE' }] }, { nodeCode: 'HR', nodeName: '人事', required: true, conditions: [] }] },
      message: '(Mock) 后端未启动，这是模拟数据',
    }
    ElMessage.warning('后端未启动，使用 Mock 数据演示')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="leave-apply">
    <header class="page-header">
      <div class="header-left">
        <h2><el-icon :size="20"><DocumentAdd /></el-icon> 请假申请</h2>
        <el-tag v-if="currentUser" type="info">当前用户：{{ currentUser.name }}</el-tag>
      </div>
    </header>

    <div class="page-body">
      <div v-if="loading" class="loading-area">
        <el-icon :size="32" class="loading-icon"><Loading /></el-icon>
        <p>加载表单中...</p>
      </div>

      <template v-else-if="formSchema">
        <!-- 表单区域 -->
        <div class="form-area" v-if="!result">
          <el-card shadow="never">
            <template #header>
              <span class="card-title">{{ formSchema.name }}</span>
            </template>
            <FormRenderer
              :key="formSchema.code"
              :form-schema="formSchema"
              @submit="handleSubmit"
              @cancel="() => {}"
            />
          </el-card>
        </div>

        <!-- 提交结果 -->
        <div v-else class="result-area">
          <el-card shadow="never">
            <template #header>
              <span class="card-title">提交结果</span>
            </template>

            <el-result
              icon="success"
              title="请假申请已提交"
              :sub-title="resultMessage || '审批流程已启动'"
            >
              <template #extra>
                <div class="result-meta">
                  <el-descriptions :column="2" border size="small">
                    <el-descriptions-item label="请假单号">
                      {{ resultLeave?.id || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="当前状态">
                      <el-tag :type="resultLeave?.status === 'APPROVED' ? 'success' : 'warning'">
                        {{ resultLeave?.status || '-' }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="流程实例">
                      {{ resultProcess?.id || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="当前节点">
                      {{ resultProcess?.currentNodeId || '已结束' }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>

                <!-- 审批链可视化 -->
                <div v-if="resultNodes.length > 0" class="chain-preview">
                  <div class="chain-title">审批链预览</div>
                  <div class="chain-steps">
                    <template v-for="(node, idx) in resultNodes" :key="idx">
                      <div v-if="idx > 0" class="step-arrow">→</div>
                      <div
                        class="step-node"
                        :class="{ required: node.required, skipped: !node.required }"
                      >
                        <div class="step-name">{{ node.nodeName }}</div>
                        <div class="step-badge">
                          {{ node.required ? '必须审批' : '已跳过' }}
                        </div>
                      </div>
                    </template>
                  </div>
                </div>

                <el-button type="primary" @click="result = null; submitting = false">
                  再提交一张
                </el-button>
              </template>
            </el-result>
          </el-card>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.leave-apply {
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

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  justify-content: center;
}

.loading-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #909399;
  margin-top: 80px;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.form-area, .result-area {
  width: 100%;
  max-width: 720px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
}

.result-meta {
  margin: 16px 0;
}

.chain-preview {
  margin: 16px 0;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  text-align: left;
}

.chain-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.chain-steps {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.step-arrow {
  color: #c0c4cc;
  font-size: 14px;
  margin: 0 4px;
}

.step-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  gap: 4px;

  &.required {
    background: #ecf5ff;
    border: 1px solid #c6e2ff;
  }

  &.skipped {
    background: #f5f5f5;
    border: 1px solid #e0e0e0;
    opacity: 0.6;
  }
}

.step-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.step-badge {
  font-size: 11px;
  color: #909399;
}
</style>
