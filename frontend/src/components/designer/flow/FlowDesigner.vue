<script setup lang="ts">
/**
 * FlowDesigner —— 流程设计器主容器
 * 两栏布局：左侧节点库 | 中间链式画布
 */
import { ref, onMounted } from 'vue'
import { useFlowDesigner } from '@/composables/useFlowDesigner'
import NodeLibrary from './NodeLibrary.vue'
import FlowCanvas from './FlowCanvas.vue'
import ConditionEditor from './ConditionEditor.vue'
import VersionDialog from '@/components/designer/VersionDialog.vue'
import SchemaPreviewPage from '@/components/pages/SchemaPreviewPage.vue'
import { flowSchemaApi } from '@/api'
import type { ApprovalNodeItem } from '@/types/flow'
import { ElMessage } from 'element-plus'

const designer = useFlowDesigner()
const conditionEditorVisible = ref(false)
const versionVisible = ref(false)
const versions = ref<Array<Record<string, unknown>>>([])
const saving = ref(false)
const previewOpen = ref(false)
const previewData = ref<Record<string, unknown> | null>(null)

function openVersionPreview(row: Record<string, unknown>) {
  previewData.value = row
  previewOpen.value = true
}

// 加载节点库
onMounted(async () => {
  try {
    const res = await fetch('/api/approval-node')
    const json = await res.json()
    if (json.code === 200) {
      designer.setNodeLibrary(json.data as ApprovalNodeItem[])
    }
  } catch {
    // 后端未启动时使用 Mock 数据
    designer.setNodeLibrary([
      { id: 1, nodeCode: 'DIRECT_LEADER', nodeName: '直属主管', handlerType: 'com.oa.lowcode.handler.DirectLeaderHandler', description: '直属上级领导审批', sortOrder: 1 },
      { id: 2, nodeCode: 'DEPT_MANAGER', nodeName: '部门经理', handlerType: 'com.oa.lowcode.handler.DeptManagerHandler', description: '部门经理审批', sortOrder: 2 },
      { id: 3, nodeCode: 'HR', nodeName: '人事', handlerType: 'com.oa.lowcode.handler.HrHandler', description: '人事最终确认', sortOrder: 3 },
      { id: 4, nodeCode: 'GM', nodeName: '总经理', handlerType: 'com.oa.lowcode.handler.GmHandler', description: '总经理终审', sortOrder: 4 },
    ])
  }
})

async function handleSave() {
  if (designer.nodes.value.length === 0) { ElMessage.warning('请先添加节点'); return }
  saving.value = true
  try {
    const json = JSON.parse(JSON.stringify(designer.schemaJson.value))
    const res = await flowSchemaApi.save(json)
    if (res.code === 200) ElMessage.success(`保存成功！版本: v${res.data?.version}`)
    else ElMessage.error(res.message || '保存失败')
  } catch {
    ElMessage.warning('后端未启动，Schema 已输出到控制台')
    console.log('Flow Schema:', JSON.stringify(designer.schemaJson.value, null, 2))
  } finally { saving.value = false }
}

async function openVersions() {
  try {
    const res = await flowSchemaApi.getVersions(designer.flowCode.value)
    if (res.code === 200) versions.value = res.data as Array<Record<string, unknown>>
  } catch { versions.value = [] }
  versionVisible.value = true
}

function handleClear() {
  designer.nodes.value.length = 0
  designer.setActiveNode(null)
  ElMessage.success('已清空')
}
</script>

<template>
  <div class="flow-designer">
    <header class="flow-header">
      <div class="header-left">
        <h2 class="header-title">
          <el-icon :size="22"><Connection /></el-icon>
          流程设计器
        </h2>
        <el-divider direction="vertical" />
        <el-input
          v-model="designer.flowName.value"
          class="name-input"
          placeholder="流程名称"
          size="default"
        />
        <el-input
          v-model="designer.flowCode.value"
          class="code-input"
          placeholder="关联表单编码"
          size="default"
        />
      </div>
      <div class="header-right">
        <el-button @click="handleClear" :disabled="designer.nodes.value.length === 0">清空</el-button>
        <el-button @click="openVersions">版本历史</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Document /></el-icon>
          保存
        </el-button>
      </div>
    </header>

    <div class="flow-body">
      <NodeLibrary />
      <FlowCanvas @edit-conditions="conditionEditorVisible = true" />
    </div>

    <ConditionEditor
      v-model:visible="conditionEditorVisible"
    />

    <VersionDialog
      v-model:visible="versionVisible"
      title="流程 Schema"
      :versions="versions"
      @preview="openVersionPreview"
    />

    <SchemaPreviewPage
      v-model:visible="previewOpen"
      type="flow"
      :data="previewData"
    />
  </div>
</template>

<style scoped lang="scss">
.flow-designer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
}

.flow-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  z-index: 10;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.name-input {
  width: 180px;
}

.code-input {
  width: 160px;
}

.header-right {
  display: flex;
  gap: 8px;
}

.flow-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
</style>
