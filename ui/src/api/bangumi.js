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

export const getAnimeWatchedEpisodes = (animeId) =>
  axios.get(`${API_BASE}/bangumi/animes/${animeId}/watched-episodes`).then((r) => r.data)

export const pullBangumiCollections = () =>
  axios.post(`${API_BASE}/bangumi/sync/pull-collections`).then((r) => r.data)

// ============ Bangumi 动画排行榜（发现页第三 Tab，仅动画） ============
// 参考 czy0729/Bangumi 客户端实现：后端按所配置的 next API 基址（官方或镜像）调用
// 开放条目浏览 /p1/subjects（匿名可用，无需登录态），支持平台/年份/月份/
// 来源/题材/地区/受众(公共标签)等组合筛选并分页返回。
// 条目卡片点击后跳转到本服务详情页（bgmMode），由
// /api/animes/bangumi/{bgmtvSubjectId}/raw-json 代理弹弹 /api/v2/bangumi/bgmtv/{id} 获取详情。

export const getBgmRankSubjects = (params) =>
  axios.get(`${API_BASE}/bangumi/rank/subjects`, { params }).then((r) => r.data)
