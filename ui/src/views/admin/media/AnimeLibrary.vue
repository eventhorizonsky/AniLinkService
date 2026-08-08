<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { formatAnimeType } from '../../../utils/animeType'
import MediaRematchDialog from '../../../components/admin/media/MediaRematchDialog.vue'
import SubtitleManager from '../../../components/admin/media/SubtitleManager.vue'

const API_BASE = '/api'

const animes = ref([])
const loading = ref(false)
const selectedAnime = ref(null)
const episodes = ref([])
const episodesLoading = ref(false)
const dialogOpen = ref(false)
const rematchDialog = ref(false)
const rematchTargetFile = ref(null)
const subtitleDialog = ref(false)
const selectedEpisodeForSubtitle = ref(null)

// episodes pagination state for server-side paging
const episodesPagination = ref({
  page: 1,
  itemsPerPage: 10,
  pageCount: 0
})

const search = ref('')
const sortBy = ref([])
const pagination = ref({
  page: 1,
  itemsPerPage: 10,
  pageCount: 1
})

const episodeHeaders = [
  { title: '文件名', key: 'fileName' },
  { title: '剧集名称', key: 'episodeTitle' },
  { title: '匹配状态', key: 'matchStatus', sortable: false },
  { title: '分辨率', key: 'resolution', sortable: false },
  { title: '时长', key: 'durationStr', sortable: false },
  { title: '文件大小', key: 'sizeStr', sortable: false },
  { title: '编码', key: 'videoFormat', sortable: false },
  { title: '操作', key: 'actions', sortable: false }
]

// 获取所有动漫
const fetchAnimes = async (pageNum = 1) => {
  loading.value = true
  try {
    const params = {
      page: pageNum,
      pageSize: pagination.value.itemsPerPage
    }
    
    // 如果有搜索关键词，添加到参数
    if (search.value.trim()) {
      params.keyword = search.value.trim()
    }
    
    const res = await axios.get(`${API_BASE}/animes`, { params })
    if (res.data?.code === 200 && res.data.data) {
      animes.value = res.data.data.content || []
      pagination.value.pageCount = res.data.data.totalElements || 0
      pagination.value.page = pageNum
      console.log('获取动漫列表成功:', pagination.value)
    }
  } catch (error) {
    console.error('获取动漫列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取动漫的剧集（服务端分页）
const fetchEpisodes = async (animeId, page = episodesPagination.value.page) => {
  episodesLoading.value = true
  try {
    const params = {
      page,
      pageSize: episodesPagination.value.itemsPerPage
    }
    const res = await axios.get(`${API_BASE}/animes/${animeId}/episodes`, { params })
    if (res.data?.code === 200 && res.data.data) {
      const data = res.data.data
      episodes.value = (data.content || []).map(ep => ({
        ...ep,
        resolution: ep.width && ep.height ? `${ep.width}x${ep.height}` : '未知',
        durationStr: formatDuration(ep.duration),
        sizeStr: formatFileSize(ep.size),
        videoFormat: formatVideoCodec(ep.videoCodec, ep.audioCodec)
      }))
      episodesPagination.value.pageCount = data.totalElements || 0
      episodesPagination.value.page = data.currentPage || page
    }
  } catch (error) {
    console.error('获取剧集列表失败:', error)
    episodes.value = []
  } finally {
    episodesLoading.value = false
  }
}

// 选择动漫并获取其剧集
const selectAnime = async (anime) => {
  selectedAnime.value = anime
  // reset pagination
  episodesPagination.value.page = 1
  dialogOpen.value = true
  await fetchEpisodes(anime.animeId, 1)
}

// 关闭详情弹窗
const closeDetails = () => {
  dialogOpen.value = false
}

// 格式化时长
const formatDuration = (ms) => {
  if (!ms) return '未知'
  const seconds = Math.floor(ms / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${minutes}:${String(secs).padStart(2, '0')}`
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

// 格式化视频编码信息
const formatVideoCodec = (video, audio) => {
  const parts = []
  if (video) parts.push(video.toUpperCase())
  if (audio) parts.push(audio.toUpperCase())
  return parts.length > 0 ? parts.join('/') : '未知'
}

// 搜索时重新加载列表
const onSearch = () => {
  pagination.value.page = 1
  fetchAnimes(1)
}

// 重置搜索
const resetSearch = () => {
  search.value = ''
  pagination.value.page = 1
  fetchAnimes(1)
}

// 表格分页/排序/过滤变化（动漫列表）
const onTableOptionsChange = (options) => {
  const page = options.page || 1
  const pageSize = options.itemsPerPage || 10
  
  // 如果每页大小改变，重置到第一页
  if (pageSize !== pagination.value.itemsPerPage) {
    pagination.value.itemsPerPage = pageSize
    pagination.value.page = 1
    fetchAnimes(1)
  } else {
    // 只改变页码
    pagination.value.page = page
    pagination.value.itemsPerPage = pageSize
    fetchAnimes(page)
  }
}

onMounted(() => {
  pagination.value.page = 1
  fetchAnimes(1)
})

// 监听剧集表格分页变化
const onEpisodesOptionsChange = (options) => {
  const page = options.page || 1
  const pageSize = options.itemsPerPage || episodesPagination.value.itemsPerPage

  if (pageSize !== episodesPagination.value.itemsPerPage) {
    episodesPagination.value.itemsPerPage = pageSize
    episodesPagination.value.page = 1
    if (selectedAnime.value) {
      fetchEpisodes(selectedAnime.value.animeId, 1)
    }
  } else {
    episodesPagination.value.page = page
    episodesPagination.value.itemsPerPage = pageSize
    if (selectedAnime.value) {
      fetchEpisodes(selectedAnime.value.animeId, page)
    }
  }
}

const getMatchStatusMeta = (status) => {
  if (status === 'MATCHED') {
    return { color: 'success', text: '已匹配' }
  }
  if (status === 'NO_MATCH_FOUND') {
    return { color: 'warning', text: '无匹配' }
  }
  return { color: 'grey', text: '未匹配' }
}

const openRematchDialog = (episode) => {
  rematchTargetFile.value = episode
  rematchDialog.value = true
}

const closeRematchDialog = () => {
  rematchDialog.value = false
  rematchTargetFile.value = null
}

const handleRematchApplied = async () => {
  if (selectedAnime.value) {
    await fetchEpisodes(selectedAnime.value.animeId, episodesPagination.value.page)
  }
}

const openSubtitleManager = (episode) => {
  selectedEpisodeForSubtitle.value = episode
  subtitleDialog.value = true
}

const closeSubtitleDialog = () => {
  subtitleDialog.value = false
  selectedEpisodeForSubtitle.value = null
}

</script>

<template>
  <div>
    <v-card elevation="2" class="mb-6">
      <v-card-title class="text-h6">
        <v-icon start>mdi-anime-box</v-icon>
        动漫库管理
      </v-card-title>

      <v-divider></v-divider>

      <v-card-text class="py-4">
        <v-row dense class="align-center mb-4">
          <v-col cols="12" md="8">
            <v-text-field
              v-model="search"
              placeholder="搜索动漫标题..."
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              density="compact"
              clearable
              hide-details
              @keyup.enter="onSearch"
            ></v-text-field>
          </v-col>
          <v-col cols="12" md="4" class="d-flex ga-2 justify-md-end">
            <v-btn
              color="primary"
              variant="elevated"
              size="small"
              :loading="loading"
              @click="onSearch"
            >
              <v-icon start>mdi-magnify</v-icon>
              搜索
            </v-btn>
            <v-btn
              color="grey"
              variant="text"
              size="small"
              @click="resetSearch"
            >
              重置
            </v-btn>
          </v-col>
        </v-row>

        <!-- 动漫列表（封面卡片，PC/移动端统一） -->
        <div v-if="loading" class="d-flex justify-center py-8">
          <v-progress-circular indeterminate color="primary" />
        </div>

        <template v-else-if="animes.length > 0">
          <div class="anime-card-grid">
            <v-card
              v-for="item in animes"
              :key="item.animeId || item.id"
              class="anime-card"
              variant="outlined"
              @click="selectAnime(item)"
            >
              <v-img
                v-if="item.imageUrl"
                :src="item.imageUrl"
                aspect-ratio="3/4"
                cover
                class="anime-cover"
              />
              <div v-else class="anime-cover-noimg">
                <v-icon size="40">mdi-image-off</v-icon>
              </div>
              <div class="anime-card-body">
                <div class="anime-card-title">{{ item.title }}</div>
                <v-chip size="x-small" variant="tonal" color="primary">{{ item.type || '未知类型' }}</v-chip>
              </div>
            </v-card>
          </div>

          <div
            v-if="pagination.pageCount > pagination.itemsPerPage"
            class="d-flex justify-center mt-4"
          >
            <v-pagination
              v-model="pagination.page"
              :length="Math.max(1, Math.ceil(pagination.pageCount / pagination.itemsPerPage))"
              @update:model-value="(p) => onTableOptionsChange({ page: p, itemsPerPage: pagination.itemsPerPage })"
            />
          </div>
        </template>

        <div v-else class="text-center py-8 text-grey">
          <v-icon size="48" class="mb-2">mdi-anime-box-outline</v-icon>
          <div>暂无动漫数据</div>
        </div>
      </v-card-text>
    </v-card>

    <!-- 剧集详情弹窗 -->
    <v-dialog v-model="dialogOpen" max-width="1200px" scrollable>
      <v-card v-if="selectedAnime" elevation="2">
        <v-card-actions class="pa-0 justify-end">
          <v-btn
            icon="mdi-close"
            variant="text"
            size="large"
            @click="closeDetails"
          ></v-btn>
        </v-card-actions>

        <v-divider></v-divider>

        <v-card-text class="py-4">
          <!-- 上方：卡片展示 (封面 + 标题 + 信息卡 + 简介 + 标签) -->
          <div class="mb-6">
            <div class="d-flex gap-8">
              <!-- 左侧：封面图片 -->
              <div class="flex-shrink-0 pr-6" style="width: 180px">
                <div class="text-center">
                  <v-img
                    v-if="selectedAnime.imageUrl"
                    :src="selectedAnime.imageUrl"
                    class="rounded"
                    aspect-ratio="3/4"
                  ></v-img>
                  <div v-else class="border-2 border-dashed p-6 rounded text-gray-500 flex items-center justify-center" style="aspect-ratio: 3/4;">
                    <div>
                      <v-icon size="40" class="mb-2">mdi-image-outline</v-icon>
                      <div class="text-caption">暂无图片</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 右侧：标题、信息、简介、标签 -->
              <div class="flex-grow-1">
                <!-- 标题和基本芯片 -->
                <div class="mb-3">
                  <h3 class="mb-2">{{ selectedAnime.title }}</h3>
                  <div class="d-flex flex-wrap gap-2 mb-3">
                    <v-chip v-if="selectedAnime.type" size="small" color="primary" variant="tonal">
                      {{ formatAnimeType(selectedAnime.type) }}
                    </v-chip>
                    <v-chip v-if="selectedAnime.year" size="small" variant="outlined">
                      {{ selectedAnime.year }}
                    </v-chip>
                    <v-chip v-if="selectedAnime.rating" size="small" color="warning" variant="tonal">
                      ★ {{ selectedAnime.rating.toFixed(1) }}
                    </v-chip>
                  </div>
                </div>

                <!-- 信息网格 (2列) -->
                <div class="grid grid-cols-2 gap-3 py-2 mb-3">
                  <div class="flex items-center gap-1">
                    <span class="font-medium text-sm text-gray-700 min-w-[60px]">总集数：</span>
                    <span class="text-sm text-gray-900">{{ selectedAnime.episodes || '-' }}</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="font-medium text-sm text-gray-700 min-w-[60px]">本地：</span>
                    <span class="text-sm text-gray-900">{{ episodesPagination.pageCount }}</span>
                  </div>
                  <div class="flex items-center gap-1" v-if="selectedAnime.duration">
                    <span class="font-medium text-sm text-gray-700 min-w-[60px]">片长：</span>
                    <span class="text-sm text-gray-900">{{ selectedAnime.duration }}</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <span class="font-medium text-sm text-gray-700 min-w-[60px]">ID：</span>
                    <span class="text-sm text-gray-900">{{ selectedAnime.animeId }}</span>
                  </div>
                </div>

                <!-- 简介 -->
                <div v-if="selectedAnime.summary" class="mb-3">
                  <div class="text-xs font-bold mb-1">简介</div>
                  <div class="max-h-[150px] overflow-y-auto p-2 bg-gray-50 rounded text-xs leading-relaxed">
                    {{ selectedAnime.summary }}
                  </div>
                </div>

                <!-- 标签 -->
                <div v-if="selectedAnime.tags" class="mb-2">
                  <div class="text-xs font-bold mb-1">标签</div>
                  <div class="flex flex-wrap gap-1">
                    <v-chip
                      v-for="tag in selectedAnime.tags.split(',')"
                      :key="tag"
                      size="x-small"
                      variant="outlined"
                    >
                      {{ tag.trim() }}
                    </v-chip>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 分割线 -->
          <v-divider class="my-4"></v-divider>

          <!-- 下方：剧集列表 -->
          <div>
            <h4 class="mb-3">本地剧集列表 (共 {{ episodesPagination.pageCount }} 集)</h4>
            <v-data-table-server
              :headers="episodeHeaders"
              :items="episodes"
              :loading="episodesLoading"
              :items-per-page="episodesPagination.itemsPerPage"
              :items-length="episodesPagination.pageCount"
              :page="episodesPagination.page"
              density="compact"
              class="elevation-1"
              hover
              @update:options="onEpisodesOptionsChange"
            >
              <template v-slot:item.fileName="{ item }">
                <div class="text-truncate text-caption" :title="item.fileName" style="max-width: 150px">
                  {{ item.fileName }}
                </div>
              </template>

              <template v-slot:item.episodeTitle="{ item }">
                <div class="text-truncate text-caption" :title="item.episodeTitle" style="max-width: 120px">
                  {{ item.episodeTitle || '-' }}
                </div>
              </template>

              <template v-slot:item.resolution="{ item }">
                <v-chip size="x-small" variant="outlined">
                  {{ item.resolution }}
                </v-chip>
              </template>

              <template v-slot:item.matchStatus="{ item }">
                <v-chip size="x-small" :color="getMatchStatusMeta(item.matchStatus).color" variant="tonal">
                  {{ getMatchStatusMeta(item.matchStatus).text }}
                </v-chip>
              </template>

              <template v-slot:item.durationStr="{ item }">
                <div class="text-caption">{{ item.durationStr }}</div>
              </template>

              <template v-slot:item.sizeStr="{ item }">
                <div class="text-caption">{{ item.sizeStr }}</div>
              </template>

              <template v-slot:item.videoFormat="{ item }">
                <v-chip size="x-small" variant="tonal" color="success">
                  {{ item.videoFormat }}
                </v-chip>
              </template>

              <template v-slot:item.actions="{ item }">
                <v-btn
                  size="x-small"
                  variant="text"
                  color="primary"
                  @click="openRematchDialog(item)"
                >
                  <v-icon start>mdi-sync</v-icon>
                  重搜匹配
                </v-btn>
                <v-btn
                  size="x-small"
                  variant="text"
                  color="info"
                  @click="openSubtitleManager(item)"
                >
                  <v-icon start>mdi-subtitles</v-icon>
                  字幕
                </v-btn>
              </template>

              <template v-slot:no-data>
                <div class="text-center py-6 text-grey">
                  <v-icon size="48" class="mb-2">mdi-file-video-outline</v-icon>
                  <div class="text-caption">暂无剧集</div>
                </div>
              </template>
            </v-data-table-server>
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>

    <MediaRematchDialog
      v-model="rematchDialog"
      :media-file="rematchTargetFile"
      @applied="handleRematchApplied"
      @update:model-value="(value) => { if (!value) closeRematchDialog() }"
    />

    <!-- 字幕管理对话框 -->
    <v-dialog v-model="subtitleDialog" max-width="900px" scrollable>
      <v-card v-if="selectedEpisodeForSubtitle">
        <v-card-title class="d-flex align-center">
          <v-icon start>mdi-subtitles</v-icon>
          字幕管理 - {{ selectedEpisodeForSubtitle.fileName }}
          <v-spacer />
          <v-btn
            icon="mdi-close"
            variant="text"
            @click="closeSubtitleDialog"
          />
        </v-card-title>
        <v-divider />
        <v-card-text class="pa-4">
          <SubtitleManager :media-file-id="selectedEpisodeForSubtitle.id" />
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
/* Tailwind CSS 补充定义 */
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

/* 动漫封面卡片网格 */
.anime-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}
.anime-card {
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.anime-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12) !important;
}
.anime-cover {
  width: 100%;
}
.anime-cover-noimg {
  width: 100%;
  aspect-ratio: 3 / 4;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f4f4f5;
  color: #c3b7ab;
}
.anime-card-body {
  padding: 10px 12px 12px;
}
.anime-card-title {
  font-size: 0.85rem;
  font-weight: 600;
  line-height: 1.4;
  color: #1f2937;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
