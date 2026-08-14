import axios from 'axios'
import { useAuth } from '../composables/useAuth'
import { showSessionExpiredDialog } from './ui-feedback'

let initialized = false

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
    (response) => response,
    (error) => {
      const status = error?.response?.status
      const hasToken = !!token.value

      if (status === 401 && hasToken) {
        clearAuth()
        showSessionExpiredDialog('登录状态已过期，请重新登录。确认后将返回首页。')
      }

      return Promise.reject(error)
    }
  )
}
