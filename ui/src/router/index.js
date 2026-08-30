import { createRouter, createWebHistory } from 'vue-router'
import { hasRoleLevel } from '../utils/constants'
import { useAuth } from '../composables/useAuth'
import { getSiteConfig } from '../api/site'
import {
  readInstalled,
  writeInstalled,
  readSiteConfig,
  writeSiteConfig,
  remoteAccessEnabled,
  remoteAccessTokenRequired,
} from '../utils/siteConfig'

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
    meta: { requiresAuth: true, roles: ['admin', 'super-admin'] }
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
  let installed = readInstalled()
  const { token, userInfo } = useAuth()
  let siteConfig = null

  // 仅在本地状态缺失时从接口获取，避免每次导航都请求 siteConfig
  if (localStorage.getItem('installed') == null) {
    try {
      const res = await getSiteConfig()
      const isInstalled = res?.data?.installed === true
      siteConfig = res?.data || null
      installed = isInstalled
      if (isInstalled) {
        writeInstalled(true)
        writeSiteConfig(siteConfig)
      } else {
        writeInstalled(false)
      }
    } catch (err) {
      console.error('获取安装状态失败:', err)
      // 默认认为未安装，清理 localStorage
      writeInstalled(false)
      installed = false
    }
  }

  // 如果已安装，访问安装页跳转到首页
  if (installed && to.path === '/install') {
    return next('/')
  }

  // 如果未安装，跳转到安装页
  if (!installed && to.path !== '/install') {
    return next('/install')
  }

  // 检查需要认证的路由
  if (to.meta.requiresAuth && !token.value) {
    return next('/')
  }

  // 检查需要特定角色的路由（如管理后台），普通用户无权访问
  if (to.meta.roles && !hasRoleLevel(userInfo.value || {}, to.meta.roles[0])) {
    return next('/')
  }

  if (to.name === 'RemoteAccess') {
    if (!siteConfig) {
      siteConfig = readSiteConfig()
    }

    if (!remoteAccessEnabled(siteConfig)) {
      return next('/')
    }

    if (remoteAccessTokenRequired(siteConfig)) {
      if (!token.value) {
        return next('/')
      }

      const allowed = hasRoleLevel(userInfo.value || {}, siteConfig?.remoteAccessRequiredRole)

      if (!allowed) {
        return next('/')
      }
    }
  }

  next()
})

export default router
