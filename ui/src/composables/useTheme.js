import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'anilink-theme'

function prefersDark() {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  )
}

function resolveInitialTheme() {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') {
    return stored
  }
  return prefersDark() ? 'dark' : 'light'
}

function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

// 模块级单例，确保布局与页面共享同一状态
const theme = ref(resolveInitialTheme())
applyTheme(theme.value)

const isDark = computed(() => theme.value === 'dark')

watch(theme, (value) => {
  applyTheme(value)
  localStorage.setItem(STORAGE_KEY, value)
})

/**
 * 前台主题切换。
 * 初始跟随系统偏好，用户手动切换后持久化到 localStorage。
 */
export function useTheme() {
  const toggleTheme = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  const setTheme = (mode) => {
    if (mode === 'light' || mode === 'dark') {
      theme.value = mode
    }
  }

  return { theme, isDark, toggleTheme, setTheme }
}

export { theme, isDark }
