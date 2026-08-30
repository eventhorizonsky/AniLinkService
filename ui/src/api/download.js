// 资源搜索与下载相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getResourceSubgroups = () => axios.get(`${API_BASE}/resource-search/subgroup`).then((r) => r.data)

export const getResourceTypes = () => axios.get(`${API_BASE}/resource-search/type`).then((r) => r.data)

export const searchResources = (params) =>
  axios.get(`${API_BASE}/resource-search/list`, { params }).then((r) => r.data)

export const downloadResource = (data) =>
  axios.post(`${API_BASE}/resource-search/download`, data).then((r) => r.data)

export const batchDownloadResource = (data) =>
  axios.post(`${API_BASE}/resource-search/download/batch`, data).then((r) => r.data)

export const getDownloadTasks = (params) =>
  axios.get(`${API_BASE}/resource-search/download-tasks`, { params }).then((r) => r.data)

export const cancelDownloadTask = (id) =>
  axios.post(`${API_BASE}/resource-search/download-tasks/${id}/cancel`).then((r) => r.data)

export const retryDownloadTask = (id) =>
  axios.post(`${API_BASE}/resource-search/download-tasks/${id}/retry`).then((r) => r.data)

export const deleteDownloadTask = (id) =>
  axios.delete(`${API_BASE}/resource-search/download-tasks/${id}`).then((r) => r.data)

export const getDownloadTaskBinding = (taskId) =>
  axios.get(`${API_BASE}/resource-search/download-tasks/${taskId}/binding`).then((r) => r.data)

export const getRssSubscriptions = () =>
  axios.get(`${API_BASE}/resource-search/rss-subscriptions`).then((r) => r.data)

export const createRssSubscription = (data) =>
  axios.post(`${API_BASE}/resource-search/rss-subscriptions`, data).then((r) => r.data)

export const updateRssSubscription = (id, data) =>
  axios.put(`${API_BASE}/resource-search/rss-subscriptions/${id}`, data).then((r) => r.data)

export const deleteRssSubscription = (id) =>
  axios.delete(`${API_BASE}/resource-search/rss-subscriptions/${id}`).then((r) => r.data)

export const triggerRssSubscription = (id) =>
  axios.post(`${API_BASE}/resource-search/rss-subscriptions/${id}/trigger`).then((r) => r.data)

export const getRssLastContent = (id) =>
  axios.get(`${API_BASE}/resource-search/rss-subscriptions/${id}/last-content`).then((r) => r.data)

export const previewRssSubscription = (data) =>
  axios.post(`${API_BASE}/resource-search/rss-subscriptions/preview`, data).then((r) => r.data)

export const getTrackerList = () =>
  axios.get(`${API_BASE}/resource-search/tracker-list/combined`).then((r) => r.data)

export const getTrackerStatus = () =>
  axios.get(`${API_BASE}/resource-search/tracker-list/status`).then((r) => r.data)

export const refreshTrackerList = () =>
  axios.post(`${API_BASE}/resource-search/tracker-list/refresh`).then((r) => r.data)

export const testResourceConnection = () =>
  axios.post(`${API_BASE}/resource-search/test-connection`).then((r) => r.data)
