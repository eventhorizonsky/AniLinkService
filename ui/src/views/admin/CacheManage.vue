<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const stats = ref(null)
const cacheTypes = ref({})

// 清理操作状态
const clearing = ref(false)
const clearTypeSelected = ref('')
const showClearAllDialog = ref(false)

const TYPE_LABELS = {
  comment: '弹幕缓存',
  bangumi: '动漫详情缓存'
}

// 格式化数字
const formatNumber = (n) => {
  if (n == null) return '—'
  return n.toLocaleString()
}

// 获取统计
const fetchStats = async () => {
  loading.value = true
  try {
    const [statsRes, typesRes] = await Promise.all([
      axios.get(`${API_BASE}/cache/stats`),
      axios.get(`${API_BASE}/cache/types`)
    ])
    if (statsRes.data?.data) {
      stats.value = statsRes.data.data
    }
    if (typesRes.data?.data) {
      cacheTypes.value = typesRes.data.data
    }
  } catch (error) {
    console.error('获取缓存统计失败:', error)
    showAppMessage('获取缓存统计失败', 'error')
  } finally {
    loading.value = false
  }
}

// 过期占比
const expiredPercent = computed(() => {
  if (!stats.value || stats.value.totalCount === 0) return 0
  return Math.round((stats.value.expiredCount / stats.value.totalCount) * 100)
})

// ===== 清理操作 =====

const doClearExpired = async () => {
  clearing.value = true
  try {
    const res = await axios.delete(`${API_BASE}/cache/expired`)
    if (res.data?.code === 200) {
      const result = res.data.data
      showAppMessage(`清理过期缓存完成，删除 ${result.deletedCount} 条`, 'success')
      await fetchStats()
    } else {
      showAppMessage(res.data?.msg || '清理失败', 'error')
    }
  } catch (error) {
    console.error('清理过期缓存失败:', error)
    showAppMessage('清理过期缓存失败', 'error')
  } finally {
    clearing.value = false
  }
}

const doClearByType = async () => {
  if (!clearTypeSelected.value) {
    showAppMessage('请先选择要清理的缓存类型', 'warning')
    return
  }
  clearing.value = true
  try {
    const res = await axios.delete(`${API_BASE}/cache/type/${clearTypeSelected.value}`)
    if (res.data?.code === 200) {
      const result = res.data.data
      showAppMessage(`按类型清理完成，删除 ${result.deletedCount} 条`, 'success')
      clearTypeSelected.value = ''
      await fetchStats()
    } else {
      showAppMessage(res.data?.msg || '清理失败', 'error')
    }
  } catch (error) {
    console.error('按类型清理缓存失败:', error)
    showAppMessage('按类型清理缓存失败', 'error')
  } finally {
    clearing.value = false
  }
}

const doClearAll = async () => {
  showClearAllDialog.value = false
  clearing.value = true
  try {
    const res = await axios.delete(`${API_BASE}/cache/all`)
    if (res.data?.code === 200) {
      const result = res.data.data
      showAppMessage(`清空全部缓存完成，删除 ${result.deletedCount} 条`, 'success')
      await fetchStats()
    } else {
      showAppMessage(res.data?.msg || '清空失败', 'error')
    }
  } catch (error) {
    console.error('清空全部缓存失败:', error)
    showAppMessage('清空全部缓存失败', 'error')
  } finally {
    clearing.value = false
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<template>
  <div>
    <!-- 加载状态 -->
    <v-card v-if="loading && !stats" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1">加载中...</p>
    </v-card>

    <template v-else-if="stats">
      <!-- 统计面板 -->
      <v-row>
        <v-col cols="12" md="3">
          <v-card class="text-center pa-4" border>
            <div class="text-h4 text-primary font-weight-bold">{{ formatNumber(stats.totalCount) }}</div>
            <div class="text-body-2 text-medium-emphasis mt-1">缓存总数</div>
          </v-card>
        </v-col>
        <v-col cols="12" md="3">
          <v-card class="text-center pa-4" border>
            <div class="text-h4 text-success font-weight-bold">{{ formatNumber(stats.validCount) }}</div>
            <div class="text-body-2 text-medium-emphasis mt-1">有效缓存</div>
          </v-card>
        </v-col>
        <v-col cols="12" md="3">
          <v-card class="text-center pa-4" border>
            <div class="text-h4 text-warning font-weight-bold">{{ formatNumber(stats.expiredCount) }}</div>
            <div class="text-body-2 text-medium-emphasis mt-1">
              已过期
              <v-chip v-if="expiredPercent > 0" size="x-small" color="warning" class="ml-1">
                {{ expiredPercent }}%
              </v-chip>
            </div>
          </v-card>
        </v-col>
        <v-col cols="12" md="3">
          <v-card class="text-center pa-4" border>
            <div class="text-body-1 font-weight-medium">
              {{ stats.latestCreatedAt || '—' }}
            </div>
            <div class="text-caption text-medium-emphasis mt-1">最近缓存时间</div>
          </v-card>
        </v-col>
      </v-row>

      <!-- 按类型分布 -->
      <v-card class="mt-4" border v-if="stats.countByType && Object.keys(stats.countByType).length">
        <v-card-title class="text-body-1 font-weight-bold">
          <v-icon start color="primary">mdi-chart-pie</v-icon>
          缓存类型分布
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col v-for="(count, type) in stats.countByType" :key="type" cols="12" sm="6" md="3">
              <v-card variant="outlined" class="pa-3 text-center">
                <div class="text-h6 font-weight-bold">{{ formatNumber(count) }}</div>
                <div class="text-caption text-medium-emphasis">{{ TYPE_LABELS[type] || type }}</div>
              </v-card>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <!-- 清理操作区 -->
      <v-card class="mt-4" border>
        <v-card-title class="text-body-1 font-weight-bold">
          <v-icon start color="error">mdi-broom</v-icon>
          缓存清理
        </v-card-title>
        <v-card-text>
          <v-row>
            <!-- Level 1: 清理过期 -->
            <v-col cols="12" md="4">
              <v-card variant="outlined" class="pa-4 h-100">
                <div class="d-flex align-center mb-3">
                  <v-avatar size="32" color="success" class="mr-2">
                    <span class="text-white text-caption font-weight-bold">1</span>
                  </v-avatar>
                  <div>
                    <div class="text-body-2 font-weight-bold">清理过期缓存</div>
                    <div class="text-caption text-medium-emphasis">仅删除 expire_time 已过的条目</div>
                  </div>
                </div>
                <v-btn
                  color="success"
                  variant="tonal"
                  block
                  :loading="clearing"
                  :disabled="stats.expiredCount === 0"
                  @click="doClearExpired"
                >
                  <v-icon start>mdi-delete-clock</v-icon>
                  清理过期 ({{ formatNumber(stats.expiredCount) }} 条)
                </v-btn>
              </v-card>
            </v-col>

            <!-- Level 2: 按类型 -->
            <v-col cols="12" md="4">
              <v-card variant="outlined" class="pa-4 h-100">
                <div class="d-flex align-center mb-3">
                  <v-avatar size="32" color="warning" class="mr-2">
                    <span class="text-white text-caption font-weight-bold">2</span>
                  </v-avatar>
                  <div>
                    <div class="text-body-2 font-weight-bold">按类型清理</div>
                    <div class="text-caption text-medium-emphasis">只清理指定类型的全部缓存</div>
                  </div>
                </div>
                <v-select
                  v-model="clearTypeSelected"
                  :items="Object.keys(cacheTypes).map(k => ({ value: k, title: TYPE_LABELS[k] || k }))"
                  label="选择缓存类型"
                  density="compact"
                  variant="outlined"
                  hide-details
                  class="mb-3"
                />
                <v-btn
                  color="warning"
                  variant="tonal"
                  block
                  :loading="clearing"
                  :disabled="!clearTypeSelected"
                  @click="doClearByType"
                >
                  <v-icon start>mdi-filter-remove</v-icon>
                  按类型清理
                </v-btn>
              </v-card>
            </v-col>

            <!-- Level 3: 全部清空 -->
            <v-col cols="12" md="4">
              <v-card variant="outlined" class="pa-4 h-100" color="error-lighten-5">
                <div class="d-flex align-center mb-3">
                  <v-avatar size="32" color="error" class="mr-2">
                    <span class="text-white text-caption font-weight-bold">3</span>
                  </v-avatar>
                  <div>
                    <div class="text-body-2 font-weight-bold text-error">清空全部缓存</div>
                    <div class="text-caption text-medium-emphasis">删除所有 API 缓存数据</div>
                  </div>
                </div>
                <v-btn
                  color="error"
                  variant="tonal"
                  block
                  :disabled="stats.totalCount === 0"
                  @click="showClearAllDialog = true"
                >
                  <v-icon start>mdi-alert-octagon</v-icon>
                  清空全部 ({{ formatNumber(stats.totalCount) }} 条)
                </v-btn>
              </v-card>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <!-- 时间范围提示 -->
      <div v-if="stats.earliestCreatedAt || stats.latestCreatedAt" class="text-caption text-medium-emphasis mt-2 px-1">
        <v-icon size="x-small">mdi-clock-outline</v-icon>
        缓存时间范围：
        {{ stats.earliestCreatedAt || '—' }} ~ {{ stats.latestCreatedAt || '—' }}
        <span class="ml-2 text-primary font-weight-medium">
          系统每小时自动清理过期缓存
        </span>
      </div>
    </template>

    <!-- 空状态 -->
    <v-card v-else class="text-center pa-8">
      <v-icon size="64" color="medium-emphasis">mdi-database-off</v-icon>
      <p class="mt-4 text-body-1 text-medium-emphasis">暂无缓存数据</p>
    </v-card>

    <!-- 全部清空确认对话框 -->
    <v-dialog v-model="showClearAllDialog" max-width="480" persistent>
      <v-card>
        <v-card-title class="text-error">
          <v-icon start color="error">mdi-alert-circle</v-icon>
          确认清空全部缓存？
        </v-card-title>
        <v-card-text>
          <v-alert type="warning" variant="tonal" density="compact" class="mb-3">
            此操作将删除所有 <strong>{{ formatNumber(stats?.totalCount) }}</strong> 条 API 缓存数据，包括有效缓存。
          </v-alert>
          <p class="text-body-2">
            清空后，后续对弹弹 API 的请求将直接回源拉取数据，短期内可能略微增加响应延迟。
            建议优先使用 <strong>"清理过期缓存"</strong> 或 <strong>"按类型清理"</strong>。
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showClearAllDialog = false">取消</v-btn>
          <v-btn color="error" variant="elevated" @click="doClearAll">
            <v-icon start>mdi-delete</v-icon>
            确认清空
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
