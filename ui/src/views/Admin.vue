<script setup>
import { ref, computed, onMounted, watch, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useTheme } from '../composables/useTheme'
import { useAuth } from '../composables/useAuth'
import { useIsMobile } from '../composables/useIsMobile'
import { isSuperAdmin as checkSuperAdmin } from '../utils/constants'
import { getCurrentUser } from '../api/auth'

const { isDark } = useTheme()
const { userInfo, setUserInfo, clearAuth } = useAuth()

const router = useRouter()

// 移动端默认收起侧边栏，桌面端默认展开
const { isMobile } = useIsMobile(1280, { useInnerWidth: true })
const drawer = ref(!isMobile.value)
const selectedItem = ref('system')

const SystemInfo = defineAsyncComponent(() => import('./admin/SystemInfo.vue'))
const Version = defineAsyncComponent(() => import('./admin/Version.vue'))
const CacheManage = defineAsyncComponent(() => import('./admin/CacheManage.vue'))
const SiteConfig = defineAsyncComponent(() => import('./admin/SiteConfig.vue'))
const ScheduledTasks = defineAsyncComponent(() => import('./admin/ScheduledTasks.vue'))
const MediaLibrary = defineAsyncComponent(() => import('./admin/media/MediaLibrary.vue'))
const VideoFileManager = defineAsyncComponent(() => import('./admin/media/VideoFileManager.vue'))
const AnimeLibrary = defineAsyncComponent(() => import('./admin/media/AnimeLibrary.vue'))
const SubtitleLibrary = defineAsyncComponent(() => import('./admin/media/SubtitleLibrary.vue'))
const QueueProgress = defineAsyncComponent(() => import('./admin/media/QueueProgress.vue'))
const ResourceDownloadCenter = defineAsyncComponent(() => import('./admin/download/DownloadCenter.vue'))
const UserManagement = defineAsyncComponent(() => import('./admin/UserManagement.vue'))
const McpAccess = defineAsyncComponent(() => import('./admin/McpAccess.vue'))
const AdminDanmaku = defineAsyncComponent(() => import('./admin/AdminDanmaku.vue'))

const mainMenuItems = [
  { id: 'system', title: '系统信息', icon: 'mdi-information', component: SystemInfo },
  { id: 'users', title: '用户管理', icon: 'mdi-account-cog', component: UserManagement },
  { id: 'danmaku', title: '弹幕管理', icon: 'mdi-comment-text-multiple', component: AdminDanmaku },
  { id: 'download-center', title: '下载中心', icon: 'mdi-download-network', component: ResourceDownloadCenter }
]

const systemSettingsMenuItems = [
  { id: 'tasks', title: '定时任务', icon: 'mdi-timer-sand', component: ScheduledTasks },
  { id: 'cache', title: '缓存管理', icon: 'mdi-cached', component: CacheManage },
  { id: 'mcp', title: 'MCP 接入', icon: 'mdi-connection', component: McpAccess },
  { id: 'version', title: '版本更新', icon: 'mdi-update', component: Version },
  { id: 'site', title: '服务配置', icon: 'mdi-web', component: SiteConfig }
]

const mediaMenuItems = [
  { id: 'queue', title: '队列进度', icon: 'mdi-progress-clock', component: QueueProgress },
  { id: 'anime', title: '动漫管理', icon: 'mdi-library', component: AnimeLibrary },
  { id: 'media', title: '媒体库配置', icon: 'mdi-folder-multiple', component: MediaLibrary },
  { id: 'files', title: '视频文件管理', icon: 'mdi-file-video', component: VideoFileManager },
  { id: 'subtitles', title: '字幕管理', icon: 'mdi-subtitles-outline', component: SubtitleLibrary }
]

const componentMap = Object.fromEntries(
  [...mainMenuItems, ...systemSettingsMenuItems, ...mediaMenuItems].map(item => [item.id, item.component])
)

const isSuperAdmin = computed(() => checkSuperAdmin(userInfo.value))

const visibleSystemSettingsItems = computed(() =>
  systemSettingsMenuItems.filter((item) => item.id !== 'mcp' || isSuperAdmin.value)
)

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await getCurrentUser()
    if (res?.code === 200 && res?.data) {
      const userData = res.data
      setUserInfo(userData)
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    handleLogout()
  }
}

const checkLoginStatus = () => {
  if (!userInfo.value) {
    router.push('/')
    return
  }
  fetchUserInfo()
}

const handleLogout = () => {
  clearAuth()
  router.push('/')
}

const currentComponent = computed(() => {
  return componentMap[selectedItem.value] || mainMenuItems[0].component
})

const fallbackMenuId = computed(() => mainMenuItems[0]?.id || 'system')

// 移动端选中导航后收起侧边栏（分组子项同样生效，分组头展开不触发）
const handleSelectMenu = (id) => {
  selectedItem.value = id
  if (isMobile.value) drawer.value = false
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
          v-for="item in mainMenuItems"
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

        <v-list-group value="system-settings">
          <template #activator="{ props }">
            <v-list-item
              v-bind="props"
              prepend-icon="mdi-cog"
              title="系统设置"
            />
          </template>

          <v-list-item
            v-for="item in visibleSystemSettingsItems"
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

    <v-app-bar :color="isDark ? 'surface' : 'primary'" elevation="2">
      <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
      <v-app-bar-title class="text-white">管理后台</v-app-bar-title>
      <v-spacer />
      <v-btn variant="text" color="white" @click="router.push('/')">
        <v-icon start>mdi-home</v-icon>
        返回首页
      </v-btn>
    </v-app-bar>

    <v-main class="bg-background">
      <v-container class="pa-6">
        <component :is="currentComponent" />
      </v-container>
    </v-main>
  </v-app>
</template>

