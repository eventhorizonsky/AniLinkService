<script setup>
import { ref, onMounted, watch } from 'vue'
import { formatAnimeType } from '../../../utils/animeType'
import MediaRematchDialog from '../../../components/admin/media/MediaRematchDialog.vue'
import SubtitleManager from '../../../components/admin/media/SubtitleManager.vue'
import { formatDuration, formatFileSize } from '../../../utils/format'
import { getMatchStatusMeta } from '../../../utils/mediaMatchStatus'
import { getAnimeList, getAnimeEpisodes } from '../../../api/anime'
import { useServerPagination } from '../../../composables/useServerPagination'

const animes = ref([])
const selectedAnime = ref(null)
const episodes = ref([])
const dialogOpen = ref(false)
const rematchDialog = ref(false)
const rematchTargetFile = ref(null)
const subtitleDialog = ref(false)
const selectedEpisodeForSubtitle = ref(null)

const search = ref('')

// 动漫列表分页（服务端）
const {
  page: animePage,
  pageSize: animePageSize,
  totalElements: animeTotalElements,
  pageCount: animePageCount,
  loading,
  fetchPage: fetchAnimePage
} = useServerPagination({
  pageSize: 10,
  fetchFn: async (query) => {
    try {
      const params = {
        page: query.page + 1,
        pageSize: query.pageSize
      }
      if (search.value.trim()) {
        params.keyword = search.value.trim()
      }
      const res = await getAnimeList(params)
      if (res?.code === 200 && res.data) {
        animes.value = res.data.content || []
      }
      return res
    } catch (error) {
      console.error('获取动漫列表失败:', error)
      return null
    }
  }
})

// 剧集列表分页（服务端，弹窗内）
const {
  page: episodesPage,
  pageSize: episodesPageSize,
  totalElements: episodesTotalElements,
  loading: episodesLoading,
  onOptionsChange: onEpisodesOptionsChange,
  fetchPage: fetchEpisodesPage
} = useServerPagination({
  pageSize: 10,
  enabled: () => !!selectedAnime.value,
  fetchFn: async (query) => {
    try {
      const res = await getAnimeEpisodes(selectedAnime.value?.animeId, {
        page: query.page + 1,
        pageSize: query.pageSize
      })
      if (res?.code === 200 && res.data) {
        episodes.value = (res.data.content || []).map(ep => ({
          ...ep,
          resolution: ep.width && ep.height ? `${ep.width}x${ep.height}` : '未知',
          durationStr: formatDuration(ep.duration),
          sizeStr: formatFileSize(ep.size),
          videoFormat: formatVideoCodec(ep.videoCodec, ep.audioCodec)
        }))
      }
      return res
    } catch (error) {
      console.error('获取剧集列表失败:', error)
      episodes.value = []
      return null
    }
  }
})

watch([episodesPage, episodesPageSize], () => {
  fetchEpisodesPage()
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

// 获取动漫的剧集（服务端分页）
const selectAnime = async (anime) => {
  selectedAnime.value = anime
  dialogOpen.value = true
  if (episodesPage.value === 1) {
    await fetchEpisodesPage()
  } else {
    episodesPage.value = 1
  }
}

// 关闭详情弹窗
const closeDetails = () => {
  dialogOpen.value = false
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
  animePage.value = 1
  fetchAnimePage()
}

// 重置搜索
const resetSearch = () => {
  search.value = ''
  animePage.value = 1
  fetchAnimePage()
}

const onAnimePageChange = (newPage) => {
  animePage.value = newPage
  fetchAnimePage()
}

onMounted(() => {
  fetchAnimePage()
})

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
    await fetchEpisodesPage()
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
      <v-card-title class="d-flex align-center ga-2">
        <i class="mdi mdi-library" style="color: var(--al-accent);"></i>
        动漫库管理
      </v-card-title>

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
            v-if="animeTotalElements > animePageSize"
            class="d-flex justify-center mt-4"
          >
            <v-pagination
              v-model="animePage"
              :length="animePageCount"
              @update:model-value="onAnimePageChange"
            />
          </div>
        </template>

        <div v-else class="text-center py-8 text-grey">
          <v-icon size="48" class="mb-2">mdi-library-outline</v-icon>
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
                    <span class="text-sm text-gray-900">{{ episodesTotalElements }}</span>
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
            <h4 class="mb-3">本地剧集列表 (共 {{ episodesTotalElements }} 集)</h4>
            <v-data-table-server
              :headers="episodeHeaders"
              :items="episodes"
              :loading="episodesLoading"
              v-model:page="episodesPage"
              v-model:items-per-page="episodesPageSize"
              :items-length="episodesTotalElements"
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
  background: rgb(var(--v-theme-surface-variant));
  color: rgb(var(--v-theme-on-surface-variant));
}
.anime-card-body {
  padding: 10px 12px 12px;
}
.anime-card-title {
  font-size: 0.85rem;
  font-weight: 600;
  line-height: 1.4;
  color: rgb(var(--v-theme-on-surface));
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
