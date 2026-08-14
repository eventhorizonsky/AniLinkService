<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getMetadataProgress, getMatchProgress } from '../../../api/media'

const metadataProgress = ref(null)
const matchProgress = ref(null)
const pollingInterval = ref(null)
const lastUpdate = ref(null)
const isPolling = ref(true)

const getMatchProcessedCount = () => {
  if (!matchProgress.value) return 0
  return Number(matchProgress.value.matched || 0) + Number(matchProgress.value.noMatch || 0)
}

// 获取全局元数据进度
const fetchMetadataProgress = async () => {
  try {
    const res = await getMetadataProgress()
    if (res?.code === 200) {
      metadataProgress.value = res.data
      lastUpdate.value = new Date()
    }
  } catch (error) {
    console.error('获取元数据进度失败:', error)
  }
}

// 获取全局弹幕匹配进度
const fetchMatchProgress = async () => {
  try {
    const res = await getMatchProgress()
    if (res?.code === 200) {
      matchProgress.value = res.data
    }
  } catch (error) {
    console.error('获取匹配进度失败:', error)
  }
}

// 启动轮询
const startPolling = () => {
  // 立刻获取一次
  fetchMetadataProgress()
  fetchMatchProgress()
  
  // 每5秒轮询一次
  pollingInterval.value = setInterval(() => {
    if (isPolling.value) {
      fetchMetadataProgress()
      fetchMatchProgress()
    }
  }, 5000)
}

// 切换轮询
const togglePolling = () => {
  isPolling.value = !isPolling.value
}

// 手动刷新
const manualRefresh = async () => {
  await Promise.all([fetchMetadataProgress(), fetchMatchProgress()])
}

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
  }
})
</script>

<template>
  <div>
    <!-- 操作栏 -->
    <v-card class="mb-4">
      <v-card-text class="d-flex gap-2 align-center">
        <span class="text-caption text-grey" v-if="lastUpdate">
          最后更新: {{ lastUpdate.toLocaleTimeString() }}
        </span>
        <v-spacer />
        <v-btn
          size="small"
          variant="text"
          :icon="isPolling ? 'mdi-pause' : 'mdi-play'"
          @click="togglePolling"
          :color="isPolling ? 'warning' : 'success'"
        >
          <v-icon>{{ isPolling ? 'mdi-pause' : 'mdi-play' }}</v-icon>
          <v-tooltip activator="parent" location="top">{{ isPolling ? '暂停轮询' : '启动轮询' }}</v-tooltip>
        </v-btn>
        <v-btn
          size="small"
          variant="text"
          icon="mdi-refresh"
          @click="manualRefresh"
          color="primary"
        >
          <v-icon>mdi-refresh</v-icon>
          <v-tooltip activator="parent" location="top">手动刷新</v-tooltip>
        </v-btn>
      </v-card-text>
    </v-card>

    <v-row dense>
      <v-col cols="12" md="6">
      <!-- 元数据进度卡片 -->
      <v-card class="h-100">
        <v-card-title class="d-flex align-center gap-2 pb-2">
          <v-icon color="info">mdi-database-refresh</v-icon>
          元数据扫描
        </v-card-title>

        <v-card-text>
          <div v-if="metadataProgress" class="space-y-4">
            <!-- 进度条 -->
            <div>
              <div class="d-flex justify-space-between align-center mb-2">
                <span class="text-body-2">扫描进度</span>
                <span class="text-caption font-weight-medium">
                  {{ metadataProgress.metadataFetched || 0 }} / {{ metadataProgress.totalFiles || 0 }}
                </span>
              </div>
              <v-progress-linear
                :model-value="metadataProgress.totalFiles ? Math.round((metadataProgress.metadataFetched / metadataProgress.totalFiles) * 100) : 0"
                height="8"
                color="info"
              />
            </div>

            <!-- 统计信息卡片 -->
            <div class="pt-2">
              <div class="grid gap-2" style="display: grid; grid-template-columns: repeat(2, 1fr);">
                <div class="pa-2 rounded" style="background-color: rgba(33, 150, 243, 0.1);">
                  <div class="text-caption text-grey">待处理</div>
                  <div class="text-h6 font-weight-bold text-info">{{ metadataProgress.pendingMetadata || 0 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(76, 175, 80, 0.1);">
                  <div class="text-caption text-grey">活跃线程</div>
                  <div class="text-h6 font-weight-bold text-success">{{ metadataProgress.activeThreads || 0 }} / {{ metadataProgress.maxPoolSize || 4 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(76, 175, 80, 0.1);">
                  <div class="text-caption text-grey">已处理</div>
                  <div class="text-h6 font-weight-bold text-success">{{ metadataProgress.totalProcessed || 0 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(244, 67, 54, 0.1);">
                  <div class="text-caption text-grey">失败任务</div>
                  <div class="text-h6 font-weight-bold text-error">{{ metadataProgress.failedTasks || 0 }}</div>
                </div>
              </div>
            </div>

            <!-- 详细信息 -->
            <div class="pt-2 text-caption text-grey space-y-1" style="border-top: 1px solid #eee;">
              <div>总提交: {{ metadataProgress.totalSubmitted || 0 }}</div>
              <div>线程池配置: {{ metadataProgress.maxPoolSize || 4 }} 核心线程</div>
            </div>
          </div>
          <div v-else class="text-center py-4 text-grey">
            <v-icon size="small">mdi-clock-outline</v-icon>
            <span class="ml-2">加载中...</span>
          </div>
        </v-card-text>
      </v-card>
      </v-col>

      <v-col cols="12" md="6">
      <!-- 弹幕匹配进度卡片 -->
      <v-card class="h-100">
        <v-card-title class="d-flex align-center gap-2 pb-2">
          <v-icon color="success">mdi-sync</v-icon>
          弹幕匹配
        </v-card-title>

        <v-card-text>
          <div v-if="matchProgress" class="space-y-4">
            <!-- 进度条 -->
            <div>
              <div class="d-flex justify-space-between align-center mb-2">
                <span class="text-body-2">匹配进度</span>
                <span class="text-caption font-weight-medium">
                  {{ getMatchProcessedCount() }} / {{ matchProgress.totalFiles || 0 }}
                </span>
              </div>
              <v-progress-linear
                :model-value="matchProgress.totalFiles ? Math.round((getMatchProcessedCount() / matchProgress.totalFiles) * 100) : 0"
                height="8"
                color="success"
              />
            </div>

            <!-- 统计信息卡片 -->
            <div class="pt-2">
              <div class="grid gap-2" style="display: grid; grid-template-columns: repeat(2, 1fr);">
                <div class="pa-2 rounded" style="background-color: rgba(33, 150, 243, 0.1);">
                  <div class="text-caption text-grey">队列待处理</div>
                  <div class="text-h6 font-weight-bold text-info">{{ matchProgress.queuePending || 0 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(76, 175, 80, 0.1);">
                  <div class="text-caption text-grey">已匹配</div>
                  <div class="text-h6 font-weight-bold text-success">{{ matchProgress.matched || 0 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(255, 193, 7, 0.1);">
                  <div class="text-caption text-grey">无匹配</div>
                  <div class="text-h6 font-weight-bold text-warning">{{ matchProgress.noMatch || 0 }}</div>
                </div>
                <div class="pa-2 rounded" style="background-color: rgba(244, 67, 54, 0.1);">
                  <div class="text-caption text-grey">失败任务</div>
                  <div class="text-h6 font-weight-bold text-error">{{ matchProgress.failedTasks || 0 }}</div>
                </div>
              </div>
            </div>

            <!-- 详细信息 -->
            <div class="pt-2 text-caption text-grey space-y-1" style="border-top: 1px solid #eee;">
              <div>总待匹配: {{ matchProgress.pendingMatch || 0 }}</div>
              <div>累计入队: {{ matchProgress.totalEnqueued || 0 }} · 累计处理: {{ matchProgress.totalProcessed || 0 }} · 累计匹配: {{ matchProgress.totalMatched || 0 }} · 累计无匹配: {{ matchProgress.totalNoMatch || 0 }}</div>
              <div>活跃批次: {{ matchProgress.activeBatches || 0 }} | 批次大小: {{ matchProgress.batchSize || 20 }} | 间隔: {{ matchProgress.queueIntervalSeconds || 30 }}s</div>
            </div>
          </div>
          <div v-else class="text-center py-4 text-grey">
            <v-icon size="small">mdi-clock-outline</v-icon>
            <span class="ml-2">加载中...</span>
          </div>
        </v-card-text>
      </v-card>
      </v-col>
    </v-row>

    <!-- 汇总信息 -->
    <v-card class="mt-4" v-if="metadataProgress || matchProgress">
      <v-card-title class="text-subtitle-2">队列汇总信息</v-card-title>
      <v-card-text class="text-caption text-grey">
        <div class="space-y-2">
          <div v-if="metadataProgress">
            元数据: 总文件 {{ metadataProgress.totalFiles || 0 }} | 已扫描 {{ metadataProgress.metadataFetched || 0 }} | 进度 {{ metadataProgress.totalFiles ? Math.round((metadataProgress.metadataFetched / metadataProgress.totalFiles) * 100) : 0 }}%
          </div>
          <div v-if="matchProgress">
            弹幕: 总文件 {{ matchProgress.totalFiles || 0 }} | 已处理 {{ getMatchProcessedCount() }} | 进度 {{ matchProgress.totalFiles ? Math.round((getMatchProcessedCount() / matchProgress.totalFiles) * 100) : 0 }}%
          </div>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.space-y-1 > div + div {
  margin-top: 0.25rem;
}

.space-y-4 > div + div {
  margin-top: 1rem;
}

.space-y-2 > div + div {
  margin-top: 0.5rem;
}
</style>
