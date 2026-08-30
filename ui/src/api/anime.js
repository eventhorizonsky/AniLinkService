// 番剧相关接口。
// 此前各页面直接 axios.get(`${API_BASE}/...`) 并重复解析 res.data，统一收敛到这里。
// 约定：本模块函数均返回响应体（res.data），调用方直接判断 .code === 200。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getAnimeRawJson = (animeId) =>
  axios.get(`${API_BASE}/animes/${animeId}/raw-json`).then((r) => r.data)

export const getAnimeRawJsonBySubject = (subjectId) =>
  axios.get(`${API_BASE}/animes/bangumi/${subjectId}/raw-json`).then((r) => r.data)

export const getScheduleRawJson = () =>
  axios.get(`${API_BASE}/animes/shin/raw-json`).then((r) => r.data)

export const getAnimeList = (params) =>
  axios.get(`${API_BASE}/animes`, { params }).then((r) => r.data)

export const getAnimeEpisodes = (animeId, params) =>
  axios.get(`${API_BASE}/animes/${animeId}/episodes`, { params }).then((r) => r.data)

export const searchDandanAnimes = (keyword) =>
  axios.get(`${API_BASE}/animes/search-dandan`, { params: { keyword } }).then((r) => r.data)

export const getSeasonList = () =>
  axios.get(`${API_BASE}/v2/bangumi/season/anime`).then((r) => r.data)

export const getSeasonAnime = (year, month) =>
  axios.get(`${API_BASE}/v2/bangumi/season/anime/${year}/${month}`).then((r) => r.data)

export const getTrendingHot = () =>
  axios.get(`${API_BASE}/v2/trending/all/hot/week`).then((r) => r.data)

export const getTrendingNewAnime = () =>
  axios.get(`${API_BASE}/v2/trending/new-anime/hot/current-season`).then((r) => r.data)

export const searchEpisodes = (params) =>
  axios.get(`${API_BASE}/v2/search/episodes`, { params }).then((r) => r.data)

export const getSubjectComments = (subjectId, { limit, offset }) =>
  axios.get(`${API_BASE}/bangumi/subjects/${subjectId}/comments`, { params: { limit, offset } }).then((r) => r.data)

export const getEpisodeComments = (animeId, episodeNumber) =>
  axios.get(`${API_BASE}/bangumi/episodes/comments`, { params: { animeId, episodeNumber } }).then((r) => r.data)
