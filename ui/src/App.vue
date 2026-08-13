<template>
  <v-app>
    <v-main class="bg-background">
      <v-container v-if="checkingInstall" class="fill-height d-flex align-center justify-center">
        <v-card class="elevation-2" width="100%" max-width="450">
          <v-card-text class="text-center pa-12">
            <v-progress-circular indeterminate color="primary" size="48" />
            <p class="mt-4 text-body-1">正在初始化系统...</p>
          </v-card-text>
        </v-card>
      </v-container>
      <router-view v-else />
    </v-main>

    <v-snackbar
      v-model="appMessage.open"
      :color="appMessage.color"
      location="bottom"
      timeout="2800"
    >
      {{ appMessage.text }}
    </v-snackbar>

    <v-dialog v-model="sessionExpiredDialog" persistent max-width="420">
      <v-card>
        <v-card-title class="text-h6">登录失效</v-card-title>
        <v-card-text>{{ sessionExpiredMessage }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" @click="confirmSessionExpired">确定</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="confirmDialog.open" max-width="420" persistent>
      <v-card>
        <v-card-title class="text-h6">{{ confirmDialog.title }}</v-card-title>
        <v-card-text>{{ confirmDialog.message }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="handleConfirmCancel">{{ confirmDialog.cancelText }}</v-btn>
          <v-btn :color="confirmDialog.color" @click="handleConfirmOk">{{ confirmDialog.confirmText }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-app>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { UiFeedbackEvents } from './utils/ui-feedback'

const router = useRouter()
const checkingInstall = ref(true)
const appMessage = ref({
  open: false,
  text: '',
  color: 'info'
})
const sessionExpiredDialog = ref(false)
const sessionExpiredMessage = ref('登录状态已过期，请重新登录。')
const confirmDialog = ref({
  open: false,
  title: '请确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  color: 'primary',
  resolver: null
})

const checkInstallStatus = async () => {
  try {
    const res = await axios.get('/api/site/config')
    const isInstalled = res.data?.data?.installed === true

    if (isInstalled) {
      localStorage.setItem('installed', 'true')
      try {
        localStorage.setItem('siteConfig', JSON.stringify(res.data.data))
      } catch (e) {
        console.error('保存配置失败:', e)
      }
    } else {
      localStorage.removeItem('installed')
    }
  } catch (error) {
    console.error('检查安装状态失败:', error)
    localStorage.removeItem('installed')
  } finally {
    checkingInstall.value = false
  }
}

onMounted(async () => {
  const handleNotify = (event) => {
    const detail = event?.detail || {}
    appMessage.value = {
      open: true,
      text: detail.message || '操作完成',
      color: detail.color || 'info'
    }
  }

  const handleSessionExpired = (event) => {
    sessionExpiredMessage.value = event?.detail?.message || '登录状态已过期，请重新登录。'
    sessionExpiredDialog.value = true
  }

  const handleConfirmRequest = (event) => {
    const detail = event?.detail || {}
    confirmDialog.value = {
      open: true,
      title: detail.title || '请确认',
      message: detail.message || '确认执行该操作吗？',
      confirmText: detail.confirmText || '确定',
      cancelText: detail.cancelText || '取消',
      color: detail.color || 'primary',
      resolver: typeof detail.resolve === 'function' ? detail.resolve : null
    }
  }

  window.addEventListener(UiFeedbackEvents.APP_NOTIFY_EVENT, handleNotify)
  window.addEventListener(UiFeedbackEvents.APP_SESSION_EXPIRED_EVENT, handleSessionExpired)
  window.addEventListener(UiFeedbackEvents.APP_CONFIRM_EVENT, handleConfirmRequest)
  cleanupListeners = () => {
    window.removeEventListener(UiFeedbackEvents.APP_NOTIFY_EVENT, handleNotify)
    window.removeEventListener(UiFeedbackEvents.APP_SESSION_EXPIRED_EVENT, handleSessionExpired)
    window.removeEventListener(UiFeedbackEvents.APP_CONFIRM_EVENT, handleConfirmRequest)
  }

  // 检查本地缓存的状态
  const stored = localStorage.getItem('installed')
  if (stored !== null) {
    checkingInstall.value = false
    return
  }

  // 如果没有本地缓存，从服务器获取
  await checkInstallStatus()
})

let cleanupListeners = null

onBeforeUnmount(() => {
  if (cleanupListeners) {
    cleanupListeners()
    cleanupListeners = null
  }
})

const confirmSessionExpired = async () => {
  sessionExpiredDialog.value = false
  await router.push('/')
}

const handleConfirmCancel = () => {
  if (confirmDialog.value.resolver) {
    confirmDialog.value.resolver(false)
  }
  confirmDialog.value.open = false
}

const handleConfirmOk = () => {
  if (confirmDialog.value.resolver) {
    confirmDialog.value.resolver(true)
  }
  confirmDialog.value.open = false
}
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
