<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { askAppConfirm, showAppMessage } from '../../../utils/ui-feedback'
import MediaRematchDialog from '../../../components/admin/media/MediaRematchDialog.vue'
import SubtitleManager from '../../../components/admin/media/SubtitleManager.vue'

const API_BASE = '/api'

const mediaFiles = ref([])
const loading = ref(false)
const detailDialog = ref(false)
const selectedLibraryId = ref(null)
const searchKeyword = ref('')
const matchedFilter = ref(null)
const mediaLibraries = ref([])
const reprocessingLibraryId = ref(null)
const rematchDialog = ref(false)
const rematchTargetFile = ref(null)
const subtitleDialog = ref(false)
const selectedFileForSubtitle = ref(null)

const pagination = ref({
  page: 1,
  itemsPerPage: 10,
  pageCount: 1
})

const selectedFile = ref(null)

const headers = [
  { title: '文件名', key: 'fileName', width: '30%' },
  { title: '大小', key: 'size', width: '10%' },
  { title: '状态', key: 'metadataFetched', width: '10%' },
  { title: '弹幕匹配', key: 'matchStatus', width: '10%' },
  { title: '视频编码', key: 'videoCodec', width: '12%' },
  { title: '分辨率', key: 'resolution', width: '12%' },
  { title: '操作', key: 'actions', width: '30%', sortable: false }
]

// 获取媒体库列表
const fetchLibraries = async () => {
  try {
    const res = await axios.get(`${API_BASE}/media-library`)
    if (res.data?.code === 200) {
      mediaLibraries.value = res.data.data || []
    }
  } catch (error) {
    console.error('获取媒体库失败:', error)
  }
}

// 获取媒体文件列表
const fetchMediaFiles = async (pageNum = 1) => {
  loading.value = true
  try {
    const params = {
      page: pageNum - 1,  // 后端page从0开始
      pageSize: pagination.value.itemsPerPage
    }
    if (selectedLibraryId.value) {
      params.libraryId = selectedLibraryId.value
    }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    if (matchedFilter.value !== null) {
      params.matched = matchedFilter.value
    }

    const res = await axios.get(`${API_BASE}/media-files`, { params })
    if (res.data?.code === 200 && res.data?.data) {
      mediaFiles.value = res.data.data.content || []
      pagination.value.pageCount = res.data.data.totalElements || 0
      pagination.value.page = pageNum
    }
  } catch (error) {
    console.error('获取媒体文件失败:', error)
  } finally {
    loading.value = false
  }
}

const openRematchDialog = (file) => {
  rematchTargetFile.value = file
  rematchDialog.value = true
}

const closeRematchDialog = () => {
  rematchDialog.value = false
  rematchTargetFile.value = null
}

const handleRematchApplied = async () => {
  await fetchMediaFiles(pagination.value.page)
}

const openSubtitleManager = (file) => {
  selectedFileForSubtitle.value = file
  subtitleDialog.value = true
}

const closeSubtitleDialog = () => {
  subtitleDialog.value = false
  selectedFileForSubtitle.value = null
}

// 查看文件详情
const viewFileDetail = (file) => {
  selectedFile.value = { ...file }
  detailDialog.value = true
}

// 删除文件
const deleteFile = async (fileId, deletePhysicalFile = false) => {
  const confirmed = await askAppConfirm({
    title: '删除文件',
    message: `确定要删除此文件${deletePhysicalFile ? '及其物理文件' : ''}吗？`,
    color: 'error'
  })
  if (!confirmed) {
    return
  }

  loading.value = true
  try {
    const res = await axios.delete(`${API_BASE}/media-files/${fileId}`, {
      params: { deleteFile: deletePhysicalFile }
    })
    if (res.data?.code === 200) {
      showAppMessage('删除成功', 'success')
      pagination.value.page = 1
      await fetchMediaFiles(1)
    } else {
      showAppMessage(res.data?.msg || '删除失败', 'error')
    }
  } catch (error) {
    showAppMessage('删除失败: ' + (error.response?.data?.msg || error.message), 'error')
  } finally {
    loading.value = false
  }
}

// 批量重新获取元数据
const reprocessMetadata = async (libraryId) => {
  if (!libraryId) {
    showAppMessage('请先选择媒体库', 'warning')
    return
  }

  const confirmed = await askAppConfirm({
    title: '重新获取元数据',
    message: '确定要重新获取此媒体库中所有文件的元数据吗？',
    color: 'warning'
  })
  if (!confirmed) {
    return
  }

  reprocessingLibraryId.value = libraryId
  try {
    const res = await axios.post(`${API_BASE}/media-files/reprocess-metadata/${libraryId}`)
    if (res.data?.code === 200) {
      showAppMessage('已提交重新获取任务', 'success')
      // 延迟刷新文件列表，等待后台任务状态更新
      setTimeout(() => {
        fetchMediaFiles(1)
      }, 5000)
    } else {
      showAppMessage(res.data?.msg || '提交任务失败', 'error')
    }
  } catch (error) {
    showAppMessage('提交任务失败: ' + (error.response?.data?.msg || error.message), 'error')
  } finally {
    reprocessingLibraryId.value = null
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

// 格式化时长
const formatDuration = (ms) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  if (hours > 0) {
    return `${hours}h ${minutes}m ${secs}s`
  }
  return `${minutes}m ${secs}s`
}

// 获取分辨率
const getResolution = (file) => {
  if (file.width && file.height) {
    return `${file.width}x${file.height}`
  }
  return '-'
}

// 库选择变化
const handleLibraryChange = () => {
  pagination.value.page = 1
  fetchMediaFiles(1)
}

const handleFilterSearch = () => {
  pagination.value.page = 1
  fetchMediaFiles(1)
}

const resetFilters = () => {
  searchKeyword.value = ''
  matchedFilter.value = null
  selectedLibraryId.value = null
  pagination.value.page = 1
  fetchMediaFiles(1)
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

// 页码或每页大小变化
const onTableOptionsChange = (options) => {
  const page = options.page || 1
  const pageSize = options.itemsPerPage || 20
  
  // 如果每页大小改变，重置到第一页
  if (pageSize !== pagination.value.itemsPerPage) {
    pagination.value.itemsPerPage = pageSize
    fetchMediaFiles(1)
  } else {
    // 只改变页码
    pagination.value.page = page
    pagination.value.itemsPerPage = pageSize
    fetchMediaFiles(page)
  }
}

onMounted(() => {
  pagination.value.page = 1
  fetchLibraries()
  fetchMediaFiles(1)
})
</script>

<template>
  <div>
    <!-- 操作工具栏 -->
    <v-card class="mb-4">
      <v-card-title class="d-flex align-center ga-2">
        <i class="mdi mdi-file-video-outline" style="color: #c45d2b;"></i>
        文件搜索
      </v-card-title>
      <v-card-text class="pa-4">
        <v-row dense class="align-center">
          <v-col cols="12" md="3">
            <v-select
              v-model="selectedLibraryId"
              :items="mediaLibraries"
              item-title="name"
              item-value="id"
              label="媒体库"
              variant="outlined"
              density="compact"
              hide-details
              @update:model-value="handleLibraryChange"
              clearable
            />
          </v-col>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="searchKeyword"
              label="文件名"
              variant="outlined"
              density="compact"
              prepend-inner-icon="mdi-magnify"
              hide-details
              clearable
              @keyup.enter="handleFilterSearch"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="matchedFilter"
              :items="[
                { title: '全部匹配状态', value: null },
                { title: '仅已匹配', value: true },
                { title: '仅未匹配', value: false }
              ]"
              item-title="title"
              item-value="value"
              label="弹幕匹配"
              variant="outlined"
              density="compact"
              hide-details
              @update:model-value="handleFilterSearch"
            />
          </v-col>
          <v-col cols="12" md="2" class="d-flex ga-2 justify-md-end">
            <v-btn color="primary" variant="elevated" size="small" @click="handleFilterSearch">查询</v-btn>
            <v-btn color="grey" variant="text" size="small" @click="resetFilters">重置</v-btn>
          </v-col>
          <v-col cols="12" class="d-flex">
            <v-btn
              v-if="selectedLibraryId"
              color="warning"
              variant="outlined"
              size="small"
              :loading="reprocessingLibraryId === selectedLibraryId"
              @click="reprocessMetadata(selectedLibraryId)"
            >
              <v-icon start>mdi-refresh</v-icon>
              重新获取元数据
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- 文件列表（桌面表格） -->
    <v-card>
      <div class="d-none d-lg-block">
        <v-data-table-server
          :headers="headers"
          :items="mediaFiles"
          :loading="loading"
          :items-per-page="pagination.itemsPerPage"
          :items-length="pagination.pageCount"
          density="compact"
          class="elevation-0"
          @update:options="onTableOptionsChange"
        >

          <template v-slot:item.size="{ item }">
            <span class="text-caption">{{ formatFileSize(item.size) }}</span>
          </template>

          <template v-slot:item.metadataFetched="{ item }">
            <v-chip
              :color="item.metadataFetched ? 'success' : 'warning'"
              size="small"
              label
            >
              {{ item.metadataFetched ? '已解析' : '待解析' }}
            </v-chip>
          </template>

          <template v-slot:item.matchStatus="{ item }">
            <v-chip
              :color="getMatchStatusMeta(item.matchStatus).color"
              size="small"
              label
            >
              {{ getMatchStatusMeta(item.matchStatus).text }}
            </v-chip>
          </template>

          <template v-slot:item.videoCodec="{ item }">
            <span class="text-caption">{{ item.videoCodec || '-' }}</span>
          </template>

          <template v-slot:item.resolution="{ item }">
            <span class="text-caption">{{ getResolution(item) }}</span>
          </template>

          <template v-slot:item.actions="{ item }">
            <div class="d-flex align-center ga-1">
              <v-btn
                icon="mdi-eye"
                variant="text"
                size="x-small"
                color="info"
                @click="viewFileDetail(item)"
              />
              <v-tooltip location="top" text="重新搜索匹配">
                <template #activator="{ props }">
                  <v-btn
                    icon="mdi-sync"
                    variant="text"
                    size="x-small"
                    color="primary"
                    :disabled="loading"
                    v-bind="props"
                    @click="openRematchDialog(item)"
                  />
                </template>
              </v-tooltip>
              <v-tooltip location="top" text="字幕管理">
                <template #activator="{ props }">
                  <v-btn
                    icon="mdi-subtitles"
                    variant="text"
                    size="x-small"
                    color="purple"
                    v-bind="props"
                    @click="openSubtitleManager(item)"
                  />
                </template>
              </v-tooltip>
              <v-menu>
                <template v-slot:activator="{ props }">
                  <v-btn
                    icon="mdi-delete"
                    variant="text"
                    size="x-small"
                    color="error"
                    v-bind="props"
                  />
                </template>
                <v-list>
                  <v-list-item @click="deleteFile(item.id, false)">
                    <v-list-item-title>删除记录</v-list-item-title>
                  </v-list-item>
                  <v-list-item @click="deleteFile(item.id, true)">
                    <v-list-item-title>删除记录及文件</v-list-item-title>
                  </v-list-item>
                </v-list>
              </v-menu>
            </div>
          </template>

          <template v-slot:no-data>
            <div class="text-center py-8">
              <v-icon size="64" color="grey-lighten-1">mdi-file-video-outline</v-icon>
              <p class="text-body-1 mt-4 text-grey">暂无视频文件</p>
            </div>
          </template>
        </v-data-table-server>
      </div>

      <!-- 文件列表（移动端卡片） -->
      <v-card-text class="d-lg-none pa-4">
        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" />
        </div>

        <template v-else-if="mediaFiles.length > 0">
          <v-card v-for="item in mediaFiles" :key="item.id" class="mb-3" variant="outlined">
            <v-card-item>
              <template #prepend>
                <v-avatar color="info" variant="tonal">
                  <v-icon>mdi-file-video</v-icon>
                </v-avatar>
              </template>
              <v-card-title class="text-body-1 text-wrap">{{ item.fileName }}</v-card-title>
              <v-card-subtitle class="text-caption text-truncate" :title="item.filePath">
                {{ item.filePath || '-' }}
              </v-card-subtitle>
            </v-card-item>

            <v-card-text class="pt-2">
              <div class="text-caption mb-2">
                {{ formatFileSize(item.size) }} · {{ getResolution(item) }} · {{ item.videoCodec || '-' }}
              </div>
              <div class="d-flex flex-wrap ga-2">
                <v-chip :color="item.metadataFetched ? 'success' : 'warning'" size="x-small" label>
                  {{ item.metadataFetched ? '已解析' : '待解析' }}
                </v-chip>
                <v-chip :color="getMatchStatusMeta(item.matchStatus).color" size="x-small" label>
                  {{ getMatchStatusMeta(item.matchStatus).text }}
                </v-chip>
              </div>
            </v-card-text>

            <v-card-actions>
              <v-btn icon="mdi-eye" variant="text" size="small" color="info" @click="viewFileDetail(item)" />
              <v-btn icon="mdi-sync" variant="text" size="small" color="primary" :disabled="loading" @click="openRematchDialog(item)" />
              <v-btn icon="mdi-subtitles" variant="text" size="small" color="purple" @click="openSubtitleManager(item)" />
              <v-spacer />
              <v-menu>
                <template v-slot:activator="{ props }">
                  <v-btn icon="mdi-delete" variant="text" size="small" color="error" v-bind="props" />
                </template>
                <v-list>
                  <v-list-item @click="deleteFile(item.id, false)">
                    <v-list-item-title>删除记录</v-list-item-title>
                  </v-list-item>
                  <v-list-item @click="deleteFile(item.id, true)">
                    <v-list-item-title>删除记录及文件</v-list-item-title>
                  </v-list-item>
                </v-list>
              </v-menu>
            </v-card-actions>
          </v-card>

          <div
            v-if="pagination.pageCount > pagination.itemsPerPage"
            class="d-flex justify-center mt-2"
          >
            <v-pagination
              v-model="pagination.page"
              :length="Math.max(1, Math.ceil(pagination.pageCount / pagination.itemsPerPage))"
              @update:model-value="(p) => onTableOptionsChange({ page: p, itemsPerPage: pagination.itemsPerPage })"
            />
          </div>
        </template>

        <div v-else class="text-center py-8">
          <v-icon size="64" color="grey-lighten-1">mdi-file-video-outline</v-icon>
          <p class="text-body-1 mt-4 text-grey">暂无视频文件</p>
        </div>
      </v-card-text>
    </v-card>

    <MediaRematchDialog
      v-model="rematchDialog"
      :media-file="rematchTargetFile"
      @applied="handleRematchApplied"
      @update:model-value="(value) => { if (!value) closeRematchDialog() }"
    />

    <!-- 文件详情对话框 -->
    <v-dialog v-model="detailDialog" max-width="900">
      <v-card v-if="selectedFile">
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">文件详情</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="detailDialog = false" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-row>
            <v-col cols="12" md="6">
              <v-list density="compact">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件名</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.fileName }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件路径</v-list-item-title>
                  <v-list-item-subtitle class="text-caption" style="word-break: break-all;">{{ selectedFile.filePath }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件大小</v-list-item-title>
                  <v-list-item-subtitle>{{ formatFileSize(selectedFile.size) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">时长</v-list-item-title>
                  <v-list-item-subtitle>{{ formatDuration(selectedFile.duration) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">MD5 哈希</v-list-item-title>
                  <v-list-item-subtitle class="font-monospace text-caption">{{ selectedFile.hash || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
            <v-col cols="12" md="6">
              <v-list density="compact">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">分辨率</v-list-item-title>
                  <v-list-item-subtitle>{{ getResolution(selectedFile) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">帧率</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.fps ? selectedFile.fps.toFixed(2) + ' fps' : '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">视频编码</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.videoCodec || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">音频编码</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.audioCodec || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">容器格式</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.containerFormat || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">HDR 类型</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.hdrType || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">色彩空间</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.colorSpace || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
          </v-row>

          <v-divider class="my-4" />

          <v-row>
            <v-col cols="12">
              <span class="text-subtitle-2 font-weight-medium">动漫信息</span>
              <v-list density="compact" class="mt-2">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">番名</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.animeTitle || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">话数</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.episodeTitle || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">弹幕库ID</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.episodeId || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
          </v-row>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn color="grey" variant="text" @click="detailDialog = false">
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 字幕管理对话框 -->
    <v-dialog v-model="subtitleDialog" max-width="900px" scrollable>
      <v-card v-if="selectedFileForSubtitle">
        <v-card-title class="d-flex align-center">
          <v-icon start>mdi-subtitles</v-icon>
          字幕管理 - {{ selectedFileForSubtitle.fileName }}
          <v-spacer />
          <v-btn
            icon="mdi-close"
            variant="text"
            @click="closeSubtitleDialog"
          />
        </v-card-title>
        <v-divider />
        <v-card-text class="pa-4">
          <SubtitleManager :media-file-id="selectedFileForSubtitle.id" />
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
:deep(.v-data-table) {
  background: transparent;
}

.font-monospace {
  font-family: 'Courier New', monospace;
}
</style>
