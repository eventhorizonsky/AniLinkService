// 媒体库与媒体文件相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getLibraries = () => axios.get(`${API_BASE}/media-library`).then((r) => r.data)

export const getLibraryPaths = (rootPath, params = {}) =>
  axios.get(`${API_BASE}/media-library/paths`, { params: { rootPath, onlyDir: true, ...params } }).then((r) => r.data)

export const createLibrary = (data) => axios.post(`${API_BASE}/media-library`, data).then((r) => r.data)

export const removeLibrary = (id) => axios.delete(`${API_BASE}/media-library/${id}`).then((r) => r.data)

export const rematchLibrary = (id) => axios.post(`${API_BASE}/media-library/rematch/${id}`).then((r) => r.data)

export const scanLibrary = (id) => axios.post(`${API_BASE}/media-library/scan/${id}`).then((r) => r.data)

export const scanAllLibraries = () => axios.post(`${API_BASE}/media-library/scan-all`).then((r) => r.data)

export const getMediaFiles = (params) => axios.get(`${API_BASE}/media-files`, { params }).then((r) => r.data)

export const updateMediaFile = (fileId, data) =>
  axios.put(`${API_BASE}/media-files/${fileId}`, data).then((r) => r.data)

export const getRematchCandidates = (mediaFileId) =>
  axios.get(`${API_BASE}/media-files/${mediaFileId}/rematch-candidates`).then((r) => r.data)

export const removeMediaFile = (fileId, params) =>
  axios.delete(`${API_BASE}/media-files/${fileId}`, { params }).then((r) => r.data)

export const reprocessMediaFileMetadata = (id) =>
  axios.post(`${API_BASE}/media-files/reprocess-metadata/${id}`).then((r) => r.data)

export const getMetadataProgress = (params) =>
  axios.get(`${API_BASE}/media-files/queue/metadata-progress`, { params }).then((r) => r.data)

export const getMatchProgress = (params) =>
  axios.get(`${API_BASE}/media-files/queue/match-progress`, { params }).then((r) => r.data)

export const getMediaPlayInfo = (fileId) =>
  axios.get(`${API_BASE}/media-files/${fileId}/play-info`).then((r) => r.data)

// ---- 安装向导阶段的媒体库接口（/init 前缀） ----

export const getInitLibraries = () => axios.get(`${API_BASE}/init/media-library`).then((r) => r.data)

export const getInitLibraryPaths = (rootPath) =>
  axios.get(`${API_BASE}/init/media-library/paths`, { params: { rootPath, onlyDir: true } }).then((r) => r.data)

export const createInitLibrary = (data) => axios.post(`${API_BASE}/init/media-library`, data).then((r) => r.data)

export const removeInitLibrary = (id) => axios.delete(`${API_BASE}/init/media-library/${id}`).then((r) => r.data)
