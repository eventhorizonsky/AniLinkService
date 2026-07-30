import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'

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
        path: 'follows',
        name: 'FollowList',
        redirect: { path: '/profile', query: { tab: 'follows' } },
        meta: { requiresAuth: true }
      },
      {
        path: 'messages',
        name: 'Messages',
        redirect: { path: '/profile', query: { tab: 'messages' } },
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
  const token = localStorage.getItem('token')
  let siteConfig = null

  // 若状态不存在，则从接口获取，避免频繁请求 siteConfig 的同时确保状态可靠
    try {
      const res = await axios.get('/api/site/config')
      const isInstalled = res.data?.data?.installed === true
      siteConfig = res.data?.data || null
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
  

  // 如果已安装，访问安装页跳转到首页
  if (installed === 'true' && to.path === '/install') {
    return next('/')
  }

  // 如果未安装，跳转到安装页
  if (installed !== 'true' && to.path !== '/install') {
    return next('/install')
  }

  // 检查需要认证的路由
  if (to.meta.requiresAuth && !token) {
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
      if (!token) {
        return next('/')
      }

      let userInfo = null
      try {
        userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      } catch (e) {
        userInfo = {}
      }

      const roleLevel = {
        user: 1,
        admin: 2,
        'super-admin': 3
      }
      const requiredRole = (requiredRoleRaw || 'user').toString().trim()
      const requiredLevel = roleLevel[requiredRole]
      const roleCodes = Array.isArray(userInfo?.roleCodeList) ? userInfo.roleCodeList : []

      let allowed = false
      if (!requiredLevel) {
        allowed = roleCodes.includes(requiredRole)
      } else {
        allowed = roleCodes.some((role) => {
          const level = roleLevel[role]
          return typeof level === 'number' && level >= requiredLevel
        })
      }

      if (!allowed) {
        return next('/')
      }
    }
  }

  next()
})

export default router
