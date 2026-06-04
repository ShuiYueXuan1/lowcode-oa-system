<script setup lang="ts">
/**
 * FormDesigner —— 表单设计器主容器
 * 三栏布局：左侧组件库 | 中间画布 | 右侧属性面板
 */
import { ref } from 'vue'
import { useFormDesigner } from '@/composables/useFormDesigner'
import ComponentPanel from './ComponentPanel.vue'
import DesignerCanvas from './DesignerCanvas.vue'
import PropertyPanel from './PropertyPanel.vue'
import PreviewDialog from '@/components/renderer/PreviewDialog.vue'
import VersionDialog from './VersionDialog.vue'
import SchemaPreviewPage from '@/components/pages/SchemaPreviewPage.vue'
import { formSchemaApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const designer = useFormDesigner()
const previewVisible = ref(false)
const versionVisible = ref(false)
const versions = ref<Array<Record<string, unknown>>>([])
const saving = ref(false)
const previewOpen = ref(false)
const previewType = ref('form')
const previewData = ref<Record<string, unknown> | null>(null)

function openVersionPreview(row: Record<string, unknown>) {
  previewData.value = row
  previewType.value = 'form'
  previewOpen.value = true
}

async function handleSave() {
  if (designer.fields.value.length === 0) {
    ElMessage.warning('请先添加字段后再保存')
    return
  }
  saving.value = true
  try {
    const json = JSON.parse(JSON.stringify(designer.schemaJson.value))
    const res = await formSchemaApi.save(json)
    if (res.code === 200) {
      ElMessage.success(`保存成功！版本: v${res.data?.version}, code: ${res.data?.code}`)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.warning('后端未启动，Schema 已输出到控制台')
    console.log('Schema JSON:', JSON.stringify(designer.schemaJson.value, null, 2))
  } finally { saving.value = false }
}

async function openVersions() {
  try {
    const res = await formSchemaApi.getVersions(designer.formCode.value)
    if (res.code === 200) versions.value = res.data as Array<Record<string, unknown>>
  } catch { versions.value = [] }
  versionVisible.value = true
}

function handlePreview() {
  if (designer.fields.value.length === 0) {
    ElMessage.warning('请先添加字段后再预览')
    return
  }
  previewVisible.value = true
}

function handleClear() {
  if (designer.fields.value.length === 0) return
  ElMessageBox.confirm('确定要清空所有字段吗？', '确认', { type: 'warning' })
    .then(() => {
      designer.fields.value.length = 0
      designer.clearActiveField()
      ElMessage.success('已清空')
    })
    .catch(() => {})
}
</script>

<template>
  <div class="form-designer">
    <!-- 顶部工具栏 -->
    <header class="designer-header">
      <div class="header-left">
        <h2 class="header-title">
          <el-icon :size="22"><Edit /></el-icon>
          表单设计器
        </h2>
        <el-divider direction="vertical" />
        <el-input
          v-model="designer.formName.value"
          class="form-name-input"
          placeholder="表单名称"
          size="default"
        />
      </div>
      <div class="header-right">
        <el-button @click="handleClear" :disabled="designer.fields.value.length === 0">清空</el-button>
        <el-button @click="handlePreview" :disabled="designer.fields.value.length === 0">预览</el-button>
        <el-button @click="openVersions">版本历史</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Document /></el-icon>
          保存
        </el-button>
      </div>
    </header>

    <!-- 三栏主体 -->
    <div class="designer-body">
      <ComponentPanel />

      <DesignerCanvas />

      <PropertyPanel />
    </div>

    <!-- 预览弹窗 -->
    <PreviewDialog
      v-model:visible="previewVisible"
      :form-schema="designer.schemaJson.value"
    />

    <VersionDialog
      v-model:visible="versionVisible"
      title="表单 Schema"
      :versions="versions"
      @preview="openVersionPreview"
    />

    <SchemaPreviewPage
      v-model:visible="previewOpen"
      :type="previewType"
      :data="previewData"
    />
  </div>
</template>

<style scoped lang="scss">
.form-designer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
}

.designer-header {
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

.form-name-input {
  width: 200px;
}

.header-right {
  display: flex;
  gap: 8px;
}

.designer-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
</style>
