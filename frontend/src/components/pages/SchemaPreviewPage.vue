<script setup lang="ts">
/**
 * 全屏预览页面 —— 三种 Schema 的可视化 + JSON 双栏预览
 *
 * <p>根据 type 参数渲染不同内容：
 * <ul>
 *   <li>form — 左侧 FormRenderer 只读渲染表单，右侧原始 JSON</li>
 *   <li>flow — 左侧审批链可视化（圆标+卡片+箭头连接线），右侧原始 JSON</li>
 *   <li>attendance — 左侧规则说明（带颜色标识）+ 基础参数 + 只读日历，右侧原始 JSON</li>
 * </ul></p>
 *
 * <p>从版本历史弹窗点击"预览"按钮打开，全屏覆盖（z-index:3000）。</p>
 */
import { computed } from 'vue'
import FormRenderer from '@/components/renderer/FormRenderer.vue'
import CalendarPanel from '@/components/designer/attendance/CalendarPanel.vue'
import type { SpecialDay } from '@/types/attendance'

const props = defineProps<{
  visible: boolean
  /** 'form' | 'flow' | 'attendance' */
  type: string
  /** 版本数据（含 schemaJson、name、version 等） */
  data: Record<string, unknown> | null
}>()

const emit = defineEmits<{ 'update:visible': [boolean] }>()

/** 原始 JSON 字符串（右侧展示） */
const jsonStr = computed(() => JSON.stringify(props.data?.schemaJson || {}, null, 2))

/** 表单 Schema：从 data 中提取 name/code/labelWidth/fields */
const formSchema = computed(() => {
  if (props.type !== 'form' || !props.data?.schemaJson) return null
  const s = props.data.schemaJson as Record<string, unknown>
  return {
    name: (props.data.name as string) || '表单',
    code: (props.data.code as string) || '',
    labelWidth: (s.labelWidth as number) || 100,
    fields: (s.fields || []) as any[],
  }
})

/** 流程节点列表 */
const flowNodes = computed(() => {
  if (props.type !== 'flow' || !props.data?.schemaJson) return []
  return ((props.data.schemaJson as any)?.nodes || []) as any[]
})

/** 考勤规则的基础参数 */
const attRule = computed(() => {
  if (props.type !== 'attendance' || !props.data?.schemaJson) return null
  return (props.data.schemaJson as any)?.baseRule || null
})

/** 考勤规则的特殊日列表 */
const attSpecialDays = computed(() => {
  if (props.type !== 'attendance' || !props.data?.schemaJson) return []
  return ((props.data.schemaJson as any)?.specialDays || []) as any[]
})
</script>

<template>
  <div v-if="visible" class="preview-overlay">
    <div class="preview-header">
      <span class="preview-title">
        预览：{{ data?.name || data?.id || 'Schema' }}
        <el-tag size="small" style="margin-left:8px">v{{ data?.version || data?.id }}</el-tag>
      </span>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </div>

    <div class="preview-body">
      <div class="preview-left">
        <!-- ====== 表单预览 ====== -->
        <template v-if="type === 'form' && formSchema">
          <div class="vis-title">表单预览</div>
          <div class="vis-card">
            <FormRenderer :form-schema="formSchema" readonly @submit="() => {}" @cancel="() => {}" />
          </div>
        </template>

        <!-- ====== 流程预览 ====== -->
        <template v-else-if="type === 'flow'">
          <div class="vis-title">审批链预览</div>
          <div class="flow-panel">
            <div class="fp-begin">
              <div class="fp-begin-dot"><el-icon :size="18"><UserFilled /></el-icon></div>
              <span class="fp-begin-label">申请人提交</span>
            </div>
            <template v-for="(n, i) in flowNodes" :key="i">
              <div class="fp-connector">
                <div class="fp-conn-line"></div>
                <div class="fp-conn-arrow"></div>
              </div>
              <div class="fp-card" :class="{ 'is-skipped': n.conditions?.length > 0 }">
                <div class="fp-card-left">
                  <div class="fp-card-num">{{ i + 1 }}</div>
                </div>
                <div class="fp-card-body">
                  <div class="fp-card-header">
                    <span class="fp-card-name">{{ n.nodeName }}</span>
                    <el-tag size="small" type="info">{{ n.nodeCode }}</el-tag>
                  </div>
                  <div v-if="n.conditions?.length" class="fp-card-conds">
                    <div v-for="(c, ci) in n.conditions" :key="ci" class="fp-cond-item">
                      <el-icon :size="14"><SetUp /></el-icon>
                      <span>当 <code>{{ c.field }}</code> {{ c.operator }} <code>{{ c.value }}</code> 时，
                        <el-tag :type="c.action === 'SKIP' ? 'warning' : 'success'" size="small" effect="plain">
                          {{ c.action === 'SKIP' ? '跳过此节点' : '必须审批' }}
                        </el-tag>
                      </span>
                    </div>
                  </div>
                  <div v-else class="fp-no-cond">
                    <el-icon :size="14"><CircleCheck /></el-icon>
                    <span>无条件，始终需要审批</span>
                  </div>
                </div>
              </div>
            </template>
            <div class="fp-connector">
              <div class="fp-conn-line"></div>
              <div class="fp-conn-arrow"></div>
            </div>
            <div class="fp-end">
              <div class="fp-end-dot"><el-icon :size="18"><CircleCheckFilled /></el-icon></div>
              <span class="fp-end-label">审批完成</span>
            </div>
          </div>
        </template>

        <!-- ====== 考勤规则预览 ====== -->
        <template v-else-if="type === 'attendance' && attRule">
          <div class="vis-title">考勤规则预览</div>

          <!-- 规则说明：6 条带颜色标识的规则 -->
          <div class="att-section">
            <div class="att-section-title">规则说明</div>
            <div class="att-rules-desc">
              <div class="att-rule-line att-rule-normal">
                <el-icon :size="18"><Clock /></el-icon>
                <span>上班 <strong>{{ attRule.workStart }}</strong>，弹性 <strong>{{ attRule.flexMinutes }}</strong> 分钟内签到 → <el-tag type="success" size="small">正常</el-tag></span>
              </div>
              <div class="att-rule-line att-rule-warn">
                <el-icon :size="18"><WarningFilled /></el-icon>
                <span>超出弹性但 ≤ <strong>{{ attRule.lateThreshold }}</strong> 分钟 → <el-tag type="warning" size="small">迟到</el-tag></span>
              </div>
              <div class="att-rule-line att-rule-danger">
                <el-icon :size="18"><CircleCloseFilled /></el-icon>
                <span>超过 <strong>{{ attRule.lateThreshold }}</strong> 分钟 → <el-tag type="danger" size="small">严重迟到</el-tag></span>
              </div>
              <div class="att-rule-line att-rule-normal">
                <el-icon :size="18"><Clock /></el-icon>
                <span>下班 <strong>{{ attRule.workEnd }}</strong>，弹性 <strong>{{ attRule.flexMinutes }}</strong> 分钟内签退 → <el-tag type="success" size="small">正常</el-tag></span>
              </div>
              <div class="att-rule-line att-rule-warn">
                <el-icon :size="18"><WarningFilled /></el-icon>
                <span>提前离开但 ≤ <strong>{{ attRule.earlyThreshold }}</strong> 分钟 → <el-tag type="warning" size="small">早退</el-tag></span>
              </div>
              <div class="att-rule-line att-rule-danger">
                <el-icon :size="18"><CircleCloseFilled /></el-icon>
                <span>超过 <strong>{{ attRule.earlyThreshold }}</strong> 分钟 → <el-tag type="danger" size="small">严重早退</el-tag></span>
              </div>
            </div>
          </div>

          <!-- 基础参数 + 只读日历（左右并排） -->
          <div class="att-bottom">
            <div class="att-section att-params">
              <div class="att-section-title">基础参数</div>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="上班时间">{{ attRule.workStart }}</el-descriptions-item>
                <el-descriptions-item label="下班时间">{{ attRule.workEnd }}</el-descriptions-item>
                <el-descriptions-item label="弹性分钟">{{ attRule.flexMinutes }}</el-descriptions-item>
                <el-descriptions-item label="迟到阈值">{{ attRule.lateThreshold }}分钟</el-descriptions-item>
                <el-descriptions-item label="早退阈值">{{ attRule.earlyThreshold }}分钟</el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="att-section att-calendar">
              <div class="att-section-title">特殊日日历 ({{ attSpecialDays.length }}天)</div>
              <CalendarPanel
                :readonly="true"
                :external-special-days="(attSpecialDays as SpecialDay[])"
                :external-year="new Date().getFullYear()"
                :external-month="new Date().getMonth() + 1"
              />
            </div>
          </div>
        </template>
      </div>

      <!-- 右侧：原始 JSON -->
      <div class="preview-right">
        <div class="json-title">原始 JSON</div>
        <pre class="json-block">{{ jsonStr }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.preview-overlay { position:fixed;inset:0;z-index:3000;background:#f0f2f5;display:flex;flex-direction:column; }
.preview-header { display:flex;align-items:center;justify-content:space-between;padding:12px 24px;background:#fff;border-bottom:1px solid #e4e7ed;flex-shrink:0; }
.preview-title { font-size:16px;font-weight:600;color:#303133; }
.preview-body { flex:1;display:flex;gap:0;overflow:hidden; }
.preview-left { flex:1;overflow-y:auto;padding:20px 24px; }
.preview-right { width:380px;flex-shrink:0;overflow-y:auto;background:#fff;border-left:1px solid #e4e7ed;display:flex;flex-direction:column; }
.vis-title { font-size:14px;font-weight:600;color:#303133;margin-bottom:12px; }
.vis-card { background:#fff;border-radius:8px;border:1px solid #ebeef5;padding:20px; }
.json-title { font-size:13px;font-weight:600;color:#606266;padding:14px 16px;border-bottom:1px solid #ebeef5;flex-shrink:0; }
.json-block { flex:1;margin:0;padding:14px 16px;font-size:12px;line-height:1.7;white-space:pre-wrap;word-break:break-all;color:#303133; }

/* 流程预览 */
.flow-panel { display:flex;flex-direction:column;align-items:center;padding:24px 0; }
.fp-begin, .fp-end { display:flex;flex-direction:column;align-items:center;gap:6px; }
.fp-begin-dot, .fp-end-dot { width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center; }
.fp-begin-dot { background:#3d7a1c;color:#fff; }
.fp-end-dot { background:#1a6fb5;color:#fff; }
.fp-begin-label, .fp-end-label { font-size:14px;color:#606266;font-weight:500; }
.fp-connector { display:flex;flex-direction:column;align-items:center; }
.fp-conn-line { width:2px;height:22px;background:#a0cfff;border-radius:1px; }
.fp-conn-arrow { width:0;height:0;border-left:6px solid transparent;border-right:6px solid transparent;border-top:8px solid #a0cfff; }
.fp-card { display:flex;gap:14px;width:420px;padding:16px 20px;background:#fff;border:2px solid #409eff;border-radius:12px;box-shadow:0 2px 12px rgba(64,158,255,0.12);transition:all 0.2s; }
.fp-card.is-skipped { opacity:0.6;border-style:dashed;border-color:#e6a23c; }
.fp-card-left { display:flex;flex-direction:column;align-items:center;gap:0;flex-shrink:0; }
.fp-card-num { width:28px;height:28px;border-radius:50%;background:#409eff;color:#fff;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700; }
.fp-card.is-skipped .fp-card-num { background:#e6a23c; }
.fp-card-body { flex:1;min-width:0; }
.fp-card-header { display:flex;align-items:center;gap:8px;margin-bottom:8px; }
.fp-card-name { font-size:15px;font-weight:600;color:#303133; }
.fp-card-conds { display:flex;flex-direction:column;gap:6px; }
.fp-cond-item { display:flex;align-items:center;gap:6px;font-size:12px;color:#606266;background:#fafafa;padding:6px 10px;border-radius:6px; }
.fp-cond-item code { background:#ecf5ff;padding:1px 5px;border-radius:3px;font-size:12px;color:#409eff; }
.fp-no-cond { display:flex;align-items:center;gap:6px;font-size:12px;color:#67c23a; }

/* 考勤预览 */
.att-section { background:#fff;border-radius:8px;border:1px solid #e4e7ed;padding:16px 20px;margin-bottom:12px; }
.att-section-title { font-size:14px;font-weight:600;color:#303133;margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #ebeef5; }
.att-rules-desc { display:flex;flex-direction:column;gap:10px; }
.att-rule-line { display:flex;align-items:center;gap:10px;font-size:14px;padding:10px 14px;border-radius:8px;font-weight:500; }
.att-rule-normal { background:#f0f9eb;color:#3d7a1c; }
.att-rule-warn { background:#fef0e6;color:#9e5e0a; }
.att-rule-danger { background:#fef0f0;color:#b02828; }
.att-bottom { display:flex;gap:16px; }
.att-params { flex:0 0 280px; }
.att-calendar { flex:1;min-width:340px; }
</style>
