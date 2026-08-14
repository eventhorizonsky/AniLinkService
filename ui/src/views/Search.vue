<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { formatAnimeType } from '../utils/animeType'
import { API_BASE } from '../utils/constants'

const route = useRoute()
const router = useRouter()

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

const libOuterEl = ref(null)

const onLibScroll = () => {
  const isMobile = window.matchMedia('(max-width: 768px)').matches
  const el = isMobile ? libOuterEl.value : libScrollEl.value
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

// ===================== Database Search (弹弹番剧库) =====================
const dbKeyword = ref('')
const dbSearching = ref(false)
const dbSearchResults = ref([])
const dbSearched = ref(false)

const dbSearch = async () => {
  const kw = dbKeyword.value.trim()
  if (kw.length < 2) {
    dbSearched.value = true
    dbSearchResults.value = []
    return
  }
  dbSearching.value = true
  dbSearched.value = true
  try {
    const res = await axios.get(`${API_BASE}/animes/search-dandan`, { params: { keyword: kw } })
    const raw = res.data?.data
    const listRaw = raw?.animes || raw?.data?.animes || []
    dbSearchResults.value = Array.isArray(listRaw) ? listRaw : []
  } catch (e) {
    console.error('搜索弹弹番剧失败:', e)
    dbSearchResults.value = []
  } finally {
    dbSearching.value = false
  }
}

const dbClearSearch = () => {
  dbKeyword.value = ''
  dbSearchResults.value = []
  dbSearched.value = false
}

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
  libOuterEl.value = document.querySelector('.app-content')
  libOuterEl.value?.addEventListener('scroll', onLibScroll, { passive: true })
})

onBeforeUnmount(() => {
  libOuterEl.value?.removeEventListener('scroll', onLibScroll)
  libOuterEl.value = null
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
          <div class="br-grid">
            <router-link v-for="a in libList" :key="a.id || a.animeId" :to="'/anime/' + a.animeId" class="br-card">
              <div class="br-card-image">
                <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.title" loading="lazy" />
                <div v-else class="rc-no-img"><i class="mdi mdi-image-off"></i></div>
                <div class="rc-hover"><i class="mdi mdi-play-circle-outline"></i></div>
              </div>
              <div class="br-card-body">
                <h4 :title="a.title">{{ a.title || '未命名动漫' }}</h4>
                <div class="br-card-meta">
                  <span v-if="a.type" class="genre">{{ formatAnimeType(a.type) }}</span>
                </div>
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
              <select v-model="dbYear" :disabled="!!dbKeyword" @change="selectSeason(dbYear, dbMonth)">
                <option v-for="y in dbYears" :key="y" :value="y">{{ y }}</option>
              </select>
            </div>
            <span class="ss-sep">年</span>
            <div class="ss-field">
              <label><i class="mdi mdi-calendar-month"></i></label>
              <select v-model="dbMonth" :disabled="!!dbKeyword" @change="selectSeason(dbYear, dbMonth)">
                <option v-for="m in dbMonths" :key="m" :value="m">{{ m }}月</option>
              </select>
            </div>
          </div>
          <div class="toolbar-info" v-if="!dbKeyword && seasonLabel && !dbLoading">
            <span class="toolbar-season">{{ seasonLabel }}</span>
            <span class="toolbar-count" v-if="dbList.length">{{ dbList.length }} 部</span>
          </div>
        </div>

        <div class="toolbar-row">
          <div class="search-box">
            <i class="mdi mdi-magnify"></i>
            <input
              v-model="dbKeyword"
              type="text"
              placeholder="搜索弹弹番剧库..."
              @keyup.enter="dbSearch"
            />
            <button v-if="dbKeyword" class="search-clear" @click="dbClearSearch">
              <i class="mdi mdi-close-circle"></i>
            </button>
          </div>
          <button class="btn-search" :disabled="dbSearching" @click="dbSearch">
            <i class="mdi mdi-magnify"></i>{{ dbSearching ? '搜索中...' : '搜索' }}
          </button>
        </div>
      </div>

      <div class="scroll-area">
        <div v-if="dbSearching" class="sk-grid"><div v-for="i in 12" :key="i" class="sk-card"></div></div>

        <div v-else-if="dbKeyword && dbSearchResults.length" class="br-grid">
          <router-link v-for="a in dbSearchResults" :key="a.animeId" :to="'/anime/' + a.animeId" class="br-card">
            <div class="br-card-image">
              <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.animeTitle || a.title" loading="lazy" decoding="async" />
              <div v-else class="rc-no-img"><i class="mdi mdi-image-off"></i></div>
              <span class="br-badge-score" v-if="a.rating"><i class="mdi mdi-star"></i>{{ Number(a.rating).toFixed(1) }}</span>
              <div class="rc-hover"><i class="mdi mdi-play-circle-outline"></i></div>
            </div>
            <div class="br-card-body">
              <h4 :title="a.animeTitle || a.title">{{ a.animeTitle || a.title || '未命名番剧' }}</h4>
              <div class="br-card-meta">
                <span v-if="a.type" class="genre">{{ formatAnimeType(a.type) }}</span>
                <span v-else-if="a.year" class="genre">{{ a.year }}</span>
              </div>
            </div>
          </router-link>
        </div>

        <div v-else-if="dbKeyword" class="empty-block">
          <i class="mdi mdi-magnify empty-icon"></i>
          <p class="empty-title">{{ dbSearched ? '没有找到相关番剧' : '输入关键词搜索弹弹番剧库' }}</p>
          <p class="empty-hint">{{ dbSearched ? '换个关键词再试试' : '按回车或点击搜索' }}</p>
        </div>

        <template v-else>
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
          <div v-else class="br-grid">
            <router-link v-for="a in dbList" :key="a.animeId" :to="'/anime/' + a.animeId" class="br-card">
              <div class="br-card-image">
                <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.animeTitle" loading="lazy" decoding="async" />
                <div v-else class="rc-no-img"><i class="mdi mdi-image-off"></i></div>
                <span class="br-badge-score" v-if="a.rating"><i class="mdi mdi-star"></i>{{ Number(a.rating).toFixed(1) }}</span>
                <div class="rc-hover"><i class="mdi mdi-play-circle-outline"></i></div>
              </div>
              <div class="br-card-body">
                <h4 :title="a.animeTitle">{{ a.animeTitle }}</h4>
              </div>
            </router-link>
          </div>
        </template>
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
  background: var(--al-border-extra);
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
  background: var(--al-bg);
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
  background: var(--al-bg);
  border: 1px solid var(--al-border-panel);
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
.toolbar-row + .toolbar-row {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--al-border-neutral);
}
.toolbar-meta {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--al-border-neutral);
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
  -webkit-overflow-scrolling: touch;
}

/* ---- search box ---- */
.search-box {
  flex: 1;
  min-width: 200px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--al-bg-soft);
  border: 1.5px solid var(--al-border-input);
  border-radius: 999px;
  padding: 0 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.search-box:focus-within {
  border-color: var(--anime-accent-red);
  background: var(--al-bg);
  box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12);
}
.search-box i { color: var(--al-text-placeholder); font-size: 1rem; flex-shrink: 0; }
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
.search-box input::placeholder { color: var(--al-text-placeholder); }
.search-clear { border: none; background: none; color: var(--al-text-placeholder); cursor: pointer; padding: 2px; font-size: 0.9rem; }
.search-clear:hover { color: var(--anime-accent-red); }

.btn-search {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: var(--anime-accent-red);
  color: var(--al-text-on-accent);
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
.btn-search:hover:not(:disabled) { background: var(--al-accent-strong); }
.btn-search:disabled { opacity: 0.6; cursor: not-allowed; }

/* ---- season selects ---- */
.season-selects { display: flex; align-items: center; gap: 6px; }
.ss-field { display: flex; align-items: center; gap: 6px; }
.ss-field label { font-size: 0.95rem; color: var(--anime-text-secondary); }
.ss-field select {
  border: 1.5px solid var(--al-border-input);
  border-radius: 10px;
  padding: 9px 32px 9px 12px;
  font-size: 0.9rem;
  color: var(--anime-text-main);
  background: var(--al-bg);
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
.ss-field select:disabled { opacity: 0.55; cursor: not-allowed; }
.ss-sep { font-size: 0.85rem; color: var(--anime-text-secondary); font-weight: 500; }

/* ========================= RESULT CARD ========================= */
/* 复用 browse.css 的 .br-card 卡片样式，这里仅保留缺失图占位与播放遮罩 */

.rc-no-img {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--al-gray-muted);
  font-size: 2rem;
  background: linear-gradient(135deg, var(--al-bg-beige), var(--al-bg-beige-13));
}

.rc-hover {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
}
.rc-hover i {
  font-size: 2rem;
  color: #fff;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}
.br-card:hover .rc-hover { opacity: 1; }

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
  background: var(--al-bg);
  border: 1px solid var(--al-border-panel);
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
.load-done { color: var(--al-gray-faint); font-size: 0.74rem; }

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
  width: 36px; height: 36px; border: 1px solid var(--al-border-input); background: var(--al-bg);
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
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}

/* ========================= RESPONSIVE ========================= */
@media (max-width: 768px) {
  .discover-root { min-height: 0; flex: none; }
  .tab-content { flex: none; min-height: 0; }
  .scroll-area { flex: none; min-height: 0; overflow-y: visible; overflow-x: visible; }
  .discover-tab { padding: 9px 16px; font-size: 0.82rem; gap: 5px; }
  .toolbar-row { flex-direction: column; align-items: stretch; }
  .search-box { width: 100%; }
  .btn-search { width: 100%; justify-content: center; }
  .season-selects { flex: 1; }
  .toolbar-info { margin-left: 0; }
}

@media (max-width: 480px) {
  .discover-tabs { width: 100%; }
  .discover-tab { flex: 1; justify-content: center; padding: 8px 10px; font-size: 0.76rem; gap: 3px; }
  .toolbar { padding: 10px 12px; }
}
</style>

<style>
/* 发现页：禁止外层滚动（PC），用 flex 精确撑满剩余高度；
   内部仅 .scroll-area 滚动，避免出现两层滚动条 */
.app-content:has(.discover-root) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (max-width: 768px) {
  /* 移动端恢复外层滚动，内容自然排布，避免内层滚动区域过小 */
  .app-content:has(.discover-root) {
    display: block;
    overflow-y: auto;
  }
}
</style>

