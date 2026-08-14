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

          <!-- 消息按钮和下拉 -->
          <div v-if="isLoggedIn" class="message-wrapper">
            <button @click="handleMessageBtnClick" class="message-btn" :title="`消息${unreadCount > 0 ? ' (' + unreadCount + ')' : ''}`">
              <i class="mdi mdi-bell-outline"></i>
              <span v-if="unreadCount > 0" class="unread-dot"></span>
            </button>

            <!-- 消息下拉窗 -->
            <div v-if="messageMenuOpen" class="message-dropdown" @click.stop @wheel.stop>
              <div class="message-dropdown-header">
                <span>消息通知</span>
                <div class="message-header-actions">
                  <span v-if="unreadCount > 0" class="unread-count-badge">{{ unreadCount }}</span>
                  <button
                    v-if="unreadCount > 0"
                    class="mark-all-read-btn"
                    :disabled="markingAllRead"
                    @click="handleMarkAllAsRead"
                  >
                    {{ markingAllRead ? '处理中...' : '一键已读' }}
                  </button>
                </div>
              </div>

              <div class="message-list">
                <div v-if="loadingMessages" class="message-loading">
                  <i class="mdi mdi-loading mdi-spin"></i>
                  <span>加载中...</span>
                </div>

                <template v-else-if="recentMessages.length > 0">
                  <div
                    v-for="msg in recentMessages"
                    :key="msg.id"
                    class="message-item"
                    :class="{ 'unread': !msg.isRead }"
                    @click="handleMessageClick(msg)"
                  >
                    <div class="message-item-indicator">
                      <span v-if="!msg.isRead" class="unread-indicator"></span>
                    </div>
                    <div class="message-item-content">
                      <div class="message-item-title">{{ msg.title }}</div>
                      <div class="message-item-text">{{ msg.content }}</div>
                      <div class="message-item-time">{{ formatRelativeTime(msg.createdAt) }}</div>
                    </div>
                  </div>
                </template>

                <div v-else class="message-empty">
                  <i class="mdi mdi-bell-off-outline"></i>
                  <span>暂无消息</span>
                </div>
              </div>

              <div class="message-dropdown-footer">
                <button @click="messageMenuOpen = false; goToMessages()" class="view-all-messages-btn">
                  查看全部消息
                </button>
              </div>
            </div>
          </div>

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

    <!-- 登录对话框 -->
    <div v-if="showLoginDialog" class="login-modal-overlay" @click.self="showLoginDialog = false">
      <div class="login-modal" @click.stop>
        <div class="login-header">
          <h2>用户登录</h2>
          <button class="close-btn" @click="showLoginDialog = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>

        <div class="login-body">
          <input
            v-model="loginForm.account"
            type="text"
            placeholder="用户名或邮箱"
            class="login-input"
            @keyup.enter="handleLogin"
          />
          <input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            class="login-input"
            @keyup.enter="handleLogin"
          />
        </div>

        <div class="login-footer">
          <button v-if="isRegisterOpen" class="btn-cancel" @click="openRegisterDialog">去注册</button>
          <button class="btn-cancel" @click="showLoginDialog = false">取消</button>
          <button class="btn-login" @click="handleLogin" :disabled="loginLoading">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 注册对话框 -->
    <div v-if="showRegisterDialog" class="login-modal-overlay" @click.self="showRegisterDialog = false">
      <div class="login-modal" @click.stop>
        <div class="login-header">
          <h2>用户注册</h2>
          <button class="close-btn" @click="showRegisterDialog = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>

        <div class="login-body">
          <input v-model="registerForm.username" type="text" placeholder="用户名" class="login-input" />
          <input v-model="registerForm.email" type="email" placeholder="邮箱" class="login-input" />
          <input v-model="registerForm.password" type="password" placeholder="密码" class="login-input" />
          <input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            class="login-input"
          />
          <div class="captcha-row">
            <input
              v-model="registerForm.captchaCode"
              type="text"
              placeholder="图形验证码"
              class="login-input"
            />
            <img
              v-if="captchaImage"
              :src="captchaImageSrc"
              class="captcha-image"
              alt="captcha"
              @click="refreshCaptcha"
            />
          </div>
          <div class="captcha-row">
            <input v-model="registerForm.emailCode" type="text" placeholder="邮箱验证码" class="login-input" />
            <button class="btn-cancel send-code-btn" :disabled="sendCodeDisabled" @click="sendEmailCode">
              {{ sendCodeText }}
            </button>
          </div>
        </div>

        <div class="login-footer">
          <button class="btn-cancel" @click="openLoginDialog">去登录</button>
          <button class="btn-cancel" @click="showRegisterDialog = false">取消</button>
          <button class="btn-login" @click="handleRegister" :disabled="registerLoading">
            {{ registerLoading ? '注册中...' : '注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showAppMessage } from '../utils/ui-feedback'
import { useTheme } from '../composables/useTheme'
import { useAuth } from '../composables/useAuth'
import { useIsMobile } from '../composables/useIsMobile'
import { formatRelativeTime } from '../utils/format'
import { DEFAULT_SITE_NAME, hasRoleLevel, isSuperAdmin } from '../utils/constants'
import { login, register, getCaptcha, sendRegisterEmailCode, getCurrentUser } from '../api/auth'
import { getUnreadCount, getMessages, markMessageRead, markAllMessagesRead } from '../api/messages'

const { isDark, toggleTheme } = useTheme()
const { token, userInfo, isLoggedIn, setToken, setUserInfo, clearAuth } = useAuth()
const router = useRouter()
const route = useRoute()
const searchQuery = ref('')
const showLoginDialog = ref(false)
const showRegisterDialog = ref(false)
const loginLoading = ref(false)
const registerLoading = ref(false)
const sendCodeLoading = ref(false)
const sendCodeCountdown = ref(0)
const userMenuOpen = ref(false)
const sidebarOpen = ref(false)
const siteConfig = ref(null)
const captchaId = ref('')
const captchaImage = ref('')

const loginForm = ref({
  account: '',
  password: ''
})

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
  emailCode: ''
})

const unreadCount = ref(0)

// 消息下拉
const messageMenuOpen = ref(false)
const recentMessages = ref([])
const loadingMessages = ref(false)
const markingAllRead = ref(false)

let countdownTimer = null

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

// 获取未读消息数
const fetchUnreadCount = async () => {
  if (!isLoggedIn.value) {
    unreadCount.value = 0
    return
  }

  try {
    const res = await getUnreadCount()
    // 后端 success 统一返回 code=200，兼容历史 code=0 的情况
    if (res?.code === 200 || res?.code === 0) {
      unreadCount.value = Number(res.data?.unreadCount || 0)
      return
    }
    unreadCount.value = 0
  } catch (error) {
    console.error('获取未读消息数失败:', error)
    unreadCount.value = 0
  }
}

// 获取最近消息
const fetchRecentMessages = async () => {
  if (!isLoggedIn.value) {
    recentMessages.value = []
    return
  }

  loadingMessages.value = true
  try {
    const res = await getMessages({
      page: 1,
      pageSize: 5
    })
    if (res?.code === 200) {
      recentMessages.value = res.data.content || []
    }
  } catch (error) {
    console.error('获取最近消息失败:', error)
  } finally {
    loadingMessages.value = false
  }
}

const handleMarkAllAsRead = async () => {
  if (markingAllRead.value || unreadCount.value <= 0) {
    return
  }

  markingAllRead.value = true
  try {
    const res = await markAllMessagesRead()
    if (res?.code === 200 || res?.code === 0) {
      await fetchUnreadCount()
      await fetchRecentMessages()
      showAppMessage('已全部标记为已读', 'success')
      return
    }
    showAppMessage(res?.msg || '一键已读失败', 'error')
  } catch (error) {
    console.error('全部标记已读失败:', error)
    showAppMessage('一键已读失败，请稍后重试', 'error')
  } finally {
    markingAllRead.value = false
  }
}

// 处理消息点击
const handleMessageClick = async (message) => {
  messageMenuOpen.value = false

  // 标记为已读
  if (!message.isRead) {
    try {
      await markMessageRead(message.id)
      await fetchUnreadCount()
      await fetchRecentMessages()
    } catch (error) {
      console.error('标记消息已读失败:', error)
    }
  }

  // 如果是剧集更新消息且有视频ID，跳转到播放页
  if (message.type === 'episode_update' && message.videoId) {
    const routeData = router.resolve({
      name: 'Player',
      params: { videoId: String(message.videoId) },
      query: {
        animeId: String(message.animeId),
        episodeId: String(message.episodeId || '')
      }
    })
    window.open(routeData.href, '_blank')
  } else if (message.animeId) {
    // 否则如果有animeId，跳转到动画详情页
    router.push(`/anime/${message.animeId}`)
  }
}

// 判断是否是移动端
const { isMobile } = useIsMobile(1280, { useInnerWidth: true })

// 处理消息按钮点击
const handleMessageBtnClick = () => {
  if (isMobile.value) {
    // 移动端直接跳转到消息列表
    goToMessages()
  } else {
    // PC端打开下拉菜单
    toggleMessageMenu()
  }
}

// 切换消息菜单
const toggleMessageMenu = () => {
  messageMenuOpen.value = !messageMenuOpen.value
  if (messageMenuOpen.value) {
    fetchRecentMessages()
  }
}

// 定期获取未读消息数
let unreadCountTimer = null
const startUnreadCountPolling = () => {
  if (!isLoggedIn.value) return

  if (!unreadCountTimer) {
    fetchUnreadCount()
    unreadCountTimer = setInterval(fetchUnreadCount, 180000) // 每3分钟检查一次
  }
}

const stopUnreadCountPolling = () => {
  if (unreadCountTimer) {
    clearInterval(unreadCountTimer)
    unreadCountTimer = null
  }
}

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await getCurrentUser()
    if (res?.code === 200 && res?.data) {
      const userData = res.data
      setUserInfo(userData)
      startUnreadCountPolling()
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
  return !!siteConfig.value?.authRegisterEnabled
})

const isRemoteAccessVisible = computed(() => {
  const enabledRaw = siteConfig.value?.remoteAccessEnabled
  const tokenRequiredRaw = siteConfig.value?.remoteAccessTokenRequired
  const requiredRoleRaw = siteConfig.value?.remoteAccessRequiredRole
  const enabled = enabledRaw === true || enabledRaw === 'true'
  const tokenRequired = tokenRequiredRaw === true || tokenRequiredRaw === 'true'

  if (!enabled) {
    return false
  }

  if (!tokenRequired) {
    return true
  }

  if (!isLoggedIn.value) {
    return false
  }

  return hasRoleLevel(userInfo.value, requiredRoleRaw)
})

const sendCodeDisabled = computed(() => {
  return sendCodeLoading.value || sendCodeCountdown.value > 0
})

const sendCodeText = computed(() => {
  if (sendCodeLoading.value) {
    return '发送中...'
  }
  if (sendCodeCountdown.value > 0) {
    return `${sendCodeCountdown.value}s`
  }
  return '发送验证码'
})

const captchaImageSrc = computed(() => {
  if (!captchaImage.value) {
    return ''
  }
  if (captchaImage.value.startsWith('data:image/')) {
    return captchaImage.value
  }
  return `data:image/png;base64,${captchaImage.value}`
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
  loadSiteConfig()
  checkLoginStatus()
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  stopUnreadCountPolling()
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', onKeydown)
})

const loadSiteConfig = () => {
  try {
    const stored = localStorage.getItem('siteConfig')
    if (stored) {
      siteConfig.value = JSON.parse(stored)
    }
  } catch (e) {
    console.error('解析本地配置失败:', e)
  }
}

const handleSearch = () => {
  const keyword = searchQuery.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword, page: '1' } : { page: '1' }
  })
}

const handleLogin = async () => {
  if (!loginForm.value.account || !loginForm.value.password) {
    showAppMessage('请输入用户名/邮箱和密码', 'warning')
    return
  }

  loginLoading.value = true
  try {
    const res = await login({
      account: loginForm.value.account,
      password: loginForm.value.password
    })

    if (res?.code === 200 && res?.data) {
      const { tokenValue } = res.data
      setToken(tokenValue)
      showLoginDialog.value = false
      loginForm.value = { account: '', password: '' }
      fetchUserInfo()
      return
    } else {
      showAppMessage(res?.msg || '登录失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '登录失败，请重试', 'error')
  } finally {
    loginLoading.value = false
  }
}

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    if (res?.code === 200 && res?.data) {
      captchaId.value = res.data.captchaId
      captchaImage.value = res.data.imageBase64
      registerForm.value.captchaCode = ''
    }
  } catch (error) {
    console.error('获取图形验证码失败:', error)
  }
}

const openLoginDialog = () => {
  showRegisterDialog.value = false
  showLoginDialog.value = true
  userMenuOpen.value = false
}

const openRegisterDialog = async () => {
  if (!isRegisterOpen.value) {
    return
  }
  showLoginDialog.value = false
  showRegisterDialog.value = true
  userMenuOpen.value = false
  await refreshCaptcha()
}

const startSendCodeCountdown = (seconds) => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  sendCodeCountdown.value = seconds
  countdownTimer = setInterval(() => {
    if (sendCodeCountdown.value <= 1) {
      clearInterval(countdownTimer)
      countdownTimer = null
      sendCodeCountdown.value = 0
      return
    }
    sendCodeCountdown.value -= 1
  }, 1000)
}

const sendEmailCode = async () => {
  if (!registerForm.value.email) {
    showAppMessage('请输入邮箱', 'warning')
    return
  }
  if (!registerForm.value.captchaCode) {
    showAppMessage('请输入图形验证码', 'warning')
    return
  }
  if (!captchaId.value) {
    await refreshCaptcha()
    showAppMessage('图形验证码已刷新，请重新输入', 'warning')
    return
  }

  sendCodeLoading.value = true
  try {
    const res = await sendRegisterEmailCode({
      email: registerForm.value.email,
      captchaId: captchaId.value,
      captchaCode: registerForm.value.captchaCode
    })

    if (res?.code === 200) {
      showAppMessage('验证码已发送，请检查邮箱', 'success')
      startSendCodeCountdown(60)
    } else {
      showAppMessage(res?.msg || '发送失败', 'error')
      await refreshCaptcha()
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '发送失败，请稍后重试', 'error')
    await refreshCaptcha()
  } finally {
    sendCodeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    showAppMessage('请填写完整注册信息', 'warning')
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    showAppMessage('两次密码输入不一致', 'warning')
    return
  }
  if (!registerForm.value.emailCode) {
    showAppMessage('请输入邮箱验证码', 'warning')
    return
  }

  registerLoading.value = true
  try {
    const res = await register({
      username: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
      emailCode: registerForm.value.emailCode
    })

    if (res?.code === 200) {
      showAppMessage('注册成功，请登录', 'success')
      registerForm.value = {
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
        captchaCode: '',
        emailCode: ''
      }
      showRegisterDialog.value = false
      userMenuOpen.value = false
      showLoginDialog.value = true
    } else {
      showAppMessage(res?.msg || '注册失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '注册失败，请稍后重试', 'error')
  } finally {
    registerLoading.value = false
  }
}

const handleLogout = () => {
  clearAuth()
  userMenuOpen.value = false
  sidebarOpen.value = false
  stopUnreadCountPolling()
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

/* 圆角图标按钮（消息 / 菜单 / 主题） */
.message-btn,
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
.message-btn:hover,
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

.unread-dot {
  position: absolute;
  top: 9px;
  right: 9px;
  width: 8px;
  height: 8px;
  background: var(--al-danger-bright);
  border-radius: 50%;
  border: 2px solid #fff;
}

/* ===== 内容区 ===== */
.app-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px 36px 48px;
}

/* ===== Message Dropdown ===== */
.message-wrapper {
  position: relative;
}

.message-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: var(--al-bg);
  border-radius: 12px;
  border: 1px solid var(--anime-border-light);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  width: 360px;
  max-height: 500px;
  overflow: hidden;
  overscroll-behavior: contain;
  animation: slideDown 0.2s ease;
  z-index: 1001;
}

.message-dropdown-header {
  padding: 16px;
  border-bottom: 1px solid var(--anime-border-light);
  background: var(--anime-bg-cream);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--anime-primary-dark);
}

.unread-count-badge {
  background: var(--al-danger-deep);
  color: var(--al-text-on-accent);
  font-size: 0.75rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
}

.message-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mark-all-read-btn {
  border: 1px solid var(--anime-border-light);
  background: var(--al-bg);
  color: var(--anime-accent-red);
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mark-all-read-btn:hover:not(:disabled) {
  background: var(--anime-bg-beige);
  border-color: var(--anime-accent-brown);
}

.mark-all-read-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.message-list {
  max-height: 380px;
  overflow-y: auto;
}

.message-loading {
  padding: 40px 20px;
  text-align: center;
  color: var(--anime-text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-loading i {
  font-size: 1.5rem;
}

.message-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--al-border-neutral);
  cursor: pointer;
  transition: background 0.2s ease;
  display: flex;
  gap: 10px;
}

.message-item:hover {
  background: var(--anime-bg-cream);
}

.message-item.unread {
  background: rgba(196, 93, 43, 0.05);
}

.message-item.unread:hover {
  background: rgba(196, 93, 43, 0.1);
}

.message-item:last-child {
  border-bottom: none;
}

.message-item-indicator {
  flex-shrink: 0;
  padding-top: 4px;
}

.unread-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: var(--al-danger-deep);
  border-radius: 50%;
}

.message-item-content {
  flex: 1;
  min-width: 0;
}

.message-item-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--anime-text-main);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item-text {
  font-size: 0.85rem;
  color: var(--anime-text-secondary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-clamp: 2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
}

.message-item-time {
  font-size: 0.75rem;
  color: var(--anime-text-secondary);
  opacity: 0.7;
}

.message-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--anime-text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-empty i {
  font-size: 2.5rem;
  opacity: 0.5;
}

.message-dropdown-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--anime-border-light);
  background: var(--al-bg);
}

.view-all-messages-btn {
  width: 100%;
  padding: 8px;
  background: transparent;
  border: 1px solid var(--anime-border-light);
  border-radius: 8px;
  color: var(--anime-accent-red);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.view-all-messages-btn:hover {
  background: var(--anime-bg-cream);
  border-color: var(--anime-accent-brown);
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

/* ===== Login / Register Modal ===== */
.login-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.login-modal {
  background: var(--al-bg);
  border-radius: 18px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.16);
  animation: slideUp 0.3s ease;
  overflow: hidden;
  border: 1px solid var(--al-border-panel);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--al-border-neutral);
  background: var(--al-bg);
}

.login-header h2 {
  font-size: 1.1rem;
  color: var(--anime-text-main);
  margin: 0;
  font-weight: 700;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--anime-text-secondary);
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: 0.2s;
  border-radius: 8px;
}

.close-btn:hover {
  color: var(--anime-accent-red);
  background: var(--al-border-hover);
  transform: rotate(90deg);
}

.login-body {
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--al-bg);
}

.login-input {
  padding: 11px 14px;
  border: 1.5px solid var(--al-border-input);
  border-radius: 10px;
  font-size: 0.95rem;
  color: var(--anime-text-main);
  font-family: inherit;
  transition: 0.2s;
  background: var(--al-bg-soft);
}

.login-input:focus {
  outline: none;
  border-color: var(--anime-accent-red);
  background: var(--al-bg);
  box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12);
}

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-row .login-input {
  flex: 1;
}

.captcha-image {
  width: 110px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--al-border-input);
  cursor: pointer;
  background: var(--al-bg);
}

.send-code-btn {
  min-width: 110px;
  white-space: nowrap;
}

.login-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--al-border-neutral);
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  background: var(--al-bg);
}

.btn-cancel {
  padding: 9px 20px;
  border: 1px solid var(--al-border-input);
  background: var(--al-bg);
  color: var(--anime-text-secondary);
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
}

.btn-cancel:hover {
  background: var(--al-bg-soft);
  color: var(--anime-text-main);
  border-color: var(--anime-accent-brown);
}

.btn-login {
  padding: 10px 24px;
  border: none;
  background: var(--anime-accent-red);
  color: var(--al-text-on-accent);
  border-radius: 10px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
  box-shadow: 0 2px 8px rgba(196, 93, 43, 0.3);
}

.btn-login:hover:not(:disabled) {
  background: var(--al-accent-strong);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(196, 93, 43, 0.4);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
  .message-dropdown {
    width: 340px;
  }
}

@media (max-width: 600px) {
  .app-topbar {
    gap: 10px;
  }
  .search-wrap {
    min-width: 0;
    flex: 1 1 auto;
  }
  .search-wrap input {
    padding: 10px 14px 10px 40px;
    font-size: 13px;
  }
  .message-dropdown {
    width: calc(100vw - 20px);
    max-width: 340px;
    right: -10px;
  }
}

@media (max-width: 480px) {
  .app-content {
    padding: 12px 12px 24px;
  }
  .app-topbar {
    padding: 0 0 12px;
  }
  .message-btn,
  .menu-toggle,
  .theme-toggle,
  .avatar {
    width: 36px;
    height: 36px;
    font-size: 15px;
  }
  .message-dropdown {
    width: calc(100vw - 16px);
    max-width: 340px;
    right: -8px;
  }
}
</style>
