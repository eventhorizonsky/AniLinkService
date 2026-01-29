import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const installed = localStorage.getItem('installed')
  const token = localStorage.getItem('token')

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

  next()
})

export default router
