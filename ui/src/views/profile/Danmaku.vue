<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pages = computed(() => {
  const t = totalPages.value
  const cur = page.value
  const start = Math.max(1, Math.min(cur - 2, t - 4))
  const arr = []
  for (let i = start; i <= Math.min(t, start + 4); i++) arr.push(i)
  return arr
})

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const res = await axios.get('/api/v2/danmaku-records/mine', {
      params: { page: page.value, pageSize: pageSize.value }
    })
    if (res.data?.code === 200) {
      list.value = res.data.data?.content || []
      total.value = Number(res.data.data?.totalElements || 0)
    } else error.value = res.data?.msg || '加载弹幕记录失败'
  } catch (e) { console.error('加载弹幕记录失败:', e); error.value = '加载弹幕记录失败' }
  finally { loading.value = false }
}

const changePage = (p) => {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  fetchData()
}

const goToPlayer = (record) => {
  if (record.videoId) {
    router.push({
      name: 'Player',
      params: { videoId: String(record.videoId) },
      query: {
        animeId: record.animeId ? String(record.animeId) : undefined,
        episodeId: record.episodeId ? String(record.episodeId) : undefined,
        t: record.time != null ? String(record.time) : undefined
      }
    })
  } else if (record.animeId) {
    router.push(`/anime/${record.animeId}`)
  }
}

const goToAnime = (record) => {
  if (record?.animeId) router.push(`/anime/${record.animeId}`)
}

const modeLabel = (mode) => {
  const map = { 1: '普通', 4: '底部', 5: '顶部' }
  return map[mode] || `模式${mode}`
}

const formatTime = (v) => {
  if (!v) return '--'
  return new Date(v).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

const formatPos = (seconds) => {
  if (seconds == null) return '--'
  const s = Math.floor(seconds)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`
}

onMounted(fetchData)
</script>

<template>
  <div class="danmaku-page">
    <div class="page-head">
      <h2><i class="mdi mdi-comment-text-multiple"></i> 我的弹幕</h2>
      <span class="sub">共 {{ total }} 条弹幕记录</span>
    </div>

    <div v-if="loading" class="br-sk-grid">
      <div v-for="i in 6" :key="i" class="sk-row"></div>
    </div>
    <div v-else-if="error" class="br-empty error"><i class="mdi mdi-alert-circle-outline"></i> {{ error }}</div>
    <div v-else-if="!list.length" class="empty-state">
      <i class="mdi mdi-comment-off-outline"></i>
      <p>还没有发过弹幕</p>
      <router-link to="/search" class="btn btn-primary"><i class="mdi mdi-compass"></i> 去发现番剧</router-link>
    </div>
    <div v-else class="danmaku-list">
      <div v-for="record in list" :key="record.id" class="danmaku-card">
        <div class="dm-content">
          <p class="dm-text" :style="record.color ? { color: '#' + Number(record.color).toString(16).padStart(6, '0') } : {}">
            {{ record.comment }}
          </p>
          <div class="dm-meta">
            <span class="dm-anime" @click="goToAnime(record)">{{ record.animeTitle || `番剧 #${record.animeId}` }}</span>
            <span class="dm-ep">{{ record.episodeTitle || `#${record.episodeId}` }}</span>
            <span class="dm-mode">{{ modeLabel(record.mode) }}</span>
            <span class="dm-time"><i class="mdi mdi-clock-outline"></i> {{ formatTime(record.createdAt) }}</span>
          </div>
        </div>
        <div class="dm-actions">
          <span class="dm-pos"><i class="mdi mdi-timer-outline"></i> {{ formatPos(record.time) }}</span>
          <button class="btn btn-ghost" @click="goToPlayer(record)"><i class="mdi mdi-play"></i> 定位</button>
        </div>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)"><i class="mdi mdi-chevron-left"></i></button>
      <button v-for="p in pages" :key="p" :class="{ active: p === page }" @click="changePage(p)">{{ p }}</button>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)"><i class="mdi mdi-chevron-right"></i></button>
    </div>
  </div>
</template>

<style scoped>
.danmaku-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.sk-row {
  height: 96px; border-radius: 14px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, #ede3d8 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 70px 20px; color: var(--anime-text-secondary);
}
.empty-state i { font-size: 3rem; opacity: 0.35; color: var(--anime-accent-red); }
.empty-state p { margin: 0; font-size: 14px; }

.danmaku-list { display: flex; flex-direction: column; gap: 10px; }
.danmaku-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  background: #fff; border: 1px solid #eceff3; border-radius: 14px;
  padding: 14px 18px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s, border-color 0.25s;
}
.danmaku-card:hover { box-shadow: 0 8px 24px rgba(0, 0, 0, 0.07); border-color: rgba(196, 93, 43, 0.18); }

.dm-content { flex: 1; min-width: 0; }
.dm-text {
  margin: 0 0 6px; font-size: 0.98rem; font-weight: 500;
  color: var(--anime-text-main); word-break: break-word;
}
.dm-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12px; color: var(--anime-text-secondary); }
.dm-anime { font-weight: 600; color: var(--anime-text-main); cursor: pointer; transition: color 0.2s; }
.dm-anime:hover { color: var(--anime-accent-red); }
.dm-ep { opacity: 0.85; }
.dm-mode { background: #f0f0f0; color: var(--anime-text-secondary); padding: 1px 8px; border-radius: 999px; font-size: 11px; }
.dm-time { display: inline-flex; align-items: center; gap: 4px; opacity: 0.75; }

.dm-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.dm-pos { font-size: 12px; color: var(--anime-accent-red); font-weight: 600; font-variant-numeric: tabular-nums; }

@media (max-width: 600px) {
  .danmaku-card { flex-direction: column; align-items: stretch; }
  .dm-actions { justify-content: flex-end; }
}
</style>
