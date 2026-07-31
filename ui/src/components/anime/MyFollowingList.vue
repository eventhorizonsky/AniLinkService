<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const followList = ref([])
const loading = ref(false)
const error = ref('')

const STATUS_LABEL_MAP = { watching: '在看', watched: '看过', on_hold: '搁置', dropped: '抛弃' }
const STATUS_COLOR_MAP = { watching: 'primary', watched: 'success', on_hold: 'warning', dropped: 'error' }
const statusLabel = (s) => STATUS_LABEL_MAP[s] || s
const statusColor = (s) => STATUS_COLOR_MAP[s] || 'grey'

const emit = defineEmits(['select-anime'])
const props = defineProps({
  isLoggedIn: {
    type: Boolean,
    default: false
  }
})

const fetchFollowList = async () => {
  if (!props.isLoggedIn) {
    return
  }
  
  loading.value = true
  error.value = ''
  try {
    const response = await axios.get('/api/follows/status/watching', {
      params: {
        page: 1,
        pageSize: 12
      }
    })
    if (response.data.code === 200) {
      const list = Array.isArray(response.data.data) ? response.data.data : (response.data.data.content || [])
      // 首页只展示已绑定本地番剧的追番，animeId 为空（未匹配）的不展示
      followList.value = list.filter((item) => item.animeId).slice(0, 12)
    } else {
      error.value = response.data.msg || '加载失败'
    }
  } catch (err) {
    console.error('Failed to fetch follow list:', err)
    error.value = '加载追番列表失败'
  } finally {
    loading.value = false
  }
}

const goToAnime = (animeId) => {
  if (!animeId) {
    goToFollowList()
    return
  }
  emit('select-anime', animeId)
}

const goToFollowList = () => {
  router.push({ path: '/profile', query: { tab: 'follows' } })
}

const goToSearch = () => {
  router.push('/search')
}

onMounted(() => {
  if (props.isLoggedIn) {
    fetchFollowList()
  }
})
</script>

<template>
  <v-card class="elevation-2 mb-6 unified-panel">
    <v-card-title class="text-h5 d-flex align-center justify-space-between unified-panel-title">
      <div class="d-flex align-center">
        <i class="mdi mdi-bookmark-multiple mr-3" style="color: #e74c3c;"></i>
        我的追番
      </div>
      <v-btn
        v-if="isLoggedIn && followList.length > 0"
        color="primary"
        size="small"
        variant="tonal"
        @click="goToFollowList"
      >
        查看全部
      </v-btn>
    </v-card-title>

    <v-card-text class="pa-6">
      <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
        {{ error }}
      </v-alert>

      <v-alert v-if="!isLoggedIn" type="info" variant="tonal" class="text-center">
        登录后可查看你的追番列表
      </v-alert>

      <v-skeleton-loader
        v-else-if="loading"
        type="image, article"
        class="mb-3"
      />

      <div v-else-if="followList.length > 0" class="schedule-grid">
        <v-card
          v-for="anime in followList"
          :key="anime.id"
          class="schedule-card"
          elevation="1"
          @click="goToAnime(anime.animeId)"
        >
          <div class="poster-wrap">
            <v-img
              :src="anime.imageUrl || 'https://assets.anixplayer.net/image/poster/default.jpg'"
              :aspect-ratio="3 / 4"
              class="poster-image"
            />
          </div>
          <v-card-text class="pa-3">
            <div class="title-row">
              <p class="anime-title">{{ anime.animeTitle }}</p>
              <v-chip
                v-if="anime.status"
                size="x-small"
                variant="tonal"
                :color="statusColor(anime.status)"
                class="status-chip"
              >
                {{ statusLabel(anime.status) }}
              </v-chip>
            </div>
            <div class="meta-row">
              <span class="follow-meta">继续观看</span>
              <span class="follow-link">查看详情</span>
            </div>
          </v-card-text>
        </v-card>
      </div>

      <v-alert v-else type="info" variant="tonal" class="text-center">
        <div class="mb-2">还没有追番，快去发现喜欢的番剧吧！</div>
        <v-btn color="primary" size="small" @click="goToSearch">探索番剧</v-btn>
      </v-alert>
    </v-card-text>
  </v-card>
</template>

<style scoped>

.schedule-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.schedule-card {
  height: 100%;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  display: flex;
  flex-direction: column;
}

.schedule-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.16) !important;
}

.poster-wrap {
  background: linear-gradient(180deg, #f3f5f9 0%, #eceff4 100%);
}

.poster-image {
  width: 100%;
}

.poster-image :deep(.v-img__img) {
  object-fit: cover;
  object-position: center top;
}

.poster-image :deep(.v-responsive__content) {
  background: transparent;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.anime-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2e241e;
  margin: 0;
  line-height: 1.35;
  min-height: calc(2 * 1.35em);
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.status-chip {
  flex-shrink: 0;
  white-space: nowrap;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6b5f55;
  font-size: 12px;
}

.follow-meta {
  color: #6b5f55;
}

.follow-link {
  color: #4b7bec;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .schedule-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}

@media (max-width: 768px) {
  .anime-title {
    font-size: 13px;
    line-height: 1.3;
    min-height: calc(2 * 1.3em);
  }
}

@media (max-width: 600px) {
  .schedule-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }
}
</style>
