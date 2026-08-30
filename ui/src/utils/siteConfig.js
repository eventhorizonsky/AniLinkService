// siteConfig / installed 的本地缓存读写。
// 此前在 router/index.js、App.vue、MainLayout.vue、SiteConfig.vue、Install.vue
// 中各自手写 JSON.parse/localStorage，统一收敛到这里。

const SITE_CONFIG_KEY = 'siteConfig'
const INSTALLED_KEY = 'installed'

export function readInstalled() {
  return localStorage.getItem(INSTALLED_KEY) === 'true'
}

export function writeInstalled(value) {
  if (value) {
    localStorage.setItem(INSTALLED_KEY, 'true')
  } else {
    localStorage.removeItem(INSTALLED_KEY)
  }
}

export function readSiteConfig() {
  try {
    const raw = localStorage.getItem(SITE_CONFIG_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch (e) {
    return {}
  }
}

export function writeSiteConfig(config) {
  try {
    localStorage.setItem(SITE_CONFIG_KEY, JSON.stringify(config || {}))
  } catch (e) {
    console.error('保存配置失败:', e)
  }
}

export function updateSiteConfig(partial) {
  writeSiteConfig({ ...readSiteConfig(), ...(partial || {}) })
}

export function clearSiteConfig() {
  localStorage.removeItem(SITE_CONFIG_KEY)
  localStorage.removeItem(INSTALLED_KEY)
}

// 远程访问相关布尔值可能来自后端 true 或历史缓存 'true'，统一做容错解析。
const asBool = (value) => value === true || value === 'true'

export const remoteAccessEnabled = (config) => asBool(config?.remoteAccessEnabled)

export const remoteAccessTokenRequired = (config) => asBool(config?.remoteAccessTokenRequired)
