<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const tasks = ref([])
const triggeringId = ref('')
const togglingId = ref('')
const isMobile = ref(false)
let pollTimer = null

const TYPE_LABELS = {
  cron: '定时',
  fixedDelay: '固定间隔'
}

const STATUS_META = {
  never: { label: '未执行', color: 'grey' },
  running: { label: '执行中', color: 'primary' },
  success: { label: '成功', color: 'success' },
  error: { label: '失败', color: 'error' }
}

const anyRunning = computed(() => tasks.value.some(t => t.running))

const checkViewport = () => {
  isMobile.value = typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(max-width: 768px)').matches
}

// 展示调度方式（尽量用用户能理解的说法）
const scheduleText = (t) => {
  if (t.type === 'cron') return t.cron || '—'
  if (t.type === 'fixedDelay') {
    const sec = (t.fixedDelayMillis || 0) / 1000
    return sec >= 60 ? `每 ${Math.round(sec / 60)} 分钟` : `每 ${sec} 秒`
  }
  return '—'
}

// 格式化耗时
const formatDuration = (ms) => {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

const statusMeta = (status) => STATUS_META[status] || STATUS_META.never

const fetchTasks = async () => {
  try {
    const res = await axios.get(`${API_BASE}/admin/scheduled-tasks`)
    if (res.data?.code === 200) {
      tasks.value = res.data.data || []
    }
  } catch (error) {
    console.error('获取定时任务列表失败:', error)
    showAppMessage('获取定时任务列表失败', 'error')
  } finally {
    loading.value = false
  }
}

const triggerTask = async (task) => {
  triggeringId.value = task.id
  try {
    const res = await axios.post(`${API_BASE}/admin/scheduled-tasks/${task.id}/trigger`)
    if (res.data?.code === 200) {
      showAppMessage(`已触发任务「${task.name}」`, 'success')
      await fetchTasks()
    } else {
      showAppMessage(res.data?.msg || '触发失败', 'error')
    }
  } catch (error) {
    console.error('触发定时任务失败:', error)
    showAppMessage('触发定时任务失败', 'error')
  } finally {
    triggeringId.value = ''
  }
}

// 切换任务启停状态
const toggleTask = async (task, enabled) => {
  togglingId.value = task.id
  try {
    const res = await axios.put(`${API_BASE}/admin/scheduled-tasks/${task.id}/enabled?enabled=${enabled}`)
    if (res.data?.code === 200) {
      showAppMessage(`已${enabled ? '开启' : '关闭'}任务「${task.name}」`, 'success')
      await fetchTasks()
    } else {
      showAppMessage(res.data?.msg || '操作失败', 'error')
      await fetchTasks()
    }
  } catch (error) {
    console.error('切换定时任务状态失败:', error)
    showAppMessage('切换定时任务状态失败', 'error')
    await fetchTasks()
  } finally {
    togglingId.value = ''
  }
}

// 有任务在执行时，轮询刷新状态
const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(() => {
    if (anyRunning.value) {
      fetchTasks()
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  fetchTasks()
  startPolling()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
  stopPolling()
})
</script>

<template>
  <div>
    <!-- 加载状态 -->
    <v-card v-if="loading && tasks.length === 0" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1">加载中...</p>
    </v-card>

    <template v-else>
      <!-- 提示 -->
      <v-alert
        v-if="anyRunning"
        type="info"
        variant="tonal"
        density="compact"
        class="mb-4"
      >
        <v-progress-circular size="16" indeterminate class="mr-2" />
        有定时任务正在执行中，列表将自动刷新...
      </v-alert>

      <!-- ===== 桌面端：表格 ===== -->
      <v-card v-if="!isMobile" border>
        <v-card-title class="text-body-1 font-weight-bold">
          <v-icon start color="primary">mdi-timer-sand</v-icon>
          定时任务
        </v-card-title>

        <v-table density="comfortable" hover>
          <thead>
            <tr>
              <th class="text-left" style="width: 170px">任务</th>
              <th class="text-left">说明</th>
              <th class="text-center" style="width: 90px">自动执行</th>
              <th class="text-left" style="width: 120px">上次执行</th>
              <th class="text-left" style="width: 90px">耗时</th>
              <th class="text-left" style="width: 90px">状态</th>
              <th class="text-left" style="width: 150px">下次执行</th>
              <th class="text-center" style="width: 110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in tasks" :key="task.id">
              <td>
                <div class="font-weight-medium">{{ task.name }}</div>
                <div class="text-caption text-medium-emphasis">{{ task.id }}</div>
              </td>
              <td>
                <div class="text-body-2">{{ task.description }}</div>
                <div class="text-caption text-medium-emphasis mt-1">
                  <v-chip size="x-small" variant="tonal" color="info" class="mr-1">
                    {{ TYPE_LABELS[task.type] || task.type }}
                  </v-chip>
                  {{ scheduleText(task) }}
                </div>
                <div v-if="task.lastResult && task.lastStatus === 'error'" class="text-caption text-error mt-1">
                  {{ task.lastResult }}
                </div>
              </td>
              <td class="text-center">
                <v-switch
                  :model-value="task.enabled"
                  :disabled="togglingId !== ''"
                  :loading="togglingId === task.id"
                  color="primary"
                  hide-details
                  density="compact"
                  @update:model-value="(val) => toggleTask(task, val)"
                />
              </td>
              <td class="text-body-2">{{ task.lastTriggeredAt || '—' }}</td>
              <td class="text-body-2">{{ formatDuration(task.lastDurationMs) }}</td>
              <td>
                <v-chip
                  size="x-small"
                  :color="statusMeta(task.lastStatus).color"
                  variant="tonal"
                  :loading="task.running"
                >
                  {{ task.running ? '执行中' : statusMeta(task.lastStatus).label }}
                </v-chip>
              </td>
              <td class="text-body-2">{{ task.nextRunAt || '—' }}</td>
              <td class="text-center">
                <v-btn
                  size="small"
                  color="primary"
                  variant="tonal"
                  :loading="triggeringId === task.id"
                  :disabled="task.running || triggeringId !== ''"
                  @click="triggerTask(task)"
                >
                  <v-icon start size="small">mdi-play</v-icon>
                  立即执行
                </v-btn>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card>

      <!-- ===== 移动端：卡片 ===== -->
      <template v-else>
        <div class="d-flex align-center mb-3">
          <div class="text-body-1 font-weight-bold">
            <v-icon start color="primary">mdi-timer-sand</v-icon>
            定时任务
          </div>
        </div>

        <v-card
          v-for="task in tasks"
          :key="task.id"
          class="mb-3"
          border
        >
          <v-card-item>
            <template #title>
              <div class="d-flex align-center justify-space-between ga-2">
                <div class="d-flex align-center ga-2" style="min-width: 0">
                  <v-chip
                    size="x-small"
                    :color="statusMeta(task.lastStatus).color"
                    variant="tonal"
                    :loading="task.running"
                    class="flex-shrink-0"
                  >
                    {{ task.running ? '执行中' : statusMeta(task.lastStatus).label }}
                  </v-chip>
                  <span class="text-subtitle-2 font-weight-bold text-truncate">{{ task.name }}</span>
                </div>
                <v-switch
                  :model-value="task.enabled"
                  :disabled="togglingId !== ''"
                  :loading="togglingId === task.id"
                  color="primary"
                  hide-details
                  density="compact"
                  class="flex-shrink-0"
                  @update:model-value="(val) => toggleTask(task, val)"
                />
              </div>
            </template>
            <template #subtitle>
              <div class="text-caption text-medium-emphasis">{{ task.id }}</div>
            </template>
          </v-card-item>

          <v-card-text class="pt-0">
            <p class="text-body-2 mb-3">{{ task.description }}</p>

            <div class="d-flex flex-wrap text-body-2">
              <div class="w-50 py-1 pr-2">
                <div class="text-caption text-medium-emphasis">调度</div>
                <div>
                  <v-chip size="x-small" variant="tonal" color="info" class="mr-1">
                    {{ TYPE_LABELS[task.type] || task.type }}
                  </v-chip>
                  <span class="text-caption">{{ scheduleText(task) }}</span>
                </div>
              </div>
              <div class="w-50 py-1">
                <div class="text-caption text-medium-emphasis">上次执行</div>
                <div class="text-body-2">{{ task.lastTriggeredAt || '—' }}</div>
              </div>
              <div class="w-50 py-1 pr-2">
                <div class="text-caption text-medium-emphasis">耗时</div>
                <div class="text-body-2">{{ formatDuration(task.lastDurationMs) }}</div>
              </div>
              <div class="w-50 py-1">
                <div class="text-caption text-medium-emphasis">下次执行</div>
                <div class="text-body-2">{{ task.nextRunAt || '—' }}</div>
              </div>
            </div>

            <div v-if="task.lastResult && task.lastStatus === 'error'" class="text-caption text-error mt-2">
              <v-icon size="small" start>mdi-alert-circle</v-icon>
              {{ task.lastResult }}
            </div>

            <v-btn
              block
              color="primary"
              variant="tonal"
              class="mt-3"
              :loading="triggeringId === task.id"
              :disabled="task.running || triggeringId !== ''"
              @click="triggerTask(task)"
            >
              <v-icon start size="small">mdi-play</v-icon>
              立即执行
            </v-btn>
          </v-card-text>
        </v-card>
      </template>

      <!-- 空状态 -->
      <v-card v-if="tasks.length === 0" class="text-center pa-8 mt-4" border>
        <v-icon size="64" color="medium-emphasis">mdi-timer-off</v-icon>
        <p class="mt-4 text-body-1 text-medium-emphasis">暂无定时任务</p>
      </v-card>

      <!-- 浮动刷新按钮 -->
      <v-btn
        class="floating-refresh"
        icon="mdi-refresh"
        color="primary"
        elevation="4"
        size="large"
        @click="fetchTasks"
      />
    </template>
  </div>
</template>

<style scoped>
.floating-refresh {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 100;
}
</style>
