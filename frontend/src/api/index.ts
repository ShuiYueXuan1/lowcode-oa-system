/**
 * 统一 API 请求层
 *
 * 设计思路：
 * - 封装 fetch，统一处理 base URL、Content-Type、错误处理
 * - 所有后端接口集中定义，避免散落各处的硬编码 URL
 * - 支持泛型返回类型，方便类型推导
 * - 后端未启动时各调用方自行 catch 后使用 Mock 数据
 */
const BASE_URL = 'http://localhost:8080'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const fullUrl = url.startsWith('http') ? url : `${BASE_URL}${url}`
  const res = await fetch(fullUrl, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options?.headers as Record<string, string>) },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  return res.json() as Promise<ApiResponse<T>>
}

export const api = {
  get: <T>(url: string) => request<T>(url, { method: 'GET' }),
  post: <T>(url: string, data?: unknown) =>
    request<T>(url, { method: 'POST', body: data ? JSON.stringify(data) : undefined }),
  put: <T>(url: string, data?: unknown) =>
    request<T>(url, { method: 'PUT', body: data ? JSON.stringify(data) : undefined }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
}

// ==================== 业务 API ====================

import type { FormSchema } from '@/types/form'

/** 表单 Schema —— 设计态保存 + 运行态查询 */
export const formSchemaApi = {
  getByCode: (code: string) => api.get<FormSchema>(`/api/form-schema/code/${code}`),
  getById: (id: number) => api.get<FormSchema>(`/api/form-schema/${id}`),
  /** 保存（自动版本管理：code 存在则 version+1，不存在则 version=1） */
  save: (data: unknown) => api.post<{ id: number; version: number; code: string }>('/api/form-schema', data),
  /** 获取 code 对应的所有历史版本 */
  getVersions: (code: string) => api.get<Array<Record<string, unknown>>>(`/api/form-schema/versions/${code}`),
  publish: (id: number) => api.put(`/api/form-schema/${id}/publish`),
}

/** 流程 Schema —— 设计态保存 + 运行态查询 */
export const flowSchemaApi = {
  save: (data: unknown) => api.post<{ id: number; version: number }>('/api/flow-schema', data),
  getVersions: (code: string) => api.get<Array<Record<string, unknown>>>(`/api/flow-schema/versions/${code}`),
}

/** 考勤 Schema —— 设计态保存 + 版本管理 */
export const attendanceSchemaApi = {
  save: (data: unknown) => api.post<{ id: number }>('/api/attendance-schema', data),
  getCurrent: () => api.get<{ id: number; schemaJson: unknown }>('/api/attendance-schema/current'),
  getVersions: () => api.get<Array<Record<string, unknown>>>('/api/attendance-schema/versions'),
  setCurrent: (id: number) => api.put(`/api/attendance-schema/${id}/set-current`),
}

/** 请假业务 —— 提交、审批、查询 */
export const leaveApi = {
  submit: (data: { formSchemaId: number; formData: unknown; applicantId: number; applicantName: string }) =>
    api.post<{
      leaveInstance: { id: number; status: string }
      processInstance: { id: number; status: string; currentNodeId: string }
      snapshot: { resolvedNodes: unknown[] }
      message: string
    }>('/api/leave/submit', data),
  approve: (recordId: number, body: { approverId: number; approverName: string; comment: string }) =>
    api.put<{ processInstance: unknown; message: string }>(`/api/leave/approve/${recordId}`, body),
  reject: (recordId: number, body: { approverId: number; approverName: string; comment: string }) =>
    api.put<{ processInstance: unknown; message: string }>(`/api/leave/reject/${recordId}`, body),
  getDetail: (leaveId: number) =>
    api.get<{ leaveInstance: unknown; processInstance: unknown; approvalRecords: unknown[] }>(`/api/leave/${leaveId}`),
}

/** 审批工作台 —— 待审批列表 + 已处理 + 审批链记录 */
export const approvalApi = {
  getPending: (approverId: number) => api.get<unknown[]>(`/api/approval/pending?approverId=${approverId}`),
  getRecords: (processId: number) => api.get<unknown[]>(`/api/approval/records/${processId}`),
}

/** 员工打卡 —— 签到、签退、今日状态、月度汇总 */
export const attendanceApi = {
  signIn: (userId: number, userName: string) =>
    api.post<{ record: unknown; signInStatus: string; message: string; needWork?: boolean }>(
      '/api/attendance/sign-in', { userId, userName }),
  signOut: (userId: number, userName: string) =>
    api.post<{ record: unknown; signOutStatus: string; message: string }>(
      '/api/attendance/sign-out', { userId, userName }),
  getToday: (userId: number) =>
    api.get<{ today: string; needWork: boolean; dayReason: string; record: unknown }>(
      `/api/attendance/today?userId=${userId}`),
  getMonthly: (userId: number, year: number, month: number) =>
    api.get<Array<{ date: string; signInTime: string; signOutTime: string; status: string; statusLabel: string }>>(
      `/api/attendance/monthly?userId=${userId}&year=${year}&month=${month}`),
}

/** 审批节点库 —— 流程设计器加载 */
export const approvalNodeApi = {
  list: () => api.get<unknown[]>('/api/approval-node'),
}
