<script>
// 组件名：供路由层 keep-alive（include）命中缓存，详情返回后还原整个发现页状态
export default { name: 'Search' }
</script>

<script setup>
import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
  watch
} from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { getAnimeList, getSeasonList, getSeasonAnime, searchDandanAnimes } from '../api/anime'
import { formatAnimeType } from '../utils/animeType'
import { formatScore } from '../utils/format'
import { useIsMobile } from '../composables/useIsMobile'
import AnimeCard from '../components/AnimeCard.vue'
import BangumiRankTab from '../components/rank/BangumiRankTab.vue'

const route = useRoute()
const router = useRouter()
const { isMobile } = useIsMobile(768)

const DISCOVER_STATE_KEY = 'anilink.discover.state'
const VALID_TABS = ['library', 'database', 'rank']

// 离开/刷新后的兜底状态：Tab 与媒体库检索关键词
const savedState = (() => {
  try {
    const o = JSON.parse(sessionStorage.getItem(DISCOVER_STATE_KEY) || 'null')
    return o && typeof o === 'object' ? o : null
  } catch {
    return null
  }
})()

// 路由 ?tab=xxx（如详情页引导"前往番剧资料库"）优先，其次会话记忆，最后默认媒体库
const routeTab = VALID_TABS.includes(route.query.tab) ? route.query.tab : null
const activeTab = ref(routeTab || (savedState && VALID_TABS.includes(savedState.tab) ? savedState.tab : 'library'))
const savedLibKeyword = typeof savedState?.libKeyword === 'string' ? savedState.libKeyword : ''

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

// Tab 与媒体库关键词状态兜底（依赖上述 ref，须在其声明之后）
const persistDiscover = () => {
  try {
    sessionStorage.setItem(
      DISCOVER_STATE_KEY,
      JSON.stringify({ tab: activeTab.value, libKeyword: libKeyword.value })
    )
  } catch {
    /* ignore */
  }
}
watch([activeTab, () => libKeyword.value], persistDiscover)

// 请求序号，防止快速切换关键词时过期响应覆盖新数据
let libFetchSeq = 0

const libHasResult = computed(() => libList.value.length > 0)

const fetchLibrary = async (append = false) => {
  if (append) libLoadingMore.value = true
  else { libLoading.value = true; libError.value = '' }

  const seq = ++libFetchSeq
  try {
    const params = { page: libPage.value, pageSize: libPageSize }
    if (libKeyword.value.trim()) params.keyword = libKeyword.value.trim()
    const res = await getAnimeList(params)
    if (seq !== libFetchSeq) return
    const data = res?.data
    const items = Array.isArray(data?.content) ? data.content : []
    if (append) libList.value.push(...items)
    else libList.value = items
    libTotal.value = Number(data?.totalElements || 0)
    const totalPages = Number(data?.totalPages || 0)
    libHasMore.value = libPage.value < totalPages
  } catch (e) {
    if (seq !== libFetchSeq) return
    libError.value = e?.response?.data?.msg || e?.message || '加载失败'
    if (!append) { libList.value = []; libTotal.value = 0; libHasMore.value = false }
  } finally {
    if (seq === libFetchSeq) {
      if (append) libLoadingMore.value = false
      else libLoading.value = false
    }
  }
}

const libSearch = () => {
  libPage.value = 1
  const q = {}
  if (libKeyword.value.trim()) q.q = libKeyword.value.trim()
  router.push({ path: '/search', query: q })
}

const libOuterEl = ref(null)

// ===== 滚动位置管理（按 Tab 分别记忆，恢复由父组件直接操作滚动元素，不依赖子组件生命周期）=====
const tabScroll = { library: 0, database: 0, rank: 0 }

const scrollEl = () => document.querySelector('.app-content') || libOuterEl.value
const rkBodyEl = () => document.querySelector('.rk-body')
// 各 Tab 的真实滚轴：排行榜/媒体库/资料库都可能走各自内部的滚动容器（.rk-body/.scroll-area），
// 内层不可滚（如内容不满、空态）时才回落到外层 .app-content
const scrollerFor = (tab) => {
  if (tab === 'rank') {
    const r = rkBodyEl()
    return r && r.scrollHeight > r.clientHeight + 2 ? r : scrollEl()
  }
  const inner = tab === 'library' ? libScrollEl.value : dbScrollEl.value
  if (inner && inner.scrollHeight > inner.clientHeight + 2) return inner
  return scrollEl()
}

// 记录滚动（0 不覆盖：路由切走时元素可能已被置顶/摘走，0 不是真实位置）
const captureCurrentScroll = () => {
  const tab = activeTab.value
  const el = scrollerFor(tab)
  if (el && el.scrollTop > 0) {
    tabScroll[tab] = el.scrollTop
    saveFull()
  }
}

const applyTo = (el, pos) => {
  if (el.scrollHeight >= pos + el.clientHeight) {
    el.scrollTop = pos
    return Math.abs(el.scrollTop - pos) < 4
  }
  el.scrollTop = Math.min(pos, Math.max(0, el.scrollHeight - el.clientHeight))
  return false
}

// 恢复滚动：轮询重试直到内容（含懒加载封面）高度足够并成功落位
const restoreTabScroll = async (tab) => {
  const pos = tabScroll[tab] || 0
  if (!pos) return
  for (let i = 0; i < 24; i++) {
    await new Promise((r) => setTimeout(r, 150))
    const el = scrollerFor(tab)
    if (!el) continue
    if (applyTo(el, pos)) return
  }
}

// 任意内部/外层滚动（捕获阶段）都能实时记录当前 Tab 的真实位置
const onDocScrollCapture = (e) => {
  const tab = activeTab.value
  const t = e.target
  if (!(t instanceof Element)) return
  const el = scrollerFor(tab)
  if (el && (t === el || (t.classList && (t.classList.contains('rk-body') || t.classList.contains('scroll-area'))))) {
    const top = t.scrollTop
    if (top > 0) {
      tabScroll[tab] = top
      saveFull()
    }
  }
}

const onLibScroll = () => {
  if (activeTab.value === 'rank') return
  const el = scrollEl()
  if (!el) return
  tabScroll[activeTab.value] = el.scrollTop
  saveFull()
  const area = isMobile.value ? el : libScrollEl.value
  if (!area || libLoadingMore.value || !libHasMore.value) return
  if (area.scrollTop + area.clientHeight >= area.scrollHeight - 60) {
    libPage.value++
    fetchLibrary(true)
  }
}

const switchTab = (tab) => {
  captureCurrentScroll()
  activeTab.value = tab
  restoreTabScroll(tab)
  saveFull()
}

// ===================== Database =====================
const dbLoading = ref(false)
const dbError = ref('')
const dbSeasons = ref([])
const dbYear = ref(null)
const dbMonth = ref(null)
const dbList = ref([])
const dbScrollEl = ref(null)

const dbYears = computed(() => [...new Set(dbSeasons.value.map(s => s.year))].sort((a, b) => b - a))
const dbMonths = computed(() => {
  if (dbYear.value == null) return []
  return dbSeasons.value.filter(s => s.year === dbYear.value).map(s => s.month).sort((a, b) => a - b)
})

let seasonFetchSeq = 0

const fetchSeasons = async () => {
  const seq = ++seasonFetchSeq
  try {
    const res = await getSeasonList()
    if (seq !== seasonFetchSeq) return
    const data = res
    if (Array.isArray(data?.seasons)) dbSeasons.value = data.seasons
    else if (Array.isArray(data)) dbSeasons.value = data
    if (dbSeasons.value.length) {
      const latest = dbSeasons.value.reduce((a, b) =>
        b.year > a.year || (b.year === a.year && b.month > a.month) ? b : a)
      dbYear.value = latest.year; dbMonth.value = latest.month
      await fetchSeasonAnime(seq)
    }
  } catch (e) {
    if (seq !== seasonFetchSeq) return
    dbError.value = '获取季度列表失败'; console.error(e)
  }
}

const fetchSeasonAnime = async (expectedSeq = seasonFetchSeq) => {
  if (dbYear.value == null || dbMonth.value == null) return
  const seq = ++seasonFetchSeq
  dbLoading.value = true; dbError.value = ''
  try {
    const res = await getSeasonAnime(dbYear.value, dbMonth.value)
    if (seq !== seasonFetchSeq) return
    const data = res
    if (Array.isArray(data?.bangumiList)) dbList.value = data.bangumiList
    else if (Array.isArray(data)) dbList.value = data
    else dbList.value = []
  } catch (e) {
    if (seq !== seasonFetchSeq) return
    dbError.value = '获取季度番剧失败'; dbList.value = []
  }
  finally { if (seq === seasonFetchSeq) dbLoading.value = false }
}

const selectSeason = async (year, month) => { dbYear.value = year; dbMonth.value = month; await fetchSeasonAnime() }

// ===================== Database Search (弹弹番剧库) =====================
const dbKeyword = ref('')
const dbSearching = ref(false)
const dbSearchResults = ref([])
const dbSearched = ref(false)
const dbSearchError = ref('')

let dbSearchSeq = 0

const dbSearch = async () => {
  const kw = dbKeyword.value.trim()
  if (kw.length < 2) {
    dbSearchSeq++
    dbSearched.value = true
    dbSearchResults.value = []
    dbSearchError.value = ''
    return
  }
  const seq = ++dbSearchSeq
  dbSearching.value = true
  dbSearched.value = true
  dbSearchError.value = ''
  try {
    const res = await searchDandanAnimes(kw)
    if (seq !== dbSearchSeq) return
    const raw = res?.data
    const listRaw = raw?.animes || raw?.data?.animes || []
    dbSearchResults.value = Array.isArray(listRaw) ? listRaw : []
  } catch (e) {
    if (seq !== dbSearchSeq) return
    console.error('搜索弹弹番剧失败:', e)
    dbSearchResults.value = []
    dbSearchError.value = e?.response?.data?.msg || '搜索失败，请稍后重试'
  } finally {
    if (seq === dbSearchSeq) dbSearching.value = false
  }
}

const dbClearSearch = () => {
  dbKeyword.value = ''
  dbSearchResults.value = []
  dbSearched.value = false
  dbSearchError.value = ''
}

// Season label
const seasonLabels = { 1:'冬季', 4:'春季', 7:'夏季', 10:'秋季' }
const seasonLabel = computed(() => {
  if (dbYear.value == null || dbMonth.value == null) return ''
  const s = seasonLabels[dbMonth.value] || `${dbMonth.value}月`
  return `${dbYear.value}年${s}`
})

// ===== 完整会话快照：切路由返回/重挂载后还原 Tab、关键词、列表、分页与滚动（不重新请求） =====
const FULL_KEY = 'anilink.discover.full.v1'
let saveTimer = 0
const saveFull = () => {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = 0
    try {
      sessionStorage.setItem(
        FULL_KEY,
        JSON.stringify({
          tab: activeTab.value,
          library: {
            keyword: libKeyword.value,
            list: libList.value.slice(0, 800),
            total: libTotal.value,
            page: libPage.value,
            hasMore: libHasMore.value
          },
          database: {
            keyword: dbKeyword.value,
            seasons: dbSeasons.value.slice(0, 400),
            year: dbYear.value,
            month: dbMonth.value,
            list: dbList.value.slice(0, 800),
            searchResults: dbSearchResults.value.slice(0, 800)
          },
          scrolls: { ...tabScroll }
        })
      )
    } catch {
      /* ignore */
    }
  }, 350)
}
watch(
  [
    () => libList.value,
    () => libTotal.value,
    () => libHasMore.value,
    () => dbKeyword.value,
    () => dbSeasons.value,
    () => dbYear.value,
    () => dbMonth.value,
    () => dbList.value,
    () => dbSearchResults.value
  ],
  saveFull
)
const loadFull = () => {
  try {
    const o = JSON.parse(sessionStorage.getItem(FULL_KEY) || 'null')
    return o && typeof o === 'object' ? o : null
  } catch {
    return null
  }
}

const syncAndFetch = () => {
  // 路由带 q 以路由为准；未带 q 时回退到上次会话的关键词
  libKeyword.value = route.query.q !== undefined ? String(route.query.q) : savedLibKeyword
  libPage.value = 1
  fetchLibrary(false)
}

watch(() => route.query.q, () => syncAndFetch())

// 外部跳转可通过 ?tab=xxx（library/database/rank）直达对应 Tab（兼容 keep-alive 复用下的二次进入）
watch(
  () => route.query.tab,
  (t) => {
    if (VALID_TABS.includes(t) && t !== activeTab.value) switchTab(t)
  }
)

onMounted(async () => {
  libOuterEl.value = document.querySelector('.app-content')
  document.addEventListener('scroll', onDocScrollCapture, { capture: true, passive: true })
  libOuterEl.value?.addEventListener('scroll', onLibScroll, { passive: true })

  const snap = loadFull()

  // 媒体库检索：有快照直接还原，避免回退后重新请求/从头开始
  if (snap && Array.isArray(snap.library?.list) && snap.library.list.length) {
    libKeyword.value = typeof snap.library.keyword === 'string' ? snap.library.keyword : ''
    libList.value = snap.library.list
    libTotal.value = Number(snap.library.total || 0)
    libPage.value = Number(snap.library.page || 1)
    libHasMore.value = !!snap.library.hasMore
    libError.value = ''
  } else {
    syncAndFetch()
  }

  // 番剧资料库：还原季节数据/当前选择/结果列表
  if (snap && Array.isArray(snap.database?.seasons) && snap.database.seasons.length) {
    dbSeasons.value = snap.database.seasons
    dbKeyword.value = typeof snap.database.keyword === 'string' ? snap.database.keyword : ''
    dbSearchResults.value = Array.isArray(snap.database.searchResults) ? snap.database.searchResults : []
    dbSearched.value = dbSearchResults.value.length > 0
    const y = Number(snap.database.year)
    const m = Number(snap.database.month)
    const hasList = Array.isArray(snap.database.list) && snap.database.list.length
    if (hasList && y && dbYears.value.includes(y)) {
      dbYear.value = y
      dbMonth.value = m
      dbList.value = snap.database.list
    } else {
      const latest = dbSeasons.value.reduce((a, b) =>
        b.year > a.year || (b.year === a.year && b.month > a.month) ? b : a)
      dbYear.value = latest.year
      dbMonth.value = latest.month
      if (!hasList) fetchSeasonAnime()
    }
  } else {
    fetchSeasons()
  }

  if (snap?.scrolls && typeof snap.scrolls === 'object') {
    Object.assign(tabScroll, { library: 0, database: 0, rank: 0 }, snap.scrolls)
  }
  restoreTabScroll(activeTab.value)
})

// keep-alive 停用/复用：离开前记录各 Tab 真实滚动位置，回来由 restoreTabScroll 恢复
// （onBeforeRouteLeave 在路由切换前执行，此时容器尚未被其它页置顶，能拿到真实位置）
onBeforeRouteLeave(() => {
  captureCurrentScroll()
})
onDeactivated(captureCurrentScroll)
onActivated(() => {
  restoreTabScroll(activeTab.value)
})

onBeforeUnmount(() => {
  captureCurrentScroll()
  document.removeEventListener('scroll', onDocScrollCapture, { capture: true })
  libOuterEl.value?.removeEventListener('scroll', onLibScroll)
  libOuterEl.value = null
})
</script>

<template>
  <div class="discover-root">
    <!-- ====== 页面头部 ====== -->
    <div class="page-head">
      <h2><i class="mdi mdi-compass"></i> 发现</h2>
      <span class="sub">浏览媒体库、番剧资料库与 Bangumi 动画排行榜</span>
    </div>

    <!-- ====== Tab Bar ====== -->
    <div class="discover-tabs">
      <button class="discover-tab" :class="{ active: activeTab === 'library' }" @click="switchTab('library')">
        <i class="mdi mdi-filmstrip-box-multiple"></i>媒体库检索
      </button>
      <button class="discover-tab" :class="{ active: activeTab === 'database' }" @click="switchTab('database')">
        <i class="mdi mdi-database-search"></i>番剧资料库
      </button>
      <button class="discover-tab" :class="{ active: activeTab === 'rank' }" @click="switchTab('rank')">
        <i class="mdi mdi-equalizer"></i>排行榜
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
            <AnimeCard
              v-for="a in libList"
              :key="a.id || a.animeId"
              :to="'/anime/' + a.animeId"
              :image-url="a.imageUrl"
              :alt="a.title"
              :title="a.title || '未命名动漫'"
              :title-attr="a.title"
              hover
            >
              <template #meta>
                <span v-if="a.type" class="genre">{{ formatAnimeType(a.type) }}</span>
              </template>
            </AnimeCard>
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

      <div ref="dbScrollEl" class="scroll-area">
        <div v-if="dbSearching" class="sk-grid"><div v-for="i in 12" :key="i" class="sk-card"></div></div>

        <div v-else-if="dbKeyword && dbSearchResults.length" class="br-grid">
          <AnimeCard
            v-for="a in dbSearchResults"
            :key="a.animeId"
            :to="'/anime/' + a.animeId"
            :image-url="a.imageUrl"
            :alt="a.animeTitle || a.title"
            :title="a.animeTitle || a.title || '未命名番剧'"
            :title-attr="a.animeTitle || a.title"
            hover
          >
            <template #badges>
              <span class="br-badge-score" v-if="a.rating"><i class="mdi mdi-star"></i>{{ formatScore(a.rating) }}</span>
            </template>
            <template #meta>
              <span v-if="a.type" class="genre">{{ formatAnimeType(a.type) }}</span>
              <span v-else-if="a.year" class="genre">{{ a.year }}</span>
            </template>
          </AnimeCard>
        </div>

        <div v-else-if="dbKeyword && dbSearchError" class="empty-block error">
          <i class="mdi mdi-alert-circle empty-icon"></i>
          <p class="empty-title">搜索失败</p>
          <p class="empty-hint">{{ dbSearchError }}</p>
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
            <AnimeCard
              v-for="a in dbList"
              :key="a.animeId"
              :to="'/anime/' + a.animeId"
              :image-url="a.imageUrl"
              :alt="a.animeTitle"
              :title="a.animeTitle"
              :title-attr="a.animeTitle"
              hover
            >
              <template #badges>
                <span class="br-badge-score" v-if="a.rating"><i class="mdi mdi-star"></i>{{ formatScore(a.rating) }}</span>
              </template>
            </AnimeCard>
          </div>
        </template>
      </div>
    </div>

    <!-- ============================ RANK (Bangumi 动画排行榜) ============================ -->
    <div v-show="activeTab === 'rank'" class="tab-content">
      <!-- keep-alive：切走/返回后保留筛选与页码；列表浏览位置由发现页父组件统一记录并恢复 -->
      <keep-alive>
        <BangumiRankTab v-if="activeTab === 'rank'" />
      </keep-alive>
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
  box-shadow: 0 0 0 4px rgba(var(--al-accent-rgb), 0.12);
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
  box-shadow: 0 2px 8px rgba(var(--al-accent-rgb), 0.3);
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
/* 复用 browse.css 的 .br-card 卡片样式与 AnimeCard 组件内的占位/遮罩 */

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

