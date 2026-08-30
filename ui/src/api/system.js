// 系统管理相关接口（系统信息/缓存/定时任务/用户/MCP）（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getSystemDashboard = () => axios.get(`${API_BASE}/system/dashboard`).then((r) => r.data)

export const getSystemInfo = () => axios.get(`${API_BASE}/system/info`).then((r) => r.data)

export const getSystemVersion = () => axios.get(`${API_BASE}/system/version`).then((r) => r.data)

export const getCacheStats = () => axios.get(`${API_BASE}/cache/stats`).then((r) => r.data)

export const getCacheTypes = () => axios.get(`${API_BASE}/cache/types`).then((r) => r.data)

export const clearCacheExpired = () => axios.delete(`${API_BASE}/cache/expired`).then((r) => r.data)

export const clearCacheByType = (type) => axios.delete(`${API_BASE}/cache/type/${type}`).then((r) => r.data)

export const clearAllCache = () => axios.delete(`${API_BASE}/cache/all`).then((r) => r.data)

export const getScheduledTasks = () => axios.get(`${API_BASE}/admin/scheduled-tasks`).then((r) => r.data)

export const triggerScheduledTask = (id) =>
  axios.post(`${API_BASE}/admin/scheduled-tasks/${id}/trigger`).then((r) => r.data)

export const setScheduledTaskEnabled = (id, enabled) =>
  axios.put(`${API_BASE}/admin/scheduled-tasks/${id}/enabled?enabled=${enabled}`).then((r) => r.data)

export const triggerLibraryRematch = () =>
  axios.post(`${API_BASE}/admin/scheduled-tasks/library-rematch/trigger`).then((r) => r.data)

export const getUserRoles = () => axios.get(`${API_BASE}/users/roles`).then((r) => r.data)

export const getUsers = (params) => axios.get(`${API_BASE}/users`, { params }).then((r) => r.data)

export const updateUser = (id, data) => axios.put(`${API_BASE}/users/${id}`, data).then((r) => r.data)

export const getMcpConfig = () => axios.get(`${API_BASE}/mcp/config`).then((r) => r.data)

export const regenerateMcpConfig = () => axios.post(`${API_BASE}/mcp/config/regenerate`).then((r) => r.data)
