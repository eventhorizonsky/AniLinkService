// 追番相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getFollowStatus = (animeId) =>
  axios.get(`${API_BASE}/follows/${animeId}`).then((r) => r.data)

export const setFollowStatus = (animeId, status) =>
  axios.put(`${API_BASE}/follows/${animeId}/status`, null, { params: { status } }).then((r) => r.data)

export const createFollow = (data) => axios.post(`${API_BASE}/follows`, data).then((r) => r.data)

export const removeFollow = (animeId) =>
  axios.delete(`${API_BASE}/follows/${animeId}`).then((r) => r.data)

export const getActiveFollows = (params) =>
  axios.get(`${API_BASE}/follows/active`, { params }).then((r) => r.data)

export const getFollowsByStatus = (status, params) =>
  axios.get(`${API_BASE}/follows/status/${status}`, { params }).then((r) => r.data)

export const getFollows = (params) => axios.get(`${API_BASE}/follows`, { params }).then((r) => r.data)

export const bindFollow = (id, data) =>
  axios.put(`${API_BASE}/follows/${id}/bind`, data).then((r) => r.data)

export const matchFollow = (id) => axios.post(`${API_BASE}/follows/${id}/match`).then((r) => r.data)
