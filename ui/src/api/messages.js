// 消息相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getUnreadCount = () => axios.get(`${API_BASE}/messages/unread-count`).then((r) => r.data)

export const getMessages = (params) => axios.get(`${API_BASE}/messages`, { params }).then((r) => r.data)

export const getMessagesByType = (type, params) =>
  axios.get(`${API_BASE}/messages/type/${type}`, { params }).then((r) => r.data)

export const markMessageRead = (id) => axios.put(`${API_BASE}/messages/${id}/read`).then((r) => r.data)

export const markAllMessagesRead = () => axios.put(`${API_BASE}/messages/mark-all-read`).then((r) => r.data)

export const markEpisodeMessagesRead = (episodeId) =>
  axios.put(`${API_BASE}/messages/read-by-episode/${encodeURIComponent(episodeId)}`).then((r) => r.data)

export const removeMessage = (id) => axios.delete(`${API_BASE}/messages/${id}`).then((r) => r.data)
