<script setup lang="ts">
/**
 * 版本历史弹窗 —— 通用的版本管理组件
 *
 * <p>用于表单 Schema、流程 Schema、考勤规则三个设计器的版本历史查看。
 * 支持：
 * <ul>
 *   <li>版本列表展示（版本号、名称、状态、保存时间）</li>
 *   <li>预览按钮 → emit('preview', row) 通知父组件打开全屏预览</li>
 *   <li>考勤规则专用：showSetCurrent=true 时显示"设为当前"按钮</li>
 * </ul></p>
 */
defineProps<{
  visible: boolean
  title: string
  versions: Record<string, unknown>[]
  showSetCurrent?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  'setCurrent': [id: number]
  'preview': [row: Record<string, unknown>]
}>()

/** 状态码 → 中文 */
function getStatusText(st: number) {
  const m: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已停用' }
  return m[st] || '未知'
}
/** 状态码 → Element Plus Tag 类型 */
function getStatusType(st: number) {
  const m: Record<number, string> = { 1: 'info', 2: 'success', 3: 'warning' }
  return m[st] || 'info'
}
/** 时间格式化：截取前 16 字符（YYYY-MM-DD HH:mm） */
function fmt(v: unknown) { return v ? String(v).substring(0, 16) : '-' }
</script>

<template>
  <el-dialog :model-value="visible" :title="title + ' - 版本历史'" width="700px" @update:model-value="emit('update:visible', $event)">
    <el-table :data="versions" size="small" stripe max-height="360">
      <el-table-column label="版本" width="60">
        <template #default="{ row }">v{{ row.version || row.id }}</template>
      </el-table-column>
      <el-table-column label="名称" min-width="120" prop="name" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="getStatusType((row.status ?? (row.isCurrent ? 2 : 1)) as number)" size="small">
            {{ row.isCurrent ? '生效中' : getStatusText((row.status ?? 1) as number) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="130">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="emit('preview', row)">预览</el-button>
          <el-button v-if="showSetCurrent" size="small" text type="success" @click="emit('setCurrent', row.id as number)">设为当前</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
