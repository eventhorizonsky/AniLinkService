// 站点配置与安装相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getSiteConfig = () => axios.get(`${API_BASE}/site/config`).then((r) => r.data)

export const sendTestEmail = (data) => axios.post(`${API_BASE}/site/test-email`, data).then((r) => r.data)

export const saveSiteConfig = (data) => axios.put(`${API_BASE}/site/config`, data).then((r) => r.data)

export const initSiteConfig = (data) => axios.post(`${API_BASE}/init/site-config`, data).then((r) => r.data)

export const getInitSystemInfo = () => axios.get(`${API_BASE}/init/system-info`).then((r) => r.data)
