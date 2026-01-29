<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const API_BASE = '/api'
const router = useRouter()

const loginDialog = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const isLoggedIn = ref(false)
const userInfo = ref(null)

const loginForm = ref({
  username: '',
  password: ''
})

// 配置axios请求拦截器，自动添加token
axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      const userData = res.data.data
      localStorage.setItem('userInfo', JSON.stringify(userData))
      isLoggedIn.value = true
      userInfo.value = userData
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    // 如果获取失败，清除登录状态
    handleLogout()
  }
}

const checkLoginStatus = () => {
  const token = localStorage.getItem('token')
  const user = localStorage.getItem('userInfo')
  if (token) {
    isLoggedIn.value = true
    if (user) {
      try {
        userInfo.value = JSON.parse(user)
      } catch (e) {
        console.error('解析用户信息失败:', e)
      }
    }
    // 获取最新的用户信息
    fetchUserInfo()
  }
}

const openLoginDialog = () => {
  loginDialog.value = true
  errorMessage.value = ''
}

const closeLoginDialog = () => {
  loginDialog.value = false
  errorMessage.value = ''
  loginForm.value = { username: '', password: '' }
}

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const res = await axios.post(`${API_BASE}/auth/login`, loginForm.value)
    if (res.data?.code === 200 && res.data?.data) {
      const { tokenValue } = res.data.data
      localStorage.setItem('token', tokenValue)
      // 登录成功后获取用户信息
      await fetchUserInfo()
      closeLoginDialog()
    } else {
      errorMessage.value = res.data?.msg || '登录失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  isLoggedIn.value = false
  userInfo.value = null
  router.push('/')
}

const goToAdmin = () => {
  router.push('/admin')
}

onMounted(() => {
  checkLoginStatus()
})
</script>

<template>
  <v-app>
    <v-app-bar color="primary" elevation="2">
      <v-app-bar-title class="text-white">AniLinkService</v-app-bar-title>
      <v-spacer />
      <v-menu>
        <template v-slot:activator="{ props }">
          <v-btn icon v-bind="props">
            <v-avatar v-if="isLoggedIn && userInfo" color="white">
              <v-icon color="primary">mdi-account</v-icon>
            </v-avatar>
            <v-icon v-else color="white" size="x-large">mdi-account-circle</v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item v-if="isLoggedIn && userInfo">
            <v-list-item-title class="font-weight-medium">{{ userInfo.username }}</v-list-item-title>
            <v-list-item-subtitle class="text-caption">
              {{ userInfo.email || '暂无邮箱' }}
              <template v-if="userInfo.roleCodeList && userInfo.roleCodeList.length > 0">
                <v-chip size="x-small" class="ml-2" color="primary">
                  {{ userInfo.roleCodeList.join(', ') }}
                </v-chip>
              </template>
            </v-list-item-subtitle>
          </v-list-item>
          <v-divider v-if="isLoggedIn && userInfo" />
          <v-list-item v-if="isLoggedIn" @click="goToAdmin">
            <v-list-item-title>
              <v-icon start>mdi-cog</v-icon>
              管理后台
            </v-list-item-title>
          </v-list-item>
          <v-list-item v-if="isLoggedIn" @click="handleLogout">
            <v-list-item-title>
              <v-icon start color="error">mdi-logout</v-icon>
              退出登录
            </v-list-item-title>
          </v-list-item>
          <v-list-item v-else @click="openLoginDialog">
            <v-list-item-title>
              <v-icon start color="primary">mdi-login</v-icon>
              登录
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>

    <v-main class="bg-grey-lighten-5">
      <v-container class="fill-height d-flex align-center justify-center">
        <v-card class="elevation-2" width="100%" max-width="450">
          <v-toolbar color="success" flat>
            <v-toolbar-title class="text-white">AniLinkService</v-toolbar-title>
            <v-spacer />
            <v-icon color="white">mdi-check-circle</v-icon>
          </v-toolbar>
          <v-card-text class="pa-8 text-center">
            <v-icon color="success" size="80">mdi-check-circle-outline</v-icon>
            <h2 class="text-h4 mt-4 mb-2">系统已就绪</h2>
            <p class="text-body-1 text-grey">欢迎访问 AniLinkService</p>
          </v-card-text>
          <v-card-actions class="pa-6">
            <v-spacer />
            <v-btn
              color="success"
              size="large"
              variant="elevated"
              @click="openLoginDialog"
              block
            >
              <v-icon start>mdi-login</v-icon>
              登录系统
            </v-btn>
            <v-spacer />
          </v-card-actions>
        </v-card>
      </v-container>
    </v-main>

    <!-- 登录弹窗 -->
    <v-dialog v-model="loginDialog" max-width="400">
      <v-card>
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">登录</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="closeLoginDialog" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
            {{ errorMessage }}
          </v-alert>

          <v-form @submit.prevent="handleLogin">
            <v-text-field
              v-model="loginForm.username"
              label="用户名"
              prepend-inner-icon="mdi-account"
              variant="outlined"
              color="primary"
              required
              class="mb-4"
            />
            <v-text-field
              v-model="loginForm.password"
              label="密码"
              type="password"
              prepend-inner-icon="mdi-lock"
              variant="outlined"
              color="primary"
              required
            />
          </v-form>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn
            color="grey"
            variant="text"
            @click="closeLoginDialog"
          >
            取消
          </v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            :loading="loading"
            :disabled="loading"
            @click="handleLogin"
          >
            登录
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-app>
</template>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
