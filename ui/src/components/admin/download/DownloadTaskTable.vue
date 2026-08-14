<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { formatBytes, formatSpeed } from '../../../utils/format'

const props = defineProps({
  title: {
    type: String,
    default: '下载任务'
  },
  tasks: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  },
  page: {
    type: Number,
    default: 1
  },
  size: {
    type: Number,
    default: 20
  },
  loading: {
    type: Boolean,
    default: false
  },
  stats: {
    type: Object,
    default: null
  },
  selection: {
    type: Array,
    default: () => []
  },
  actionLoadingTaskId: {
    type: [String, Number, null],
    default: null
  },
  sseConnected: {
    type: Boolean,
    default: false
  },
  sseReconnectAttempts: {
    type: Number,
    default: 0
  },
  showRefreshButton: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits([
  'update:page',
  'update:size',
  'update:selection',
  'filter-change',
  'reconnect',
  'cancel',
  'retry',
  'delete',
  'batch-cancel',
  'batch-delete',
  'binding'
])

const STATUS_META = {
  PENDING: { label: '等待中', color: 'grey' },
  RUNNING: { label: '下载中', color: 'primary' },
  SEEDING: { label: '做种中', color: 'purple' },
  MOVING: { label: '迁移中', color: 'info' },
  SCANNING: { label: '扫描中', color: 'teal' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'warning' },
  FAILED: { label: '失败', color: 'error' },
  STALLED: { label: '停滞', color: 'orange' }
}

const ACTIVE_STATUSES = ['PENDING', 'RUNNING', 'SEEDING', 'MOVING', 'SCANNING']

const activeFilter = ref('all')
const keyword = ref('')
const detailDialog = ref(false)
const detailTask = ref(null)
const isMobile = ref(false)

const checkViewport = () => {
  isMobile.value =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(max-width: 768px)').matches
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
})

const formatStatus = (status) => STATUS_META[status]?.label || status || '-'
const statusColor = (status) => STATUS_META[status]?.color || 'grey'

const canCancel = (status) => ['PENDING', 'RUNNING', 'SEEDING', 'MOVING', 'SCANNING'].includes(status)
const canRetry = (status) => ['FAILED', 'CANCELLED', 'STALLED'].includes(status)
const canDelete = (status) => ['COMPLETED', 'FAILED', 'CANCELLED', 'STALLED'].includes(status)
const canViewBinding = (status) => ['COMPLETED', 'SEEDING'].includes(status)

const toNum = (v) => Number(v) || 0

const statusFilters = computed(() => {
  const s = props.stats || {}
  return [
    { key: 'all', label: '全部', count: toNum(s.active) + toNum(s.completed) + toNum(s.failed) + toNum(s.cancelled) + toNum(s.stalled) },
    { key: 'active', label: '进行中', count: toNum(s.active) },
    { key: 'COMPLETED', label: '已完成', count: toNum(s.completed) },
    { key: 'FAILED', label: '失败', count: toNum(s.failed) },
    { key: 'CANCELLED', label: '已取消', count: toNum(s.cancelled) },
    { key: 'STALLED', label: '停滞', count: toNum(s.stalled) }
  ]
})

const emitFilterChange = () => {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
  emit('filter-change', { status: activeFilter.value, keyword: (keyword.value || '').trim() })
}

let keywordTimer = null
// 关键词清空/删字/输入统一走这里防抖触发,避免点击清除时读到旧值或删字后无事件
watch(keyword, () => {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
  }
  keywordTimer = setTimeout(emitFilterChange, 300)
})

const allSelected = computed(() => {
  return props.tasks.length > 0 && props.tasks.every((task) => props.selection.includes(task.id))
})

const toggleAll = (checked) => {
  emit('update:selection', checked ? props.tasks.map((task) => task.id) : [])
}

const isSelected = (id) => props.selection.includes(id)
const toggleOne = (id) => {
  const next = isSelected(id) ? props.selection.filter((v) => v !== id) : [...props.selection, id]
  emit('update:selection', next)
}

const cancellableSelection = computed(() => {
  const selectedMap = new Set(props.selection)
  return props.tasks.filter((t) => selectedMap.has(t.id) && canCancel(t.status))
})

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const resolveTaskSpeed = (task) => ({
  down: (task.downloadSpeedText || '').replace(/[↓↑]/g, '').trim() || '-',
  up: (task.uploadSpeedText || '').replace(/[↓↑]/g, '').trim() || '-'
})

const openDetail = (task) => {
  detailTask.value = task
  detailDialog.value = true
}

const sizeOptions = [20, 50, 100]
</script>

<template>
  <v-card>
    <v-card-title class="d-flex align-center justify-space-between ga-3 title-row">
      <span>{{ props.title }}</span>
      <div class="d-flex align-center ga-2 status-chips">
        <v-chip size="small" color="primary" variant="tonal">
          <v-icon start size="small">mdi-arrow-down</v-icon>
          下载 {{ formatSpeed(props.stats?.downloadBps) }}
        </v-chip>
        <v-chip size="small" color="info" variant="tonal">
          <v-icon start size="small">mdi-arrow-up</v-icon>
          上传 {{ formatSpeed(props.stats?.uploadBps) }}
        </v-chip>
        <v-tooltip :text="props.sseConnected ? '重新建立实时推送连接' : '实时推送已断开，点击重连'" location="bottom">
          <template #activator="{ props: tooltipProps }">
            <v-btn
              v-bind="tooltipProps"
              v-if="props.showRefreshButton"
              size="small"
              :variant="props.sseConnected ? 'outlined' : 'flat'"
              :color="props.sseConnected ? 'default' : 'warning'"
              :loading="props.loading"
              @click="emit('reconnect')"
            >
              <v-icon start size="small">{{ props.sseConnected ? 'mdi-wifi' : 'mdi-wifi-off' }}</v-icon>
              重连
            </v-btn>
          </template>
        </v-tooltip>
        <v-tooltip
          :text="props.sseReconnectAttempts > 0 ? `实时推送已断开，正在自动重连（第 ${props.sseReconnectAttempts} 次）` : (props.sseConnected ? '实时推送已连接' : '实时推送断开')"
          location="bottom"
        >
          <template #activator="{ props: tooltipProps }">
            <v-chip v-bind="tooltipProps" :color="props.sseConnected ? 'success' : 'warning'" size="small" variant="tonal">
              <v-icon start size="small">mdi-wifi</v-icon>
              {{ props.sseConnected ? '实时' : (props.sseReconnectAttempts > 0 ? '重连中' : '未连接') }}
            </v-chip>
          </template>
        </v-tooltip>
      </div>
    </v-card-title>

    <v-card-text>
      <div class="d-flex align-center justify-space-between ga-3 mb-3 flex-wrap">
        <div class="d-flex align-center ga-1 flex-wrap">
          <v-chip
            v-for="filter in statusFilters"
            :key="filter.key"
            size="small"
            :variant="activeFilter === filter.key ? 'flat' : 'outlined'"
            :color="activeFilter === filter.key ? 'primary' : 'default'"
            class="cursor-pointer"
            @click="activeFilter = filter.key; emitFilterChange()"
          >
            {{ filter.label }} {{ filter.count }}
          </v-chip>
        </div>
        <div class="d-flex align-center ga-2">
          <v-text-field
            v-model="keyword"
            label="搜索任务"
            prepend-inner-icon="mdi-magnify"
            variant="outlined"
            density="compact"
            hide-details
            clearable
            class="search-field"
            @keyup.enter="emitFilterChange"
          />
        </div>
      </div>

      <div v-if="props.selection.length > 0" class="d-flex align-center ga-3 mb-3 pa-2 rounded" style="background: rgba(var(--v-theme-primary), 0.08);">
        <span class="text-body-2">
          已选 {{ props.selection.length }} 项
        </span>
        <v-spacer />
        <v-btn
          size="small"
          color="warning"
          variant="outlined"
          :disabled="cancellableSelection.length === 0"
          @click="emit('batch-cancel', props.selection)"
        >
          <v-icon start size="small">mdi-stop-circle-outline</v-icon>
          批量取消
        </v-btn>
        <v-btn size="small" color="error" variant="outlined" @click="emit('batch-delete', props.selection)">
          <v-icon start size="small">mdi-delete-outline</v-icon>
          批量删除
        </v-btn>
      </div>

      <v-alert
        v-if="props.sseReconnectAttempts > 3"
        type="warning"
        variant="tonal"
        density="compact"
        class="mb-3"
      >
        实时推送多次重连失败，任务进度可能无法自动更新。
        <v-btn size="x-small" variant="text" color="primary" @click="emit('reconnect')">重连</v-btn>
      </v-alert>

      <v-table v-if="!isMobile" density="compact" fixed-header height="420">
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
            <th>任务</th>
            <th>目标库</th>
            <th>状态</th>
            <th>进度</th>
            <th>下载/上传速度</th>
            <th>已下载/总大小</th>
            <th style="width: 150px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in props.tasks" :key="task.id" :class="{ 'row-selected': isSelected(task.id) }">
            <td>
              <v-checkbox
                :model-value="isSelected(task.id)"
                density="compact"
                hide-details
                @update:model-value="toggleOne(task.id)"
              />
            </td>
            <td>
              <div class="title-cell" :title="task.title">{{ task.title }}</div>
              <div v-if="task.subgroupName || task.typeName" class="text-caption text-medium-emphasis">
                {{ [task.subgroupName, task.typeName].filter(Boolean).join(' · ') }}
              </div>
            </td>
            <td>{{ task.libraryName || task.libraryId || '-' }}</td>
            <td>
              <v-chip :color="statusColor(task.status)" size="small" variant="flat">
                {{ formatStatus(task.status) }}
              </v-chip>
            </td>
            <td>
              <div class="d-flex align-center ga-2">
                <v-progress-linear
                  :model-value="task.progressPercent || 0"
                  height="8"
                  rounded
                  :color="task.status === 'FAILED' ? 'error' : task.status === 'STALLED' ? 'orange' : 'primary'"
                  style="width: 100px;"
                />
                <span class="text-caption">{{ task.progressPercent || 0 }}%</span>
              </div>
            </td>
            <td class="text-caption">
              <div class="d-flex align-center ga-1">
                <v-icon size="13" color="primary">mdi-arrow-down</v-icon>
                <span>{{ resolveTaskSpeed(task).down }}</span>
              </div>
              <div class="d-flex align-center ga-1 text-medium-emphasis">
                <v-icon size="13" color="info">mdi-arrow-up</v-icon>
                <span>{{ resolveTaskSpeed(task).up }}</span>
              </div>
            </td>
            <td class="text-caption">{{ formatBytes(task.downloadedBytes) }} / {{ formatBytes(task.totalBytes) }}</td>
            <td>
              <div class="d-flex align-center ga-1">
                <v-btn
                  size="small"
                  color="warning"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  :disabled="!canCancel(task.status)"
                  @click="emit('cancel', task)"
                >
                  <v-icon start size="small">mdi-stop-circle-outline</v-icon>
                  取消
                </v-btn>
                <v-btn
                  v-if="canRetry(task.status)"
                  size="small"
                  color="info"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  @click="emit('retry', task)"
                >
                  <v-icon start size="small">mdi-restart</v-icon>
                  重试
                </v-btn>
                <v-menu location="bottom end">
                  <template #activator="{ props: menuProps }">
                    <v-btn v-bind="menuProps" size="small" variant="text" icon="mdi-dots-vertical" />
                  </template>
                  <v-list density="compact">
                    <v-list-item prepend-icon="mdi-information-outline" title="详情" @click="openDetail(task)" />
                    <v-list-item
                      prepend-icon="mdi-link-variant"
                      title="查看绑定"
                      :disabled="!canViewBinding(task.status)"
                      @click="emit('binding', task.id)"
                    />
                    <v-list-item
                      prepend-icon="mdi-delete-outline"
                      title="删除"
                      color="error"
                      :disabled="!canDelete(task.status)"
                      @click="emit('delete', task)"
                    />
                  </v-list>
                </v-menu>
              </div>
            </td>
          </tr>
          <tr v-if="props.loading">
            <td colspan="8" class="text-center py-4">
              <v-progress-circular indeterminate size="28" color="primary" />
            </td>
          </tr>
          <tr v-else-if="props.tasks.length === 0">
            <td colspan="8" class="text-center py-10">
              <v-icon size="40" color="grey-lighten-1" class="mb-2">mdi-download-off</v-icon>
              <div class="text-medium-emphasis">暂无下载任务</div>
            </td>
          </tr>
        </tbody>
      </v-table>

      <!-- ===== 移动端：卡片 ===== -->
      <template v-else>
        <div v-if="props.loading && props.tasks.length === 0" class="text-center py-8">
          <v-progress-circular indeterminate size="28" color="primary" />
        </div>
        <div v-else-if="props.tasks.length === 0" class="text-center py-10">
          <v-icon size="40" color="grey-lighten-1" class="mb-2">mdi-download-off</v-icon>
          <div class="text-medium-emphasis">暂无下载任务</div>
        </div>
        <template v-else>
          <v-card
            v-for="task in props.tasks"
            :key="task.id"
            class="mb-3"
            :class="{ 'task-selected': isSelected(task.id) }"
          >
            <v-card-item>
              <template #title>
                <div class="d-flex align-center ga-2" style="min-width: 0">
                  <v-checkbox
                    :model-value="isSelected(task.id)"
                    density="compact"
                    hide-details
                    class="flex-shrink-0"
                    @update:model-value="toggleOne(task.id)"
                  />
                  <div class="flex-grow-1" style="min-width: 0">
                    <div class="text-subtitle-2 font-weight-bold text-truncate">{{ task.title }}</div>
                    <div v-if="task.subgroupName || task.typeName" class="text-caption text-medium-emphasis text-truncate">
                      {{ [task.subgroupName, task.typeName].filter(Boolean).join(' · ') }}
                    </div>
                  </div>
                  <v-chip :color="statusColor(task.status)" size="small" variant="flat" class="flex-shrink-0">
                    {{ formatStatus(task.status) }}
                  </v-chip>
                </div>
              </template>
            </v-card-item>

            <v-card-text class="pt-0">
              <div class="d-flex align-center ga-2 mb-2">
                <v-progress-linear
                  :model-value="task.progressPercent || 0"
                  height="8"
                  rounded
                  :color="task.status === 'FAILED' ? 'error' : task.status === 'STALLED' ? 'orange' : 'primary'"
                />
                <span class="text-caption flex-shrink-0">{{ task.progressPercent || 0 }}%</span>
              </div>

              <div class="d-flex flex-wrap text-body-2">
                <div class="w-50 py-1 pr-2">
                  <div class="text-caption text-medium-emphasis">目标库</div>
                  <div class="text-body-2 text-truncate">{{ task.libraryName || task.libraryId || '-' }}</div>
                </div>
                <div class="w-50 py-1">
                  <div class="text-caption text-medium-emphasis">大小</div>
                  <div class="text-body-2 text-truncate">{{ formatBytes(task.downloadedBytes) }} / {{ formatBytes(task.totalBytes) }}</div>
                </div>
                <div class="w-50 py-1 pr-2">
                  <div class="text-caption text-medium-emphasis">下载速度</div>
                  <div class="text-body-2">
                    <v-icon size="14" color="primary">mdi-arrow-down</v-icon>
                    {{ resolveTaskSpeed(task).down }}
                  </div>
                </div>
                <div class="w-50 py-1">
                  <div class="text-caption text-medium-emphasis">上传速度</div>
                  <div class="text-body-2">
                    <v-icon size="14" color="info">mdi-arrow-up</v-icon>
                    {{ resolveTaskSpeed(task).up }}
                  </div>
                </div>
              </div>

              <div class="d-flex align-center ga-2 mt-2">
                <v-btn
                  size="small"
                  color="warning"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  :disabled="!canCancel(task.status)"
                  @click="emit('cancel', task)"
                >
                  <v-icon start size="small">mdi-stop-circle-outline</v-icon>
                  取消
                </v-btn>
                <v-btn
                  v-if="canRetry(task.status)"
                  size="small"
                  color="info"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  @click="emit('retry', task)"
                >
                  <v-icon start size="small">mdi-restart</v-icon>
                  重试
                </v-btn>
                <v-spacer />
                <v-menu location="top end">
                  <template #activator="{ props: menuProps }">
                    <v-btn v-bind="menuProps" size="small" variant="text" icon="mdi-dots-vertical" />
                  </template>
                  <v-list density="compact">
                    <v-list-item prepend-icon="mdi-information-outline" title="详情" @click="openDetail(task)" />
                    <v-list-item
                      prepend-icon="mdi-link-variant"
                      title="查看绑定"
                      :disabled="!canViewBinding(task.status)"
                      @click="emit('binding', task.id)"
                    />
                    <v-list-item
                      prepend-icon="mdi-delete-outline"
                      title="删除"
                      color="error"
                      :disabled="!canDelete(task.status)"
                      @click="emit('delete', task)"
                    />
                  </v-list>
                </v-menu>
              </div>
            </v-card-text>
          </v-card>

          <div v-if="props.loading" class="text-center py-4">
            <v-progress-circular indeterminate size="28" color="primary" />
          </div>
        </template>
      </template>

      <div class="d-flex align-center justify-end ga-4 mt-3 flex-wrap">
        <v-select
          :model-value="props.size"
          :items="sizeOptions"
          density="compact"
          variant="outlined"
          hide-details
          style="max-width: 110px;"
          @update:model-value="emit('update:size', $event)"
        />
        <v-pagination
          :model-value="props.page"
          :length="pageCount"
          :total-visible="isMobile ? 3 : 7"
          density="compact"
          @update:model-value="emit('update:page', $event)"
        />
      </div>
    </v-card-text>

    <v-dialog v-model="detailDialog" max-width="720">
      <v-card v-if="detailTask">
        <v-card-title class="d-flex align-center justify-space-between ga-3">
          <span class="text-truncate">{{ detailTask.title }}</span>
          <v-chip :color="statusColor(detailTask.status)" size="small" variant="flat">
            {{ formatStatus(detailTask.status) }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-alert
            v-if="detailTask.errorMessage"
            type="error"
            variant="tonal"
            density="compact"
            class="mb-3"
          >
            {{ detailTask.errorMessage }}
          </v-alert>
          <v-list lines="two">
            <v-list-item title="目标媒体库" :subtitle="detailTask.libraryName || detailTask.libraryId || '-'" />
            <v-list-item title="字幕组" :subtitle="detailTask.subgroupName || '-'" />
            <v-list-item title="资源类型" :subtitle="detailTask.typeName || '-'" />
            <v-list-item title="资源大小" :subtitle="detailTask.fileSize || '-'" />
            <v-list-item title="发布时间" :subtitle="detailTask.publishDate || '-'" />
            <v-list-item title="磁力链接" :subtitle="detailTask.magnet || '-'" />
            <v-list-item title="暂存目录" :subtitle="detailTask.tempDir || '-'" />
            <v-list-item title="最终路径" :subtitle="detailTask.finalPath || '-'" />
            <v-list-item title="进度" :subtitle="`${detailTask.progressPercent || 0}% （${formatBytes(detailTask.downloadedBytes)} / ${formatBytes(detailTask.totalBytes)}）`" />
            <v-list-item title="创建时间" :subtitle="formatTime(detailTask.createdAt)" />
            <v-list-item title="开始时间" :subtitle="formatTime(detailTask.startedAt)" />
            <v-list-item title="完成时间" :subtitle="formatTime(detailTask.finishedAt)" />
          </v-list>
          <div v-if="detailTask.outputMessage" class="mt-3">
            <div class="text-subtitle-2 font-weight-medium mb-1">运行日志</div>
            <pre class="log-box">{{ detailTask.outputMessage }}</pre>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="detailDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<style scoped>
.title-cell {
  max-width: 320px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cursor-pointer {
  cursor: pointer;
}

.row-selected {
  background: rgba(var(--v-theme-primary), 0.06);
}

.task-selected {
  border: 1px solid rgb(var(--v-theme-primary)) !important;
  background: rgba(var(--v-theme-primary), 0.06);
}

@media (max-width: 768px) {
  .title-row {
    flex-wrap: wrap;
    row-gap: 8px;
  }

  .status-chips {
    flex-wrap: wrap;
    row-gap: 6px;
    max-width: 100%;
  }

  .search-field {
    max-width: none !important;
    width: 100%;
  }
}

.log-box {
  max-height: 240px;
  overflow: auto;
  padding: 10px;
  margin: 0;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  border-radius: 8px;
  background: rgb(var(--v-theme-surface-variant));
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
