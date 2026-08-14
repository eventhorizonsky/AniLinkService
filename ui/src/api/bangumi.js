// Bangumi 同步与收藏相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getBangumiAccountStatus = () =>
  axios.get(`${API_BASE}/bangumi/account/status`).then((r) => r.data)

export const bindBangumiAccount = (data) =>
  axios.post(`${API_BASE}/bangumi/account/bind`, data).then((r) => r.data)

export const unbindBangumiAccount = () =>
  axios.delete(`${API_BASE}/bangumi/account/bind`).then((r) => r.data)

export const getSubjectCollection = (subjectId) =>
  axios.get(`${API_BASE}/bangumi/subjects/${subjectId}/collection`).then((r) => r.data)

export const saveSubjectCollection = (subjectId, data) =>
  axios.post(`${API_BASE}/bangumi/subjects/${subjectId}/collection`, data).then((r) => r.data)

export const syncEpisodeWatched = (params) =>
  axios.post(`${API_BASE}/bangumi/sync/episode-watched`, null, { params }).then((r) => r.data)

export const pullBangumiCollections = () =>
  axios.post(`${API_BASE}/bangumi/sync/pull-collections`).then((r) => r.data)
