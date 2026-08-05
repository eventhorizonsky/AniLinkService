<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { formatAnimeType } from '../utils/animeType'

const route = useRoute()
const router = useRouter()
const API_BASE = '/api'

const activeTab = ref('library')

// ===================== Library =====================
const libLoading = ref(false)
const libLoadingMore = ref(false)
const libError = ref('')
const libKeyword = ref('')
const libTotal = ref(0)
const libList = ref([])
const libPage = ref(1)
const libHasMore = ref(false)
const libScrollEl = ref(null)
const libPageSize = 24

const libHasResult = computed(() => libList.value.length > 0)

const buildMeta = (anime) => {
  const parts = []
  if (anime?.type) parts.push(formatAnimeType(anime.type))
  if (anime?.animeId) parts.push(`ID: ${anime.animeId}`)
  return parts.length ? parts.join(' | ') : ''
}

const fetchLibrary = async (append = false) => {
  if (append) libLoadingMore.value = true
  else { libLoading.value = true; libError.value = '' }

  try {
    const params = { page: libPage.value, pageSize: libPageSize }
    if (libKeyword.value.trim()) params.keyword = libKeyword.value.trim()
    const res = await axios.get(`${API_BASE}/animes`, { params })
    if (res.data?.code !== 200) throw new Error(res.data?.msg || '请求失败')
    const data = res.data?.data
    const items = Array.isArray(data?.content) ? data.content : []
    if (append) libList.value.push(...items)
    else libList.value = items
    libTotal.value = Number(data?.totalElements || 0)
    const totalPages = Number(data?.totalPages || 0)
    libHasMore.value = libPage.value < totalPages
  } catch (e) {
    libError.value = e?.response?.data?.msg || e?.message || '加载失败'
    if (!append) { libList.value = []; libTotal.value = 0; libHasMore.value = false }
  } finally {
    if (append) libLoadingMore.value = false
    else libLoading.value = false
  }
}

const libSearch = () => {
  libPage.value = 1
  const q = {}
  if (libKeyword.value.trim()) q.q = libKeyword.value.trim()
  router.push({ path: '/search', query: q })
}

const onLibScroll = () => {
  const el = libScrollEl.value
  if (!el || libLoadingMore.value || !libHasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60) {
    libPage.value++
    fetchLibrary(true)
  }
}

// ===================== Database =====================
const dbLoading = ref(false)
const dbError = ref('')
const dbSeasons = ref([])
const dbYear = ref(null)
const dbMonth = ref(null)
const dbList = ref([])

const dbYears = computed(() => [...new Set(dbSeasons.value.map(s => s.year))].sort((a, b) => b - a))
const dbMonths = computed(() => {
  if (dbYear.value == null) return []
  return dbSeasons.value.filter(s => s.year === dbYear.value).map(s => s.month).sort((a, b) => a - b)
})

const fetchSeasons = async () => {
  try {
    const res = await axios.get(`${API_BASE}/v2/bangumi/season/anime`)
    const data = res.data
    if (Array.isArray(data?.seasons)) dbSeasons.value = data.seasons
    else if (Array.isArray(data)) dbSeasons.value = data
    if (dbSeasons.value.length) {
      const latest = dbSeasons.value.reduce((a, b) =>
        b.year > a.year || (b.year === a.year && b.month > a.month) ? b : a)
      dbYear.value = latest.year; dbMonth.value = latest.month
      await fetchSeasonAnime()
    }
  } catch (e) { dbError.value = '获取季度列表失败'; console.error(e) }
}

const fetchSeasonAnime = async () => {
  if (dbYear.value == null || dbMonth.value == null) return
  dbLoading.value = true; dbError.value = ''
  try {
    const res = await axios.get(`${API_BASE}/v2/bangumi/season/anime/${dbYear.value}/${dbMonth.value}`)
    const data = res.data
    if (Array.isArray(data?.bangumiList)) dbList.value = data.bangumiList
    else if (Array.isArray(data)) dbList.value = data
    else dbList.value = []
  } catch (e) { dbError.value = '获取季度番剧失败'; dbList.value = [] }
  finally { dbLoading.value = false }
}

const selectSeason = async (year, month) => { dbYear.value = year; dbMonth.value = month; await fetchSeasonAnime() }
const toAnime = (id) => { if (id) router.push(`/anime/${id}`) }

// Season label
const seasonLabels = { 1:'冬季', 4:'春季', 7:'夏季', 10:'秋季' }
const seasonLabel = computed(() => {
  if (dbYear.value == null || dbMonth.value == null) return ''
  const s = seasonLabels[dbMonth.value] || `${dbMonth.value}月`
  return `${dbYear.value}年${s}`
})

const syncAndFetch = () => {
  libKeyword.value = String(route.query.q || '')
  libPage.value = 1
  fetchLibrary(false)
}

watch(() => route.query.q, () => syncAndFetch())

onMounted(() => {
  syncAndFetch()
  fetchSeasons()
})
</script>

<template>
  <div class="discover-root">
    <!-- ====== 页面头部 ====== -->
    <div class="page-head">
      <h2><i class="mdi mdi-compass"></i> 发现</h2>
      <span class="sub">浏览媒体库与番剧资料库</span>
    </div>

    <!-- ====== Tab Bar ====== -->
    <div class="discover-tabs">
      <button class="discover-tab" :class="{ active: activeTab === 'library' }" @click="activeTab = 'library'">
        <i class="mdi mdi-filmstrip-box-multiple"></i>媒体库检索
      </button>
      <button class="discover-tab" :class="{ active: activeTab === 'database' }" @click="activeTab = 'database'">
        <i class="mdi mdi-database-search"></i>番剧资料库
      </button>
    </div>

    <!-- ============================ LIBRARY ============================ -->
    <div v-show="activeTab === 'library'" class="tab-content">
      <div class="toolbar">
        <div class="toolbar-row">
          <div class="search-box">
            <i class="mdi mdi-magnify"></i>
            <input v-model="libKeyword" type="text" placeholder="搜索本地媒体库..."
              @keyup.enter="libSearch" />
            <button v-if="libKeyword" class="search-clear" @click="libKeyword=''; libSearch()">
              <i class="mdi mdi-close-circle"></i>
            </button>
          </div>
          <button class="btn-search" @click="libSearch" :disabled="libLoading">
            <i class="mdi mdi-magnify"></i>搜索
          </button>
        </div>
        <div class="toolbar-meta" v-if="libHasResult && !libLoading">
          共 <strong>{{ libTotal }}</strong> 条结果
        </div>
      </div>

      <div ref="libScrollEl" class="scroll-area" @scroll="onLibScroll">
        <div v-if="libLoading" class="sk-grid"><div v-for="i in 12" :key="i" class="sk-card"></div></div>
        <div v-else-if="libError" class="empty-block error"><i class="mdi mdi-alert-circle"></i>{{ libError }}</div>
        <div v-else-if="!libHasResult && libKeyword" class="empty-block">
          <i class="mdi mdi-movie-open-off-outline empty-icon"></i>
          <p class="empty-title">没有找到匹配的动漫</p>
          <p class="empty-hint">试试其他关键词，或切换到"番剧资料库"浏览</p>
        </div>
        <div v-else-if="!libHasResult" class="empty-block">
          <i class="mdi mdi-magnify empty-icon"></i>
          <p class="empty-title">输入关键词搜索本地媒体库</p>
          <p class="empty-hint">可搜索动漫标题</p>
        </div>
        <template v-else>
          <div class="result-grid">
            <router-link v-for="a in libList" :key="a.id || a.animeId" :to="'/anime/' + a.animeId" class="rc-card">
              <div class="rc-poster">
                <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.title" loading="lazy" />
                <div v-else class="rc-no-img"><i class="mdi mdi-image-off"></i></div>
                <div class="rc-gradient"></div>
                <div class="rc-hover"><i class="mdi mdi-play-circle-outline"></i></div>
              </div>
              <div class="rc-body">
                <p class="rc-title" :title="a.title">{{ a.title || '未命名动漫' }}</p>
                <span v-if="buildMeta(a)" class="rc-meta">{{ buildMeta(a) }}</span>
              </div>
            </router-link>
          </div>
          <div v-if="libLoadingMore" class="load-more"><i class="mdi mdi-loading mdi-spin"></i> 加载更多...</div>
          <div v-else-if="!libHasMore && libList.length > libPageSize" class="load-more load-done">— 已加载全部 {{ libTotal }} 条 —</div>
        </template>
      </div>
    </div>

    <!-- ============================ DATABASE ============================ -->
    <div v-show="activeTab === 'database'" class="tab-content">
      <div class="toolbar">
        <div class="toolbar-row">
          <div class="season-selects">
            <div class="ss-field">
              <label><i class="mdi mdi-calendar"></i></label>
              <select v-model="dbYear" @change="selectSeason(dbYear, dbMonth)">
                <option v-for="y in dbYears" :key="y" :value="y">{{ y }}</option>
              </select>
            </div>
            <span class="ss-sep">年</span>
            <div class="ss-field">
              <label><i class="mdi mdi-calendar-month"></i></label>
              <select v-model="dbMonth" @change="selectSeason(dbYear, dbMonth)">
                <option v-for="m in dbMonths" :key="m" :value="m">{{ m }}月</option>
              </select>
            </div>
          </div>
          <div class="toolbar-info" v-if="seasonLabel && !dbLoading">
            <span class="toolbar-season">{{ seasonLabel }}</span>
            <span class="toolbar-count" v-if="dbList.length">{{ dbList.length }} 部</span>
          </div>
        </div>
      </div>

      <div class="scroll-area">
        <div v-if="dbLoading" class="sk-grid"><div v-for="i in 12" :key="i" class="sk-card"></div></div>
        <div v-else-if="dbError" class="empty-block error"><i class="mdi mdi-alert-circle"></i>{{ dbError }}</div>
        <div v-else-if="!dbSeasons.length" class="empty-block">
          <i class="mdi mdi-database-off-outline empty-icon"></i>
          <p class="empty-title">番剧资料库暂不可用</p>
          <p class="empty-hint">请检查弹弹 API 配置</p>
        </div>
        <div v-else-if="!dbList.length" class="empty-block">
          <i class="mdi mdi-movie-open-off-outline empty-icon"></i>
          <p class="empty-title">该季度暂无番剧</p>
        </div>
        <div v-else class="result-grid">
          <router-link v-for="a in dbList" :key="a.animeId" :to="'/anime/' + a.animeId" class="rc-card">
            <div class="rc-poster">
              <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.animeTitle" loading="lazy" decoding="async" />
              <div v-else class="rc-no-img"><i class="mdi mdi-image-off"></i></div>
              <div class="rc-gradient"></div>
              <span class="rc-badge" v-if="a.rating"><i class="mdi mdi-star"></i>{{ Number(a.rating).toFixed(1) }}</span>
              <div class="rc-hover"><i class="mdi mdi-play-circle-outline"></i></div>
            </div>
            <div class="rc-body">
              <p class="rc-title" :title="a.animeTitle">{{ a.animeTitle }}</p>
            </div>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========================= ROOT ========================= */
.discover-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
  animation: discover-in 0.35s ease-out;
}
@keyframes discover-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

/* ========================= TABS ========================= */
.discover-tabs {
  display: flex;
  gap: 6px;
  background: #f4f4f5;
  border-radius: 12px;
  padding: 4px;
  width: fit-content;
  flex-shrink: 0;
}
.discover-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: none;
  background: transparent;
  color: var(--anime-text-secondary);
  padding: 10px 22px;
  border-radius: 9px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.discover-tab:hover { color: var(--anime-accent-red); }
.discover-tab.active {
  background: #fff;
  color: var(--anime-accent-red);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.discover-tab i { font-size: 1.05rem; }

/* ========================= TAB CONTENT ========================= */
.tab-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ========================= TOOLBAR ========================= */
.toolbar {
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 14px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-meta {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  font-size: 0.78rem;
  color: var(--anime-text-secondary);
}
.toolbar-meta strong { color: var(--anime-text-main); font-weight: 700; }
.toolbar-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.toolbar-season { font-size: 0.88rem; font-weight: 700; color: var(--anime-text-main); }
.toolbar-count  { font-size: 0.76rem; color: var(--anime-text-secondary); background: var(--anime-bg-beige); padding: 2px 10px; border-radius: 999px; }

/* ========================= SCROLL AREA ========================= */
.scroll-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: visible;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 2px;
  max-height: calc(100vh - 300px);
  -webkit-overflow-scrolling: touch;
}

/* ---- search box ---- */
.search-box {
  flex: 1;
  min-width: 200px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f9fafb;
  border: 1.5px solid #e5e7eb;
  border-radius: 999px;
  padding: 0 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.search-box:focus-within {
  border-color: var(--anime-accent-red);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12);
}
.search-box i { color: #9ca3af; font-size: 1rem; flex-shrink: 0; }
.search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.9rem;
  color: var(--anime-text-main);
  padding: 10px 0;
  font-family: inherit;
}
.search-box input::placeholder { color: #9ca3af; }
.search-clear { border: none; background: none; color: #9ca3af; cursor: pointer; padding: 2px; font-size: 0.9rem; }
.search-clear:hover { color: var(--anime-accent-red); }

.btn-search {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: var(--anime-accent-red);
  color: #fff;
  font-weight: 600;
  padding: 0 22px;
  border-radius: 999px;
  font-size: 0.88rem;
  cursor: pointer;
  transition: background 0.2s;
  font-family: inherit;
  height: 42px;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(196, 93, 43, 0.3);
}
.btn-search:hover:not(:disabled) { background: #a65628; }
.btn-search:disabled { opacity: 0.6; cursor: not-allowed; }

/* ---- season selects ---- */
.season-selects { display: flex; align-items: center; gap: 6px; }
.ss-field { display: flex; align-items: center; gap: 6px; }
.ss-field label { font-size: 0.95rem; color: var(--anime-text-secondary); }
.ss-field select {
  border: 1.5px solid #e5e7eb;
  border-radius: 10px;
  padding: 9px 32px 9px 12px;
  font-size: 0.9rem;
  color: var(--anime-text-main);
  background: #fff;
  cursor: pointer;
  outline: none;
  font-family: inherit;
  min-width: 80px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath d='M6 8L1 3h10z' fill='%238b6f5e'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  transition: border-color 0.2s;
}
.ss-field select:focus { border-color: var(--anime-accent-red); }
.ss-sep { font-size: 0.85rem; color: var(--anime-text-secondary); font-weight: 500; }

/* ========================= RESULT GRID ========================= */
.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 14px;
}

/* ========================= RESULT CARD ========================= */
.rc-card {
  display: block;
  text-decoration: none;
  color: inherit;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s, border-color 0.25s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.rc-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.1);
  border-color: rgba(196, 93, 43, 0.2);
  z-index: 5;
  position: relative;
}

.rc-poster {
  position: relative;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  background: var(--anime-bg-beige);
}
.rc-poster img { width: 100%; height: 100%; object-fit: cover; object-position: center top; transition: transform 0.5s ease; }
.rc-card:hover .rc-poster img { transform: scale(1.04); }
.rc-no-img {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: #c3b7ab; font-size: 2rem;
  background: linear-gradient(135deg, #f4eee7, #e8e0d6);
}
.rc-gradient {
  position: absolute; inset: auto 0 0 0; height: 40%;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.4), transparent);
  pointer-events: none;
}
.rc-badge {
  position: absolute; top: 8px; left: 8px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  color: #fbbf24; font-size: 0.7rem; font-weight: 700;
  padding: 2px 9px; border-radius: 999px;
  display: inline-flex; align-items: center; gap: 3px;
}
.rc-badge i { font-size: 0.6rem; }

.rc-hover {
  position: absolute; inset: 0;
  background: rgba(0, 0, 0, 0.25);
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s; pointer-events: none;
}
.rc-hover i { font-size: 2rem; color: #fff; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3)); }
.rc-card:hover .rc-hover { opacity: 1; }

.rc-body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.rc-title {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--anime-text-main);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.3em;
}
.rc-meta { font-size: 0.68rem; color: var(--anime-text-secondary); font-weight: 500; }

/* ========================= EMPTY BLOCK ========================= */
.empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 20px;
  color: var(--anime-text-secondary);
  text-align: center;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 14px;
}
.empty-block.error { color: var(--anime-accent-red); }
.empty-icon { font-size: 2.8rem; opacity: 0.3; color: var(--anime-accent-red); }
.empty-title { margin: 0; font-size: 0.95rem; font-weight: 600; color: var(--anime-text-main); }
.empty-hint  { margin: 0; font-size: 0.8rem; opacity: 0.7; }

/* ========================= LOAD MORE ========================= */
.load-more {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 14px 0 4px; font-size: 0.8rem; color: var(--anime-text-secondary); flex-shrink: 0;
}
.load-done { color: #b3b3b3; font-size: 0.74rem; }

/* ========================= PAGER (database) ========================= */
.pager {
  flex-shrink: 0;
  margin-top: 4px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}
.pager button {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border: 1px solid #e5e7eb; background: #fff;
  border-radius: 10px; cursor: pointer; font-size: 1rem; color: var(--anime-text-main);
  transition: all 0.2s;
}
.pager button:hover:not(:disabled) { border-color: var(--anime-accent-red); color: var(--anime-accent-red); }
.pager button:disabled { opacity: 0.35; cursor: not-allowed; }
.pager-num { font-size: 0.85rem; color: var(--anime-text-secondary); font-weight: 600; font-variant-numeric: tabular-nums; }

/* ========================= SKELETON ========================= */
.sk-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 14px; }
.sk-card {
  aspect-ratio: 2 / 3; border-radius: 14px;
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, #ede3d8 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

/* ========================= RESPONSIVE ========================= */
@media (max-width: 768px) {
  .discover-root { min-height: 0; }
  .scroll-area { max-height: calc(100vh - 260px); }
  .discover-tab { padding: 9px 16px; font-size: 0.82rem; gap: 5px; }
  .toolbar-row { flex-direction: column; align-items: stretch; }
  .search-box { width: 100%; }
  .btn-search { width: 100%; justify-content: center; }
  .season-selects { flex: 1; }
  .toolbar-info { margin-left: 0; }
  .result-grid { grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; }
}

@media (max-width: 480px) {
  .discover-tabs { width: 100%; }
  .discover-tab { flex: 1; justify-content: center; padding: 8px 10px; font-size: 0.76rem; gap: 3px; }
  .scroll-area { max-height: calc(100vh - 250px); }
  .result-grid { grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .rc-title { font-size: 0.74rem; }
  .rc-body { padding: 8px 8px 9px; }
  .toolbar { padding: 10px 12px; }
}
</style>

<style>
/* 发现页：禁止外层滚动，用 flex 精确撑满剩余高度，避免出现滚动条 */
.app-content:has(.discover-root) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (max-width: 768px) {
  /* 移动端恢复滚动，内容自然排布 */
  .app-content:has(.discover-root) {
    display: block;
    overflow-y: auto;
  }
}
</style>

