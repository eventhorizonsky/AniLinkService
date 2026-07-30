<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { showAppMessage } from '../utils/ui-feedback'

const router = useRouter()
const route = useRoute()
const API_BASE = '/api'

const activeTab = ref(['history', 'follows', 'danmaku', 'messages', 'binding'].includes(route.query.tab) ? route.query.tab : 'history')

const historyList = ref([])
const historyLoading = ref(false)
const historyError = ref('')
const historyPage = ref(1)
const historyPageSize = ref(12)
const historyTotal = ref(0)

const followList = ref([])
const followLoading = ref(false)
const followError = ref('')
const followPage = ref(1)
const followPageSize = ref(20)
const followTotal = ref(0)
const followStatus = ref('')  // 空字符串 = 显示全部，不筛选
const followKeyword = ref('')
const statusUpdatingAnimeId = ref(null)
const pullingBangumi = ref(false)
const mobileMenuAnimeId = ref(null)  // 移动端弹窗：当前打开的是哪个番剧

// 确认对话框
const confirmDialog = ref({ show: false, title: '', message: '', resolve: null })
const showConfirm = (title, message) => {
  return new Promise((resolve) => {
    confirmDialog.value = { show: true, title, message, resolve }
  })
}

const messages = ref([])
const messagesLoading = ref(false)
const messagesError = ref('')
const messagePage = ref(1)
const messagePageSize = ref(20)
const messageTotal = ref(0)
const messageFilterType = ref('')
const selectedMessage = ref(null)

const danmakuRecords = ref([])
const danmakuLoading = ref(false)
const danmakuError = ref('')
const danmakuPage = ref(1)
const danmakuPageSize = ref(20)
const danmakuTotal = ref(0)

const bangumiLoading = ref(false)
const bangumiBinding = ref(false)
const bangumiUnbinding = ref(false)
const bangumiTokenInput = ref('')
const bangumiStatus = ref({
  bound: false,
  tokenValid: false,
  tokenExpired: false,
  bangumiUserId: null,
  bangumiUsername: '',
  bangumiNickname: '',
  profile: null,
  statusMessage: '未绑定 Bangumi 账号'
})

const tabs = [
  { value: 'history', label: '观看历史', icon: 'mdi mdi-history' },
  { value: 'follows', label: '我的追番', icon: 'mdi mdi-bookmark-multiple' },
  { value: 'danmaku', label: '我的弹幕', icon: 'mdi mdi-comment-text-multiple' },
  { value: 'messages', label: '消息中心', icon: 'mdi mdi-bell-outline' },
  { value: 'binding', label: '账号绑定', icon: 'mdi mdi-link-variant' }
]

const messageTypes = [
  { label: '全部', value: '', color: 'grey' },
  { label: '剧集更新', value: 'episode_update', color: 'primary' },
  { label: '系统通知', value: 'system', color: 'grey-darken-1' }
]

const followStatuses = [
  { label: '在看', value: 'watching', color: 'primary' },
  { label: '看过', value: 'watched', color: 'success' },
  { label: '搁置', value: 'on_hold', color: 'warning' },
  { label: '抛弃', value: 'dropped', color: 'error' }
]

const followStatusOptions = [
  { title: '全部', value: '' },
  ...followStatuses.map(s => ({ title: s.label, value: s.value }))
]

const syncTabQuery = (tab) => {
  const query = { ...route.query }
  if (tab === 'history') {
    delete query.tab
  } else {
    query.tab = tab
  }
  router.replace({ query })
}

watch(() => route.query.tab, (tab) => {
  activeTab.value = ['history', 'follows', 'danmaku', 'messages', 'binding'].includes(tab) ? tab : 'history'
})

watch(activeTab, async (tab) => {
  syncTabQuery(tab)
  if (tab === 'history') {
    await fetchPlayHistory()
  }
  if (tab === 'follows') {
    await fetchFollowList()
  }
  if (tab === 'danmaku') {
    await fetchDanmakuRecords()
  }
  if (tab === 'messages') {
    await fetchMessages()
  }
  if (tab === 'binding') {
    await fetchBangumiStatus()
  }
})

const typeLabel = (type) => {
  return messageTypes.find((t) => t.value === type)?.label || type
}

const typeChipColor = (type) => {
  return messageTypes.find((t) => t.value === type)?.color || 'grey'
}

const fetchMessages = async () => {
  messagesLoading.value = true
  messagesError.value = ''
  try {
    const params = {
      page: messagePage.value,
      pageSize: messagePageSize.value
    }

    let url = '/api/messages'
    if (messageFilterType.value) {
      url = `/api/messages/type/${messageFilterType.value}`
    }

    const response = await axios.get(url, { params })
    if (response.data?.code === 200) {
      messages.value = response.data.data?.content || []
      messageTotal.value = Number(response.data.data?.totalElements || 0)
      return
    }
    messagesError.value = response.data?.msg || '加载消息列表失败'
  } catch (err) {
    console.error('加载消息列表失败:', err)
    messagesError.value = '加载消息列表失败'
  } finally {
    messagesLoading.value = false
  }
}

const markMessageAsRead = async (messageId) => {
  try {
    const response = await axios.put(`/api/messages/${messageId}/read`)
    if (response.data?.code === 200) {
      await fetchMessages()
    }
  } catch (err) {
    console.error('标记消息已读失败:', err)
  }
}

const markAllMessagesAsRead = async () => {
  try {
    const response = await axios.put('/api/messages/mark-all-read')
    if (response.data?.code === 200 || response.data?.code === 0) {
      await fetchMessages()
      return
    }
    showAppMessage(response.data?.msg || '全部已读失败', 'error')
  } catch (err) {
    console.error('全部标记已读失败:', err)
    showAppMessage('全部已读失败', 'error')
  }
}

const deleteMessage = async (messageId) => {
  const ok = await showConfirm('删除消息', '确定要删除这条消息吗？')
  if (!ok) return

  try {
    const response = await axios.delete(`/api/messages/${messageId}`)
    if (response.data?.code === 200) {
      await fetchMessages()
      selectedMessage.value = null
      return
    }
    showAppMessage(response.data?.msg || '删除失败', 'error')
  } catch (err) {
    console.error('删除消息失败:', err)
    showAppMessage('删除消息失败', 'error')
  }
}

const selectMessage = async (message) => {
  selectedMessage.value = message
  if (!message.isRead) {
    await markMessageAsRead(message.id)
  }
}

const fetchDanmakuRecords = async () => {
  danmakuLoading.value = true
  danmakuError.value = ''
  try {
    const response = await axios.get('/api/v2/danmaku-records/mine', {
      params: { page: danmakuPage.value, pageSize: danmakuPageSize.value }
    })
    if (response.data?.code === 200) {
      danmakuRecords.value = response.data.data?.content || []
      danmakuTotal.value = Number(response.data.data?.totalElements || 0)
    } else {
      danmakuError.value = response.data?.msg || '加载弹幕记录失败'
    }
  } catch (err) {
    console.error('加载弹幕记录失败:', err)
    danmakuError.value = '加载弹幕记录失败'
  } finally {
    danmakuLoading.value = false
  }
}

const goToDanmakuPlayer = (record) => {
  const targetVideoId = record.videoId
  const targetAnimeId = record.animeId
  const targetEpisodeId = record.episodeId
  if (targetVideoId) {
    router.push({
      name: 'Player',
      params: { videoId: String(targetVideoId) },
      query: {
        animeId: targetAnimeId ? String(targetAnimeId) : undefined,
        episodeId: targetEpisodeId ? String(targetEpisodeId) : undefined,
        t: record.time != null ? String(record.time) : undefined,
      }
    })
  } else if (targetAnimeId) {
    router.push(`/anime/${targetAnimeId}`)
  }
}

const danmakuModeLabel = (mode) => {
  const map = { 1: '普通', 4: '底部', 5: '顶部' }
  return map[mode] || `模式${mode}`
}

const handleMessageFilterChange = async () => {
  messagePage.value = 1
  await fetchMessages()
}

const handleMessagePageChange = async (page) => {
  messagePage.value = page
  await fetchMessages()
}

const fetchCurrentUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      localStorage.setItem('userInfo', JSON.stringify(res.data.data))
    }
  } catch (err) {
    console.error('刷新当前用户信息失败:', err)
  }
}

const fetchPlayHistory = async () => {
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await axios.get('/api/play-history', {
      params: {
        page: historyPage.value,
        pageSize: historyPageSize.value,
      },
    })
    if (response.data?.code === 200) {
      historyList.value = response.data.data?.content || []
      historyTotal.value = Number(response.data.data?.totalElements || 0)
    } else {
      historyError.value = response.data?.msg || '加载播放历史失败'
    }
  } catch (err) {
    console.error('加载播放历史失败:', err)
    historyError.value = '加载播放历史失败'
  } finally {
    historyLoading.value = false
  }
}

const goToPlayer = (item) => {
  if (!item?.videoId) return
  router.push({
    name: 'Player',
    params: { videoId: String(item.videoId) },
    query: {
      animeId: String(item.animeId || ''),
      episodeId: String(item.episodeId || ''),
    },
  })
}

const deleteHistoryItem = async (id) => {
  if (!id) return
  const ok = await showConfirm('删除记录', '确定删除这条播放历史吗？')
  if (!ok) return
  try {
    const response = await axios.delete(`/api/play-history/${id}`)
    if (response.data?.code === 200) {
      if (historyList.value.length === 1 && historyPage.value > 1) {
        historyPage.value -= 1
      }
      await fetchPlayHistory()
    } else {
      showAppMessage(response.data?.msg || '删除失败', 'error')
    }
  } catch (err) {
    console.error('删除播放历史失败:', err)
    showAppMessage('删除播放历史失败', 'error')
  }
}

const clearHistory = async () => {
  const ok = await showConfirm('清空历史', '确定清空所有播放历史吗？该操作不可恢复。')
  if (!ok) return
  try {
    const response = await axios.delete('/api/play-history/clear')
    if (response.data?.code === 200) {
      historyPage.value = 1
      await fetchPlayHistory()
    } else {
      showAppMessage(response.data?.msg || '清空失败', 'error')
    }
  } catch (err) {
    console.error('清空播放历史失败:', err)
    showAppMessage('清空播放历史失败', 'error')
  }
}

const STATUS_ORDER = { watching: 1, watched: 2, on_hold: 3, dropped: 4 }

const fetchFollowList = async () => {
  followLoading.value = true
  followError.value = ''
  try {
    const params = {
      page: followPage.value,
      pageSize: followPageSize.value
    }

    // 按状态下拉框筛选（空=全部）
    const url = followStatus.value ? `/api/follows/status/${followStatus.value}` : '/api/follows'
    const response = await axios.get(url, { params })

    if (response.data?.code === 200) {
      if (Array.isArray(response.data.data)) {
        const keyword = followKeyword.value.trim().toLowerCase()
        const filtered = response.data.data.filter((item) => {
          return !keyword || String(item.animeTitle || '').toLowerCase().includes(keyword)
        })
        // 按状态优先级排序：在看→看过→搁置→抛弃
        filtered.sort((a, b) => (STATUS_ORDER[a.status] || 99) - (STATUS_ORDER[b.status] || 99))
        followList.value = filtered
        followTotal.value = followList.value.length
      } else {
        const rawList = response.data.data?.content || []
        const keyword = followKeyword.value.trim().toLowerCase()
        const filtered = rawList.filter((item) => {
          return !keyword || String(item.animeTitle || '').toLowerCase().includes(keyword)
        })
        filtered.sort((a, b) => (STATUS_ORDER[a.status] || 99) - (STATUS_ORDER[b.status] || 99))
        followList.value = filtered
        followTotal.value = response.data.data?.totalElements || 0
      }
    } else {
      followError.value = response.data?.msg || '加载追番列表失败'
    }
  } catch (err) {
    console.error('加载追番列表失败:', err)
    followError.value = '加载追番列表失败'
  } finally {
    followLoading.value = false
  }
}

const goToAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}

const statusLabel = (status) => {
  const map = { watching: '在看', watched: '看过', on_hold: '搁置', dropped: '抛弃' }
  return map[status] || status
}

const statusChipColor = (status) => {
  const map = { watching: 'primary', watched: 'success', on_hold: 'warning', dropped: 'error' }
  return map[status] || 'grey'
}

const CASCADE_COLORS = { watching: '#ff9800', watched: '#4caf50', on_hold: '#ffc107', dropped: '#ef5350' }
const cascadeColor = (s) => CASCADE_COLORS[s] || '#999'

const changeFollowStatus = async (animeId, status) => {
  const currentFollow = followList.value.find((item) => item.animeId === animeId)
  if (currentFollow?.status === status) {
    return
  }

  statusUpdatingAnimeId.value = animeId
  try {
    const response = await axios.put(`/api/follows/${animeId}/status`, null, { params: { status } })
    if (response.data?.code === 200) {
      await fetchFollowList()
      return
    }
    showAppMessage(response.data?.msg || '更新追番状态失败', 'error')
  } catch (err) {
    console.error('更新追番状态失败:', err)
    showAppMessage('更新追番状态失败', 'error')
  } finally {
    statusUpdatingAnimeId.value = null
  }
}

const unfollow = async (animeId, animeTitle) => {
  const ok = await showConfirm('取消追番', `确定要取消追番《${animeTitle}》吗？`)
  if (!ok) return
  try {
    const response = await axios.delete(`/api/follows/${animeId}`)
    if (response.data?.code === 200) {
      await fetchFollowList()
      return
    }
    showAppMessage(response.data?.msg || '取消追番失败', 'error')
  } catch (err) {
    console.error('取消追番失败:', err)
    showAppMessage('取消追番失败', 'error')
  }
}

const pullBangumiCollections = async () => {
  const ok = await showConfirm('拉取 Bangumi 追番',
    '将从 Bangumi 拉取你的所有动画收藏并同步到本地追番列表。以 Bangumi 数据为准，同名番剧的状态将被覆盖。是否继续？')
  if (!ok) return
  pullingBangumi.value = true
  try {
    const res = await axios.post('/api/bangumi/sync/pull-collections')
    if (res.data?.code === 200 && res.data?.data) {
      const d = res.data.data
      const msg = `同步完成：共 ${d.total} 条，新增 ${d.created} 条，更新 ${d.updated} 条，跳过 ${d.skipped} 条`
      showAppMessage(msg, 'success')
      await fetchFollowList()
    } else {
      showAppMessage(res.data?.msg || '拉取失败', 'error')
    }
  } catch (err) {
    console.error('拉取 Bangumi 追番失败:', err)
    showAppMessage('拉取 Bangumi 追番失败', 'error')
  } finally {
    pullingBangumi.value = false
  }
}

const fetchBangumiStatus = async () => {
  bangumiLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/bangumi/account/status`)
    if (res.data?.code === 200 && res.data?.data) {
      bangumiStatus.value = res.data.data
      await fetchCurrentUserInfo()
      return
    }
    showAppMessage(res.data?.msg || '获取 Bangumi 绑定状态失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || '获取 Bangumi 绑定状态失败', 'error')
  } finally {
    bangumiLoading.value = false
  }
}

const bindBangumiToken = async () => {
  const token = bangumiTokenInput.value.trim()
  if (!token) {
    showAppMessage('请输入 Bangumi Access Token', 'warning')
    return
  }
  bangumiBinding.value = true
  try {
    const res = await axios.post(`${API_BASE}/bangumi/account/bind`, { accessToken: token })
    if (res.data?.code === 200 && res.data?.data) {
      bangumiStatus.value = res.data.data
      bangumiTokenInput.value = ''
      await fetchCurrentUserInfo()
      showAppMessage('Bangumi 账号绑定成功', 'success')
      return
    }
    showAppMessage(res.data?.msg || 'Bangumi 绑定失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || 'Bangumi 绑定失败', 'error')
  } finally {
    bangumiBinding.value = false
  }
}

const unbindBangumiToken = async () => {
  bangumiUnbinding.value = true
  try {
    const res = await axios.delete(`${API_BASE}/bangumi/account/bind`)
    if (res.data?.code === 200) {
      bangumiStatus.value = {
        bound: false,
        tokenValid: false,
        tokenExpired: false,
        bangumiUserId: null,
        bangumiUsername: '',
        bangumiNickname: '',
        profile: null,
        statusMessage: '未绑定 Bangumi 账号'
      }
      await fetchCurrentUserInfo()
      showAppMessage('已解除 Bangumi 绑定', 'success')
      return
    }
    showAppMessage(res.data?.msg || '解除 Bangumi 绑定失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || '解除 Bangumi 绑定失败', 'error')
  } finally {
    bangumiUnbinding.value = false
  }
}

const bangumiStatusTone = computed(() => {
  if (!bangumiStatus.value.bound) return 'info'
  if (bangumiStatus.value.tokenValid) return 'success'
  if (bangumiStatus.value.tokenExpired) return 'warning'
  return 'info'
})

const bangumiAvatar = computed(() => {
  return bangumiStatus.value.profile?.avatar?.medium
    || bangumiStatus.value.profile?.avatar?.large
    || bangumiStatus.value.profile?.avatar?.small
    || ''
})

const messageTotalPages = computed(() => Math.max(1, Math.ceil(messageTotal.value / messagePageSize.value)))

const messageUnreadCount = computed(() => messages.value.filter((m) => !m.isRead).length)

const historyTotalPages = computed(() => Math.max(1, Math.ceil(historyTotal.value / historyPageSize.value)))
const followTotalPages = computed(() => Math.max(1, Math.ceil(followTotal.value / followPageSize.value)))
const danmakuTotalPages = computed(() => Math.max(1, Math.ceil(danmakuTotal.value / danmakuPageSize.value)))

const formatDateTime = (value) => {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const formatDate = (value) => {
  if (!value) return '--'
  return new Date(value).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

const getProgressText = (item) => {
  const progress = Number(item?.progressSeconds || 0)
  const duration = Number(item?.durationSeconds || 0)
  const percent = Number(item?.progressPercentage || 0)
  if (!duration) return `${progress}s`
  return `${progress}s / ${duration}s (${Math.min(100, Math.max(0, percent))}%)`
}

const closeMobilePopup = () => { mobileMenuAnimeId.value = null }
onMounted(async () => {
  document.addEventListener('click', closeMobilePopup)
  // Bangumi 状态始终拉取（追番页需要知道是否已绑定）
  fetchBangumiStatus()
  if (activeTab.value === 'history') {
    await fetchPlayHistory()
  } else if (activeTab.value === 'follows') {
    await fetchFollowList()
  } else if (activeTab.value === 'danmaku') {
    await fetchDanmakuRecords()
  } else if (activeTab.value === 'messages') {
    await fetchMessages()
  }
})
onBeforeUnmount(() => document.removeEventListener('click', closeMobilePopup))
</script>

<template>
  <div class="profile-page unified-page-shell">
    <v-card class="profile-page-card unified-panel elevation-2">
      <v-card-title class="text-h5 d-flex align-center ga-2 unified-panel-title">
        <i class="mdi mdi-account-circle-outline" style="color: #c45d2b;"></i>
        个人中心
      </v-card-title>
      <v-card-text class="pa-5">
        <v-container class="profile-container">
          <div class="profile-shell">
            <aside class="profile-sidebar">
              <button
                v-for="tab in tabs"
                :key="tab.value"
                class="profile-tab-btn"
                :class="{ active: activeTab === tab.value }"
                @click="activeTab = tab.value"
              >
                <i :class="tab.icon"></i>
                <span>{{ tab.label }}</span>
              </button>
            </aside>

            <section class="profile-main">
              <section v-if="activeTab === 'history'" class="profile-section">
                <div class="text-h5 d-flex align-center justify-space-between profile-section-title">
                  <div class="d-flex align-center">
                    <i class="mdi mdi-history mr-3" style="color: #c45d2b;"></i>
                    观看历史
                  </div>
                  <v-btn variant="tonal" color="error" :disabled="historyLoading || historyTotal === 0" @click="clearHistory">
                    清空历史
                  </v-btn>
                </div>
                <div class="profile-section-body">
              <v-alert v-if="historyError" type="error" variant="tonal" class="mb-4">{{ historyError }}</v-alert>
              <v-skeleton-loader v-if="historyLoading" type="list-item-avatar-three-line@4" />
              <div v-else-if="historyList.length > 0" class="history-list">
                <v-card v-for="item in historyList" :key="item.id" class="history-card" elevation="1">
                  <div class="history-main-card">
                    <div class="history-content">
                      <h3 class="history-title">{{ item.animeTitle || `番剧 #${item.animeId}` }}</h3>
                      <p class="history-subtitle">最近播放: {{ item.videoName || `视频 #${item.videoId || '-'}` }}</p>
                      <p class="history-meta">播放进度: {{ getProgressText(item) }}</p>
                      <p class="history-meta">最近播放时间: {{ formatDateTime(item.lastPlayTime) }}</p>
                    </div>
                    <div class="history-actions">
                      <v-btn color="primary" :disabled="!item.videoId" @click="goToPlayer(item)">继续播放</v-btn>
                      <v-btn variant="outlined" color="error" @click="deleteHistoryItem(item.id)">删除</v-btn>
                    </div>
                  </div>
                </v-card>
              </div>
              <v-alert v-else type="info" variant="tonal" class="text-center">暂无播放历史</v-alert>
              <div v-if="historyTotalPages > 1" class="d-flex justify-center mt-6">
                <v-pagination v-model="historyPage" :length="historyTotalPages" @update:model-value="fetchPlayHistory" />
              </div>
                </div>
              </section>

              <section v-else-if="activeTab === 'follows'" class="profile-section">
                <div class="text-h5 d-flex align-center profile-section-title">
                  <i class="mdi mdi-bookmark-multiple mr-3" style="color: #e74c3c;"></i>
                  我的追番
                </div>
                <div class="profile-section-body">
              <v-alert v-if="followError" type="error" variant="tonal" class="mb-4">{{ followError }}</v-alert>

              <!-- 搜索 + 筛选 -->
              <div class="follow-search-row">
                <v-text-field
                  v-model="followKeyword"
                  placeholder="搜索番剧标题..."
                  density="compact"
                  variant="outlined"
                  hide-details
                  @keyup.enter="followPage = 1; fetchFollowList()"
                >
                  <template #prepend-inner>
                    <i class="mdi mdi-magnify"></i>
                  </template>
                </v-text-field>
                <v-select
                  v-model="followStatus"
                  :items="followStatusOptions"
                  density="compact"
                  variant="outlined"
                  hide-details
                  style="max-width: 110px;"
                  @update:model-value="followPage = 1; fetchFollowList()"
                />
                <v-btn color="primary" variant="tonal" @click="followPage = 1; fetchFollowList()">搜索</v-btn>
                <v-btn
                  v-if="bangumiStatus.bound"
                  color="orange"
                  variant="outlined"
                  :loading="pullingBangumi"
                  @click="pullBangumiCollections"
                >
                  <i class="mdi mdi-cloud-download mr-1"></i>拉取 Bangumi
                </v-btn>
              </div>

              <v-skeleton-loader v-if="followLoading" type="card@3" />

              <!-- 追番卡片列表 -->
              <div v-else-if="followList.length > 0" class="follow-cards">
                <v-card
                  v-for="follow in followList"
                  :key="follow.id"
                  class="follow-cover-card"
                  elevation="1"
                  hover
                  @click="goToAnime(follow.animeId)"
                >
                  <div class="follow-cover-inner">
                    <!-- 封面 -->
                    <div class="follow-cover-poster">
                      <v-img
                        v-if="follow.imageUrl"
                        :src="follow.imageUrl"
                        :alt="follow.animeTitle"
                        cover
                        class="follow-cover-img"
                      >
                        <template #placeholder>
                          <v-skeleton-loader type="image" />
                        </template>
                      </v-img>
                      <div v-else class="follow-cover-placeholder">
                        <i class="mdi mdi-image-outline"></i>
                      </div>
                      <!-- 状态 Tag -->
                      <v-chip
                        class="follow-cover-tag"
                        :color="statusChipColor(follow.status)"
                        size="x-small"
                        variant="elevated"
                      >
                        {{ statusLabel(follow.status) }}
                      </v-chip>
                    </div>
                    <!-- 信息 -->
                    <div class="follow-cover-info">
                      <h3 class="follow-cover-title">{{ follow.animeTitle }}</h3>
                      <div class="follow-cover-date">追番 {{ formatDate(follow.followAt) }}</div>
                      <!-- 桌面端：级联按钮组 -->
                      <div class="status-cascade status-cascade-desktop" @click.stop>
                        <button
                          v-for="s in followStatuses"
                          :key="s.value"
                          class="status-cascade-btn"
                          :class="{ active: follow.status === s.value }"
                          :style="{ '--cascade-color': cascadeColor(s.value) }"
                          :disabled="statusUpdatingAnimeId === follow.animeId"
                          :title="'标记为: ' + s.label"
                          @click="changeFollowStatus(follow.animeId, s.value)"
                        >{{ s.label }}</button>
                      </div>
                      <!-- 移动端：标记按钮 + 弹出选择 -->
                      <div class="status-cascade-mobile" @click.stop>
                        <button
                          class="mobile-mark-btn"
                          @click="mobileMenuAnimeId = (mobileMenuAnimeId === follow.animeId ? null : follow.animeId)"
                        >
                          <i class="mdi mdi-tag-outline"></i> 标记
                        </button>
                        <div v-if="mobileMenuAnimeId === follow.animeId" class="mobile-status-popup">
                          <button
                            v-for="s in followStatuses"
                            :key="s.value"
                            class="mobile-status-item"
                            :class="{ active: follow.status === s.value }"
                            @click="changeFollowStatus(follow.animeId, s.value); mobileMenuAnimeId = null"
                          >
                            <span class="mobile-status-dot" :style="{ background: cascadeColor(s.value) }"></span>
                            {{ s.label }}
                            <i v-if="follow.status === s.value" class="mdi mdi-check"></i>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </v-card>
              </div>

              <v-alert v-else type="info" variant="tonal" class="text-center">
                <div class="text-h6 mb-2">还没有追番哦</div>
                <div>快去首页发现喜欢的番剧吧！</div>
              </v-alert>

              <div v-if="followTotalPages > 1" class="d-flex justify-center mt-6">
                <v-pagination v-model="followPage" :length="followTotalPages" @update:model-value="fetchFollowList" />
              </div>
                </div>
              </section>

              <section v-else-if="activeTab === 'danmaku'" class="profile-section">
                <div class="text-h5 d-flex align-center profile-section-title">
                  <i class="mdi mdi-comment-text-multiple mr-3" style="color: #9b59b6;"></i>
                  我的弹幕
                </div>
                <div class="profile-section-body">
                  <v-alert v-if="danmakuError" type="error" variant="tonal" class="mb-4">{{ danmakuError }}</v-alert>
                  <v-skeleton-loader v-if="danmakuLoading" type="list-item-avatar-three-line@4" />
                  <div v-else-if="danmakuRecords.length > 0" class="history-list">
                    <v-card v-for="item in danmakuRecords" :key="item.id" class="history-card" elevation="1">
                      <div class="history-main-card">
                        <div class="history-content">
                          <p class="history-subtitle">
                            <v-chip size="x-small" :color="item.mode === 5 ? 'primary' : item.mode === 4 ? 'success' : 'grey'" variant="tonal" class="mr-2">
                              {{ danmakuModeLabel(item.mode) }}
                            </v-chip>
                            {{ item.comment }}
                          </p>
                          <p class="history-meta">
                            番剧: {{ item.animeTitle || `#${item.animeId || '-'}` }}
                            &nbsp;|&nbsp; 剧集: {{ item.episodeTitle || `弹幕库 #${item.episodeId || '-'}` }}
                          </p>
                          <p class="history-meta">
                            时间: {{ item.time != null ? item.time.toFixed(1) + 's' : '-' }}
                            &nbsp;|&nbsp; 发送于: {{ formatDateTime(item.createdAt) }}
                          </p>
                        </div>
                        <div class="history-actions">
                          <v-btn color="primary"
                            :disabled="!item.videoId && !item.animeId"
                            @click="goToDanmakuPlayer(item)">
                            跳转播放
                          </v-btn>
                        </div>
                      </div>
                    </v-card>
                  </div>
                  <v-alert v-else type="info" variant="tonal" class="text-center">暂无弹幕记录</v-alert>
                  <div v-if="danmakuTotalPages > 1" class="d-flex justify-center mt-6">
                    <v-pagination v-model="danmakuPage" :length="danmakuTotalPages" @update:model-value="fetchDanmakuRecords" />
                  </div>
                </div>
              </section>

              <section v-else-if="activeTab === 'messages'" class="profile-section">
                <div class="text-h5 d-flex align-center justify-space-between profile-section-title">
                  <div class="d-flex align-center">
                    <i class="mdi mdi-bell-outline mr-3" style="color: #3498db;"></i>
                    消息中心
                    <v-badge
                      v-if="messageUnreadCount > 0"
                      :content="messageUnreadCount"
                      color="error"
                      class="ml-3"
                    />
                  </div>
                  <v-btn
                    v-if="messageUnreadCount > 0"
                    color="primary"
                    size="small"
                    variant="tonal"
                    @click="markAllMessagesAsRead"
                  >
                    全部已读
                  </v-btn>
                </div>

                <div class="profile-section-body">
                  <v-alert v-if="messagesError" type="error" variant="tonal" class="mb-4">
                    {{ messagesError }}
                  </v-alert>

                  <div class="mb-6 d-flex flex-wrap gap-2">
                    <v-btn
                      v-for="type in messageTypes"
                      :key="type.value"
                      :variant="messageFilterType === type.value ? 'flat' : 'tonal'"
                      :color="messageFilterType === type.value ? type.color || 'grey' : 'grey'"
                      size="small"
                      rounded="pill"
                      @click="messageFilterType = type.value; handleMessageFilterChange()"
                    >
                      {{ type.label }}
                    </v-btn>
                  </div>

                  <v-skeleton-loader
                    v-if="messagesLoading"
                    type="list-item-avatar-three-line@3"
                    class="mb-3"
                  />

                  <div v-else-if="messages.length > 0" class="message-list">
                    <div
                      v-for="message in messages"
                      :key="message.id"
                      class="message-item"
                      :class="{ 'message-unread': !message.isRead }"
                      @click="selectMessage(message)"
                    >
                      <div class="message-item-inner">
                        <div class="message-dot" :class="{ 'dot-unread': !message.isRead }"></div>
                        <div class="message-body">
                          <div class="message-header">
                            <v-chip
                              :color="typeChipColor(message.type)"
                              size="x-small"
                              variant="tonal"
                              class="mr-2 message-type-chip"
                            >
                              {{ typeLabel(message.type) }}
                            </v-chip>
                            <span class="message-title">{{ message.title }}</span>
                          </div>
                          <p class="message-content">{{ message.content }}</p>
                          <div class="message-footer">
                            <v-chip
                              v-if="message.animeTitle"
                              size="x-small"
                              variant="outlined"
                              color="primary"
                              class="message-anime-chip"
                              @click.stop="goToAnime(message.animeId)"
                            >
                              {{ message.animeTitle }}
                            </v-chip>
                            <span class="message-time">{{ formatDateTime(message.createdAt) }}</span>
                            <v-btn
                              icon
                              size="x-small"
                              variant="text"
                              class="message-delete-btn"
                              @click.stop="deleteMessage(message.id)"
                            >
                              <i class="mdi mdi-delete-outline"></i>
                            </v-btn>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <v-alert v-else type="info" variant="tonal" class="text-center">
                    <div class="text-h6 mb-2">暂无消息</div>
                    <div>当有新的番剧更新或系统通知时，会在这里显示</div>
                  </v-alert>

                  <div v-if="messageTotalPages > 1" class="d-flex justify-center mt-6">
                    <v-pagination
                      v-model="messagePage"
                      :length="messageTotalPages"
                      @update:model-value="handleMessagePageChange"
                    />
                  </div>
                </div>
              </section>

              <section v-else class="profile-section">
                <div class="text-h6 d-flex align-center ga-2 profile-section-title">
                  <v-icon color="deep-orange-darken-1">mdi-account-key</v-icon>
                  Bangumi 账号绑定
                </div>
                <div class="profile-section-body">
              <v-progress-linear
                v-if="bangumiLoading || bangumiBinding || bangumiUnbinding"
                indeterminate
                color="deep-orange-darken-1"
                class="mb-4"
              />
              <v-alert type="info" variant="tonal" class="mb-4">
                请前往
                <a href="https://next.bgm.tv/demo/access-token" target="_blank" rel="noopener noreferrer">https://next.bgm.tv/demo/access-token</a>
                登录后获取 Access Token。请注意：该 Token 会保存到服务端，请仅在你信任的环境中进行绑定。
              </v-alert>

              <v-alert :type="bangumiStatusTone" variant="tonal" class="mb-4">
                {{ bangumiStatus.statusMessage || '未绑定 Bangumi 账号' }}
              </v-alert>

              <div v-if="bangumiStatus.bound && bangumiStatus.profile" class="bangumi-user-card mb-4">
                <img v-if="bangumiAvatar" :src="bangumiAvatar" alt="Bangumi Avatar" class="bangumi-avatar" />
                <div>
                  <div class="bangumi-user-name">{{ bangumiStatus.bangumiNickname || bangumiStatus.bangumiUsername }}</div>
                  <div class="bangumi-user-meta">@{{ bangumiStatus.bangumiUsername }} · ID {{ bangumiStatus.bangumiUserId }}</div>
                  <div class="bangumi-user-meta">{{ bangumiStatus.profile?.sign || '这个用户还没有签名。' }}</div>
                </div>
              </div>

              <v-text-field
                v-model="bangumiTokenInput"
                label="Bangumi Access Token"
                variant="outlined"
                hide-details="auto"
                placeholder="粘贴从 next.bgm.tv 获取的 Access Token"
              />

              <div class="integration-actions mt-4">
                <v-btn color="deep-orange-darken-1" :loading="bangumiBinding" :disabled="bangumiBinding" @click="bindBangumiToken">
                  绑定或更新 Token
                </v-btn>
                <v-btn
                  variant="outlined"
                  color="grey-darken-1"
                  :disabled="!bangumiStatus.bound || bangumiUnbinding"
                  :loading="bangumiUnbinding"
                  @click="unbindBangumiToken"
                >
                  解除绑定
                </v-btn>
                <v-btn variant="text" color="primary" :disabled="bangumiLoading" @click="fetchBangumiStatus">
                  刷新状态
                </v-btn>
              </div>
                </div>
              </section>
            </section>
          </div>
        </v-container>
      </v-card-text>
    </v-card>

    <v-dialog v-model="selectedMessage" max-width="600">
      <v-card v-if="selectedMessage">
        <v-card-title class="profile-section-title">
          <div class="d-flex align-center">
            <v-chip
              :color="typeChipColor(selectedMessage.type)"
              size="small"
              variant="tonal"
              class="mr-3"
            >
              {{ typeLabel(selectedMessage.type) }}
            </v-chip>
            {{ selectedMessage.title }}
          </div>
        </v-card-title>

        <v-card-text class="pa-6">
          <p class="message-detail-content mb-4">
            {{ selectedMessage.content }}
          </p>

          <v-divider class="mb-4" />

          <div v-if="selectedMessage.animeTitle" class="mb-4">
            <div class="text-caption text-grey mb-2">相关番剧</div>
            <v-chip
              color="primary"
              variant="tonal"
              @click="goToAnime(selectedMessage.animeId); selectedMessage = null"
            >
              {{ selectedMessage.animeTitle }}
            </v-chip>
          </div>

          <div class="text-caption text-grey">
            <div class="mb-1">发送时间: {{ formatDateTime(selectedMessage.createdAt) }}</div>
            <div v-if="selectedMessage.isRead">阅读时间: {{ formatDateTime(selectedMessage.readAt) }}</div>
          </div>
        </v-card-text>

        <v-card-actions>
          <v-spacer />
          <v-btn
            color="error"
            variant="text"
            @click="deleteMessage(selectedMessage.id)"
          >
            删除
          </v-btn>
          <v-btn
            color="primary"
            variant="text"
            @click="selectedMessage = null"
          >
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 通用确认对话框 -->
    <v-dialog v-model="confirmDialog.show" max-width="420" persistent>
      <v-card>
        <v-card-title class="text-h6">{{ confirmDialog.title }}</v-card-title>
        <v-card-text class="pt-2" style="white-space: pre-line;">{{ confirmDialog.message }}</v-card-text>
        <v-card-actions class="d-flex justify-end gap-2 pa-4">
          <v-btn variant="outlined" @click="confirmDialog.resolve(false); confirmDialog.show = false">取消</v-btn>
          <v-btn color="primary" @click="confirmDialog.resolve(true); confirmDialog.show = false">确认</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.profile-page { padding: 0; min-height: calc(100vh - 140px); overflow-x: clip; }
.profile-page :deep(.v-card-text) { overflow-x: clip; }
.profile-page-card { border-radius: 16px; min-height: calc(100vh - 140px); display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
.profile-page-card :deep(.v-card-text) { min-width: 0; overflow: hidden; }
.profile-container { min-width: 0; }
.profile-container { padding: 0; }
.profile-shell { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 20px; align-items: start; }
.profile-sidebar { display: grid; gap: 10px; position: sticky; top: 88px; }
.profile-tab-btn { display: flex; align-items: center; gap: 10px; width: 100%; border: 1px solid #e5d8cc; background: #fff; border-radius: 14px; padding: 14px 16px; cursor: pointer; color: #5f5148; font-weight: 600; transition: 0.2s ease; }
.profile-tab-btn:hover, .profile-tab-btn.active { border-color: #c45d2b; color: #c45d2b; background: #fff8f3; }
.profile-section-title { background: transparent; border-bottom: 1px solid #e7ddd3; color: #2f2b28; padding: 0 0 14px; }
.profile-section-body { padding: 16px 0 2px; }
.history-list { display: grid; gap: 12px; }
.history-card { border-radius: 12px; }
.history-main-card { display: flex; gap: 16px; justify-content: space-between; align-items: center; padding: 16px; }
.history-title { margin: 0 0 6px; font-size: 1.05rem; color: #2f2b28; }
.history-subtitle, .history-meta { margin: 0; color: #666; font-size: 0.92rem; line-height: 1.5; }
.history-actions { display: flex; gap: 8px; flex-shrink: 0; }
.follow-list { display: grid; gap: 10px; }
.follow-row { padding: 14px; display: flex; justify-content: space-between; gap: 14px; }
.follow-title { margin: 0 0 8px; color: #2f2b28; cursor: pointer; }
.follow-meta { color: #6b5f55; font-size: 0.86rem; }

/* ===== 追番卡片列表 ===== */
.follow-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.follow-cover-card {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  overflow: hidden;
  border-radius: 12px;
}
.follow-cover-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.14) !important;
}

.follow-cover-inner {
  display: flex;
  flex-direction: column;
}

.follow-cover-poster {
  position: relative;
  aspect-ratio: 2/3;
  overflow: hidden;
  background: #f0ebe4;
}

.follow-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.follow-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8e2da, #d5cec4);
  color: #a39386;
  font-size: 2.5rem;
}

.follow-cover-tag {
  position: absolute;
  top: 8px;
  left: 8px;
  font-weight: 600;
  letter-spacing: 0.02em;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
}

.follow-cover-info {
  padding: 12px 14px 14px;
}

.follow-cover-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #2e241e;
  line-height: 1.3;
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-cover-date {
  font-size: 0.75rem;
  color: #a39386;
  margin-bottom: 10px;
}

/* ===== 级联状态按钮组 ===== */
.status-cascade {
  display: flex;
  gap: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e0d8cf;
}

.status-cascade-btn {
  flex: 1;
  border: none;
  background: #faf7f3;
  padding: 6px 0;
  font-size: 0.7rem;
  color: #8b7e74;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, font-weight 0.15s;
  border-right: 1px solid #e0d8cf;
  white-space: nowrap;
  text-align: center;
}

.status-cascade-btn:last-child {
  border-right: none;
}

.status-cascade-btn:hover {
  background: #f0e8dc;
  color: #5f5148;
}

.status-cascade-btn.active {
  background: var(--cascade-color, #ff9800);
  color: #fff;
  font-weight: 700;
}

.status-cascade-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 搜索行 ===== */
.follow-search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  align-items: center;
}
.follow-search-row > .v-text-field { flex: 1; min-width: 0; }
.follow-search-row > .v-select { flex: 0 0 110px; }
.follow-search-row > .v-btn { flex-shrink: 0; white-space: nowrap; }

/* ===== 移动端标记按钮（默认隐藏） ===== */
.status-cascade-mobile { display: none; }

.mobile-mark-btn {
  width: 100%;
  border: 1px solid #e0d8cf;
  border-radius: 8px;
  background: #faf7f3;
  padding: 7px 10px;
  font-size: 0.78rem;
  color: #6b5f55;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.mobile-mark-btn:hover { background: #f0e8dc; }

.mobile-status-popup {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 50;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 -4px 20px rgba(0,0,0,0.18);
  padding: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mobile-status-item {
  flex: 1 1 calc(50% - 4px);
  min-width: calc(50% - 4px);
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: #faf7f3;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 0.82rem;
  color: #5f5148;
  cursor: pointer;
  text-align: left;
}
.mobile-status-item:hover { background: #f0e8dc; }
.mobile-status-item.active { background: #fef9f5; color: #c45d2b; font-weight: 600; }

.mobile-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.mobile-status-item .mdi-check {
  margin-left: auto;
  color: #c45d2b;
  font-size: 0.9rem;
}
.follow-actions { display: flex; gap: 8px; align-items: flex-start; }
.profile-section-body { min-width: 0; overflow: clip; }

/* ===== 消息列表（纯div，不再依赖 v-card） ===== */
.message-list { display: flex; flex-direction: column; gap: 10px; min-width: 0; overflow: clip; }

.message-item {
  border: 1px solid #e7ddd3;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s ease;
  min-width: 0;
  overflow: hidden;
  contain: layout style;
  width: 100%;
}
.message-item:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.message-item.message-unread { border-left: 4px solid #4b7bec; background: #f7fbff; }

.message-item-inner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.message-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d0d0d0;
  margin-top: 6px;
}
.dot-unread { background: #4b7bec; }

.message-body {
  flex: 1 1 0%;
  min-width: 0;
  overflow: clip;
  contain: layout style;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  min-width: 0;
}

.message-type-chip { flex-shrink: 0; }

.message-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #2f2b28;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.message-content {
  font-size: 0.88rem;
  line-height: 1.55;
  color: #555;
  margin: 0 0 8px;
  max-height: calc(2 * 1.55em);
  overflow: hidden;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.message-anime-chip {
  flex-shrink: 1;
  min-width: 0;
  max-width: 200px;
}
.message-anime-chip :deep(.v-chip__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.message-time {
  font-size: 0.72rem;
  color: #999;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.message-delete-btn { flex-shrink: 0; margin-left: auto; }
.message-detail-content {
  font-size: 0.95rem;
  line-height: 1.8;
  color: #555;
  white-space: pre-wrap;
}
.integration-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.bangumi-user-card { display: flex; gap: 14px; align-items: center; padding: 14px; background: #fff8f3; border: 1px solid #f2d7c6; border-radius: 14px; }
.bangumi-avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; background: #f1e5db; }
.bangumi-user-name { font-size: 1rem; font-weight: 700; color: #2f2b28; }
.bangumi-user-meta { color: #6b5f55; font-size: 0.88rem; line-height: 1.5; }
@media (max-width: 960px) {
  .profile-shell { grid-template-columns: minmax(0, 1fr); min-width: 0; }
  .profile-sidebar { position: static; display: flex; flex-wrap: wrap; gap: 8px; }
  .profile-sidebar .profile-tab-btn { flex: 1 1 auto; min-width: 0; white-space: nowrap; }
}
@media (max-width: 500px) {
  .profile-sidebar .profile-tab-btn { font-size: 0.82rem; padding: 10px 12px; }
}
@media (max-width: 760px) {
  .history-main-card { flex-direction: column; align-items: stretch; }
  .follow-cards { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
  .history-actions, .follow-actions, .profile-sidebar, .integration-actions { width: 100%; }
  .history-actions :deep(.v-btn), .follow-actions :deep(.v-btn), .integration-actions :deep(.v-btn) { flex: 1; }

  /* 搜索行移动端换行 */
  .follow-search-row {
    flex-wrap: wrap;
    gap: 6px;
  }
  .follow-search-row > .v-text-field { flex: 1 1 100%; }
  .follow-search-row > .v-select { flex: 0 0 100px; min-width: 100px; }
  .follow-search-row > .v-select :deep(.v-field__input) { min-width: 80px; }
  .follow-search-row > .v-btn { flex: 1; min-width: 0; font-size: 0.82rem; }

  /* 级联按钮组：桌面端隐藏，移动端显示弹出式 */
  .status-cascade-desktop { display: none; }
  .status-cascade-mobile { display: block; position: relative; }
}

@media (max-width: 600px) {
  .profile-page,
  .profile-page-card {
    min-height: calc(100vh - 110px);
  }
}
</style>

<style>
/* 消息卡片宽度收敛 — 非 scoped，contain 创建隔离边界 */
.profile-container,
.profile-container .v-container,
.message-list,
.message-item,
.message-item-inner,
.message-body { min-width: 0 !important; max-width: 100% !important; overflow: clip !important; contain: layout style !important; }
</style>
