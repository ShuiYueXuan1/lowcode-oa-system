<script setup lang="ts">
/**
 * RuleEditor —— 基础考勤规则编辑器
 * 配置上下班时间、弹性分钟、迟到早退阈值
 */
import { computed } from 'vue'
import { useAttendanceDesigner } from '@/composables/useAttendanceDesigner'

const designer = useAttendanceDesigner()

const ruleError = computed(() => {
  const r = designer.baseRule.value
  if (r.flexMinutes >= r.lateThreshold) {
    return '弹性分钟必须小于迟到阈值，否则永远不会判定为"迟到"'
  }
  if (r.flexMinutes >= r.earlyThreshold) {
    return '弹性分钟必须小于早退阈值，否则永远不会判定为"早退"'
  }
  return ''
})
</script>

<template>
  <div class="rule-editor">
    <div class="section-title">基础规则</div>

    <div class="rule-grid">
      <div class="rule-item">
        <label>上班时间</label>
        <el-time-picker
          :model-value="designer.baseRule.value.workStart"
          format="HH:mm"
          value-format="HH:mm"
          size="small"
          style="width: 130px"
          @update:model-value="(v: string) => designer.baseRule.value.workStart = v"
        />
      </div>

      <div class="rule-item">
        <label>下班时间</label>
        <el-time-picker
          :model-value="designer.baseRule.value.workEnd"
          format="HH:mm"
          value-format="HH:mm"
          size="small"
          style="width: 130px"
          @update:model-value="(v: string) => designer.baseRule.value.workEnd = v"
        />
      </div>

      <div class="rule-item">
        <label>弹性分钟</label>
        <el-input-number
          :model-value="designer.baseRule.value.flexMinutes"
          :min="0"
          :max="60"
          size="small"
          style="width: 130px"
          @update:model-value="(v: number) => designer.baseRule.value.flexMinutes = v ?? 0"
        />
        <span class="unit">分钟</span>
      </div>

      <div class="rule-item">
        <label>迟到阈值</label>
        <el-input-number
          :model-value="designer.baseRule.value.lateThreshold"
          :min="1"
          :max="240"
          size="small"
          style="width: 130px"
          @update:model-value="(v: number) => designer.baseRule.value.lateThreshold = v ?? 30"
        />
        <span class="unit">分钟</span>
      </div>

      <div class="rule-item">
        <label>早退阈值</label>
        <el-input-number
          :model-value="designer.baseRule.value.earlyThreshold"
          :min="1"
          :max="240"
          size="small"
          style="width: 130px"
          @update:model-value="(v: number) => designer.baseRule.value.earlyThreshold = v ?? 30"
        />
        <span class="unit">分钟</span>
      </div>
    </div>

    <div v-if="ruleError" class="rule-error">
      <el-alert :title="ruleError" type="error" :closable="false" show-icon />
    </div>

    <div class="rule-desc">
      <el-alert
        title="规则说明"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <ul>
            <li>上班 {{ designer.baseRule.value.workStart }}，弹性 {{ designer.baseRule.value.flexMinutes }} 分钟内不算迟到</li>
            <li>超过弹性时间但未超过 {{ designer.baseRule.value.lateThreshold }} 分钟 → 迟到</li>
            <li>超过 {{ designer.baseRule.value.lateThreshold }} 分钟 → 严重迟到</li>
            <li>下班 {{ designer.baseRule.value.workEnd }}，弹性 {{ designer.baseRule.value.flexMinutes }} 分钟内不算早退</li>
            <li>超过弹性时间但未超过 {{ designer.baseRule.value.earlyThreshold }} 分钟离开 → 早退</li>
            <li>超过 {{ designer.baseRule.value.earlyThreshold }} 分钟离开 → 严重早退</li>
          </ul>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rule-editor {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.rule-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 10px;

  label {
    font-size: 13px;
    color: #606266;
    width: 80px;
    flex-shrink: 0;
  }
}

.unit {
  font-size: 12px;
  color: #909399;
}

.rule-error {
  margin-top: 12px;
}

.rule-desc {
  margin-top: 16px;

  ul {
    margin: 0;
    padding-left: 18px;
    font-size: 12px;
    color: #606266;
    line-height: 1.8;
  }
}
</style>
