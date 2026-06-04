<script setup lang="ts">
/**
 * ConditionEditor —— 条件分支编辑弹窗
 * 编辑选中节点的条件规则（field / operator / value / action）
 */
import { watch } from 'vue'
import { useFlowDesigner } from '@/composables/useFlowDesigner'
import { OPERATOR_LABELS, ACTION_LABELS } from '@/types/flow'
import type { ConditionOperator, ConditionAction } from '@/types/flow'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const designer = useFlowDesigner()

// 当弹窗打开时，如果节点没有条件则自动添加一个空条件
watch(() => props.visible, (val) => {
  if (val && designer.activeNode.value && designer.activeNode.value.conditions.length === 0) {
    designer.addCondition(designer.activeNode.value.nodeId)
  }
})

function handleAdd() {
  if (!designer.activeNode.value) return
  designer.addCondition(designer.activeNode.value.nodeId)
}

function handleRemove(index: number) {
  if (!designer.activeNode.value) return
  designer.removeCondition(designer.activeNode.value.nodeId, index)
}

function onFieldChange(index: number, val: string) {
  if (!designer.activeNode.value) return
  designer.updateCondition(designer.activeNode.value.nodeId, index, { field: val })
}

function onOperatorChange(index: number, val: ConditionOperator) {
  if (!designer.activeNode.value) return
  designer.updateCondition(designer.activeNode.value.nodeId, index, { operator: val })
}

function onValueChange(index: number, val: string) {
  if (!designer.activeNode.value) return
  const numVal = isNaN(Number(val)) ? val : Number(val)
  designer.updateCondition(designer.activeNode.value.nodeId, index, { value: numVal })
}

function onActionChange(index: number, val: ConditionAction) {
  if (!designer.activeNode.value) return
  designer.updateCondition(designer.activeNode.value.nodeId, index, { action: val })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="条件分支配置"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <template v-if="designer.activeNode.value">
      <div class="condition-header">
        <span class="condition-target">
          节点：<strong>{{ designer.activeNode.value.nodeName }}</strong>
        </span>
        <span class="condition-hint">
          配置条件后，运行时根据表单数据决定该节点是否需要审批
        </span>
      </div>

      <div class="condition-list">
        <div
          v-for="(cond, idx) in designer.activeNode.value.conditions"
          :key="idx"
          class="condition-row"
        >
          <span class="cond-label">条件 {{ idx + 1 }}</span>
          <div class="cond-inputs">
            <span class="cond-text">当</span>
            <el-input
              :model-value="cond.field"
              size="small"
              placeholder="表单字段 key"
              style="width: 130px"
              @input="(v: string) => onFieldChange(idx, v)"
            />
            <el-select
              :model-value="cond.operator"
              size="small"
              style="width: 110px"
              @change="(v: ConditionOperator) => onOperatorChange(idx, v)"
            >
              <el-option
                v-for="(label, op) in OPERATOR_LABELS"
                :key="op"
                :label="label"
                :value="op"
              />
            </el-select>
            <el-input
              :model-value="cond.value"
              size="small"
              placeholder="值"
              style="width: 100px"
              @input="(v: string) => onValueChange(idx, v)"
            />
            <span class="cond-text">时</span>
            <el-select
              :model-value="cond.action"
              size="small"
              style="width: 110px"
              @change="(v: ConditionAction) => onActionChange(idx, v)"
            >
              <el-option
                v-for="(label, act) in ACTION_LABELS"
                :key="act"
                :label="label"
                :value="act"
              />
            </el-select>
          </div>
          <el-button
            size="small"
            text
            type="danger"
            class="cond-remove"
            @click="handleRemove(idx)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

      <el-button size="small" type="primary" plain @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加条件
      </el-button>
    </template>

    <div v-else class="no-node">
      请先在画布中选中一个节点
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.condition-header {
  margin-bottom: 20px;
}

.condition-target {
  font-size: 14px;
  color: #303133;
  display: block;
  margin-bottom: 4px;
}

.condition-hint {
  font-size: 12px;
  color: #909399;
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.cond-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  width: 48px;
  flex-shrink: 0;
}

.cond-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
}

.cond-text {
  font-size: 12px;
  color: #909399;
}

.cond-remove {
  flex-shrink: 0;
}

.no-node {
  text-align: center;
  color: #c0c4cc;
  padding: 40px;
  font-size: 14px;
}
</style>
