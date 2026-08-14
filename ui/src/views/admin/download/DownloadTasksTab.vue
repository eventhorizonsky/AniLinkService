<script setup>
import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue'
import { showAppMessage, askAppConfirm } from '../../../utils/ui-feedback'
import DownloadTaskTable from '../../../components/admin/download/DownloadTaskTable.vue'
import { formatSpeed } from '../../../utils/format'
import { ACTIVE_TASK_STATUSES } from '../../../utils/taskStatus'
import { getDownloadTasks, cancelDownloadTask, deleteDownloadTask } from '../../../api/download'

const emit = defineEmits(['stats-loaded'])

const props = defineProps({
  stats: {
    type: Object,
    default: null
  },
  liveTasks: {
    type: Array,
    default: () => []
  },
  sseConnected: {
    type: Boolean,
    default: false
  },
  sseReconnectAttempts: {
    type: Number,
    default: 0
  },
  actions: {
    type: Object,
    default: () => ({})
  }
})

const tasks = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const selection = ref([])
const deletingTarget = ref(null)

const filterStatus = ref('all')
const filterKeyword = ref('')
const actionLoadingTaskId = ref(null)

let refreshTimer = null

const fetchTasks = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filterStatus.value !== 'all') params.status = filterStatus.value
    if (filterKeyword.value.trim()) params.keyword = filterKeyword.value.trim()
    const res = await getDownloadTasks(params)
    const data = res?.data || {}
    tasks.value = data.items || []
    total.value = data.total || 0
    emit('stats-loaded', data.stats || null)
  } catch (error) {
    console.error('获取下载任务失败:', error)
    showAppMessage(error.response?.data?.msg || '获取下载任务失败', 'error')
  } finally {
    loading.value = false
  }
}

const scheduleRefresh = () => {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  refreshTimer = setTimeout(() => {
    fetchTasks()
  }, 400)
}

const statCards = computed(() => [
  { label: '活跃任务', value: props.stats?.active ?? '-', icon: 'mdi-progress-download', color: 'primary' },
  { label: '下载中', value: props.stats?.running ?? '-', icon: 'mdi-download', color: 'teal' },
  { label: '做种中', value: props.stats?.seeding ?? '-', icon: 'mdi-upload-network', color: 'purple' },
  { label: '下载速度', value: formatSpeed(props.stats?.downloadBps), icon: 'mdi-arrow-down-bold', color: 'blue' },
  { label: '上传速度', value: formatSpeed(props.stats?.uploadBps), icon: 'mdi-arrow-up-bold', color: 'indigo' },
  { label: '今日完成', value: props.stats?.todayCompleted ?? '-', icon: 'mdi-check-circle-outline', color: 'success' },
  { label: '停滞', value: props.stats?.stalled ?? '-', icon: 'mdi-pause-circle-outline', color: 'orange' },
  { label: '今日失败', value: props.stats?.todayFailed ?? '-', icon: 'mdi-alert-circle-outline', color: 'error' },
  { label: '今日取消', value: props.stats?.todayCancelled ?? '-', icon: 'mdi-cancel', color: 'warning' }
])

const matchesFilter = (task) => {
  if (filterStatus.value === 'all') return true
  if (filterStatus.value === 'active') return ACTIVE_TASK_STATUSES.includes(task.status)
  return task.status === filterStatus.value
}

const matchesKeyword = (task) => {
  const kw = filterKeyword.value.trim().toLowerCase()
  if (!kw) return true
  return [task.title, task.libraryName, task.subgroupName, task.typeName]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
    .includes(kw)
}

// SSE 直接驱动:把实时推送的任务按 id 合并进当前页,仅结构性变化(新任务/删除/状态移出筛选)时才补一次 HTTP 请求
watch(
  () => props.liveTasks,
  (live) => {
    if (!Array.isArray(live)) return

    if (live.length === 0) {
      if (tasks.value.length > 0 && filterStatus.value === 'all' && !filterKeyword.value.trim()) {
        scheduleRefresh()
      }
      return
    }

    const liveById = new Map(live.map((t) => [t.id, t]))
    let removedFromFilter = false
    tasks.value.forEach((row) => {
      const liveRow = liveById.get(row.id)
      if (liveRow) {
        Object.assign(row, liveRow)
        if (!removedFromFilter && !matchesFilter(row)) {
          removedFromFilter = true
        }
      }
    })

    if (removedFromFilter) {
      scheduleRefresh()
      return
    }

    // 当前视图中的行已被删除(SSE 中已不存在)
    const liveAllIds = new Set(live.map((t) => t.id))
    if (tasks.value.some((row) => !liveAllIds.has(row.id))) {
      scheduleRefresh()
      return
    }

    // 无任何筛选且在第 1 页时,若 SSE 顶部出现本页没有的新任务,补一次请求
    if (filterStatus.value === 'all' && !filterKeyword.value.trim() && page.value === 1) {
      const topIds = new Set(live.slice(0, size.value).map((t) => t.id))
      const visibleIds = new Set(tasks.value.map((t) => t.id))
      if ([...topIds].some((id) => !visibleIds.has(id))) {
        scheduleRefresh()
      }
    }
  },
  { deep: false }
)

const withActionLoading = async (task, fn) => {
  actionLoadingTaskId.value = task.id
  try {
    return await fn()
  } finally {
    actionLoadingTaskId.value = null
  }
}

const handleReconnect = () => {
  props.actions.reconnect?.()
  fetchTasks()
}

const handleCancel = async (task) => {
  const ok = await withActionLoading(task, () => props.actions.cancelTask?.(task))
  if (ok) scheduleRefresh()
}

const handleRetry = async (task) => {
  const ok = await withActionLoading(task, () => props.actions.retryTask?.(task))
  if (ok) scheduleRefresh()
}

const openDeleteDialog = (task) => {
  deletingTarget.value = task
}

const confirmDelete = async () => {
  const task = deletingTarget.value
  if (!task) return
  const ok = await props.actions.deleteTask?.(task)
  if (ok) {
    selection.value = selection.value.filter((id) => id !== task.id)
    scheduleRefresh()
  }
  deletingTarget.value = null
}

const onFilterChange = ({ status, keyword }) => {
  filterStatus.value = status
  filterKeyword.value = keyword
  page.value = 1
  selection.value = []
  fetchTasks()
}

const onPageChange = (value) => {
  page.value = value
  fetchTasks()
}

const onSizeChange = (value) => {
  size.value = value
  page.value = 1
  fetchTasks()
}

const handleBatchCancel = async (ids) => {
  const confirmed = await askAppConfirm({
    title: '批量取消',
    message: `确认取消选中的 ${ids.length} 个下载任务吗？`,
    color: 'warning'
  })
  if (!confirmed) return
  for (const id of ids) {
    try {
      await cancelDownloadTask(id)
    } catch (error) {
      console.error('批量取消失败:', error)
    }
  }
  showAppMessage(`已提交 ${ids.length} 个任务的取消请求`, 'success')
  selection.value = []
  scheduleRefresh()
}

const handleBatchDelete = async (ids) => {
  const confirmed = await askAppConfirm({
    title: '批量删除',
    message: `确认删除选中的 ${ids.length} 个下载任务吗？对应暂存文件将一并清理，媒体库文件不受影响`,
    color: 'error'
  })
  if (!confirmed) return
  let failed = 0
  for (const id of ids) {
    try {
      await deleteDownloadTask(id)
    } catch (error) {
      failed += 1
      console.error('批量删除失败:', error)
    }
  }
  showAppMessage(failed === 0 ? `已删除 ${ids.length} 个任务` : `删除完成，${failed} 个失败`, failed === 0 ? 'success' : 'error')
  selection.value = []
  scheduleRefresh()
}

onMounted(() => {
  fetchTasks()
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
})
</script>

<template>
  <div>
    <div class="stats-grid mb-4">
      <v-card
        v-for="card in statCards"
        :key="card.label"
        :color="card.color"
        variant="tonal"
        class="stats-card"
      >
        <v-card-text class="d-flex align-center ga-3">
          <v-avatar :color="card.color" variant="tonal" size="44" rounded>
            <v-icon :color="card.color">{{ card.icon }}</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold">{{ card.value }}</div>
            <div class="text-caption text-medium-emphasis">{{ card.label }}</div>
          </div>
        </v-card-text>
      </v-card>
    </div>

    <DownloadTaskTable
      :tasks="tasks"
      :total="total"
      :page="page"
      :size="size"
      :loading="loading"
      :stats="props.stats"
      v-model:selection="selection"
      :action-loading-task-id="actionLoadingTaskId"
      :sse-connected="sseConnected"
      :sse-reconnect-attempts="sseReconnectAttempts"
      @filter-change="onFilterChange"
      @update:page="onPageChange"
      @update:size="onSizeChange"
      @reconnect="handleReconnect"
      @cancel="handleCancel"
      @retry="handleRetry"
      @delete="openDeleteDialog"
      @batch-cancel="handleBatchCancel"
      @batch-delete="handleBatchDelete"
      @binding="(taskId) => props.actions.openBinding?.(taskId)"
    />

    <v-dialog
      :model-value="!!deletingTarget"
      max-width="480"
      @update:model-value="(value) => { if (!value) deletingTarget = null }"
    >
      <v-card v-if="deletingTarget">
        <v-card-title>
          <v-icon start color="error">mdi-delete-alert-outline</v-icon>
          删除下载任务
        </v-card-title>
        <v-card-text>
          <div class="mb-2">确认删除任务「{{ deletingTarget.title }}」？</div>
          <div class="text-body-2 text-medium-emphasis">对应暂存文件将一并清理，媒体库中已入库的文件不受影响。</div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="deletingTarget = null">取消</v-btn>
          <v-btn color="error" @click="confirmDelete">
            <v-icon start size="small">mdi-delete</v-icon>
            删除
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.stats-card :deep(.v-card-text) {
  padding: 14px 16px;
}
</style>
