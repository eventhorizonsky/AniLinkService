import axios from 'axios'
import { useAuth } from '../composables/useAuth'
import { showSessionExpiredDialog } from './ui-feedback'

let initialized = false
let sessionExpiredShown = false

// 业务成功码。后端约定 code === 200，历史接口存在 code === 0，统一在这里兼容。
export const SUCCESS_CODES = [200, 0]

/**
 * 判断响应体是否为业务成功。
 * 注意：无 code 字段的响应（如部分原始数据/流接口）视为成功。
 */
export const isBusinessSuccess = (body) =>
  body && typeof body === 'object' && !Array.isArray(body) &&
  typeof body.code === 'number' && SUCCESS_CODES.includes(body.code)

/**
 * 业务错误（HTTP 200 但 code 非成功码）。
 * 携带 code / msg / response，兼容既有 `error.response?.data?.msg` 读取方式。
 */
export class BusinessError extends Error {
  constructor(body, status = 200) {
    super(body?.msg || `请求失败（code=${body?.code}）`)
    this.name = 'BusinessError'
    this.code = body?.code
    this.msg = body?.msg
    this.response = { data: body, status }
  }
}

/**
 * 统一解包响应体：业务失败时抛出 BusinessError，
 * 成功时直接返回 body.data。供调用方省去重复的 `code === 200` 判断。
 */
export const unwrap = (body) => {
  if (!body || typeof body !== 'object' || Array.isArray(body) || typeof body.code !== 'number') {
    return body
  }
  if (!SUCCESS_CODES.includes(body.code)) {
    throw new BusinessError(body)
  }
  return body.data
}

export function setupHttpInterceptors() {
  if (initialized) {
    return
  }
  initialized = true

  const { token, clearAuth } = useAuth()

  axios.interceptors.request.use(
    (config) => {
      if (token.value) {
        config.headers.satoken = token.value
      }
      return config
    },
    (error) => Promise.reject(error)
  )

  axios.interceptors.response.use(
    (response) => {
      // 统一拦截业务失败：HTTP 200 但 code 非成功码时按错误抛出，
      // 让各调用方不再各自判断，避免把业务失败当成功/空数据处理。
      const body = response?.data
      if (
        body &&
        typeof body === 'object' &&
        !Array.isArray(body) &&
        typeof body.code === 'number' &&
        !SUCCESS_CODES.includes(body.code)
      ) {
        return Promise.reject(new BusinessError(body, response.status))
      }
      return response
    },
    (error) => {
      const status = error?.response?.status
      const hasToken = !!token.value

      if (status === 401 && hasToken) {
        // 多个并行请求同时 401 时只弹一次对话框
        if (!sessionExpiredShown) {
          sessionExpiredShown = true
          clearAuth()
          showSessionExpiredDialog('登录状态已过期，请重新登录。确认后将返回首页。')
          setTimeout(() => {
            sessionExpiredShown = false
          }, 2000)
        }
      }

      return Promise.reject(error)
    }
  )
}
