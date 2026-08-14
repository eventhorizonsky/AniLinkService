import { ref, computed } from 'vue'

// 模块级单例：token / userInfo 为响应式状态，所有组件共享同一份，
// 避免各处直接读 localStorage 导致登录/登出后 UI 不更新（此前依赖 location.reload()）。
const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'
const REMEMBER_ME_KEY = 'rememberMe'

function readUserInfo() {
  try {
    const raw = localStorage.getItem(USER_INFO_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const userInfo = ref(readUserInfo())

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)

  function setToken(value) {
    token.value = value || ''
    if (value) {
      localStorage.setItem(TOKEN_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  function setUserInfo(value) {
    userInfo.value = value
    if (value) {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(USER_INFO_KEY)
    }
  }

  function clearAuth() {
    setToken('')
    setUserInfo(null)
    localStorage.removeItem(REMEMBER_ME_KEY)
  }

  return { token, userInfo, isLoggedIn, setToken, setUserInfo, clearAuth }
}
