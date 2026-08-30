// 认证相关接口（返回响应体 res.data）。

import axios from 'axios'
import { API_BASE } from '../utils/constants'

export const login = (data) => axios.post(`${API_BASE}/auth/login`, data).then((r) => r.data)

export const register = (data) => axios.post(`${API_BASE}/auth/register`, data).then((r) => r.data)

export const getCaptcha = () => axios.get(`${API_BASE}/auth/captcha`).then((r) => r.data)

export const sendRegisterEmailCode = (data) =>
  axios.post(`${API_BASE}/auth/send-register-email-code`, data).then((r) => r.data)

export const getCurrentUser = () => axios.post(`${API_BASE}/auth/currentUser`).then((r) => r.data)
