<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const searching = ref(false)
const creatingTask = ref(false)
const downloadingTaskKeys = ref(new Set())

const keyword = ref('')
const subgroup = ref(null)
const type = ref(null)

const subgroups = ref([])
const types = ref([])
const libraries = ref([])
const selectedLibraryId = ref(null)

const hasMore = ref(false)
const resources = ref([])
const selection = ref([])

const fetchSubgroups = async () => {
  try {
    const res = await axios.get(`${API_BASE}/resource-search/subgroup`)
    subgroups.value = res.data?.data || []
  } catch (error) {
    console.error('加载字幕组失败:', error)
  }
}

const fetchTypes = async () => {
  try {
    const res = await axios.get(`${API_BASE}/resource-search/type`)
    types.value = res.data?.data || []
  } catch (error) {
    console.error('加载资源类型失败:', error)
  }
}

const fetchLibraries = async () => {
  try {
    const res = await axios.get(`${API_BASE}/media-library`)
    const rawLibraries = res.data?.data || []
    libraries.value = rawLibraries.map((item) => ({ ...item, id: String(item.id) }))

    const selected = selectedLibraryId.value != null ? String(selectedLibraryId.value) : null
    const exists = selected != null && libraries.value.some((item) => item.id === selected)
    if (!exists && libraries.value.length > 0) {
      selectedLibraryId.value = libraries.value[0].id
    }
  } catch (error) {
    console.error('加载媒体库失败:', error)
    showAppMessage(error.response?.data?.msg || '加载媒体库失败', 'error')
  }
}

const rowKey = (row) => `${row.magnet}-${row.title}`

const searchResources = async (append = false) => {
  if (!keyword.value.trim()) {
    showAppMessage('请输入搜索关键词', 'warning')
    return
  }
  searching.value = true
  try {
    const res = await axios.get(`${API_BASE}/resource-search/list`, {
      params: {
        keyword: keyword.value.trim(),
        subgroup: subgroup.value,
        type: type.value,
        offset: append ? resources.value.length : 0
      }
    })
    const payload = res.data?.data || {}
    const items = payload.resources || []
    if (append) {
      resources.value = [...resources.value, ...items]
    } else {
      resources.value = items
      selection.value = []
    }
    hasMore.value = payload.hasMore === true
  } catch (error) {
    console.error('搜索资源失败:', error)
    showAppMessage(error.response?.data?.msg || '搜索资源失败', 'error')
  } finally {
    searching.value = false
  }
}

const isDownloading = (row) => downloadingTaskKeys.value.has(rowKey(row))

const createDownloadTask = async (row) => {
  if (!selectedLibraryId.value) {
    showAppMessage('请先选择媒体库', 'warning')
    return
  }

  const key = rowKey(row)
  if (downloadingTaskKeys.value.has(key)) {
    return
  }

  downloadingTaskKeys.value = new Set(downloadingTaskKeys.value).add(key)
  creatingTask.value = true
  try {
    const res = await axios.post(`${API_BASE}/resource-search/download`, {
      title: row.title,
      magnet: row.magnet,
      pageUrl: row.pageUrl,
      fileSize: row.fileSize,
      publishDate: row.publishDate,
      subgroupName: row.subgroupName,
      typeName: row.typeName,
      libraryId: String(selectedLibraryId.value)
    })

    if (res.data?.code === 200) {
      showAppMessage('下载任务已创建', 'success')
    } else {
      showAppMessage(res.data?.msg || '创建下载任务失败', 'error')
    }
  } catch (error) {
    console.error('创建下载任务失败:', error)
    showAppMessage(error.response?.data?.msg || '创建下载任务失败', 'error')
  } finally {
    creatingTask.value = false
    downloadingTaskKeys.value = new Set(downloadingTaskKeys.value)
    downloadingTaskKeys.value.delete(key)
  }
}

const batchDownload = async () => {
  if (!selectedLibraryId.value) {
    showAppMessage('请先选择媒体库', 'warning')
    return
  }
  if (selection.value.length === 0) {
    showAppMessage('请先勾选要下载的资源', 'warning')
    return
  }

  const selectedMap = new Map(resources.value.map((row) => [rowKey(row), row]))
  const items = selection.value
    .map((key) => selectedMap.get(key))
    .filter(Boolean)
    .map((row) => ({
      title: row.title,
      magnet: row.magnet,
      pageUrl: row.pageUrl,
      fileSize: row.fileSize,
      publishDate: row.publishDate,
      subgroupName: row.subgroupName,
      typeName: row.typeName
    }))

  if (items.length === 0) {
    showAppMessage('选中的资源无效', 'warning')
    return
  }

  creatingTask.value = true
  try {
    const res = await axios.post(`${API_BASE}/resource-search/download/batch`, {
      libraryId: String(selectedLibraryId.value),
      items
    })
    if (res.data?.code === 200) {
      const data = res.data.data || {}
      const parts = []
      if (data.created > 0) parts.push(`成功 ${data.created} 个`)
      if (data.duplicated > 0) parts.push(`已存在 ${data.duplicated} 个`)
      if (data.errors?.length > 0) parts.push(`失败 ${data.errors.length} 个`)
      showAppMessage(`批量下载完成：${parts.join('，') || '无结果'}`, data.errors?.length > 0 ? 'warning' : 'success')
      if (data.errors?.length > 0) {
        console.warn('批量下载失败明细:', data.errors)
      }
      selection.value = []
    } else {
      showAppMessage(res.data?.msg || '批量下载失败', 'error')
    }
  } catch (error) {
    console.error('批量下载失败:', error)
    showAppMessage(error.response?.data?.msg || '批量下载失败', 'error')
  } finally {
    creatingTask.value = false
  }
}

const allSelected = computed(() => {
  return resources.value.length > 0 && resources.value.every((row) => selection.value.includes(rowKey(row)))
})

const toggleAll = (checked) => {
  selection.value = checked ? resources.value.map(rowKey) : []
}

const toggleOne = (key) => {
  selection.value = selection.value.includes(key)
    ? selection.value.filter((k) => k !== key)
    : [...selection.value, key]
}

onMounted(async () => {
  loading.value = true
  await Promise.allSettled([fetchSubgroups(), fetchTypes(), fetchLibraries()])
  loading.value = false
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-title>
        <v-icon start>mdi-cloud-search</v-icon>
        资源搜索与下载
      </v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="keyword"
              label="搜索关键词"
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              hide-details
              @keyup.enter="searchResources(false)"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="subgroup"
              :items="subgroups"
              item-title="name"
              item-value="id"
              label="字幕组"
              clearable
              variant="outlined"
              hide-details
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-select
              v-model="type"
              :items="types"
              item-title="name"
              item-value="id"
              label="资源类型"
              clearable
              variant="outlined"
              hide-details
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="selectedLibraryId"
              :items="libraries"
              item-title="name"
              item-value="id"
              label="目标媒体库"
              variant="outlined"
              hide-details
            />
          </v-col>
        </v-row>

        <div class="mt-4 d-flex ga-3 align-center">
          <v-btn color="primary" :loading="searching" :disabled="loading || searching" @click="searchResources(false)">
            <v-icon start>mdi-magnify</v-icon>
            搜索
          </v-btn>
          <v-btn
            color="teal-darken-1"
            :loading="creatingTask"
            :disabled="selection.length === 0 || !selectedLibraryId"
            @click="batchDownload"
          >
            <v-icon start>mdi-download-multiple</v-icon>
            批量下载{{ selection.length > 0 ? ` (${selection.length})` : '' }}
          </v-btn>
        </div>

        <v-alert v-if="hasMore && resources.length === 0" class="mt-4" type="warning" variant="tonal" density="comfortable">
          搜索结果过多，当前仅显示部分结果，可使用「加载更多」继续获取。
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card class="mb-4">
      <v-card-title>
        <div class="d-flex align-center justify-space-between ga-3">
          <span>搜索结果</span>
          <span class="text-caption text-medium-emphasis">{{ resources.length }} 条</span>
        </div>
      </v-card-title>
      <v-card-text>
        <v-table density="compact" fixed-header height="360">
          <thead>
            <tr>
              <th style="width: 40px;">
                <v-checkbox
                  :model-value="allSelected"
                  density="compact"
                  hide-details
                  @update:model-value="toggleAll"
                />
              </th>
              <th>标题</th>
              <th>类型</th>
              <th>字幕组</th>
              <th>大小</th>
              <th>发布时间</th>
              <th style="width: 100px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in resources" :key="rowKey(row)">
              <td>
                <v-checkbox
                  :model-value="selection.includes(rowKey(row))"
                  density="compact"
                  hide-details
                  @update:model-value="toggleOne(rowKey(row))"
                />
              </td>
              <td class="title-cell">{{ row.title }}</td>
              <td>{{ row.typeName }}</td>
              <td>{{ row.subgroupName }}</td>
              <td>{{ row.fileSize }}</td>
              <td>{{ row.publishDate }}</td>
              <td>
                <v-btn
                  size="small"
                  color="teal-darken-1"
                  variant="outlined"
                  :loading="isDownloading(row)"
                  :disabled="isDownloading(row) || !selectedLibraryId"
                  @click="createDownloadTask(row)"
                >
                  下载
                </v-btn>
              </td>
            </tr>
            <tr v-if="searching">
              <td colspan="7" class="text-center py-4">
                <v-progress-circular indeterminate size="28" color="primary" />
              </td>
            </tr>
            <tr v-else-if="resources.length === 0">
              <td colspan="7" class="text-center text-medium-emphasis py-6">暂无搜索结果</td>
            </tr>
          </tbody>
        </v-table>

        <div v-if="hasMore" class="text-center mt-3">
          <v-btn variant="outlined" :loading="searching" @click="searchResources(true)">
            <v-icon start>mdi-chevron-down</v-icon>
            加载更多
          </v-btn>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.title-cell {
  max-width: 420px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
