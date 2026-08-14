<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { usePagination } from '../../composables/usePagination'
import { DEFAULT_POSTER, API_BASE } from '../../utils/constants'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)

const danmakuHex = (record) => {
  const c = Number(record?.color)
  if (!Number.isFinite(c) || c <= 0) return ''
  return '#' + c.toString(16).padStart(6, '0')
}

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const res = await axios.get(`${API_BASE}/v2/danmaku-records/mine`, {
      params: { page: page.value, pageSize: pageSize.value }
    })
    if (res.data?.code === 200) {
      list.value = res.data.data?.content || []
      total.value = Number(res.data.data?.totalElements || 0)
    } else error.value = res.data?.msg || '加载弹幕记录失败'
  } catch (e) { console.error('加载弹幕记录失败:', e); error.value = '加载弹幕记录失败' }
  finally { loading.value = false }
}

const { page, pageSize, totalPages, pages, changePage } = usePagination({
  pageSize: 20,
  getTotal: () => total.value,
  onPageChange: fetchData,
})

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
        <div class="dm-poster" @click="goToAnime(record)">
          <img :src="record.imageUrl || DEFAULT_POSTER" :alt="record.animeTitle" loading="lazy" />
          <div class="dm-poster-shade"></div>
          <div class="dm-poster-play"><i class="mdi mdi-play"></i></div>
        </div>

        <div class="dm-content">
          <div class="dm-text-row">
            <span class="dm-color" :style="{ background: danmakuHex(record) || '#d1d5db' }" title="弹幕颜色"></span>
            <p class="dm-text">
              {{ record.comment }}
            </p>
          </div>
          <div class="dm-meta">
            <span class="dm-anime" @click="goToAnime(record)">{{ record.animeTitle || `番剧 #${record.animeId}` }}</span>
            <span class="dm-ep">{{ record.episodeTitle || `#${record.episodeId}` }}</span>
            <span class="dm-mode">{{ modeLabel(record.mode) }}</span>
          </div>
          <div class="dm-time"><i class="mdi mdi-clock-outline"></i> {{ formatTime(record.createdAt) }}</div>
        </div>

        <div class="dm-side">
          <span class="dm-pos"><i class="mdi mdi-timer-outline"></i> {{ formatPos(record.time) }}</span>
          <button class="btn btn-ghost btn-sm" @click="goToPlayer(record)"><i class="mdi mdi-play"></i> 定位</button>
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
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 70px 20px; color: var(--anime-text-secondary);
}
.empty-state i { font-size: 3rem; opacity: 0.35; color: var(--anime-accent-red); }
.empty-state p { margin: 0; font-size: 14px; }

.danmaku-list { display: flex; flex-direction: column; gap: 12px; }

.danmaku-card {
  display: flex;
  align-items: stretch;
  gap: 14px;
  background: var(--al-bg);
  border: 1px solid var(--al-border-panel);
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
}
.danmaku-card:hover {
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.08);
  border-color: rgba(196, 93, 43, 0.2);
  transform: translateY(-2px);
}

/* 封面缩略图 */
.dm-poster {
  position: relative;
  flex-shrink: 0;
  width: 86px;
  aspect-ratio: 2 / 3;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  background: var(--anime-bg-beige);
}
.dm-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
  transition: transform 0.4s ease;
}
.danmaku-card:hover .dm-poster img { transform: scale(1.05); }

.dm-poster-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.4) 0%, transparent 50%);
  pointer-events: none;
}

.dm-poster-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.danmaku-card:hover .dm-poster-play { opacity: 1; }

.dm-content { flex: 1; min-width: 0; display: flex; flex-direction: column; }

/* 弹幕文本行：颜色标签紧贴文本 */
.dm-text-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

/* 弹幕颜色小标签 */
.dm-color {
  flex-shrink: 0;
  width: 13px;
  height: 13px;
  margin-top: 5px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}

.dm-text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 1rem;
  font-weight: 500;
  line-height: 1.5;
  color: var(--anime-text-main);
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.dm-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12px; color: var(--anime-text-secondary); }
.dm-anime { font-weight: 600; color: var(--anime-text-main); cursor: pointer; transition: color 0.2s; }
.dm-anime:hover { color: var(--anime-accent-red); }
.dm-ep { opacity: 0.85; }
.dm-mode { background: var(--al-border-neutral); color: var(--anime-text-secondary); padding: 1px 8px; border-radius: 999px; font-size: 11px; }
.dm-time {
  margin-top: auto;
  padding-top: 8px;
  font-size: 12px;
  color: var(--anime-text-secondary);
  opacity: 0.8;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dm-side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
}
.dm-pos { font-size: 12px; color: var(--anime-accent-red); font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }

.btn-sm { padding: 7px 13px; font-size: 12px; border-radius: 9px; }

@media (max-width: 600px) {
  .dm-poster { width: 76px; }
  .danmaku-card { gap: 12px; padding: 12px; }
  .dm-side { justify-content: flex-end; }
}
</style>
