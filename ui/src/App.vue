<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const API_BASE = '/api'

const checkingInstall = ref(true)
const siteConfig = ref(null)

const loadSiteConfig = async () => {
  const stored = localStorage.getItem('siteConfig')
  if (stored) {
    try {
      siteConfig.value = JSON.parse(stored)
      return
    } catch (e) {
      console.error('解析本地配置失败:', e)
    }
  }

  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    if (res.data?.data) {
      siteConfig.value = res.data.data
      localStorage.setItem('siteConfig', JSON.stringify(res.data.data))
    }
  } catch (error) {
    console.error('获取站点配置失败:', error)
  }
}

const checkInstallStatus = async () => {
  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    const isInstalled = res.data?.data?.installed === true

    if (isInstalled) {
      localStorage.setItem('installed', 'true')
      await loadSiteConfig()
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

onMounted(() => {
  const installed = localStorage.getItem('installed')
  if (installed === 'true') {
    loadSiteConfig()
    checkingInstall.value = false
  } else {
    checkInstallStatus()
  }
})
</script>

<template>
  <v-app>
    <v-main class="bg-grey-lighten-5">
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
  </v-app>
</template>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
