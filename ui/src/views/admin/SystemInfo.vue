<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { askAppConfirm, showAppMessage } from '../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const rematching = ref(false)
const stats = ref(null)
const systemInfo = ref(null)
const updatedAt = ref('')

const fetchDashboard = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/system/dashboard`)
    if (res.data?.code === 200 && res.data?.data) {
      stats.value = res.data.data
    }
  } catch (error) {
    console.error('获取看板数据失败:', error)
    showAppMessage('获取看板数据失败', 'error')
  } finally {
    loading.value = false
  }
}

const fetchSystemInfo = async () => {
  try {
    const res = await axios.get(`${API_BASE}/system/info`)
    if (res.data?.data) {
      systemInfo.value = res.data.data
    }
  } catch (error) {
    console.error('获取系统信息失败:', error)
  }
}

const refreshAll = () => {
  fetchDashboard()
  fetchSystemInfo()
  updatedAt.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 一键触发 library-rematch 定时任务（重新识别媒体库中未能匹配的剧集文件）
const triggerRematch = async () => {
  const confirmed = await askAppConfirm({
    title: '触发重新识别',
    message: '将把媒体库中所有未匹配的剧集文件重新加入匹配队列，尝试自动匹配到对应番剧。是否继续？',
    confirmText: '立即执行',
    color: 'warning'
  })
  if (!confirmed) {
    return
  }

  rematching.value = true
  try {
    const res = await axios.post(`${API_BASE}/admin/scheduled-tasks/library-rematch/trigger`)
    if (res.data?.code === 200) {
      showAppMessage(res.data?.msg || '已触发重新识别任务', 'success')
      // 稍后刷新，等待任务更新统计
      setTimeout(() => fetchDashboard(), 3000)
    } else {
      showAppMessage(res.data?.msg || '触发失败', 'error')
    }
  } catch (error) {
    console.error('触发重新识别失败:', error)
    showAppMessage(error.response?.data?.msg || '触发重新识别失败', 'error')
  } finally {
    rematching.value = false
  }
}

const formatNumber = (value) => {
  if (value == null) return '0'
  return Number(value).toLocaleString()
}

// 将字节拆分为 { value, unit }，如 { 52.8, 'GB' }
const splitBytes = (bytes) => {
  if (bytes == null) return { value: '0', unit: 'B' }
  const size = Number(bytes)
  if (size < 1024) return { value: String(size), unit: 'B' }
  const units = ['KB', 'MB', 'GB', 'TB', 'PB']
  let v = size
  let u = 'B'
  for (const uu of units) {
    v = v / 1024
    u = uu
    if (v < 1024) break
  }
  return { value: v >= 100 ? v.toFixed(0) : v.toFixed(1), unit: u }
}

const formatUptime = (seconds) => {
  if (seconds == null) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  let text = ''
  if (days > 0) text += `${days} 天 `
  if (hours > 0 || days > 0) text += `${hours} 小时 `
  text += `${minutes} 分钟`
  return text
}

const formatUptimeShort = (seconds) => {
  if (seconds == null) return '-'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
}

const hasUnmatched = computed(() => Number(stats.value?.unmatchedCount) > 0)

const matchRate = computed(() => {
  const matched = Number(stats.value?.matchedCount) || 0
  const unmatched = Number(stats.value?.unmatchedCount) || 0
  const total = matched + unmatched
  return total > 0 ? Math.round((matched / total) * 100) : 0
})

const queueStatus = computed(() => Number(stats.value?.pendingMatchQueueCount) > 0 ? '排队中' : '空闲')

const todayText = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

// KPI 网格（Vuetify 语义色 + v-avatar 纯色图标块）
const kpiCards = computed(() => {
  const s = stats.value || {}
  const storage = splitBytes(s.mediaTotalSizeBytes)
  return [
    { label: '动漫总数', icon: 'mdi-movie-open', color: 'primary', value: formatNumber(s.animeCount), trend: `已匹配 ${formatNumber(s.matchedCount)} 文件` },
    { label: '媒体文件', icon: 'mdi-folder', color: 'info', value: formatNumber(s.mediaFileCount), trend: `匹配率 ${matchRate.value}%` },
    { label: '已匹配', icon: 'mdi-check-circle', color: 'success', value: formatNumber(s.matchedCount), trend: `未匹配 ${formatNumber(s.unmatchedCount)}` },
    { id: 'unmatched', label: '未匹配', icon: 'mdi-sync-alert', color: 'error', value: formatNumber(s.unmatchedCount) },
    { label: '字幕文件', icon: 'mdi-subtitles', color: 'purple', value: formatNumber(s.subtitleCount), trend: `弹幕 ${formatNumber(s.danmakuCount)}` },
    { label: 'RSS 订阅', icon: 'mdi-rss', color: 'orange', value: formatNumber(s.rssSubscriptionCount), trend: `活跃中 ${formatNumber(s.rssEnabledCount)}` },
    { label: '媒体库', icon: 'mdi-library-books', color: 'indigo', value: formatNumber(s.libraryCount), trend: `用户 ${formatNumber(s.userCount)}` },
    { label: '待匹配队列', icon: 'mdi-progress-clock', color: 'warning', value: formatNumber(s.pendingMatchQueueCount), trend: queueStatus.value },
    { label: '总大小', icon: 'mdi-database', color: 'pink', value: storage.value, unit: storage.unit, trend: `${formatNumber(s.mediaTotalSizeBytes)} B` }
  ]
})

// 系统信息徽标
const dbBadge = computed(() => {
  const type = systemInfo.value?.dbType
  const version = systemInfo.value?.dbVersion
  if (!type) return '数据库'
  return version ? `${type} · v${version}` : type
})

const memoryPercent = computed(() => {
  const total = Number(systemInfo.value?.totalMemoryMB) || 0
  const free = Number(systemInfo.value?.freeMemoryMB) || 0
  return total > 0 ? Math.min(100, (free / total) * 100) : 0
})

const osText = computed(() => {
  const name = systemInfo.value?.osName
  const version = systemInfo.value?.osVersion
  return name ? (version ? `${name} ${version}` : name) : '-'
})

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div class="dashboard-root">
    <div v-if="loading" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1">加载中...</p>
    </div>

    <template v-else>
      <!-- ═══ 顶栏 ═══ -->
      <v-card class="mb-4" border>
        <v-card-text class="d-flex align-center justify-space-between flex-wrap ga-3">
          <div class="d-flex align-center ga-3">
            <v-avatar color="primary" size="40" rounded>
              <v-icon color="white">mdi-view-dashboard</v-icon>
            </v-avatar>
            <div>
              <div class="text-subtitle-1 font-weight-bold">
                媒体库看板
                <span class="text-caption text-medium-emphasis">· AniLink</span>
              </div>
              <div class="text-caption text-medium-emphasis mt-1">{{ todayText }}</div>
            </div>
          </div>
          <div class="d-flex align-center ga-2 flex-wrap">
            <v-chip size="small" variant="tonal" prepend-icon="mdi-laptop">
              {{ systemInfo?.hostname || '-' }}
            </v-chip>
            <v-chip size="small" variant="tonal" prepend-icon="mdi-timer-outline">
              运行 {{ formatUptimeShort(systemInfo?.uptimeSeconds) }}
            </v-chip>
            <v-chip size="small" variant="tonal" prepend-icon="mdi-memory">
              {{ formatNumber(systemInfo?.freeMemoryMB) }} / {{ formatNumber(systemInfo?.totalMemoryMB) }} MB
            </v-chip>
          </div>
        </v-card-text>
      </v-card>

      <!-- ═══ KPI 网格 ═══ -->
      <v-row dense>
        <v-col
          v-for="card in kpiCards"
          :key="card.label"
          cols="6"
          sm="4"
          md="3"
          lg="2"
        >
          <v-card
            class="pa-4 h-100 d-flex flex-column"
            :class="card.id === 'unmatched' && hasUnmatched ? 'border-error' : ''"
            border
          >
            <div class="d-flex align-center ga-3">
              <v-avatar :color="card.color" size="40" rounded class="flex-shrink-0">
                <v-icon color="white" size="22">{{ card.icon }}</v-icon>
              </v-avatar>
              <div style="min-width: 0">
                <div class="text-h5 font-weight-bold metric-value">
                  {{ card.value }}<span v-if="card.unit" class="text-caption text-medium-emphasis"> {{ card.unit }}</span>
                </div>
                <div class="text-caption text-medium-emphasis mt-1">{{ card.label }}</div>
              </div>
            </div>

            <!-- 底部趋势 / 一键重新识别按钮（mt-auto 保证整行对齐） -->
            <div class="mt-auto pt-3">
              <v-btn
                v-if="card.id === 'unmatched'"
                size="small"
                color="primary"
                variant="tonal"
                block
                :loading="rematching"
                :disabled="!hasUnmatched"
                @click="triggerRematch"
              >
                <v-icon start size="small">mdi-sync</v-icon>
                一键重新识别
              </v-btn>
              <div v-else class="text-caption text-medium-emphasis">
                {{ card.trend }}
              </div>
            </div>
          </v-card>
        </v-col>
      </v-row>

      <!-- ═══ 系统信息 ═══ -->
      <div class="d-flex align-center flex-wrap ga-2 mt-4 mb-3">
        <span class="text-subtitle-2 font-weight-bold">系统信息</span>
        <v-chip size="small" color="primary" variant="tonal">{{ dbBadge }}</v-chip>
        <v-chip v-if="systemInfo?.liquibaseEnabled" size="small" color="success" variant="tonal">
          Liquibase ✓
        </v-chip>
      </div>

      <v-row dense>
        <v-col cols="12" sm="6" md="4">
          <v-card class="pa-3 h-100" border>
            <div class="d-flex align-center justify-space-between ga-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">数据库</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">
                {{ systemInfo?.dbType || '-' }}
                <v-chip v-if="systemInfo?.dbVersion" size="x-small" variant="tonal" class="ml-1">{{ systemInfo.dbVersion }}</v-chip>
              </span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">服务器 IP</span>
              <span class="text-body-2 font-weight-medium font-mono text-truncate" style="min-width: 0">{{ systemInfo?.serverIp || '-' }}</span>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" sm="6" md="4">
          <v-card class="pa-3 h-100" border>
            <div class="d-flex align-center justify-space-between ga-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">主机名</span>
              <span class="text-body-2 font-weight-medium text-truncate" style="min-width: 0">{{ systemInfo?.hostname || '-' }}</span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">操作系统</span>
              <span class="text-body-2 font-weight-medium text-truncate" style="min-width: 0">{{ osText }}</span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">架构</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">{{ systemInfo?.osArch || '-' }}</span>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" sm="6" md="4">
          <v-card class="pa-3 h-100" border>
            <div class="d-flex align-center justify-space-between ga-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">Java 运行时</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">
                {{ systemInfo?.javaVersion || '-' }}
                <v-chip v-if="systemInfo?.javaVendor" size="x-small" variant="tonal" class="ml-1">{{ systemInfo.javaVendor }}</v-chip>
              </span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">处理器核心</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">
                {{ systemInfo?.availableProcessors ?? '-' }}
                <v-chip size="x-small" variant="tonal" class="ml-1">可用</v-chip>
              </span>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" sm="6" md="4">
          <v-card class="pa-3 h-100" border>
            <div class="d-flex align-center justify-space-between ga-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">内存</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">
                {{ formatNumber(systemInfo?.freeMemoryMB) }}
                <span class="text-caption text-medium-emphasis"> / {{ formatNumber(systemInfo?.totalMemoryMB) }} MB</span>
              </span>
            </div>
            <v-progress-linear :model-value="memoryPercent" color="primary" height="5" rounded class="mt-2" />
            <div class="d-flex align-center justify-space-between ga-2 mt-1">
              <span class="text-caption text-medium-emphasis flex-shrink-0">最大内存</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">{{ formatNumber(systemInfo?.maxMemoryMB) }} MB</span>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" sm="6" md="4">
          <v-card class="pa-3 h-100" border>
            <div class="d-flex align-center justify-space-between ga-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">启动时间</span>
              <span class="text-body-2 font-weight-medium text-truncate" style="min-width: 0">{{ formatUptime(systemInfo?.uptimeSeconds) }}</span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">Liquibase</span>
              <span class="text-body-2 font-weight-medium" style="min-width: 0">
                <span class="text-success">● 已启用</span>
                <span class="text-caption text-medium-emphasis"> · {{ systemInfo?.liquibaseChangeSets ?? 0 }} 变更集</span>
              </span>
            </div>
            <div class="d-flex align-center justify-space-between ga-2 mt-2">
              <span class="text-caption text-medium-emphasis flex-shrink-0">最后执行</span>
              <span class="text-body-2 font-weight-medium font-mono text-truncate" style="min-width: 0">{{ systemInfo?.liquibaseLastExecuted || '-' }}</span>
            </div>
          </v-card>
        </v-col>
      </v-row>

      <!-- ═══ Liquibase 详情行 ═══ -->
      <v-card class="mt-3" border>
        <v-card-text class="d-flex flex-wrap align-center ga-4">
          <div class="d-flex align-center ga-2">
            <v-icon color="primary" size="20">mdi-database</v-icon>
            <span class="text-body-2 text-medium-emphasis">Liquibase</span>
            <span class="text-body-2 font-weight-medium text-success">● 已启用</span>
          </div>
          <div class="d-flex align-center ga-2">
            <v-icon color="primary" size="20">mdi-source-branch</v-icon>
            <span class="text-body-2 text-medium-emphasis">变更集</span>
            <span class="text-body-2 font-weight-medium">{{ systemInfo?.liquibaseChangeSets ?? 0 }}</span>
          </div>
          <div class="d-flex align-center ga-2">
            <v-icon color="primary" size="20">mdi-check-circle</v-icon>
            <span class="text-body-2 text-medium-emphasis">初始化</span>
            <span class="text-body-2 font-weight-medium text-success">✓ 已完成</span>
          </div>
          <div class="d-flex align-center ga-2">
            <v-icon color="primary" size="20">mdi-calendar-month</v-icon>
            <span class="text-body-2 text-medium-emphasis">最后执行</span>
            <span class="text-body-2 font-weight-medium font-mono">{{ systemInfo?.liquibaseLastExecuted || '-' }}</span>
          </div>
          <div class="d-flex align-center ga-1 ml-auto text-caption text-medium-emphasis">
            <v-icon size="18">mdi-information-outline</v-icon>
            所有系统就绪
          </div>
        </v-card-text>
      </v-card>

      <!-- ═══ 页脚 ═══ -->
      <footer class="d-flex justify-space-between flex-wrap ga-2 text-caption text-medium-emphasis mt-4 pt-3 page-footer">
        <span>媒体库看板 · AniLink</span>
        <span>数据更新于 {{ updatedAt }}</span>
      </footer>

      <!-- 浮动刷新按钮 -->
      <v-btn
        class="floating-refresh"
        icon="mdi-refresh"
        color="primary"
        elevation="4"
        size="large"
        :loading="loading"
        @click="refreshAll"
      />
    </template>
  </div>
</template>

<style scoped>
.dashboard-root {
  height: calc(100vh - 112px);
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.dashboard-root::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.floating-refresh {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 100;
}

.metric-value {
  font-variant-numeric: tabular-nums;
}

.font-mono {
  font-family: 'Roboto Mono', Consolas, monospace;
}

.border-error {
  border-color: rgba(var(--v-theme-error), 0.6) !important;
}

.page-footer {
  border-top: 1px solid rgba(0, 0, 0, 0.12);
}
</style>
