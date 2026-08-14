<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import PaginationBar from '../../components/PaginationBar.vue'
import { showAppMessage, askAppConfirm } from '../../utils/ui-feedback'
import { usePagination } from '../../composables/usePagination'
import { API_BASE } from '../../utils/constants'
import { formatMonthDayTime } from '../../utils/format'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const filterType = ref('')

const typeFilters = [
  { label: '全部', value: '' },
  { label: '剧集更新', value: 'episode_update' },
  { label: '系统通知', value: 'system' }
]

const unreadCount = computed(() => list.value.filter((m) => !m.isRead).length)

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    const url = filterType.value ? `${API_BASE}/messages/type/${filterType.value}` : `${API_BASE}/messages`
    const res = await axios.get(url, { params })
    if (res.data?.code === 200) {
      list.value = res.data.data?.content || []
      total.value = Number(res.data.data?.totalElements || 0)
    } else error.value = res.data?.msg || '加载消息列表失败'
  } catch (e) { console.error('加载消息列表失败:', e); error.value = '加载消息列表失败' }
  finally { loading.value = false }
}

const { page, pageSize, totalPages, pages, changePage } = usePagination({
  pageSize: 20,
  getTotal: () => total.value,
  onPageChange: fetchData,
})

const applyFilter = (value) => {
  filterType.value = value
  page.value = 1
  fetchData()
}

const openMessage = async (msg) => {
  if (!msg.isRead) {
    try { await axios.put(`${API_BASE}/messages/${msg.id}/read`) } catch (e) { /* ignore */ }
    msg.isRead = true
  }
  if (msg.type === 'episode_update' && msg.videoId) {
    const routeData = router.resolve({
      name: 'Player',
      params: { videoId: String(msg.videoId) },
      query: {
        animeId: msg.animeId ? String(msg.animeId) : undefined,
        episodeId: msg.episodeId ? String(msg.episodeId) : undefined
      }
    })
    window.open(routeData.href, '_blank')
  } else if (msg.animeId) {
    router.push(`/anime/${msg.animeId}`)
  }
}

const markAllRead = async () => {
  try {
    const res = await axios.put(`${API_BASE}/messages/mark-all-read`)
    if (res.data?.code === 200 || res.data?.code === 0) {
      showAppMessage('已全部标记为已读', 'success')
      await fetchData()
    } else showAppMessage(res.data?.msg || '一键已读失败', 'error')
  } catch (e) { showAppMessage('一键已读失败，请稍后重试', 'error') }
}

const removeMessage = async (id) => {
  const ok = await askAppConfirm({ title: '删除消息', message: '确定要删除这条消息吗？' })
  if (!ok) return
  try {
    const res = await axios.delete(`${API_BASE}/messages/${id}`)
    if (res.data?.code === 200) {
      if (list.value.length === 1 && page.value > 1) page.value -= 1
      await fetchData()
    } else showAppMessage(res.data?.msg || '删除失败', 'error')
  } catch (e) { showAppMessage('删除失败', 'error') }
}

const typeLabel = (type) => typeFilters.find((t) => t.value === type)?.label || type || '通知'

onMounted(fetchData)
</script>

<template>
  <div class="messages-page">
    <div class="page-head">
      <h2><i class="mdi mdi-bell-outline"></i> 消息中心</h2>
      <div class="page-head-actions">
        <span v-if="unreadCount > 0" class="unread-summary">{{ unreadCount }} 条未读</span>
        <button class="btn btn-ghost" :disabled="unreadCount === 0" @click="markAllRead">
          <i class="mdi mdi-check-all"></i> 一键已读
        </button>
      </div>
    </div>

    <div class="filter-pills">
      <button
        v-for="f in typeFilters"
        :key="f.value"
        class="pill"
        :class="{ active: filterType === f.value }"
        @click="applyFilter(f.value)"
      >{{ f.label }}</button>
    </div>

    <div v-if="loading" class="br-sk-grid">
      <div v-for="i in 6" :key="i" class="sk-row"></div>
    </div>
    <div v-else-if="error" class="br-empty error"><i class="mdi mdi-alert-circle-outline"></i> {{ error }}</div>
    <div v-else-if="!list.length" class="empty-state">
      <i class="mdi mdi-bell-off-outline"></i>
      <p>暂无消息</p>
    </div>
    <div v-else class="message-list">
      <div
        v-for="msg in list"
        :key="msg.id"
        class="message-card"
        :class="{ unread: !msg.isRead }"
        @click="openMessage(msg)"
      >
        <div class="msg-indicator">
          <span v-if="!msg.isRead" class="unread-dot"></span>
        </div>
        <div class="msg-main">
          <div class="msg-title-row">
            <span class="msg-title">{{ msg.title }}</span>
            <span class="msg-type" :class="msg.type === 'episode_update' ? 'type-update' : 'type-system'">{{ typeLabel(msg.type) }}</span>
          </div>
          <p class="msg-content">{{ msg.content }}</p>
          <span class="msg-time"><i class="mdi mdi-clock-outline"></i> {{ formatMonthDayTime(msg.createdAt) }}</span>
        </div>
        <button class="msg-delete" title="删除" @click.stop="removeMessage(msg.id)">
          <i class="mdi mdi-delete-outline"></i>
        </button>
      </div>
    </div>

    <PaginationBar :page="page" :total-pages="totalPages" :pages="pages" :total-text="`共 ${total} 条`" @change="changePage" />
  </div>
</template>

<style scoped>
.messages-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.sk-row {
  height: 96px; border-radius: 14px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

.unread-summary { font-size: 13px; color: var(--anime-accent-red); font-weight: 600; }

.filter-pills { display: flex; gap: 8px; margin-bottom: 18px; flex-wrap: wrap; }
.pill {
  border: 1px solid var(--al-border-input); background: var(--al-bg);
  color: var(--anime-text-secondary);
  padding: 7px 16px; border-radius: 999px;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.2s; font-family: inherit;
}
.pill:hover { border-color: var(--anime-accent-red); color: var(--anime-accent-red); }
.pill.active { background: rgba(196, 93, 43, 0.1); border-color: var(--anime-accent-red); color: var(--anime-accent-red); font-weight: 600; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 70px 20px; color: var(--anime-text-secondary);
}
.empty-state i { font-size: 3rem; opacity: 0.35; color: var(--anime-accent-red); }
.empty-state p { margin: 0; font-size: 14px; }

.message-list { display: flex; flex-direction: column; gap: 10px; }
.message-card {
  display: flex; align-items: flex-start; gap: 12px;
  background: var(--al-bg); border: 1px solid var(--al-border-panel); border-radius: 14px;
  padding: 14px 16px; cursor: pointer; position: relative;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s, border-color 0.25s;
}
.message-card:hover { box-shadow: 0 8px 24px rgba(0, 0, 0, 0.07); border-color: rgba(196, 93, 43, 0.18); }
.message-card.unread { background: rgba(196, 93, 43, 0.04); border-color: rgba(196, 93, 43, 0.15); }

.msg-indicator { flex-shrink: 0; padding-top: 5px; }
.unread-dot { display: inline-block; width: 8px; height: 8px; background: var(--al-danger-bright); border-radius: 50%; }

.msg-main { flex: 1; min-width: 0; }
.msg-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.msg-title { font-size: 0.95rem; font-weight: 600; color: var(--anime-text-main); }
.msg-type { font-size: 11px; padding: 1px 8px; border-radius: 999px; }
.type-update { background: rgba(196, 93, 43, 0.12); color: var(--anime-accent-red); }
.type-system { background: var(--al-border-neutral); color: var(--anime-text-secondary); }
.msg-content { margin: 0 0 6px; font-size: 0.88rem; color: var(--anime-text-secondary); line-height: 1.55; }
.msg-time { font-size: 12px; color: var(--anime-text-secondary); opacity: 0.75; display: inline-flex; align-items: center; gap: 4px; }

.msg-delete {
  flex-shrink: 0; border: none; background: none;
  color: var(--al-gray-faint); font-size: 18px; cursor: pointer;
  padding: 4px; border-radius: 8px; transition: all 0.2s;
  opacity: 0;
}
.message-card:hover .msg-delete { opacity: 1; }
.msg-delete:hover { color: var(--al-danger); background: var(--al-danger-soft); }
</style>
