<script setup>
import { ref, computed, onMounted, watch, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const API_BASE = '/api'
const router = useRouter()

const drawer = ref(true)
const selectedItem = ref('system')

const SystemInfo = defineAsyncComponent(() => import('./admin/SystemInfo.vue'))
const SiteConfig = defineAsyncComponent(() => import('./admin/SiteConfig.vue'))
const MediaLibrary = defineAsyncComponent(() => import('./admin/media/MediaLibrary.vue'))
const VideoFileManager = defineAsyncComponent(() => import('./admin/media/VideoFileManager.vue'))
const AnimeLibrary = defineAsyncComponent(() => import('./admin/media/AnimeLibrary.vue'))
const SubtitleLibrary = defineAsyncComponent(() => import('./admin/media/SubtitleLibrary.vue'))
const QueueProgress = defineAsyncComponent(() => import('./admin/media/QueueProgress.vue'))
const ResourceDownload = defineAsyncComponent(() => import('./admin/media/ResourceDownload.vue'))
const ResourceDownloadProgress = defineAsyncComponent(() => import('./admin/download/ResourceDownloadProgress.vue'))
const ResourceDownloadSettings = defineAsyncComponent(() => import('./admin/download/ResourceDownloadSettings.vue'))
const ResourceRssSubscription = defineAsyncComponent(() => import('./admin/download/ResourceRssSubscription.vue'))
const UserManagement = defineAsyncComponent(() => import('./admin/UserManagement.vue'))
const McpAccess = defineAsyncComponent(() => import('./admin/McpAccess.vue'))

const mainMenuItems = [
  { id: 'system', title: '系统信息', icon: 'mdi-information', component: SystemInfo },
  { id: 'mcp', title: 'MCP 接入', icon: 'mdi-connection', component: McpAccess },
  { id: 'users', title: '用户管理', icon: 'mdi-account-cog', component: UserManagement }
]

const siteMenuItem = { id: 'site', title: '服务配置', icon: 'mdi-web', component: SiteConfig }

const mediaMenuItems = [
  { id: 'queue', title: '队列进度', icon: 'mdi-progress-clock', component: QueueProgress },
  { id: 'anime', title: '动漫管理', icon: 'mdi-library', component: AnimeLibrary },
  { id: 'media', title: '媒体库配置', icon: 'mdi-folder-multiple', component: MediaLibrary },
  { id: 'files', title: '视频文件管理', icon: 'mdi-file-video', component: VideoFileManager },
  { id: 'subtitles', title: '字幕管理', icon: 'mdi-subtitles-outline', component: SubtitleLibrary }
]

const resourceDownloadMenuItems = [
  { id: 'resource-search-download', title: '资源搜索下载', icon: 'mdi-cloud-search', component: ResourceDownload },
  { id: 'resource-rss-subscription', title: 'RSS订阅下载', icon: 'mdi-rss-box', component: ResourceRssSubscription },
  { id: 'resource-download-progress', title: '下载任务进度', icon: 'mdi-download-multiple', component: ResourceDownloadProgress },
  { id: 'resource-download-settings', title: '资源与下载器配置', icon: 'mdi-tune-variant', component: ResourceDownloadSettings }
]

const componentMap = Object.fromEntries(
  [...mainMenuItems, ...mediaMenuItems, ...resourceDownloadMenuItems, siteMenuItem].map(item => [item.id, item.component])
)

const userInfo = ref(null)

const isSuperAdmin = computed(() =>
  Array.isArray(userInfo.value?.roleCodeList) && userInfo.value.roleCodeList.includes('super-admin')
)

const visibleMainMenuItems = computed(() =>
  mainMenuItems.filter((item) => item.id !== 'mcp' || isSuperAdmin.value)
)

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      const userData = res.data.data
      localStorage.setItem('userInfo', JSON.stringify(userData))
      userInfo.value = userData
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    handleLogout()
  }
}

const checkLoginStatus = () => {
  const token = localStorage.getItem('token')
  const user = localStorage.getItem('userInfo')
  if (!token || !user) {
    router.push('/')
    return
  }
  try {
    userInfo.value = JSON.parse(user)
    // 获取最新的用户信息
    fetchUserInfo()
  } catch (e) {
    console.error('解析用户信息失败:', e)
    router.push('/')
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  userInfo.value = null
  router.push('/')
}

const currentComponent = computed(() => {
  return componentMap[selectedItem.value] || mainMenuItems[0].component
})

const fallbackMenuId = computed(() => visibleMainMenuItems.value[0]?.id || 'system')

const handleSelectMenu = (id) => {
  selectedItem.value = id
}

onMounted(() => {
  checkLoginStatus()
})

// 非超级管理员无法访问 MCP 菜单：若当前选中 mcp，则切回可见首项
watch([isSuperAdmin, () => selectedItem.value], () => {
  if (selectedItem.value === 'mcp' && !isSuperAdmin.value) {
    selectedItem.value = fallbackMenuId.value
  }
})
</script>

<template>
  <v-app>
    <v-navigation-drawer v-model="drawer" :rail="false">
      <v-list>
        <v-list-item
          prepend-icon="mdi-account-circle"
          :title="userInfo?.username || 'Admin'"
        >
          <template v-if="userInfo?.roleCodeList && userInfo.roleCodeList.length > 0" v-slot:append>
            <v-chip size="x-small" color="primary">
              {{ userInfo.roleCodeList[0] }}
            </v-chip>
          </template>
        </v-list-item>
      </v-list>

      <v-divider></v-divider>

      <v-list density="compact" nav>
        <v-list-item
          v-for="item in visibleMainMenuItems"
          :key="item.id"
          :value="item.id"
          :active="selectedItem === item.id"
          @click="handleSelectMenu(item.id)"
          :prepend-icon="item.icon"
          :title="item.title"
          color="primary"
          link
        ></v-list-item>

        <v-list-group value="media-management">
          <template #activator="{ props }">
            <v-list-item
              v-bind="props"
              prepend-icon="mdi-folder-cog"
              title="媒体管理"
            />
          </template>

          <v-list-item
            v-for="item in mediaMenuItems"
            :key="item.id"
            :value="item.id"
            :active="selectedItem === item.id"
            @click="handleSelectMenu(item.id)"
            :prepend-icon="item.icon"
            :title="item.title"
            class="pl-6"
            color="primary"
            link
          ></v-list-item>
        </v-list-group>

        <v-list-group value="resource-download">
          <template #activator="{ props }">
            <v-list-item
              v-bind="props"
              prepend-icon="mdi-download-network"
              title="资源下载（实验性功能）"
            />
          </template>

          <v-list-item
            v-for="item in resourceDownloadMenuItems"
            :key="item.id"
            :value="item.id"
            :active="selectedItem === item.id"
            @click="handleSelectMenu(item.id)"
            :prepend-icon="item.icon"
            :title="item.title"
            class="pl-6"
            color="primary"
            link
          ></v-list-item>
        </v-list-group>

        <v-list-item
          :value="siteMenuItem.id"
          :active="selectedItem === siteMenuItem.id"
          @click="handleSelectMenu(siteMenuItem.id)"
          :prepend-icon="siteMenuItem.icon"
          :title="siteMenuItem.title"
          color="primary"
          link
        ></v-list-item>
      </v-list>

      <template v-slot:append>
        <div class="pa-2">
          <v-btn block color="error" variant="outlined" @click="handleLogout">
            <v-icon start>mdi-logout</v-icon>
            退出登录
          </v-btn>
        </div>
      </template>
    </v-navigation-drawer>

    <v-app-bar color="primary" elevation="2">
      <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
      <v-app-bar-title class="text-white">管理后台</v-app-bar-title>
      <v-spacer />
      <v-btn variant="text" color="white" @click="router.push('/')">
        <v-icon start>mdi-home</v-icon>
        返回首页
      </v-btn>
    </v-app-bar>

    <v-main class="bg-grey-lighten-5">
      <v-container class="pa-6">
        <component :is="currentComponent" />
      </v-container>
    </v-main>
  </v-app>
</template>

