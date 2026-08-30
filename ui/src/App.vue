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
        <v-card-text>
          <p class="confirm-message">{{ confirmDialog.message }}</p>
          <div v-if="confirmDialog.links?.length" class="confirm-links">
            <a
              v-for="l in confirmDialog.links"
              :key="l.href"
              :href="l.href"
              target="_blank"
              rel="noopener noreferrer"
              class="confirm-link"
            >
              <i class="mdi mdi-open-in-new"></i> {{ l.text }}
            </a>
          </div>
        </v-card-text>
        <v-card-actions
          class="confirm-actions"
          :class="{ 'is-stacked': confirmDialog.actions.length > 0 }"
        >
          <v-spacer v-if="!confirmDialog.actions.length" />
          <v-btn variant="text" @click="handleConfirmCancel">{{ confirmDialog.cancelText }}</v-btn>
          <v-btn
            v-for="a in confirmDialog.actions"
            :key="a.value"
            variant="text"
            :color="a.color || 'primary'"
            @click="handleAction(a)"
          >{{ a.text }}</v-btn>
          <v-btn :color="confirmDialog.color" @click="handleConfirmOk">{{ confirmDialog.confirmText }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-app>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { UiFeedbackEvents } from './utils/ui-feedback'
import { getSiteConfig } from './api/site'
import { readInstalled, writeInstalled, writeSiteConfig } from './utils/siteConfig'

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
  links: [],
  actions: [],
  resolver: null
})

const checkInstallStatus = async () => {
  try {
    const res = await getSiteConfig()
    const isInstalled = res?.data?.installed === true

    if (isInstalled) {
      writeInstalled(true)
      writeSiteConfig(res.data)
    } else {
      writeInstalled(false)
    }
  } catch (error) {
    console.error('检查安装状态失败:', error)
    writeInstalled(false)
  } finally {
    checkingInstall.value = false
  }
}

let cleanupListeners = null

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
      links: Array.isArray(detail.links) ? detail.links : [],
      actions: Array.isArray(detail.actions) ? detail.actions : [],
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
  if (readInstalled()) {
    checkingInstall.value = false
    return
  }
  // 兼容旧缓存为字符串 'false' 时也直接跳过服务器请求
  const stored = localStorage.getItem('installed')
  if (stored !== null) {
    checkingInstall.value = false
    return
  }

  // 如果没有本地缓存，从服务器获取
  await checkInstallStatus()
})

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

const handleAction = (action) => {
  if (confirmDialog.value.resolver) {
    confirmDialog.value.resolver(action.value)
  }
  confirmDialog.value.open = false
}
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}

.confirm-message {
  margin: 0 0 8px;
  white-space: pre-line;
}

/* 按钮多的场景（有附加操作）竖排通栏，避免窄屏横向溢出 */
.confirm-actions.is-stacked {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 12px 24px 16px;
}

.confirm-actions.is-stacked .v-btn {
  width: 100%;
  margin: 0;
}

.confirm-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--al-border-neutral);
}

.confirm-link {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--anime-text-secondary);
  text-decoration: none;
  padding: 6px 8px;
  border-radius: 8px;
  word-break: break-all;
  transition: background 0.15s ease, color 0.15s ease;
}

.confirm-link i {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--anime-accent-red);
}

.confirm-link:hover {
  background: rgba(var(--al-accent-rgb), 0.08);
  color: var(--anime-accent-red);
}
</style>
