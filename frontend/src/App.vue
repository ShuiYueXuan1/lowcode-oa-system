<script setup lang="ts">
/**
 * App.vue —— 应用根组件
 * 顶部 Tab 导航：表单设计器 | 流程设计器
 */
import { ref } from 'vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import FlowDesigner from '@/components/designer/flow/FlowDesigner.vue'
import AttendanceConfig from '@/components/pages/AttendanceConfig.vue'
import LeaveManagement from '@/components/pages/LeaveManagement.vue'
import ApprovalPage from '@/components/pages/ApprovalPage.vue'
import AttendancePage from '@/components/pages/AttendancePage.vue'

const activeTab = ref<'form' | 'flow' | 'attendance' | 'leave' | 'approval' | 'punch'>('form')

const designTabs = [
  { key: 'form' as const, label: '表单设计器', icon: 'Edit' },
  { key: 'flow' as const, label: '流程设计器', icon: 'Connection' },
  { key: 'attendance' as const, label: '考勤规则', icon: 'Clock' },
]
const runTabs = [
  { key: 'leave' as const, label: '请假管理', icon: 'DocumentAdd' },
  { key: 'approval' as const, label: '审批工作台', icon: 'Checked' },
  { key: 'punch' as const, label: '员工打卡', icon: 'Clock' },
]
</script>

<template>
  <div class="app-root">
    <nav class="app-nav">
      <div class="nav-brand">低代码 OA 系统</div>
      <div class="nav-tabs">
        <div class="tab-group">
          <span class="tab-group-label">设计态</span>
          <button
            v-for="tab in designTabs"
            :key="tab.key"
            class="nav-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <el-icon :size="16"><component :is="tab.icon" /></el-icon>
            {{ tab.label }}
          </button>
        </div>
        <el-divider direction="vertical" />
        <div class="tab-group">
          <span class="tab-group-label">运行态</span>
          <button
            v-for="tab in runTabs"
            :key="tab.key"
            class="nav-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <el-icon :size="16"><component :is="tab.icon" /></el-icon>
            {{ tab.label }}
          </button>
        </div>
      </div>
    </nav>
    <div class="app-body">
      <FormDesigner v-show="activeTab === 'form'" />
      <FlowDesigner v-show="activeTab === 'flow'" />
      <AttendanceConfig v-show="activeTab === 'attendance'" />
      <LeaveManagement v-show="activeTab === 'leave'" />
      <ApprovalPage v-show="activeTab === 'approval'" />
      <AttendancePage v-show="activeTab === 'punch'" />
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial,
    sans-serif;
  -webkit-font-smoothing: antialiased;
}

#app {
  height: 100%;
}
</style>

<style scoped>
.app-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-nav {
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 20px;
  background: #1d1e1f;
  color: #fff;
  flex-shrink: 0;
  z-index: 20;
}

.nav-brand {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-right: 24px;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.nav-tabs {
  display: flex;
  gap: 4px;
  align-items: center;
}

.tab-group {
  display: flex;
  gap: 4px;
  align-items: center;
}

.tab-group-label {
  font-size: 10px;
  color: #606570;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-right: 2px;
  font-weight: 600;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #a0a4a8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-tab:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #e0e0e0;
}

.nav-tab.active {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-weight: 500;
}

.app-body {
  flex: 1;
  overflow: hidden;
}
</style>
