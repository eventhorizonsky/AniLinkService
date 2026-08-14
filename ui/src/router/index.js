import { createRouter, createWebHistory } from 'vue-router'
import { hasRoleLevel } from '../utils/constants'
import { useAuth } from '../composables/useAuth'
import { getSiteConfig } from '../api/site'

const routes = [
  {
    path: '/install',
    name: 'Install',
    component: () => import('../views/Install.vue')
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/Admin.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('../views/Home.vue')
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('../views/Search.vue')
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('../views/Schedule.vue')
      },
      {
        path: 'anime/:animeId',
        name: 'AnimeDetail',
        component: () => import('../views/AnimeDetail.vue')
      },
      {
        path: 'anime/bgm/:subjectId',
        name: 'AnimeDetailBgm',
        component: () => import('../views/AnimeDetail.vue'),
        props: { bgmMode: true }
      },
      {
        path: 'play/:videoId',
        name: 'Player',
        component: () => import('../views/Player.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile/history',
        name: 'ProfileHistory',
        component: () => import('../views/profile/History.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile/follows',
        name: 'ProfileFollows',
        component: () => import('../views/profile/Follows.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile/danmaku',
        name: 'ProfileDanmaku',
        component: () => import('../views/profile/Danmaku.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile/messages',
        name: 'ProfileMessages',
        component: () => import('../views/profile/Messages.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'profile/binding',
        name: 'ProfileBinding',
        component: () => import('../views/profile/Binding.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'follows',
        name: 'FollowList',
        redirect: '/profile/follows',
        meta: { requiresAuth: true }
      },
      {
        path: 'messages',
        name: 'Messages',
        redirect: '/profile/messages',
        meta: { requiresAuth: true }
      },
      {
        path: 'remote-access',
        name: 'RemoteAccess',
        component: () => import('../views/RemoteAccess.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  let installed = localStorage.getItem('installed')
  const { token, userInfo } = useAuth()
  let siteConfig = null

  // 仅在本地状态缺失时从接口获取，避免每次导航都请求 siteConfig
  if (installed == null) {
    try {
      const res = await getSiteConfig()
      const isInstalled = res?.data?.installed === true
      siteConfig = res?.data || null
      installed = isInstalled ? 'true' : 'false'
      if (isInstalled) {
        localStorage.setItem('installed', 'true')
        localStorage.setItem('siteConfig', JSON.stringify(siteConfig || {}))
      } else {
        localStorage.removeItem('installed')
      }
    } catch (err) {
      console.error('获取安装状态失败:', err)
      // 默认认为未安装，清理 localStorage
      localStorage.removeItem('installed')
      installed = 'false'
    }
  }

  // 如果已安装，访问安装页跳转到首页
  if (installed === 'true' && to.path === '/install') {
    return next('/')
  }

  // 如果未安装，跳转到安装页
  if (installed !== 'true' && to.path !== '/install') {
    return next('/install')
  }

  // 检查需要认证的路由
  if (to.meta.requiresAuth && !token.value) {
    return next('/')
  }

  if (to.name === 'RemoteAccess') {
    if (!siteConfig) {
      try {
        siteConfig = JSON.parse(localStorage.getItem('siteConfig') || '{}')
      } catch (e) {
        siteConfig = {}
      }
    }

    const enabledRaw = siteConfig?.remoteAccessEnabled
    const tokenRequiredRaw = siteConfig?.remoteAccessTokenRequired
    const requiredRoleRaw = siteConfig?.remoteAccessRequiredRole
    const enabled = enabledRaw === true || enabledRaw === 'true'
    const tokenRequired = tokenRequiredRaw === true || tokenRequiredRaw === 'true'
    if (!enabled) {
      return next('/')
    }

    if (tokenRequired) {
      if (!token.value) {
        return next('/')
      }

      const allowed = hasRoleLevel(userInfo.value || {}, requiredRoleRaw)

      if (!allowed) {
        return next('/')
      }
    }
  }

  next()
})

export default router
