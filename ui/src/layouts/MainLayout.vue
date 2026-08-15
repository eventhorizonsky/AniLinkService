<template>
  <div class="main-layout">
    <!-- ===== 侧边栏 ===== -->
    <aside class="app-sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-brand">
        <i class="mdi mdi-magic-staff brand-icon"></i>
        <div class="brand-text">
          <h1>{{ siteConfig?.siteName || DEFAULT_SITE_NAME }}</h1>
          <span>AniLink · 追番</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-label">导航</div>
        <router-link
          v-for="item in navPrimary"
          :key="'p' + item.label"
          :to="item.to"
          class="nav-link"
          :active-class="item.exactOnly ? undefined : 'nav-active'"
          exact-active-class="nav-active"
          @click="sidebarOpen = false"
        >
          <i class="mdi" :class="item.icon"></i>
          <span class="nav-text">{{ item.label }}</span>
        </router-link>

        <template v-if="isLoggedIn">
          <div class="nav-label">我的</div>
          <router-link
            v-for="item in navMine"
            :key="'m' + item.label"
            :to="item.to"
            class="nav-link"
            :active-class="item.exactOnly ? undefined : 'nav-active'"
            exact-active-class="nav-active"
            @click="sidebarOpen = false"
          >
            <i class="mdi" :class="item.icon"></i>
            <span class="nav-text">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <div class="sidebar-footer">
        <button class="footer-link theme-footer-btn" @click="toggleTheme">
          <i class="mdi" :class="isDark ? 'mdi-weather-night' : 'mdi-white-balance-sunny'"></i>
          {{ isDark ? '浅色模式' : '深色模式' }}
        </button>
        <router-link v-if="isAdmin" to="/admin" class="footer-link">
          <i class="mdi mdi-cog"></i> 后台管理
        </router-link>
        <a v-if="isLoggedIn" href="#" class="footer-link" @click.prevent="handleLogout">
          <i class="mdi mdi-logout"></i> 退出登录
        </a>
        <a v-else href="#" class="footer-link" @click.prevent="showLoginDialog = true">
          <i class="mdi mdi-login"></i> 登录 / 注册
        </a>
      </div>
    </aside>

    <!-- 移动端遮罩 -->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- ===== 主区 ===== -->
    <main class="app-main">
      <!-- 内容区（顶部栏作为内容区首行，随内容滚动，与参考布局一致） -->
      <div class="app-content">
        <!-- 顶部栏 -->
        <header class="app-topbar">
          <button class="menu-toggle" aria-label="切换侧栏" @click="sidebarOpen = !sidebarOpen">
            <i class="mdi mdi-menu"></i>
          </button>

        <div class="search-wrap">
          <i class="mdi mdi-magnify"></i>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索动漫、角色、声优…"
            @keyup.enter="handleSearch"
          />
          <span class="shortcut">⌘K</span>
        </div>

        <div class="topbar-actions">
          <!-- 主题切换 -->
          <button
            class="theme-toggle"
            :title="isDark ? '切换到浅色主题' : '切换到深色主题'"
            aria-label="切换主题"
            @click="toggleTheme"
          >
            <i class="mdi" :class="isDark ? 'mdi-weather-night' : 'mdi-white-balance-sunny'"></i>
          </button>

          <!-- 消息铃铛和下拉 -->
          <MessageBell v-if="isLoggedIn" v-model="messageMenuOpen" />

          <!-- 用户菜单 -->
          <div class="user-menu-wrapper">
            <button class="avatar" :title="isLoggedIn ? currentUser : '登录'" @click="userMenuOpen = !userMenuOpen">
              <template v-if="isLoggedIn">{{ avatarChar }}</template>
              <i v-else class="mdi mdi-account"></i>
            </button>
            <div v-if="userMenuOpen" class="user-dropdown" @wheel.stop>
              <a v-if="!isLoggedIn" href="#" @click.prevent="showLoginDialog = true; userMenuOpen = false" class="dropdown-item">
                <i class="mdi mdi-login"></i>
                <span>登录</span>
              </a>
              <a
                v-if="!isLoggedIn && isRegisterOpen"
                href="#"
                @click.prevent="openRegisterDialog(); userMenuOpen = false"
                class="dropdown-item"
              >
                <i class="mdi mdi-account-plus"></i>
                <span>注册</span>
              </a>

              <template v-if="isLoggedIn">
                <div class="dropdown-header">
                  <span class="username">{{ currentUser }}</span>
                </div>
                <a href="#" @click.prevent="goToProfile" class="dropdown-item">
                  <i class="mdi mdi-account-circle"></i>
                  <span>个人中心</span>
                </a>
                <div class="dropdown-divider"></div>
                <a v-if="isAdmin" href="#" @click.prevent="goToAdmin" class="dropdown-item">
                  <i class="mdi mdi-cog"></i>
                  <span>后台管理</span>
                </a>
                <a href="#" @click.prevent="handleLogout" class="dropdown-item logout">
                  <i class="mdi mdi-logout"></i>
                  <span>登出</span>
                </a>
              </template>
            </div>
          </div>
        </div>
        </header>

        <router-view />
      </div>
    </main>

    <!-- 登录 / 注册弹窗 -->
    <LoginDialog
      v-model="showLoginDialog"
      :register-enabled="isRegisterOpen"
      @request-register="openRegisterDialog"
      @login-success="fetchUserInfo"
    />
    <RegisterDialog
      v-model="showRegisterDialog"
      :captcha-enabled="true"
      @request-login="openLoginDialog"
      @register-success="openLoginDialog"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTheme } from '../composables/useTheme'
import { useAuth } from '../composables/useAuth'
import { DEFAULT_SITE_NAME, hasRoleLevel, isSuperAdmin } from '../utils/constants'
import { getCurrentUser } from '../api/auth'
import { readSiteConfig, remoteAccessEnabled, remoteAccessTokenRequired } from '../utils/siteConfig'
import LoginDialog from '../components/LoginDialog.vue'
import RegisterDialog from '../components/RegisterDialog.vue'
import MessageBell from '../components/MessageBell.vue'

const { isDark, toggleTheme } = useTheme()
const { token, userInfo, isLoggedIn, setUserInfo, clearAuth } = useAuth()
const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const showLoginDialog = ref(false)
const showRegisterDialog = ref(false)
const userMenuOpen = ref(false)
const sidebarOpen = ref(false)
const siteConfig = ref(readSiteConfig())

// 消息下拉开关（由 MessageBell 通过 v-model 控制）
const messageMenuOpen = ref(false)

// 侧边栏导航（显隐规则沿用旧版顶部导航）
const navPrimary = computed(() => [
  { label: '首页', icon: 'mdi-home', to: '/', exactOnly: true },
  { label: '发现', icon: 'mdi-compass', to: '/search' },
  { label: '新番时间表', icon: 'mdi-calendar-week', to: '/schedule' },
  ...(isRemoteAccessVisible.value ? [{ label: '远程访问', icon: 'mdi-lan-connect', to: '/remote-access' }] : [])
])

const navMine = [
  { label: '观看历史', icon: 'mdi-history', to: '/profile/history', exactOnly: true },
  { label: '我的追番', icon: 'mdi-bookmark-multiple', to: '/profile/follows', exactOnly: true },
  { label: '我的弹幕', icon: 'mdi-comment-text-multiple', to: '/profile/danmaku', exactOnly: true },
  { label: '消息中心', icon: 'mdi-bell-outline', to: '/profile/messages', exactOnly: true },
  { label: '账号绑定', icon: 'mdi-link-variant', to: '/profile/binding', exactOnly: true }
]

const avatarChar = computed(() => {
  const name = userInfo.value?.username
  return name ? name.charAt(0).toUpperCase() : '用'
})

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await getCurrentUser()
    if (res?.code === 200 && res?.data) {
      const userData = res.data
      setUserInfo(userData)
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    // 如果获取失败，清除登录状态
    handleLogout()
  }
}

// 检查登录状态：token/userInfo 已由 useAuth 从 localStorage 初始化，这里仅拉取最新用户信息。
const checkLoginStatus = () => {
  if (token.value) {
    fetchUserInfo()
  }
}

const isRegisterOpen = computed(() => {
  return !!readSiteConfig()?.authRegisterEnabled
})

const isRemoteAccessVisible = computed(() => {
  const config = readSiteConfig()
  const enabled = remoteAccessEnabled(config)
  if (!enabled) {
    return false
  }
  if (!remoteAccessTokenRequired(config)) {
    return true
  }
  if (!isLoggedIn.value) {
    return false
  }
  return hasRoleLevel(userInfo.value, config?.remoteAccessRequiredRole)
})

const currentUser = computed(() => {
  return userInfo.value?.username || ''
})

const isAdmin = computed(() => isSuperAdmin(userInfo.value))

const syncDocumentTitle = () => {
  if (typeof document === 'undefined') {
    return
  }
  document.title = siteConfig.value?.siteName || DEFAULT_SITE_NAME
}

// 点击外部关闭下拉菜单 / 移动端收起侧边栏
const handleClickOutside = (event) => {
  if (messageMenuOpen.value) {
    const messageWrapper = event.target.closest('.message-wrapper')
    if (!messageWrapper) {
      messageMenuOpen.value = false
    }
  }
  if (userMenuOpen.value) {
    const userMenuWrapper = event.target.closest('.user-menu-wrapper')
    if (!userMenuWrapper) {
      userMenuOpen.value = false
    }
  }
}

// 键盘快捷键：Ctrl/⌘ + K 聚焦搜索；Escape 收起侧边栏
const onKeydown = (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    const input = document.querySelector('.search-wrap input')
    if (input) input.focus()
  }
  if (e.key === 'Escape' && sidebarOpen.value) {
    sidebarOpen.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    const contentEl = document.querySelector('.app-content')
    if (contentEl) {
      contentEl.scrollTop = 0
    }
  }
)

watch(
  () => siteConfig.value?.siteName,
  () => {
    syncDocumentTitle()
  },
  { immediate: true }
)

onMounted(() => {
  checkLoginStatus()
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', onKeydown)
})

const handleSearch = () => {
  const keyword = searchQuery.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword, page: '1' } : { page: '1' }
  })
}

const openLoginDialog = () => {
  showRegisterDialog.value = false
  showLoginDialog.value = true
  userMenuOpen.value = false
}

const openRegisterDialog = () => {
  if (!isRegisterOpen.value) {
    return
  }
  showLoginDialog.value = false
  showRegisterDialog.value = true
  userMenuOpen.value = false
}

const handleLogout = () => {
  clearAuth()
  userMenuOpen.value = false
  sidebarOpen.value = false
  router.push('/')
}

const goToProfile = () => {
  userMenuOpen.value = false
  router.push('/profile')
}

const goToMessages = () => {
  userMenuOpen.value = false
  router.push('/profile/messages')
}

const goToAdmin = () => {
  userMenuOpen.value = false
  router.push('/admin')
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

#app {
  height: 100%;
  overflow: hidden;
}

/* ===== 主布局：侧边栏 + 主区 ===== */
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--al-bg);
  position: relative;
}

/* ===== 侧边栏 ===== */
.app-sidebar {
  flex: 0 0 230px;
  width: 230px;
  height: 100%;
  background: linear-gradient(180deg, var(--al-sidebar-bg-1) 0%, var(--al-sidebar-bg-2) 100%);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(196, 93, 43, 0.14);
  padding: 28px 16px 24px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  z-index: 100;
  box-shadow: 2px 0 24px rgba(0, 0, 0, 0.02);
  transition: transform 0.35s ease;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px 28px 6px;
  border-bottom: 1px solid var(--al-border-neutral);
  margin-bottom: 20px;
}
.sidebar-brand .brand-icon {
  font-size: 28px;
  line-height: 1;
  color: var(--anime-accent-red);
}
.brand-text h1 {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
  line-height: 1.2;
  color: var(--anime-accent-red);
}
@supports (background-clip: text) or (-webkit-background-clip: text) {
  .brand-text h1 {
    background: linear-gradient(135deg, var(--anime-accent-red), var(--al-accent-gold));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}
.brand-text span {
  font-size: 12px;
  font-weight: 400;
  color: var(--anime-text-secondary);
  display: block;
  margin-top: 2px;
  -webkit-text-fill-color: var(--anime-text-secondary);
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  color: var(--al-text-faint);
  padding: 14px 12px 8px;
}

.sidebar-nav a.nav-link {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 500;
  color: var(--al-text-brown-13);
  text-decoration: none;
  transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}
.sidebar-nav a.nav-link i {
  width: 20px;
  font-size: 16px;
  text-align: center;
  color: var(--al-text-faint);
  transition: 0.3s;
}
.sidebar-nav a.nav-link:hover {
  background: var(--al-bg-beige-3);
  color: var(--anime-text-main);
}
.sidebar-nav a.nav-link:hover i {
  color: var(--anime-accent-red);
}

/* ★ 选中项 — 棕色胶囊 */
.sidebar-nav a.nav-link.nav-active {
  background: rgba(196, 93, 43, 0.13);
  color: var(--al-accent-deep);
  font-weight: 600;
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(196, 93, 43, 0.15);
}
.sidebar-nav a.nav-link.nav-active i {
  color: var(--anime-accent-red);
}

.sidebar-footer {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid var(--anime-border-light);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sidebar-footer .footer-link {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--anime-text-secondary);
  text-decoration: none;
  transition: 0.3s;
}
.sidebar-footer .footer-link i {
  width: 20px;
  font-size: 15px;
  text-align: center;
  color: var(--anime-text-secondary);
}
.sidebar-footer .footer-link:hover {
  background: var(--anime-bg-cream);
  color: var(--anime-text-main);
}
.sidebar-footer .theme-footer-btn {
  display: none;
  background: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

/* 移动端遮罩 */
.sidebar-overlay {
  display: none;
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  z-index: 95;
  backdrop-filter: blur(4px);
}
.sidebar-overlay.show {
  display: block;
}

/* ===== 主区 ===== */
.app-main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 顶部栏（内容区首行，随内容滚动） ===== */
.app-topbar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 4px 0 20px;
  flex-wrap: wrap;
}

.search-wrap {
  flex: 1;
  min-width: 200px;
  position: relative;
}
.search-wrap > i {
  position: absolute;
  left: 18px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--al-text-placeholder);
  font-size: 15px;
  pointer-events: none;
}
.search-wrap input {
  width: 100%;
  padding: 12px 18px 12px 48px;
  border: 1.5px solid var(--al-border-input);
  border-radius: 999px;
  font-size: 14px;
  font-weight: 400;
  background: var(--al-bg-soft);
  color: var(--al-text-input);
  transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  outline: none;
  font-family: inherit;
}
.search-wrap input::placeholder {
  color: var(--al-text-placeholder);
  font-weight: 300;
}
.search-wrap input:focus {
  border-color: var(--anime-accent-red);
  background: var(--al-bg);
  box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.15);
}
.search-wrap .shortcut {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  font-weight: 500;
  color: var(--al-text-placeholder);
  background: var(--al-border-subtle);
  padding: 2px 10px;
  border-radius: 8px;
  letter-spacing: 0.3px;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 圆角图标按钮（菜单 / 主题） */
.menu-toggle,
.theme-toggle {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 999px;
  background: var(--al-bg-cream);
  color: var(--al-text-secondary);
  font-size: 17px;
  cursor: pointer;
  transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px solid transparent;
}
.menu-toggle:hover,
.theme-toggle:hover {
  background: rgba(196, 93, 43, 0.08);
  color: var(--al-accent);
  border-color: rgba(196, 93, 43, 0.2);
}
.menu-toggle {
  display: none;
}

/* 用户头像 */
.avatar {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(196, 93, 43, 0.18), rgba(179, 129, 91, 0.25));
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 17px;
  color: var(--anime-accent-red);
  cursor: pointer;
  border: 2px solid rgba(196, 93, 43, 0.2);
  transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-family: inherit;
}
.avatar:hover {
  border-color: var(--anime-accent-red);
  transform: scale(1.03);
}
.avatar i {
  font-size: 22px;
  color: var(--anime-text-secondary);
}

.user-menu-wrapper {
  position: relative;
}

/* ===== 内容区 ===== */
.app-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px 36px 48px;
}

/* ===== User Dropdown ===== */
.user-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  background: var(--al-bg);
  border-radius: 14px;
  border: 1px solid var(--al-border-panel);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14), 0 2px 6px rgba(0, 0, 0, 0.05);
  min-width: 190px;
  margin-top: 8px;
  padding: 6px;
  overscroll-behavior: contain;
  animation: slideDown 0.2s ease;
  z-index: 1001;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-header {
  padding: 10px 12px;
  border-bottom: 1px solid var(--al-border-neutral);
  margin-bottom: 4px;
}

.username {
  color: var(--anime-text-main);
  font-weight: 700;
  font-size: 0.92rem;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 9px;
  color: var(--anime-text-secondary);
  text-decoration: none;
  transition: 0.15s;
  font-size: 0.9rem;
}

.dropdown-item:hover {
  background: rgba(196, 93, 43, 0.08);
  color: var(--anime-accent-red);
}

.dropdown-item i {
  font-size: 1rem;
  width: 20px;
  text-align: center;
}

.dropdown-item.logout {
  color: var(--al-danger);
  margin-top: 2px;
  border-top: 1px solid var(--al-border-neutral);
  border-radius: 0;
  padding-top: 10px;
}

.dropdown-item.logout:hover {
  background: var(--al-danger-soft);
}

.dropdown-divider {
  height: 1px;
  background: var(--al-border-neutral);
  margin: 4px 0;
}

/* ===== Responsive ===== */
@media (max-width: 1280px) {
  .app-sidebar {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: 260px;
    transform: translateX(-100%);
    box-shadow: 0 8px 40px rgba(0, 0, 0, 0.18);
    border-right: none;
    z-index: 110;
  }
  .app-sidebar.open {
    transform: translateX(0);
  }
  .menu-toggle {
    display: flex;
  }
  .app-content {
    padding: 18px 18px 32px;
  }
  .app-topbar {
    padding: 0 0 16px;
    gap: 12px;
  }
  .search-wrap .shortcut {
    display: none;
  }
}

@media (max-width: 600px) {
  .app-topbar {
    gap: 10px;
  }
  .theme-toggle {
    display: none;
  }
  .sidebar-footer .theme-footer-btn {
    display: flex;
  }
  .search-wrap {
    min-width: 0;
    flex: 1 1 auto;
  }
  .search-wrap input {
    padding: 10px 14px 10px 40px;
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .app-content {
    padding: 12px 12px 24px;
  }
  .app-topbar {
    padding: 0 0 12px;
  }
  .menu-toggle,
  .theme-toggle,
  .avatar {
    width: 36px;
    height: 36px;
    font-size: 15px;
  }
}
</style>
