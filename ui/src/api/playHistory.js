// 播放历史相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const savePlayProgress = (data) => axios.post(`${API_BASE}/play-history/progress`, data).then((r) => r.data)

export const getPlayResume = (animeId) =>
  axios.get(`${API_BASE}/play-history/anime/${animeId}/resume`).then((r) => r.data)

export const getPlayHistory = (params) => axios.get(`${API_BASE}/play-history`, { params }).then((r) => r.data)

export const removePlayHistory = (id) => axios.delete(`${API_BASE}/play-history/${id}`).then((r) => r.data)

export const clearPlayHistory = () => axios.delete(`${API_BASE}/play-history/clear`).then((r) => r.data)
