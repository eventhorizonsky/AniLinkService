<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { askAppConfirm, showAppMessage } from '../../../utils/ui-feedback'
import { API_BASE } from '../../../utils/constants'

const mediaLibraries = ref([])
const loading = ref(false)
const dialog = ref(false)
const errorMessage = ref('')
const scanning = ref(false)
const pathTree = ref([])
const loadingPaths = ref(false)
const showPathTree = ref(false)
const rootPath = ref('/')

// 进度追踪相关
const progressData = ref({})
const showProgress = ref({})
const pollIntervals = ref({})

const getLibraryMatchProcessedCount = (libraryId) => {
  const match = progressData.value[libraryId]?.match
  if (!match) return 0
  return Number(match.matched || 0) + Number(match.noMatch || 0)
}

// 获取进度数据（确保响应式）
const getProgressData = (libraryId) => {
  return progressData.value[libraryId]
}

const newLibrary = ref({
  name: '',
  path: ''
})

// 获取元数据进度
const fetchMetadataProgress = async (libraryId) => {
  try {
    const res = await axios.get(`${API_BASE}/media-files/queue/metadata-progress`, {
      params: { libraryId }
    })
    if (res.data?.code === 200) {
      // 确保库的进度对象初始化
      if (!progressData.value[libraryId]) {
        progressData.value[libraryId] = { metadata: null, match: null }
      }
      // 直接赋值(Vue 3 的响应式自动处理)
      progressData.value[libraryId].metadata = res.data.data
    }
  } catch (error) {
    console.error('获取元数据进度失败:', error)
  }
}

// 获取弹幕匹配进度
const fetchMatchProgress = async (libraryId) => {
  try {
    const res = await axios.get(`${API_BASE}/media-files/queue/match-progress`, {
      params: { libraryId }
    })
    if (res.data?.code === 200) {
      // 确保库的进度对象初始化
      if (!progressData.value[libraryId]) {
        progressData.value[libraryId] = { metadata: null, match: null }
      }
      // 直接赋值(Vue 3 的响应式自动处理)
      progressData.value[libraryId].match = res.data.data
    }
  } catch (error) {
    console.error('获取匹配进度失败:', error)
  }
}

// 启动进度轮询
const startProgressPolling = (libraryId) => {
  // 停止已有的轮询
  if (pollIntervals.value[libraryId]) {
    clearInterval(pollIntervals.value[libraryId])
  }
  
  showProgress.value[libraryId] = true
  
  // 立刻获取一次进度
  fetchMetadataProgress(libraryId)
  fetchMatchProgress(libraryId)
  
  // 每5秒轮询一次
  const interval = setInterval(() => {
    fetchMetadataProgress(libraryId)
    fetchMatchProgress(libraryId)
  }, 5000)
  
  pollIntervals.value[libraryId] = interval
}

// 停止进度轮询
const stopProgressPolling = (libraryId) => {
  const interval = pollIntervals.value[libraryId]
  if (interval) {
    clearInterval(interval)
    delete pollIntervals.value[libraryId]
  }
  showProgress.value[libraryId] = false
}

// 切换进度显示
const toggleProgressDisplay = (libraryId) => {
  const currentState = showProgress.value[libraryId] || false
  showProgress.value[libraryId] = !currentState
  
  if (!currentState) {
    // 展开时，如果没有轮询就启动轮询
    if (!pollIntervals.value[libraryId]) {
      startProgressPolling(libraryId)
    }
  } else {
    // 收起时，停止轮询
    stopProgressPolling(libraryId)
  }
}

const fetchLibraries = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/media-library`)
    if (res.data?.code === 200) {
      mediaLibraries.value = res.data.data || []
    }
  } catch (error) {
    console.error('获取媒体库失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchPaths = async (path = rootPath.value) => {
  loadingPaths.value = true
  try {
    const res = await axios.get(`${API_BASE}/media-library/paths`, {
      params: {
        rootPath: path,
        onlyDir: true
      }
    })
    if (res.data?.code === 200) {
      const items = res.data.data || []
      pathTree.value = items.map(item => ({
        id: item.path,
        title: item.name,
        children: []
      }))
    }
  } catch (error) {
    console.error('获取路径失败:', error)
  } finally {
    loadingPaths.value = false
  }
}

const handleNodeSelect = async (item) => {
  loadingPaths.value = true
  try {
    const res = await axios.get(`${API_BASE}/media-library/paths`, {
      params: {
        rootPath: item.id,
        onlyDir: true
      }
    })
    if (res.data?.code === 200) {
      const items = res.data.data || []
      if (items.length > 0) {
        item.children = items.map(child => ({
          id: child.path,
          title: child.name,
          children: []
        }))
      }
    }
  } catch (error) {
    console.error('获取子路径失败:', error)
  } finally {
    loadingPaths.value = false
  }
}

const addLibrary = async () => {
  if (!newLibrary.value.name || !newLibrary.value.path) {
    errorMessage.value = '请填写媒体库名称和路径'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const res = await axios.post(`${API_BASE}/media-library`, newLibrary.value)
    if (res.data?.code === 200) {
      dialog.value = false
      newLibrary.value = { name: '', path: '' }
      showPathTree.value = false
      await fetchLibraries()
    } else {
      errorMessage.value = res.data?.msg || '添加媒体库失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '添加媒体库失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const deleteLibrary = async (id) => {
  const confirmed = await askAppConfirm({
    title: '删除媒体库',
    message: '确定要删除这个媒体库吗？',
    color: 'error'
  })
  if (!confirmed) return

  try {
    const res = await axios.delete(`${API_BASE}/media-library/${id}`)
    if (res.data?.code === 200) {
      await fetchLibraries()
    }
  } catch (error) {
    showAppMessage('删除失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  }
}

const scanLibrary = async (id) => {
  scanning.value = true
  try {
    const res = await axios.post(`${API_BASE}/media-files/reprocess-metadata/${id}`)
    if (res.data?.code === 200) {
      showAppMessage(res.data.msg || '重新获取元数据已触发', 'success')
      await fetchLibraries()
      // 启动进度轮询
      startProgressPolling(id)
    }
  } catch (error) {
    showAppMessage('重新获取元数据失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  } finally {
    scanning.value = false
  }
}

const rematchLibrary = async (id) => {
  scanning.value = true
  try {
    const res = await axios.post(`${API_BASE}/media-library/rematch/${id}`)
    if (res.data?.code === 200) {
      showAppMessage(res.data.msg || '弹幕重新匹配已触发', 'success')
      await fetchLibraries()
      // 启动进度轮询
      startProgressPolling(id)
    }
  } catch (error) {
    showAppMessage('重新匹配失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  } finally {
    scanning.value = false
  }
}

const scanAll = async () => {
  const confirmed = await askAppConfirm({
    title: '批量重新获取元数据',
    message: '确定要重新获取所有媒体库的元数据吗？',
    color: 'warning'
  })
  if (!confirmed) return

  scanning.value = true
  try {
    // 逐个为所有库提交重新获取元数据的任务
    for (const lib of mediaLibraries.value) {
      await axios.post(`${API_BASE}/media-files/reprocess-metadata/${lib.id}`)
    }
    showAppMessage('所有媒体库的元数据重新获取已提交', 'success')
    await fetchLibraries()
    // 为所有库启动进度轮询
    mediaLibraries.value.forEach(lib => {
      startProgressPolling(lib.id)
    })
  } catch (error) {
    showAppMessage('提交任务失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  } finally {
    scanning.value = false
  }
}

const openAddDialog = () => {
  errorMessage.value = ''
  showPathTree.value = false
  newLibrary.value = { name: '', path: '' }
  dialog.value = true
}

const closeDialog = () => {
  dialog.value = false
  errorMessage.value = ''
  showPathTree.value = false
  pathTree.value = []
}

const togglePathTree = () => {
  if (!showPathTree.value) {
    fetchPaths(rootPath.value)
  }
  showPathTree.value = !showPathTree.value
}

const onPathSelect = (selected) => {
  if (selected && selected.length > 0) {
    newLibrary.value.path = selected[0]
    showPathTree.value = false
  }
}

onMounted(() => {
  fetchLibraries()
})

onUnmounted(() => {
  // 清除所有轮询interval
  Object.values(pollIntervals.value).forEach((interval) => {
    clearInterval(interval)
  })
  pollIntervals.value = {}
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-text class="d-flex gap-2">
        <v-btn
          color="primary"
          variant="elevated"
          @click="openAddDialog"
        >
          <v-icon start>mdi-plus</v-icon>
          添加媒体库
        </v-btn>
        <v-btn
          v-if="mediaLibraries.length > 0"
          color="info"
          variant="elevated"
          :loading="scanning"
          :disabled="scanning"
          @click="scanAll"
        >
          <v-icon start>mdi-database-refresh</v-icon>
          重新获取所有元数据
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card v-if="mediaLibraries.length === 0 && !loading" class="text-center pa-8">
      <v-icon size="64" color="grey-lighten-1">mdi-folder-open-outline</v-icon>
      <p class="text-body-1 mt-4 text-grey">暂无媒体库，请添加</p>
    </v-card>

    <v-card v-else-if="mediaLibraries.length > 0">
      <v-list>
        <template v-for="library in mediaLibraries" :key="library.id">
          <v-list-item>
            <template v-slot:prepend>
              <v-icon color="primary" size="large">mdi-folder</v-icon>
            </template>
            <v-list-item-title class="font-weight-medium">{{ library.name }}</v-list-item-title>
            <v-list-item-subtitle>{{ library.path }}</v-list-item-subtitle>
            <template v-slot:append>
              <v-chip :color="library.status === 'OK' ? 'success' : 'error'" size="small">
                {{ library.status }}
              </v-chip>
              <v-btn
                icon
                variant="text"
                color="info"
                size="small"
                :loading="scanning"
                @click="scanLibrary(library.id)"
              >
                <v-icon>mdi-database-refresh</v-icon>
                <v-tooltip activator="parent" location="top">重新获取元数据</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="success"
                size="small"
                :loading="scanning"
                @click="rematchLibrary(library.id)"
              >
                <v-icon>mdi-sync</v-icon>
                <v-tooltip activator="parent" location="top">重新匹配弹幕</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="warning"
                size="small"
                @click="toggleProgressDisplay(library.id)"
              >
                <v-icon>{{ showProgress[library.id] ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
                <v-tooltip activator="parent" location="top">{{ showProgress[library.id] ? '隐藏' : '显示' }}进度</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="error"
                size="small"
                @click="deleteLibrary(library.id)"
              >
                <v-icon>mdi-delete</v-icon>
                <v-tooltip activator="parent" location="top">删除媒体库</v-tooltip>
              </v-btn>
            </template>
          </v-list-item>

          <!-- 进度显示区域 -->
          <v-expand-transition>
            <div v-if="showProgress[library.id]">
              <v-divider />
              <v-card variant="flat" class="ma-2 pa-4" color="surface">
                <!-- 元数据扫描进度 -->
                <div v-if="progressData[library.id] && progressData[library.id].metadata" class="mb-6">
                  <div class="d-flex justify-space-between align-center mb-2">
                    <span class="font-weight-medium">
                      <v-icon small>mdi-database-refresh</v-icon>
                      元数据扫描进度
                    </span>
                    <span class="text-caption text-grey">
                      {{ progressData[library.id].metadata.metadataFetched || 0 }} / {{ progressData[library.id].metadata.totalFiles || 0 }}
                    </span>
                  </div>
                  <v-progress-linear
                    :model-value="progressData[library.id].metadata.totalFiles > 0 ? Math.round((progressData[library.id].metadata.metadataFetched / progressData[library.id].metadata.totalFiles) * 100) : 0"
                    height="6"
                    color="info"
                    class="mb-2"
                  />
                  <div class="text-caption text-grey">
                    <div>队列待处理: {{ progressData[library.id].metadata.pendingMetadata || 0 }} · 活跃线程: {{ progressData[library.id].metadata.activeThreads || 0 }} / {{ progressData[library.id].metadata.maxPoolSize || 4 }}</div>
                    <div v-if="progressData[library.id].metadata.totalSubmitted > 0">提交任务: {{ progressData[library.id].metadata.totalSubmitted }} · 已处理: {{ progressData[library.id].metadata.totalProcessed }} · 失败: {{ progressData[library.id].metadata.failedTasks || 0 }}</div>
                  </div>
                </div>
                <div v-else class="mb-6 text-center text-grey">
                  <v-icon small>mdi-clock-outline</v-icon>
                  等待元数据扫描数据...
                </div>

                <!-- 弹幕匹配进度 -->
                <div v-if="progressData[library.id] && progressData[library.id].match" class="mb-6">
                  <div class="d-flex justify-space-between align-center mb-2">
                    <span class="font-weight-medium">
                      <v-icon small>mdi-sync</v-icon>
                      弹幕匹配进度
                    </span>
                    <span class="text-caption text-grey">
                      {{ getLibraryMatchProcessedCount(library.id) }} / {{ progressData[library.id].match.totalFiles || 0 }}
                    </span>
                  </div>
                  <v-progress-linear
                    :model-value="progressData[library.id].match.totalFiles > 0 ? Math.round((getLibraryMatchProcessedCount(library.id) / progressData[library.id].match.totalFiles) * 100) : 0"
                    height="6"
                    color="success"
                    class="mb-2"
                  />
                  <div class="text-caption text-grey">
                    <div>队列待处理: {{ progressData[library.id].match.queuePending || 0 }} · 总待匹配: {{ progressData[library.id].match.pendingMatch || 0 }} · 活跃批次: {{ progressData[library.id].match.activeBatches || 0 }}</div>
                    <div v-if="progressData[library.id].match.totalEnqueued > 0">当前已处理: {{ getLibraryMatchProcessedCount(library.id) }} · 当前已匹配: {{ progressData[library.id].match.matched || 0 }} · 当前无匹配: {{ progressData[library.id].match.noMatch || 0 }} · 累计入队: {{ progressData[library.id].match.totalEnqueued }} · 累计匹配: {{ progressData[library.id].match.totalMatched }} · 累计无匹配: {{ progressData[library.id].match.totalNoMatch || 0 }} · 失败: {{ progressData[library.id].match.failedTasks || 0 }}</div>
                  </div>
                </div>
                <div v-else class="text-center text-grey">
                  <v-icon small>mdi-clock-outline</v-icon>
                  等待弹幕匹配数据...
                </div>

                <v-divider class="my-4" />
                
                <div class="text-center">
                  <v-btn 
                    size="small" 
                    variant="text" 
                    color="primary"
                    @click="showProgress[library.id] = false"
                  >
                    收起
                  </v-btn>
                </div>
              </v-card>
            </div>
          </v-expand-transition>
        </template>
      </v-list>
    </v-card>

    <v-progress-linear v-else indeterminate color="primary" />

    <!-- 添加媒体库对话框 -->
    <v-dialog v-model="dialog" max-width="700">
      <v-card>
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">添加媒体库</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="closeDialog" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
            {{ errorMessage }}
          </v-alert>

          <v-form>
            <v-text-field
              v-model="newLibrary.name"
              label="媒体库名称"
              prepend-inner-icon="mdi-label"
              variant="outlined"
              color="primary"
              required
              class="mb-4"
            />

            <div class="mb-4">
              <v-text-field
                v-model="newLibrary.path"
                label="媒体库路径"
                prepend-inner-icon="mdi-folder-open"
                variant="outlined"
                color="primary"
                required
                readonly
                @click="togglePathTree"
                :append-inner-icon="showPathTree ? 'mdi-menu-up' : 'mdi-menu-down'"
              />

              <v-card v-if="showPathTree" variant="outlined" class="mt-2 pa-2" max-height="300" style="overflow-y: auto">
                <v-treeview
                  :items="pathTree"
                  item-value="id"
                  :load-children="handleNodeSelect"
                  activatable
                  density="compact"
                  @update:activated="onPathSelect"
                >
                  <template v-slot:prepend="{ item }">
                    <v-icon>mdi-folder</v-icon>
                  </template>
                </v-treeview>
                <v-progress-linear v-if="loadingPaths" indeterminate />
              </v-card>
            </div>
          </v-form>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn
            color="grey"
            variant="text"
            @click="closeDialog"
          >
            取消
          </v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            :loading="loading"
            :disabled="loading"
            @click="addLibrary"
          >
            添加
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
