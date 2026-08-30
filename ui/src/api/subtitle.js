// 字幕相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getSubtitles = (videoId) =>
  axios.get(`${API_BASE}/media-files/${videoId}/subtitles`, { params: { _ts: Date.now() } }).then((r) => r.data)

export const getSubtitleDownloadUrl = (id) => `${API_BASE}/subtitles/${id}/download`

export const getSubtitleList = (params) => axios.get(`${API_BASE}/subtitles`, { params }).then((r) => r.data)

export const setSubtitleOffset = (id, offsetMs) =>
  axios.put(`${API_BASE}/subtitles/${id}/offset`, null, { params: { offset: offsetMs } }).then((r) => r.data)

export const deleteSubtitle = (id) => axios.delete(`${API_BASE}/subtitles/${id}`).then((r) => r.data)

export const uploadSubtitle = (formData, config = {}) =>
  axios.post(`${API_BASE}/subtitles/upload`, formData, config).then((r) => r.data)

export const rescanSubtitles = (mediaFileId) =>
  axios.post(`${API_BASE}/subtitles/rescan/${mediaFileId}`).then((r) => r.data)
