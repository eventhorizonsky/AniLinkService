<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const followList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const filterStatus = ref('watching')
const searchKeyword = ref('')
const error = ref('')

const statuses = [
  { label: '在看', value: 'watching', color: 'primary' },
  { label: '看过', value: 'watched', color: 'success' },
  { label: '搁置', value: 'on_hold', color: 'warning' },
  { label: '抛弃', value: 'dropped', color: 'error' }
]

const statusLabel = (status) => {
  return statuses.find(s => s.value === status)?.label || status
}

const statusChipColor = (status) => {
  return statuses.find(s => s.value === status)?.color || 'grey'
}

const fetchFollowList = async () => {
  loading.value = true
  error.value = ''
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    
    let url = '/api/follows'
    if (filterStatus.value) {
      url = `/api/follows/status/${filterStatus.value}`
    }
    
    const response = await axios.get(url, { params })
    if (response.data.code === 200) {
      if (Array.isArray(response.data.data)) {
        // 不分页的响应
        followList.value = response.data.data.filter(item => {
          return !searchKeyword.value || item.animeTitle.toLowerCase().includes(searchKeyword.value.toLowerCase())
        })
        totalElements.value = followList.value.length
      } else {
        // 分页响应
        followList.value = response.data.data.content || []
        totalElements.value = response.data.data.totalElements || 0
      }
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

const handleUnfollow = async (animeId, animeTitle) => {
  if (!confirm(`确定要取消追番《${animeTitle}》吗？`)) {
    return
  }
  
  try {
    const response = await axios.delete(`/api/follows/${animeId}`)
    if (response.data.code === 200) {
      await fetchFollowList()
    } else {
      alert(response.data.msg || '取消追番失败')
    }
  } catch (err) {
    console.error('Failed to unfollow anime:', err)
    alert('取消追番失败')
  }
}

const updateStatus = async (animeId, newStatus) => {
  try {
    const response = await axios.put(`/api/follows/${animeId}/status`, null, {
      params: { status: newStatus }
    })
    if (response.data.code === 200) {
      await fetchFollowList()
    } else {
      alert(response.data.msg || '更新状态失败')
    }
  } catch (err) {
    console.error('Failed to update follow status:', err)
    alert('更新状态失败')
  }
}

const goToAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}

const goToHome = () => {
  router.push('/')
}

const handleFilterChange = () => {
  currentPage.value = 1
  fetchFollowList()
}

const handleSearch = () => {
  currentPage.value = 1
  fetchFollowList()
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchFollowList()
}

const totalPages = computed(() => {
  return Math.ceil(totalElements.value / pageSize.value)
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

onMounted(() => {
  fetchFollowList()
})
</script>

<template>
  <div class="follow-page">
    <v-container>
      <v-card class="elevation-2">
        <v-card-title class="text-h5 bg-grey-lighten-4 d-flex align-center">
          <i class="fa-solid fa-heart mr-3" style="color: #e74c3c;"></i>
          我的追番
        </v-card-title>

        <v-card-text class="pa-6">
          <!-- 错误提示 -->
          <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
            {{ error }}
          </v-alert>

          <!-- 筛选和搜索 -->
          <div class="mb-6">
            <div class="d-flex flex-wrap gap-2 mb-4">
              <v-btn
                v-for="status in statuses"
                :key="status.value"
                :variant="filterStatus === status.value ? 'flat' : 'tonal'"
                :color="filterStatus === status.value ? status.color : 'grey'"
                size="small"
                rounded="pill"
                @click="filterStatus = status.value; handleFilterChange()"
              >
                {{ status.label }}
              </v-btn>
            </div>

            <div class="d-flex gap-2">
              <v-text-field
                v-model="searchKeyword"
                placeholder="搜索番剧标题..."
                density="compact"
                variant="outlined"
                hide-details
                @keyup.enter="handleSearch"
              >
                <template #prepend-inner>
                  <i class="fa-solid fa-search"></i>
                </template>
              </v-text-field>
              <v-btn
                color="primary"
                @click="handleSearch"
              >
                搜索
              </v-btn>
            </div>
          </div>

          <!-- 加载状态 -->
          <v-skeleton-loader
            v-if="loading"
            type="list-item-avatar-three-line@3"
            class="mb-3"
          />

          <!-- 追番列表 -->
          <div v-else-if="followList.length > 0">
            <v-card
              v-for="follow in followList"
              :key="follow.id"
              class="mb-4 follow-card"
              elevation="1"
              hover
            >
              <div class="follow-card-content">
                <!-- 封面 -->
                <div 
                  class="poster-container"
                  @click="goToAnime(follow.animeId)"
                  style="cursor: pointer;"
                >
                  <v-img
                    v-if="follow.imageUrl"
                    :src="follow.imageUrl"
                    :alt="follow.animeTitle"
                    class="poster-image"
                    cover
                  >
                    <template #placeholder>
                      <v-skeleton-loader type="image" />
                    </template>
                  </v-img>
                  <div v-else class="no-poster">
                    <i class="fa-solid fa-image"></i>
                  </div>
                </div>

                <!-- 信息 -->
                <div class="follow-info">
                  <div>
                    <h3 
                      class="anime-title mb-2"
                      @click="goToAnime(follow.animeId)"
                      style="cursor: pointer;"
                    >
                      {{ follow.animeTitle }}
                    </h3>
                    
                    <div class="mb-3">
                      <v-chip
                        :color="statusChipColor(follow.status)"
                        size="small"
                        variant="tonal"
                        class="mr-2"
                      >
                        {{ statusLabel(follow.status) }}
                      </v-chip>
                      <v-chip
                        v-if="follow.tags"
                        size="small"
                        variant="outlined"
                      >
                        {{ follow.tags }}
                      </v-chip>
                    </div>

                    <div class="text-caption text-grey">
                      <div>追番时间: {{ formatDate(follow.followAt) }}</div>
                      <div>更新时间: {{ formatDate(follow.updatedAt) }}</div>
                    </div>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="follow-actions">
                  <v-btn
                    color="primary"
                    size="small"
                    @click="goToAnime(follow.animeId)"
                  >
                    <i class="fa-solid fa-play mr-2"></i>
                    查看详情
                  </v-btn>

                  <v-select
                    :model-value="follow.status"
                    :items="statuses"
                    item-title="label"
                    item-value="value"
                    density="compact"
                    variant="outlined"
                    hide-details
                    class="status-select"
                    @update:model-value="updateStatus(follow.animeId, $event)"
                  />

                  <v-btn
                    color="error"
                    size="small"
                    variant="outlined"
                    @click="handleUnfollow(follow.animeId, follow.animeTitle)"
                  >
                    <i class="fa-solid fa-heart-crack mr-2"></i>
                    取消追番
                  </v-btn>
                </div>
              </div>
            </v-card>
          </div>

          <!-- 空状态 -->
          <v-alert v-else type="info" variant="tonal" class="text-center">
            <div class="text-h6 mb-2">还没有追番哦</div>
            <div>
              快去
              <a @click="goToHome" class="text-primary" style="cursor: pointer; text-decoration: underline;">
                首页
              </a>
              发现喜欢的番剧吧！
            </div>
          </v-alert>

          <!-- 分页 -->
          <div v-if="!filterStatus && totalPages > 1" class="d-flex justify-center mt-6">
            <v-pagination
              v-model="currentPage"
              :length="totalPages"
              @update:model-value="handlePageChange"
            />
          </div>
        </v-card-text>
      </v-card>
    </v-container>
  </div>
</template>

<style scoped>
.follow-page {
  padding: 24px 0;
}

.follow-card-content {
  display: flex;
  gap: 20px;
  padding: 16px;
  align-items: flex-start;
}

.poster-container {
  position: relative;
  flex-shrink: 0;
  width: 100px;
  height: 142px;
  transition: transform 0.3s ease;
  border-radius: 8px;
  overflow: hidden;
}

.poster-container:hover {
  transform: scale(1.02);
}

.poster-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-poster {
  width: 100px;
  height: 142px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 32px;
  border-radius: 8px;
}

.follow-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.follow-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
  width: 140px;
}

.status-select {
  min-height: auto !important;
}

.status-select :deep(.v-field) {
  min-height: 36px !important;
}

.status-select :deep(.v-field__input) {
  min-height: 36px !important;
  padding-top: 4px !important;
  padding-bottom: 4px !important;
}

.anime-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #2c3e50;
  transition: color 0.2s ease;
  line-height: 1.4;
}

.anime-title:hover {
  color: #4b7bec;
}

.follow-card {
  transition: all 0.3s ease;
}

.follow-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}

.gap-2 {
  gap: 8px;
}

@media (max-width: 768px) {
  .follow-card-content {
    flex-direction: column;
  }
  
  .follow-actions {
    width: 100%;
  }
}
</style>

