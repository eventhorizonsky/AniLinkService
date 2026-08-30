// 远程访问凭据接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const getRemoteCredential = () =>
  axios.get(`${API_BASE}/remote-access/credential`).then((r) => r.data)

export const regenerateRemoteCredential = () =>
  axios.post(`${API_BASE}/remote-access/credential/regenerate`).then((r) => r.data)
