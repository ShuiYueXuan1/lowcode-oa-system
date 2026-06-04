<script setup lang="ts">
/**
 * PreviewDialog —— 表单预览弹窗
 * 在模态窗口中渲染 FormRenderer，模拟真实用户视角
 */
import { computed } from 'vue'
import type { FormSchema } from '@/types/form'
import FormRenderer from './FormRenderer.vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
  formSchema: FormSchema
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const schemaJsonString = computed(() =>
  JSON.stringify(props.formSchema, null, 2),
)

function handleSubmit(data: Record<string, unknown>) {
  console.log('表单提交数据：', JSON.stringify(data, null, 2))
  ElMessage.success({
    message: '表单数据已输出到控制台，请按 F12 查看',
    duration: 3000,
  })
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="`预览：${formSchema.name}`"
    width="750px"
    :close-on-click-modal="false"
    draggable
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <div class="preview-layout">
      <div class="preview-form">
        <FormRenderer
          :key="formSchema.name"
          :form-schema="formSchema"
          @submit="handleSubmit"
          @cancel="handleClose"
        />
      </div>

      <div class="preview-json">
        <div class="json-header">当前 Schema</div>
        <pre class="json-content">{{ schemaJsonString }}</pre>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.preview-layout {
  display: flex;
  gap: 24px;
  max-height: 65vh;
}

.preview-form {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-json {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.json-header {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
}

.json-content {
  flex: 1;
  overflow-y: auto;
  margin: 0;
  padding: 12px;
  font-size: 11px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
