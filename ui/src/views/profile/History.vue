<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPlayHistory, removePlayHistory, clearPlayHistory } from '../../api/playHistory'
import PaginationBar from '../../components/PaginationBar.vue'
import { showAppMessage, askAppConfirm } from '../../utils/ui-feedback'
import { usePagination } from '../../composables/usePagination'
import { DEFAULT_POSTER } from '../../utils/constants'
import { formatMonthDayTime } from '../../utils/format'
import { clampProgressPercent, formatPlayProgressText } from '../../utils/playProgress'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)

const progressPercent = (item) => clampProgressPercent(item?.progressPercentage)

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const res = await getPlayHistory({ page: page.value, pageSize: pageSize.value })
    if (res?.code === 200) {
      list.value = res.data?.content || []
      total.value = Number(res.data?.totalElements || 0)
    } else error.value = res?.msg || '加载播放历史失败'
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
  const ok = await askAppConfirm({ title: '删除播放历史', message: '确定删除这条播放历史吗？' })
  if (!ok) return
  try {
    const res = await removePlayHistory(id)
    if (res?.code === 200) {
      if (list.value.length === 1 && page.value > 1) page.value -= 1
      await fetchData()
    } else showAppMessage(res?.msg || '删除失败', 'error')
  } catch (e) { console.error('删除播放历史失败:', e); showAppMessage('删除播放历史失败', 'error') }
}

const clearAll = async () => {
  const ok = await askAppConfirm({ title: '清空播放历史', message: '确定清空所有播放历史吗？该操作不可恢复。', color: 'error' })
  if (!ok) return
  try {
    const res = await clearPlayHistory()
    if (res?.code === 200) {
      page.value = 1
      await fetchData()
    } else showAppMessage(res?.msg || '清空失败', 'error')
  } catch (e) { console.error('清空播放历史失败:', e); showAppMessage('清空播放历史失败', 'error') }
}

const { page, pageSize, totalPages, pages, changePage } = usePagination({
  pageSize: 12,
  getTotal: () => total.value,
  onPageChange: fetchData,
})

const progressText = (item) => formatPlayProgressText(item)

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
        <div class="hc-poster" @click="goToAnime(item)">
          <img :src="item.imageUrl || DEFAULT_POSTER" :alt="item.animeTitle" loading="lazy" />
          <div class="hc-poster-shade"></div>
          <div class="hc-poster-play"><i class="mdi mdi-play"></i></div>
          <div class="hc-poster-progress">
            <div class="hc-poster-progress-fill" :style="{ width: progressPercent(item) + '%' }"></div>
          </div>
        </div>

        <div class="hc-body">
          <h3 class="hc-title" @click="goToAnime(item)">{{ item.animeTitle || `番剧 #${item.animeId}` }}</h3>
          <p class="hc-episode">{{ item.videoName || `视频 #${item.videoId || '-'}` }}</p>

          <div class="hc-meta">
            <span class="hc-time"><i class="mdi mdi-clock-outline"></i> {{ formatMonthDayTime(item.lastPlayTime) }}</span>
            <span class="hc-progress-text">{{ progressText(item) }}</span>
          </div>

          <div class="hc-actions">
            <button class="btn btn-primary btn-sm" @click="goToPlayer(item)"><i class="mdi mdi-play"></i> 继续播放</button>
            <button class="btn btn-ghost btn-sm btn-sm--icon" title="删除记录" @click="deleteItem(item.id)">
              <i class="mdi mdi-delete-outline"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    <PaginationBar :page="page" :total-pages="totalPages" :pages="pages" :total-text="`共 ${total} 条`" @change="changePage" />
  </div>
</template>

<style scoped>
.history-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.sk-row {
  height: 108px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
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

.history-list { display: flex; flex-direction: column; gap: 14px; }

.history-card {
  display: flex;
  gap: 16px;
  background: var(--al-bg);
  border: 1px solid var(--al-border-panel);
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s, transform 0.25s, border-color 0.25s;
}
.history-card:hover {
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.08);
  border-color: rgba(196, 93, 43, 0.2);
  transform: translateY(-2px);
}

/* 封面 */
.hc-poster {
  position: relative;
  flex-shrink: 0;
  width: 108px;
  aspect-ratio: 2 / 3;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  background: var(--anime-bg-beige);
}
.hc-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
  transition: transform 0.4s ease;
}
.history-card:hover .hc-poster img { transform: scale(1.05); }

.hc-poster-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.42) 0%, transparent 45%);
  pointer-events: none;
}

.hc-poster-play {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.history-card:hover .hc-poster-play { opacity: 1; }

/* 封面上进度条 */
.hc-poster-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
}
.hc-poster-progress-fill {
  height: 100%;
  background: var(--anime-accent-red);
  border-radius: 0 999px 999px 0;
  transition: width 0.4s ease;
}

/* 信息区 */
.hc-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.hc-title {
  margin: 0 0 6px;
  font-size: 1.04rem;
  line-height: 1.35;
  color: var(--anime-text-main);
  cursor: pointer;
  transition: color 0.2s;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.hc-title:hover { color: var(--anime-accent-red); }

.hc-episode {
  margin: 0 0 10px;
  color: var(--anime-text-secondary);
  font-size: 0.86rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hc-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.hc-time {
  font-size: 12px;
  color: var(--anime-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.hc-progress-text {
  font-size: 12px;
  color: var(--anime-accent-red);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.hc-actions {
  margin-top: auto;
  display: flex;
  gap: 8px;
}

/* 小号按钮 */
.btn-sm {
  padding: 7px 13px;
  font-size: 12px;
  border-radius: 9px;
}
.btn-sm--icon { padding: 7px 10px; }

@media (max-width: 600px) {
  .hc-poster { width: 92px; }
  .history-card { gap: 12px; padding: 12px; }
  .hc-actions { flex-wrap: wrap; }
}
</style>
