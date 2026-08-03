<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pages = computed(() => {
  const t = totalPages.value
  const cur = page.value
  const arr = []
  const start = Math.max(1, Math.min(cur - 2, t - 4))
  for (let i = start; i <= Math.min(t, start + 4); i++) arr.push(i)
  return arr
})

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const res = await axios.get('/api/play-history', {
      params: { page: page.value, pageSize: pageSize.value }
    })
    if (res.data?.code === 200) {
      list.value = res.data.data?.content || []
      total.value = Number(res.data.data?.totalElements || 0)
    } else error.value = res.data?.msg || '加载播放历史失败'
  } catch (e) { console.error('加载播放历史失败:', e); error.value = '加载播放历史失败' }
  finally { loading.value = false }
}

const goToPlayer = (item) => {
  if (!item?.videoId) return
  router.push({
    name: 'Player',
    params: { videoId: String(item.videoId) },
    query: {
      animeId: String(item.animeId || ''),
      episodeId: String(item.episodeId || '')
    }
  })
}

const goToAnime = (item) => {
  if (item?.animeId) router.push(`/anime/${item.animeId}`)
}

const deleteItem = async (id) => {
  if (!id) return
  const ok = window.confirm('确定删除这条播放历史吗？')
  if (!ok) return
  try {
    const res = await axios.delete(`/api/play-history/${id}`)
    if (res.data?.code === 200) {
      if (list.value.length === 1 && page.value > 1) page.value -= 1
      await fetchData()
    } else showAppMessage(res.data?.msg || '删除失败', 'error')
  } catch (e) { console.error('删除播放历史失败:', e); showAppMessage('删除播放历史失败', 'error') }
}

const clearAll = async () => {
  const ok = window.confirm('确定清空所有播放历史吗？该操作不可恢复。')
  if (!ok) return
  try {
    const res = await axios.delete('/api/play-history/clear')
    if (res.data?.code === 200) {
      page.value = 1
      await fetchData()
    } else showAppMessage(res.data?.msg || '清空失败', 'error')
  } catch (e) { console.error('清空播放历史失败:', e); showAppMessage('清空播放历史失败', 'error') }
}

const changePage = (p) => {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  fetchData()
}

const formatTime = (v) => {
  if (!v) return '--'
  return new Date(v).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

const progressText = (item) => {
  const p = Number(item?.progressSeconds || 0)
  const d = Number(item?.durationSeconds || 0)
  const percent = Number(item?.progressPercentage || 0)
  if (!d) return `${p}s`
  return `${p}s / ${d}s · ${Math.min(100, Math.max(0, percent))}%`
}

onMounted(fetchData)
</script>

<template>
  <div class="history-page">
    <div class="page-head">
      <h2><i class="mdi mdi-history"></i> 观看历史</h2>
      <div class="page-head-actions">
        <button class="btn btn-danger" :disabled="loading || total === 0" @click="clearAll">
          <i class="mdi mdi-delete-sweep-outline"></i> 清空历史
        </button>
      </div>
    </div>

    <div v-if="loading" class="br-sk-grid">
      <div v-for="i in 6" :key="i" class="sk-row"></div>
    </div>

    <div v-else-if="error" class="br-empty error"><i class="mdi mdi-alert-circle-outline"></i> {{ error }}</div>

    <div v-else-if="!list.length" class="empty-state">
      <i class="mdi mdi-history"></i>
      <p>还没有观看记录</p>
      <router-link to="/search" class="btn btn-primary"><i class="mdi mdi-magnify"></i> 去发现番剧</router-link>
    </div>

    <div v-else class="history-list">
      <div v-for="item in list" :key="item.id" class="history-card">
        <div class="history-info">
          <div class="history-title-row">
            <h3 @click="goToAnime(item)">{{ item.animeTitle || `番剧 #${item.animeId}` }}</h3>
            <span class="history-time"><i class="mdi mdi-clock-outline"></i> {{ formatTime(item.lastPlayTime) }}</span>
          </div>
          <p class="history-sub">{{ item.videoName || `视频 #${item.videoId || '-'}` }}</p>
          <div class="progress-row">
            <div class="progress-track">
              <div
                class="progress-fill"
                :style="{ width: Math.min(100, Math.max(0, Number(item.progressPercentage || 0))) + '%' }"
              ></div>
            </div>
            <span class="progress-text">{{ progressText(item) }}</span>
          </div>
        </div>
        <div class="history-actions">
          <button class="btn btn-primary" @click="goToPlayer(item)"><i class="mdi mdi-play"></i> 继续播放</button>
          <button class="btn btn-ghost" @click="deleteItem(item.id)"><i class="mdi mdi-delete-outline"></i></button>
        </div>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)"><i class="mdi mdi-chevron-left"></i></button>
      <button v-for="p in pages" :key="p" :class="{ active: p === page }" @click="changePage(p)">{{ p }}</button>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)"><i class="mdi mdi-chevron-right"></i></button>
      <span class="info">共 {{ total }} 条</span>
    </div>
  </div>
</template>

<style scoped>
.history-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.sk-row {
  height: 108px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, #ede3d8 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 70px 20px;
  color: var(--anime-text-secondary);
}
.empty-state i { font-size: 3rem; opacity: 0.35; color: var(--anime-accent-red); }
.empty-state p { margin: 0; font-size: 14px; }

.history-list { display: flex; flex-direction: column; gap: 12px; }

.history-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
}
.history-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.07);
  border-color: rgba(196, 93, 43, 0.18);
}

.history-info { flex: 1; min-width: 0; }
.history-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.history-title-row h3 {
  margin: 0 0 4px;
  font-size: 1.02rem;
  color: var(--anime-text-main);
  cursor: pointer;
  transition: color 0.2s;
}
.history-title-row h3:hover { color: var(--anime-accent-red); }
.history-time { font-size: 12px; color: var(--anime-text-secondary); display: inline-flex; align-items: center; gap: 4px; }
.history-sub { margin: 0 0 10px; color: var(--anime-text-secondary); font-size: 0.88rem; }

.progress-row { display: flex; align-items: center; gap: 12px; }
.progress-track { flex: 1; height: 6px; border-radius: 999px; background: #f0f0f0; overflow: hidden; }
.progress-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--anime-accent-red), #e08a4e); transition: width 0.4s ease; }
.progress-text { font-size: 12px; color: var(--anime-text-secondary); font-variant-numeric: tabular-nums; white-space: nowrap; }

.history-actions { display: flex; gap: 8px; flex-shrink: 0; }

@media (max-width: 600px) {
  .history-card { flex-direction: column; align-items: stretch; }
  .history-actions { justify-content: flex-end; }
}
</style>
