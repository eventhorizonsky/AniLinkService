<script setup>
import { ref, onMounted, inject } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'

const API_BASE = '/api'

const navigateTo = inject('navigateTo', null)

// RSS proxy status
const rssProxyConfigured = ref(false)
const rssProxyHost = ref('')
const rssProxyPort = ref(0)

const loading = ref(false)
const saving = ref(false)
const subscriptions = ref([])
const libraries = ref([])
const dialog = ref(false)
const editingId = ref(null)
const contentDialog = ref(false)
const contentLoading = ref(false)
const contentTitle = ref('')
const contentCheckedAt = ref(null)
const fetchedContent = ref('')
const triggeringId = ref(null)

// Preview state
const previewDialog = ref(false)
const previewLoading = ref(false)
const previewResult = ref(null)

const formatLocalDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const form = ref({
  name: '',
  feedUrl: '',
  libraryId: null,
  intervalMinutes: 30,
  enabled: true,
  includeFilter: '',
  excludeFilter: ''
})

const fetchLibraries = async () => {
  const res = await axios.get(`${API_BASE}/media-library`)
  libraries.value = (res.data?.data || []).map((item) => ({ ...item, id: String(item.id) }))
}

const fetchSubscriptions = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/resource-search/rss-subscriptions`)
    subscriptions.value = res.data?.data || []
  } catch (error) {
    console.error('获取 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '获取 RSS 订阅失败', 'error')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.value = {
    name: '',
    feedUrl: '',
    libraryId: libraries.value.length > 0 ? String(libraries.value[0].id) : null,
    intervalMinutes: 30,
    enabled: true,
    includeFilter: '',
    excludeFilter: ''
  }
  dialog.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.value = {
    name: row.name,
    feedUrl: row.feedUrl,
    libraryId: row.libraryId != null ? String(row.libraryId) : null,
    intervalMinutes: row.intervalMinutes || 30,
    enabled: row.enabled !== false,
    includeFilter: row.includeFilter || '',
    excludeFilter: row.excludeFilter || ''
  }
  dialog.value = true
}

const saveSubscription = async () => {
  if (!form.value.name || !form.value.feedUrl || !form.value.libraryId) {
    showAppMessage('请填写订阅源名称、RSS 地址和目标媒体库', 'warning')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      feedUrl: form.value.feedUrl,
      libraryId: String(form.value.libraryId),
      intervalMinutes: Math.max(1, Number(form.value.intervalMinutes || 30)),
      enabled: !!form.value.enabled,
      includeFilter: form.value.includeFilter || null,
      excludeFilter: form.value.excludeFilter || null
    }
    let res
    if (editingId.value) {
      res = await axios.put(`${API_BASE}/resource-search/rss-subscriptions/${editingId.value}`, payload)
    } else {
      res = await axios.post(`${API_BASE}/resource-search/rss-subscriptions`, payload)
    }
    if (res.data?.code === 200) {
      showAppMessage('保存成功', 'success')
      dialog.value = false
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '保存失败', 'error')
    }
  } catch (error) {
    console.error('保存 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

const deleteSubscription = async (row) => {
  try {
    const res = await axios.delete(`${API_BASE}/resource-search/rss-subscriptions/${row.id}`)
    if (res.data?.code === 200) {
      showAppMessage('删除成功', 'success')
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '删除失败', 'error')
    }
  } catch (error) {
    console.error('删除 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '删除失败', 'error')
  }
}

const triggerNow = async (row) => {
  triggeringId.value = row.id
  try {
    const res = await axios.post(`${API_BASE}/resource-search/rss-subscriptions/${row.id}/trigger`)
    if (res.data?.code === 200) {
      showAppMessage('已触发检查', 'success')
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '触发失败', 'error')
    }
  } catch (error) {
    console.error('触发 RSS 检查失败:', error)
    showAppMessage(error.response?.data?.msg || '触发失败', 'error')
  } finally {
    triggeringId.value = null
  }
}

const viewLastFetchedContent = async (row) => {
  contentDialog.value = true
  contentLoading.value = true
  contentTitle.value = row?.name || 'RSS 订阅'
  contentCheckedAt.value = null
  fetchedContent.value = ''
  try {
    const res = await axios.get(`${API_BASE}/resource-search/rss-subscriptions/${row.id}/last-content`)
    if (res.data?.code === 200 && res.data?.data) {
      contentTitle.value = res.data.data.name || contentTitle.value
      contentCheckedAt.value = res.data.data.lastCheckedAt || null
      fetchedContent.value = res.data.data.lastFetchedContent || ''
    } else {
      showAppMessage(res.data?.msg || '获取解析结果失败', 'error')
    }
  } catch (error) {
    console.error('获取 RSS 解析结果失败:', error)
    showAppMessage(error.response?.data?.msg || '获取解析结果失败', 'error')
  } finally {
    contentLoading.value = false
  }
}

const runFilterPreview = async () => {
  if (!form.value.feedUrl) {
    showAppMessage('请先填写 RSS 地址', 'warning')
    return
  }
  previewLoading.value = true
  previewResult.value = null
  try {
    const res = await axios.post(`${API_BASE}/resource-search/rss-subscriptions/preview`, {
      feedUrl: form.value.feedUrl,
      includeFilter: form.value.includeFilter || null,
      excludeFilter: form.value.excludeFilter || null
    })
    if (res.data?.code === 200) {
      previewResult.value = res.data.data
      previewDialog.value = true
    } else {
      showAppMessage(res.data?.msg || '预览失败', 'error')
    }
  } catch (error) {
    console.error('预览过滤失败:', error)
    showAppMessage(error.response?.data?.msg || '预览失败', 'error')
  } finally {
    previewLoading.value = false
  }
}

const fetchRssProxyConfig = async () => {
  try {
    const res = await axios.get(`${API_BASE}/site/config`)
    const data = res.data?.data || {}
    const host = data.rssProxyHost || ''
    const port = Number(data.rssProxyPort || 0)
    rssProxyHost.value = host
    rssProxyPort.value = port
    rssProxyConfigured.value = !!(host && port > 0)
  } catch (error) {
    console.error('获取 RSS 代理配置失败:', error)
  }
}

onMounted(async () => {
  await fetchLibraries()
  await fetchSubscriptions()
  await fetchRssProxyConfig()
})
</script>

<template>
  <div>
    <v-card>
      <v-card-title class="d-flex align-center justify-space-between">
        <span>
          <v-icon start>mdi-rss-box</v-icon>
          RSS 订阅下载
        </span>
        <v-btn color="primary" @click="openCreate">
          <v-icon start>mdi-plus</v-icon>
          新建订阅
        </v-btn>
      </v-card-title>
      <v-card-text>
        <!-- RSS 代理状态提示 -->
        <v-alert
          :type="rssProxyConfigured ? 'success' : 'warning'"
          variant="tonal"
          density="compact"
          class="mb-4"
        >
          <template v-if="rssProxyConfigured">
            RSS 请求代理已配置: {{ rssProxyHost }}:{{ rssProxyPort }}
          </template>
          <template v-else>
            RSS 请求代理未配置。如果 RSS 源无法直接访问，建议配置代理。
          </template>
          <template #append>
            <v-btn
              v-if="navigateTo"
              size="small"
              variant="text"
              color="primary"
              @click="navigateTo('resource-download-settings')"
            >
              <v-icon start size="small">mdi-open-in-new</v-icon>
              配置代理
            </v-btn>
          </template>
        </v-alert>

        <v-table density="compact" fixed-header height="460">
          <thead>
            <tr>
              <th>名称</th>
              <th>RSS 地址</th>
              <th>目标媒体库</th>
              <th>间隔(分钟)</th>
              <th>过滤规则</th>
              <th>启用</th>
              <th>最近检查</th>
              <th>错误</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in subscriptions" :key="row.id">
              <td>{{ row.name }}</td>
              <td class="ellipsis">{{ row.feedUrl }}</td>
              <td>{{ row.libraryName || row.libraryId }}</td>
              <td>{{ row.intervalMinutes }}</td>
              <td class="ellipsis">
                <template v-if="row.includeFilter || row.excludeFilter">
                  <span v-if="row.includeFilter" class="text-caption">包含: {{ row.includeFilter }}</span>
                  <br v-if="row.includeFilter && row.excludeFilter" />
                  <span v-if="row.excludeFilter" class="text-caption">排除: {{ row.excludeFilter }}</span>
                </template>
                <span v-else class="text-medium-emphasis">-</span>
              </td>
              <td>
                <v-chip :color="row.enabled ? 'success' : 'grey'" size="small" variant="tonal">
                  {{ row.enabled ? '启用' : '停用' }}
                </v-chip>
              </td>
              <td>{{ formatLocalDateTime(row.lastCheckedAt) }}</td>
              <td class="ellipsis">{{ row.lastError || '-' }}</td>
              <td>
                <div class="d-flex ga-2">
                  <v-btn size="small" variant="outlined" color="info" :loading="triggeringId === row.id" @click="triggerNow(row)">立即检查</v-btn>
                  <v-btn size="small" variant="outlined" color="secondary" @click="viewLastFetchedContent(row)">查看解析结果</v-btn>
                  <v-btn size="small" variant="outlined" @click="openEdit(row)">编辑</v-btn>
                  <v-btn size="small" variant="outlined" color="error" @click="deleteSubscription(row)">删除</v-btn>
                </div>
              </td>
            </tr>
            <tr v-if="subscriptions.length === 0">
              <td colspan="9" class="text-center text-medium-emphasis py-6">暂无 RSS 订阅</td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>

    <v-dialog v-model="dialog" max-width="720">
      <v-card>
        <v-card-title>{{ editingId ? '编辑订阅' : '新建订阅' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.name" label="订阅源名称" variant="outlined" class="mb-3" />
          <v-text-field v-model="form.feedUrl" label="RSS 地址" variant="outlined" class="mb-3" />
          <v-select
            v-model="form.libraryId"
            :items="libraries"
            item-title="name"
            item-value="id"
            label="目标媒体库"
            variant="outlined"
            class="mb-3"
          />
          <v-text-field
            v-model.number="form.intervalMinutes"
            type="number"
            min="1"
            label="更新间隔（分钟）"
            hint="最小 1 分钟"
            persistent-hint
            variant="outlined"
            class="mb-3"
          />
          <v-divider class="my-3" />
          <div class="text-subtitle-2 font-weight-medium mb-2">
            <v-icon size="small" start>mdi-regex</v-icon>
            正则过滤（匹配标题，留空表示不过滤）
          </div>
          <v-text-field
            v-model="form.includeFilter"
            label="正向过滤"
            placeholder="例如: 简|CHS|GB — 符合条件的条目才会被下载"
            hint="匹配标题的正则表达式，只有命中的条目才会被下载"
            persistent-hint
            variant="outlined"
            class="mb-2"
          />
          <v-text-field
            v-model="form.excludeFilter"
            label="排除过滤"
            placeholder="例如: 繁|CHT|BIG5 — 符合条件的条目将被跳过"
            hint="匹配标题的正则表达式，命中的条目会被排除"
            persistent-hint
            variant="outlined"
            class="mb-3"
          />
          <v-btn
            color="info"
            variant="outlined"
            :loading="previewLoading"
            @click="runFilterPreview"
            class="mb-3"
            block
          >
            <v-icon start>mdi-magnify</v-icon>
            预览过滤效果
          </v-btn>
          <v-divider class="my-3" />
          <v-switch v-model="form.enabled" label="启用订阅" color="primary" inset />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="saveSubscription">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="contentDialog" max-width="960">
      <v-card>
        <v-card-title class="d-flex align-center justify-space-between">
          <span>
            {{ contentTitle }} - 最近解析结果
          </span>
          <v-chip size="small" variant="tonal" color="primary">
            最近检查: {{ formatLocalDateTime(contentCheckedAt) }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-skeleton-loader v-if="contentLoading" type="paragraph@6" />
          <div v-else class="fetched-content-box">
            <pre v-if="fetchedContent">{{ fetchedContent }}</pre>
            <div v-else class="text-medium-emphasis">暂无解析结果，请先执行一次“立即检查”</div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" variant="text" @click="contentDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Preview dialog -->
    <v-dialog v-model="previewDialog" max-width="900">
      <v-card>
        <v-card-title class="d-flex align-center justify-space-between">
          <span>过滤预览结果</span>
          <div class="d-flex ga-2">
            <v-chip size="small" color="success" variant="tonal">
              包含: {{ previewResult?.includedCount ?? 0 }}
            </v-chip>
            <v-chip size="small" color="error" variant="tonal">
              排除: {{ previewResult?.excludedCount ?? 0 }}
            </v-chip>
            <v-chip size="small" variant="tonal">
              总计: {{ previewResult?.totalCount ?? 0 }}
            </v-chip>
          </div>
        </v-card-title>
        <v-card-text>
          <!-- Regex validation errors -->
          <v-alert
            v-if="previewResult && !previewResult.includeFilterValid"
            type="error"
            variant="tonal"
            density="compact"
            class="mb-2"
          >
            正向过滤正则语法错误: {{ previewResult.includeFilterError }}
          </v-alert>
          <v-alert
            v-if="previewResult && !previewResult.excludeFilterValid"
            type="error"
            variant="tonal"
            density="compact"
            class="mb-2"
          >
            排除过滤正则语法错误: {{ previewResult.excludeFilterError }}
          </v-alert>

          <v-skeleton-loader v-if="previewLoading" type="paragraph@6" />
          <div v-else class="preview-entries-box">
            <div
              v-for="(entry, idx) in (previewResult?.entries || [])"
              :key="idx"
              class="preview-entry-row d-flex align-center pa-2 mb-1"
              :class="(entry.included && !entry.excluded) ? 'bg-included' : 'bg-excluded'"
            >
              <v-icon
                :color="(entry.included && !entry.excluded) ? 'success' : 'error'"
                class="mr-2"
                size="20"
              >
                {{ (entry.included && !entry.excluded) ? 'mdi-check-circle' : 'mdi-close-circle' }}
              </v-icon>
              <div class="flex-grow-1" style="min-width: 0;">
                <div class="text-body-2 text-truncate">{{ entry.title || '(无标题)' }}</div>
                <div class="text-caption text-medium-emphasis mt-1">
                  <template v-if="!entry.included">
                    <v-chip size="x-small" color="warning" class="mr-1">未匹配正向过滤</v-chip>
                  </template>
                  <template v-if="entry.excluded">
                    <v-chip size="x-small" color="error" class="mr-1">命中排除过滤</v-chip>
                  </template>
                  <template v-if="entry.included && !entry.excluded">
                    <v-chip size="x-small" color="success">通过</v-chip>
                  </template>
                </div>
              </div>
            </div>
            <div v-if="previewResult && (!previewResult.entries || previewResult.entries.length === 0)" class="text-center text-medium-emphasis py-6">
              没有匹配的条目
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" variant="text" @click="previewDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.ellipsis {
  max-width: 280px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fetched-content-box {
  max-height: 65vh;
  overflow: auto;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  background: #fafafa;
}

.fetched-content-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}

.preview-entries-box {
  max-height: 60vh;
  overflow-y: auto;
}

.preview-entry-row {
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.bg-included {
  background-color: rgba(76, 175, 80, 0.08);
}

.bg-excluded {
  background-color: rgba(244, 67, 54, 0.08);
}
</style>
