// 弹幕相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getMyDanmakuRecords = (params) =>
  axios.get(`${API_BASE}/v2/danmaku-records/mine`, { params }).then((r) => r.data)

export const getDanmakuComments = (episodeId) =>
  axios.get(`${API_BASE}/v2/comment/${episodeId}`, { params: { withRelated: true } }).then((r) => r.data)

export const sendDanmakuComment = (episodeId, data) =>
  axios.post(`${API_BASE}/v2/comment/${episodeId}/app`, data).then((r) => r.data)

export const getAdminDanmakuRecords = (params) =>
  axios.get(`${API_BASE}/admin/danmaku-records`, { params }).then((r) => r.data)
