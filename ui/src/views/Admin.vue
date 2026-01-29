<script setup>
import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const API_BASE = '/api'
const router = useRouter()

const drawer = ref(true)
const selectedItem = ref('system')

const SystemInfo = defineAsyncComponent(() => import('./admin/SystemInfo.vue'))
const SiteConfig = defineAsyncComponent(() => import('./admin/SiteConfig.vue'))
const MediaLibrary = defineAsyncComponent(() => import('./admin/MediaLibrary.vue'))

const menuItems = [
  { id: 'system', title: '系统信息', icon: 'mdi-information', component: SystemInfo },
  { id: 'site', title: '站点配置', icon: 'mdi-web', component: SiteConfig },
  { id: 'media', title: '媒体库配置', icon: 'mdi-folder-multiple', component: MediaLibrary }
]

const userInfo = ref(null)
const userMenu = ref(false)

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
  const item = menuItems.find(i => i.id === selectedItem.value)
  return item ? item.component : menuItems[0].component
})

const handleSelectMenu = (id) => {
  selectedItem.value = id
}

onMounted(() => {
  checkLoginStatus()
})
</script>

<template>
  <v-app>
    <v-navigation-drawer v-model="drawer" :rail="false">
      <v-list>
        <v-list-item
          prepend-avatar="https://cdn.vuetifyjs.com/images/john.png"
          :title="userInfo?.username || 'Admin'"
          :subtitle="userInfo?.email || '暂无邮箱'"
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
          v-for="item in menuItems"
          :key="item.id"
          :value="item.id"
          :active="selectedItem === item.id"
          @click="handleSelectMenu(item.id)"
          :prepend-icon="item.icon"
          :title="item.title"
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
      <v-menu v-model="userMenu">
        <template v-slot:activator="{ props }">
          <v-btn icon v-bind="props">
            <v-avatar color="white">
              <v-icon color="primary">mdi-account</v-icon>
            </v-avatar>
          </v-btn>
        </template>
        <v-list>
          <v-list-item>
            <v-list-item-title class="font-weight-medium">{{ userInfo?.username }}</v-list-item-title>
            <v-list-item-subtitle>
              {{ userInfo?.email || '暂无邮箱' }}
              <template v-if="userInfo?.roleCodeList && userInfo.roleCodeList.length > 0">
                <v-chip size="x-small" class="ml-2" color="primary">
                  {{ userInfo.roleCodeList.join(', ') }}
                </v-chip>
              </template>
            </v-list-item-subtitle>
          </v-list-item>
          <v-divider />
          <v-list-item @click="router.push('/')">
            <v-list-item-title>
              <v-icon start>mdi-home</v-icon>
              返回首页
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>

    <v-main class="bg-grey-lighten-5">
      <v-container class="pa-6">
        <component :is="currentComponent" />
      </v-container>
    </v-main>
  </v-app>
</template>

