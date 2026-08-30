import { ref, computed, watch } from 'vue'
import {
  DEFAULT_THEME_COLOR_KEY,
  getThemeColorPreset,
  buildAccentStyleCss,
} from '../utils/themeColors'

const STORAGE_KEY = 'anilink-theme'
const ACCENT_STORAGE_KEY = 'anilink-theme-color'
const ACCENT_STYLE_ID = 'anilink-accent-style'

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

function resolveInitialAccentKey() {
  const stored = localStorage.getItem(ACCENT_STORAGE_KEY)
  if (stored && getThemeColorPreset(stored)) {
    return stored
  }
  return DEFAULT_THEME_COLOR_KEY
}

function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

/**
 * 将当前主题色预设以 <style> 形式注入文档末尾，
 * 覆盖 theme.css 中 :root / .dark 的默认强调色变量。
 */
function applyAccentStyle() {
  if (typeof document === 'undefined') {
    return
  }
  const preset = getThemeColorPreset(accentKey.value)
  if (!preset) {
    return
  }
  let styleEl = document.getElementById(ACCENT_STYLE_ID)
  if (!styleEl) {
    styleEl = document.createElement('style')
    styleEl.id = ACCENT_STYLE_ID
    document.head.appendChild(styleEl)
  }
  styleEl.textContent = buildAccentStyleCss(preset)
}

// 模块级单例，确保布局与页面共享同一状态
const theme = ref(resolveInitialTheme())
const accentKey = ref(resolveInitialAccentKey())
applyTheme(theme.value)
applyAccentStyle()

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

  /** 切换主题色预设（仅本地保存，不上服务端） */
  const setAccentColor = (key) => {
    if (!getThemeColorPreset(key)) {
      return
    }
    accentKey.value = key
    localStorage.setItem(ACCENT_STORAGE_KEY, key)
    applyAccentStyle()
  }

  return { theme, isDark, accentKey, toggleTheme, setTheme, setAccentColor }
}

export { theme, isDark, accentKey }
