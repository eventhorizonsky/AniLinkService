<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const API_BASE = '/api'

// ===================== Follow List =====================
const followList = ref([])
const followLoading = ref(false)

// ===================== Schedule =====================
const bangumiList = ref([])
const scheduleLoading = ref(false)
const scheduleError = ref('')
const activeDay = ref(new Date().getDay())

const weekTabs = [
  { label: '日', key: 0 }, { label: '一', key: 1 },
  { label: '二', key: 2 }, { label: '三', key: 3 },
  { label: '四', key: 4 }, { label: '五', key: 5 },
  { label: '六', key: 6 }
]

// ===================== Trending =====================
const trendingHot = ref([])
const trendingNewAnime = ref([])
const trendingLoading = ref(false)
const trendingError = ref('')
const hoveredHot = ref(null)
const hoveredNew = ref(null)

const isLoggedIn = computed(() => !!localStorage.getItem('token'))

// ===================== Fetch =====================
const fetchFollowList = async () => {
  if (!isLoggedIn.value) return
  followLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/follows/status/watching`, { params: { page: 1, pageSize: 8 } })
    if (res.data?.code === 200) {
      const data = res.data.data
      followList.value = (Array.isArray(data) ? data : (data?.content || [])).slice(0, 8)
    }
  } catch (e) { console.error('Fetch follow list failed:', e) }
  finally { followLoading.value = false }
}

const fetchSchedule = async () => {
  scheduleLoading.value = true; scheduleError.value = ''
  try {
    const res = await fetch(`${API_BASE}/animes/shin/raw-json`)
    const result = await res.json()
    if (result.code !== 200 || !result.data || !Array.isArray(result.data.bangumiList))
      throw new Error('新番接口返回结构不正确')
    const nd = (d) => { if (d === 7) return 0; return Number.isInteger(d) ? d : -1 }
    bangumiList.value = result.data.bangumiList
      .map(i => ({ ...i, airDay: nd(i.airDay) }))
      .filter(i => i.airDay >= 0 && i.airDay <= 6)
  } catch (e) { scheduleError.value = e?.message || '新番数据加载失败'; bangumiList.value = [] }
  finally { scheduleLoading.value = false }
}

const filteredBangumi = computed(() =>
  bangumiList.value.filter(i => i.airDay === activeDay.value).sort((a, b) => (b.rating || 0) - (a.rating || 0))
)
const dayCount = (d) => bangumiList.value.filter(i => i.airDay === d).length

const fetchTrending = async () => {
  trendingLoading.value = true; trendingError.value = ''
  try {
    const [hotRes, newRes] = await Promise.allSettled([
      axios.get(`${API_BASE}/v2/trending/all/hot/week`),
      axios.get(`${API_BASE}/v2/trending/new-anime/hot/current-season`)
    ])
    if (hotRes.status === 'fulfilled' && hotRes.value?.data)
      trendingHot.value = extractList(hotRes.value.data).slice(0, 10)
    if (newRes.status === 'fulfilled' && newRes.value?.data)
      trendingNewAnime.value = extractList(newRes.value.data).slice(0, 10)
  } catch (e) { trendingError.value = '榜单数据加载失败'; console.error(e) }
  finally { trendingLoading.value = false }
}

const extractList = (data) => {
  if (Array.isArray(data)) return data
  if (data && typeof data === 'object') {
    if (Array.isArray(data.bangumiList)) return data.bangumiList
    for (const k of ['animeList', 'results', 'data', 'animes'])
      if (Array.isArray(data[k])) return data[k]
    if (data.animeId) return [data]
  }
  return []
}

const goToFollowList = () => router.push({ path: '/profile', query: { tab: 'follows' } })
const goToSearch = () => router.push('/search')

const rankClass = (i) => {
  if (i === 0) return 'rank-gold'; if (i === 1) return 'rank-silver'; if (i === 2) return 'rank-bronze'
  return ''
}
const fmtScore = (v) => {
  if (v == null || v === '') return '-'
  const n = Number(v); return Number.isNaN(n) ? '-' : n.toFixed(1)
}

onMounted(() => { fetchSchedule(); fetchTrending(); if (isLoggedIn.value) fetchFollowList() })
</script>

<template>
  <div class="home-root">
    <div class="home-main">
      <!-- ===== Left ===== -->
      <div class="home-left">
        <!-- 我的追番 -->
        <section class="home-section follow-section">
          <div class="section-header">
            <div class="section-title">
              <i class="mdi mdi-bookmark-multiple section-icon" style="color:#e74c3c"></i>
              <span>我的追番</span>
            </div>
            <button v-if="isLoggedIn && followList.length" class="more-btn" @click="goToFollowList">
              全部 <i class="mdi mdi-chevron-right"></i>
            </button>
          </div>
          <div class="section-body">
            <div v-if="!isLoggedIn" class="empty">  <i class="mdi mdi-login-variant"></i> <span>登录后查看追番</span> </div>
            <div v-else-if="followLoading" class="sk-grid"><div v-for="i in 6" :key="i" class="sk-card"></div></div>
            <div v-else-if="!followList.length" class="empty">
              <i class="mdi mdi-movie-open-star-outline"></i> <span>还没有追番，去</span>
              <button class="link" @click="goToSearch">发现番剧</button>
            </div>
            <div v-else class="card-grid">
              <router-link v-for="a in followList" :key="a.id" :to="'/anime/' + a.animeId" class="anime-card">
                <div class="card-poster">
                  <img :src="a.imageUrl || 'https://assets.anixplayer.net/image/poster/default.jpg'" :alt="a.animeTitle" loading="lazy" />
                  <div class="poster-gradient"></div>
                  <div class="poster-hover"><i class="mdi mdi-play-circle-outline"></i></div>
                </div>
                <div class="card-info">
                  <p class="card-title" :title="a.animeTitle">{{ a.animeTitle }}</p>
                  <span class="card-meta" :class="a.status === 'watching' ? 'meta-active' : 'meta-done'">
                    {{ a.status === 'watching' ? '追番中' : a.status === 'completed' ? '已完成' : '已弃' }}
                  </span>
                </div>
              </router-link>
            </div>
          </div>
        </section>

        <!-- 新番时间表 -->
        <section class="home-section schedule-section">
          <div class="section-header">
            <div class="section-title">
              <i class="mdi mdi-calendar-week section-icon" style="color:#4b7bec"></i>
              <span>新番时间表</span>
            </div>
          </div>
          <div class="section-body">
            <div class="day-tabs">
              <button v-for="t in weekTabs" :key="t.key" class="day-tab"
                :class="{ active: activeDay === t.key, today: t.key === new Date().getDay() }"
                @click="activeDay = t.key">
                <span class="day-label">{{ t.label }}</span>
                <span class="day-badge">{{ dayCount(t.key) }}</span>
              </button>
            </div>
            <div v-if="scheduleLoading" class="sk-grid"><div v-for="i in 6" :key="i" class="sk-card"></div></div>
            <div v-else-if="scheduleError" class="empty error"><i class="mdi mdi-alert-circle-outline"></i><span>{{ scheduleError }}</span></div>
            <div v-else-if="!filteredBangumi.length" class="empty"><i class="mdi mdi-coffee-outline"></i><span>该日暂无新番</span></div>
            <div v-else class="card-grid">
              <router-link v-for="a in filteredBangumi" :key="a.animeId" :to="'/anime/' + a.animeId" class="anime-card">
                <div class="card-poster">
                  <img :src="a.imageUrl || 'https://assets.anixplayer.net/image/poster/default.jpg'" :alt="a.animeTitle" loading="lazy" />
                  <div class="poster-gradient"></div>
                  <span class="poster-score"><i class="mdi mdi-star"></i>{{ Number(a.rating || 0).toFixed(1) }}</span>
                  <span v-if="a.isOnAir" class="poster-dot" title="连载中"></span>
                  <div class="poster-hover"><i class="mdi mdi-play-circle-outline"></i></div>
                </div>
                <div class="card-info">
                  <p class="card-title" :title="a.animeTitle">{{ a.animeTitle }}</p>
                </div>
              </router-link>
            </div>
          </div>
        </section>
      </div>

      <!-- ===== Right ===== -->
      <div class="home-right">
        <!-- 热门趋势 -->
        <section class="home-section trending-panel">
          <div class="section-header">
            <div class="section-title">
              <i class="mdi mdi-fire section-icon" style="color:#f97316"></i>
              <span>热门趋势</span>
            </div>
            <span class="badge-pill">周榜</span>
          </div>
          <div class="section-body">
            <div v-if="trendingLoading" class="sk-list"><div v-for="i in 8" :key="i" class="sk-line"></div></div>
            <div v-else-if="trendingError" class="empty"><i class="mdi mdi-alert-circle-outline"></i><span>{{ trendingError }}</span></div>
            <div v-else-if="!trendingHot.length" class="empty"><i class="mdi mdi-fire-off"></i><span>暂无数据</span></div>
            <template v-else>
              <div class="t-hover-area" @mouseleave="hoveredHot = null">
                <div class="t-preview-slot" :class="{ active: hoveredHot }">
                  <div v-if="hoveredHot" class="t-preview-card">
                    <img :src="hoveredHot.imageUrl" :alt="hoveredHot.animeTitle" />
                    <div class="t-preview-info">
                      <span class="t-preview-score"><i class="mdi mdi-star"></i>{{ fmtScore(hoveredHot.rating) }}</span>
                      <p class="t-preview-title">{{ hoveredHot.animeTitle }}</p>
                    </div>
                  </div>
                  <span v-else class="t-preview-hint"><i class="mdi mdi-gesture-tap"></i>悬停条目查看详情</span>
                </div>
                <ul class="trending-list">
                  <li v-for="(a, i) in trendingHot" :key="a.animeId || i" class="t-item"
                    @mouseenter="hoveredHot = a">
                    <router-link :to="'/anime/' + a.animeId" class="t-link" @click.stop>
                    <span class="t-rank" :class="rankClass(i)">{{ i + 1 }}</span>
                    <img v-if="a.imageUrl" :src="a.imageUrl" class="t-thumb" loading="lazy" />
                    <span v-else class="t-thumb-ph"></span>
                    <span class="t-title" :title="a.animeTitle">{{ a.animeTitle }}</span>
                    <span class="t-heat" v-if="a.heat">{{ a.heat }}</span>
                    <span class="t-score"><i class="mdi mdi-star"></i>{{ fmtScore(a.rating) }}</span>
                  </router-link>
                </li>
              </ul>
              </div>
            </template>
          </div>
        </section>

        <!-- 新番热度 -->
        <section class="home-section trending-panel">
          <div class="section-header">
            <div class="section-title">
              <i class="mdi mdi-rocket-launch section-icon" style="color:#8b5cf6"></i>
              <span>新番热度</span>
            </div>
            <span class="badge-pill">本季</span>
          </div>
          <div class="section-body">
            <div v-if="trendingLoading" class="sk-list"><div v-for="i in 6" :key="i" class="sk-line"></div></div>
            <div v-else-if="!trendingNewAnime.length" class="empty"><i class="mdi mdi-rocket-launch-off"></i><span>暂无数据</span></div>
            <template v-else>
              <div class="t-hover-area" @mouseleave="hoveredNew = null">
                <div class="t-preview-slot" :class="{ active: hoveredNew }">
                  <div v-if="hoveredNew" class="t-preview-card">
                    <img :src="hoveredNew.imageUrl" :alt="hoveredNew.animeTitle" />
                    <div class="t-preview-info">
                      <span class="t-preview-score"><i class="mdi mdi-star"></i>{{ fmtScore(hoveredNew.rating) }}</span>
                      <p class="t-preview-title">{{ hoveredNew.animeTitle }}</p>
                    </div>
                  </div>
                  <span v-else class="t-preview-hint"><i class="mdi mdi-gesture-tap"></i>悬停条目查看详情</span>
                </div>
                <ul class="trending-list">
                  <li v-for="(a, i) in trendingNewAnime" :key="a.animeId || i" class="t-item"
                    @mouseenter="hoveredNew = a">
                    <router-link :to="'/anime/' + a.animeId" class="t-link" @click.stop>
                      <span class="t-rank" :class="rankClass(i)">{{ i + 1 }}</span>
                      <img v-if="a.imageUrl" :src="a.imageUrl" class="t-thumb" loading="lazy" />
                      <span v-else class="t-thumb-ph"></span>
                      <span class="t-title" :title="a.animeTitle">{{ a.animeTitle }}</span>
                      <span class="t-score"><i class="mdi mdi-star"></i>{{ fmtScore(a.rating) }}</span>
                    </router-link>
                  </li>
                </ul>
              </div>
            </template>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========================= ROOT ========================= */
.home-root {
  display: flex; flex-direction: column;
  height: calc(100vh - 135px); min-height: 0;
  animation: home-in .35s ease-out;
}
@keyframes home-in { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:translateY(0) } }

.home-main {
  flex: 1; display: grid;
  grid-template-columns: 1fr 320px; gap: 14px; min-height: 0;
}
.home-left  { display:flex; flex-direction:column; gap:12px; min-height:0; }
.home-right { display:flex; flex-direction:column; gap:12px; min-height:0; }

/* ========================= SECTION ========================= */
.home-section {
  background: #fdfbf9;
  border: 1px solid #e7ddd3;
  border-radius: 14px;
  display: flex; flex-direction: column;
  overflow: hidden;  /* ensures all 4 corners round */
}
.follow-section   { flex-shrink: 0; }
.schedule-section { flex: 1; min-height: 0; }
.schedule-section .section-body { overflow-y: auto; }
.trending-panel   { flex: 1; min-height: 0; overflow: hidden; }
.trending-panel .section-body { overflow-y: auto; }

.section-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid #f0e8df;
  background: linear-gradient(180deg, #fefcfa 0%, #fdfbf9 100%);
  flex-shrink: 0;
}
.section-title { display:flex; align-items:center; gap:7px; font-size:.9rem; font-weight:700; color:#2e241e; }
.section-icon  { font-size: 1.05rem; }

.badge-pill {
  font-size: .7rem; color: #9e8c7e; font-weight: 500;
  background: #f4eee7; padding: 2px 8px; border-radius: 999px;
}
.more-btn {
  display: inline-flex; align-items: center; gap: 2px; border: none;
  background: transparent; color: #c45d2b; font-size: .76rem; font-weight: 600;
  cursor: pointer; padding: 3px 8px; border-radius: 6px; transition: all .2s;
}
.more-btn:hover { background: rgba(196,93,43,.08); }

.section-body { padding: 10px 14px; }

/* ========================= EMPTY / SKELETON ========================= */
.empty {
  display:flex; align-items:center; justify-content:center; gap:6px;
  padding:22px 14px; color:#9e8c7e; font-size:.83rem;
}
.empty i { font-size:1.05rem; opacity:.6; }
.empty.error { color:#c45d2b; }
.link { border:none; background:transparent; color:#c45d2b; font-weight:600; cursor:pointer; font-size:inherit; text-decoration:underline; text-underline-offset:2px; }

.sk-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(95px,1fr)); gap:10px; }
.sk-card { aspect-ratio:3/4; border-radius:10px; background:linear-gradient(135deg,#f0ebe5 25%,#e8e0d8 50%,#f0ebe5 75%); background-size:200% 100%; animation:shim 1.4s ease-in-out infinite; }
.sk-list { display:flex; flex-direction:column; gap:5px; }
.sk-line { height:38px; border-radius:8px; background:linear-gradient(135deg,#f0ebe5 25%,#e8e0d8 50%,#f0ebe5 75%); background-size:200% 100%; animation:shim 1.4s ease-in-out infinite; }
@keyframes shim { 0%{background-position:200% 0} 100%{background-position:-200% 0} }

/* ========================= ANIME CARD ========================= */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(105px, 1fr));
  gap: 10px;
}

.anime-card {
  display: block;
  cursor: pointer;
  background: #fff;
  border: 1px solid #ece6df;
  border-radius: 11px;
  overflow: hidden;
  transition: transform .22s ease, box-shadow .22s ease;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
  text-decoration: none;
  color: inherit;
}
.anime-card:hover {
  transform: scale(1.04);
  box-shadow: 0 8px 24px rgba(0,0,0,.14);
  z-index: 10;
  position: relative;
}

/* poster */
.card-poster {
  position: relative;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  background: #e8e0d6;
}
.card-poster img {
  width: 100%; height: 100%;
  object-fit: cover; object-position: center top;
}

/* bottom gradient on poster */
.poster-gradient {
  position: absolute; inset: auto 0 0 0; height: 45%;
  background: linear-gradient(to top, rgba(0,0,0,.45) 0%, transparent 100%);
  pointer-events: none;
}

/* score badge on poster (schedule only) */
.poster-score {
  position: absolute; top: 6px; left: 6px;
  background: rgba(0,0,0,.55); backdrop-filter: blur(4px);
  color: #fbbf24; font-size: .66rem; font-weight: 700;
  padding: 2px 7px; border-radius: 999px;
  display: inline-flex; align-items: center; gap: 2px;
}
.poster-score i { font-size: .55rem; }

/* green dot */
.poster-dot {
  position: absolute; top: 6px; right: 6px;
  width: 8px; height: 8px; border-radius: 50%;
  background: #22c55e; border: 2px solid #fff;
  box-shadow: 0 0 6px rgba(34,197,94,.5);
}

/* hover play overlay */
.poster-hover {
  position: absolute; inset: 0;
  background: rgba(0,0,0,.28);
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity .2s ease; pointer-events: none;
}
.poster-hover i { font-size: 1.8rem; color:#fff; filter:drop-shadow(0 2px 4px rgba(0,0,0,.3)); }
.anime-card:hover .poster-hover { opacity: 1; }

/* card info area — fixed height, rating always at bottom */
.card-info {
  padding: 6px 7px 7px;
  height: 50px;              /* fixed total height */
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;           /* clip stray overflow */
  max-width: 100%;
}
.card-title {
  flex: 1;
  margin: 0; max-width: 100%;
  font-size: .72rem; font-weight: 600; color: #2e241e;
  line-height: 1.35;
  /* line-clamp with hard max-height fallback */
  display: -webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical;
  overflow: hidden; word-break: break-word; overflow-wrap: anywhere;
  max-height: 2.7em;               /* 2 × 1.35 line-height, clips 3rd line */
  text-align: center;
}
.card-meta {
  flex-shrink: 0;
  font-size: .64rem; font-weight: 600;
  padding: 1px 7px; border-radius: 999px;
  margin-top: 3px;
}
.card-meta.meta-active { background: rgba(196,93,43,.12); color: #c45d2b; }
.card-meta.meta-done   { background: rgba(34,197,94,.1);  color: #16a34a; }

/* ========================= DAY TABS ========================= */
.day-tabs { display:flex; gap:4px; margin-bottom:10px; flex-shrink:0; }
.day-tab {
  display:flex; flex-direction:column; align-items:center; gap:1px; border:none;
  background:#f4eee7; color:#8b6f5e; padding:4px 0; border-radius:8px;
  cursor:pointer; transition:all .2s; min-width:40px; flex:1;
}
.day-tab:hover { background:#ede3d8; color:#5c4032; }
.day-tab.active { background:#2e241e; color:#fff; }
.day-tab.today:not(.active) .day-label { color:#c45d2b; font-weight:700; }
.day-label { font-size:.7rem; font-weight:600; }
.day-badge { font-size:.62rem; font-weight:700; opacity:.7; }
.day-tab.active .day-badge { opacity:1; }

/* ========================= TRENDING LIST ========================= */
.trending-list { list-style:none; margin:0; padding:0; display:flex; flex-direction:column; gap:1px; }
.t-item {
  display:flex; align-items:center; padding:0; border-radius:8px;
  min-width:0;
}
.t-link {
  display:flex; align-items:center; gap:8px; padding:5px 8px; border-radius:8px;
  text-decoration:none; color:inherit; flex:1; min-width:0;
  transition:background .15s;
}
.t-link:hover { background:linear-gradient(90deg,#fef9f4 0%,#fdfbf9 100%); }

/* ===== sticky preview slot — follows scroll, never shifts list ===== */
.t-preview-slot {
  position:sticky; top:0; z-index:5;
  height: 22px;                     /* thin when empty */
  display:flex; align-items:center; justify-content:center;
  transition: height .28s ease;
  overflow:hidden;
  background:#fdfbf9;               /* cover list items behind */
}
.t-preview-slot.active {
  height: 127px;                    /* expands to fit card */
}
.t-preview-hint {
  font-size: .68rem; color: #bfb0a0;
  display:inline-flex; align-items:center; gap:4px;
  transition: opacity .2s;
}
.t-preview-card {
  display:flex; gap:12px; padding:10px 12px;
  background:linear-gradient(135deg,#fef9f4,#fdf5ec);
  border:1px solid #ead9c8; border-radius:12px;
  width:100%;
}
.t-preview-card img {
  width:80px; height:107px; border-radius:8px;
  object-fit:cover; object-position:center top; flex-shrink:0;
  box-shadow:0 3px 12px rgba(0,0,0,.14);
}
.t-preview-info {
  display:flex; flex-direction:column; justify-content:center; gap:6px; min-width:0;
}
.t-preview-score {
  display:inline-flex; align-items:center; gap:3px; align-self:flex-start;
  background:rgba(196,93,43,.12); color:#c45d2b;
  font-size:.78rem; font-weight:700; padding:3px 10px; border-radius:999px;
}
.t-preview-score i { font-size:.65rem; }
.t-preview-title {
  margin:0; font-size:.82rem; font-weight:600; color:#2e241e;
  line-height:1.35; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical;
  overflow:hidden;
}

.t-rank {
  flex-shrink:0; width:20px; height:20px; border-radius:5px;
  display:flex; align-items:center; justify-content:center;
  font-size:.68rem; font-weight:700; background:#f0ebe5; color:#8b6f5e;
}
.rank-gold   { background:linear-gradient(135deg,#fbbf24,#f59e0b); color:#fff; box-shadow:0 1px 4px rgba(245,158,11,.3); }
.rank-silver { background:linear-gradient(135deg,#cbd5e1,#94a3b8); color:#fff; box-shadow:0 1px 4px rgba(148,163,184,.3); }
.rank-bronze { background:linear-gradient(135deg,#fed7aa,#fb923c); color:#fff; box-shadow:0 1px 4px rgba(251,146,60,.3); }

.t-thumb    { flex-shrink:0; width:28px; height:37px; border-radius:4px; object-fit:cover; object-position:center top; background:#e8e0d6; }
.t-thumb-ph { flex-shrink:0; width:28px; height:37px; border-radius:4px; background:linear-gradient(135deg,#f0ebe5,#e8e0d8); }

.t-title {
  flex:1; min-width:0; font-size:.8rem; font-weight:500; color:#2e241e;
  white-space:nowrap; overflow:hidden; text-overflow:ellipsis;
}

.t-heat { flex-shrink:0; font-size:.65rem; color:#9e8c7e; font-weight:500; font-variant-numeric:tabular-nums; }
.t-score {
  flex-shrink:0; font-size:.74rem; font-weight:600; color:#c45d2b;
  display:inline-flex; align-items:center; gap:2px; min-width:34px; justify-content:flex-end;
}
.t-score i { font-size:.6rem; }

/* ================================================================ */
/*                        RESPONSIVE                                 */
/* ================================================================ */
@media (max-width: 1280px) {
  .home-main { grid-template-columns: 1fr 290px; gap: 12px; }
}

@media (max-width: 1024px) {
  .home-root { height: auto; flex: 1; }
  .home-main { grid-template-columns: 1fr; gap: 12px; }
  .home-right { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .trending-panel { flex: none; max-height: 380px; }
  .schedule-section { flex: none; max-height: 420px; }
}

@media (max-width: 768px) {
  .home-right { grid-template-columns: 1fr 1fr; gap: 10px; }
  .trending-panel { max-height: 340px; }
  .schedule-section { max-height: 380px; }
  .card-grid { grid-template-columns: repeat(auto-fill, minmax(90px, 1fr)); gap: 8px; }
  .card-info { height: 46px; }
  .card-title { font-size: .68rem; }
  .section-header { padding: 7px 12px; }
  .section-body   { padding: 8px 12px; }
}

@media (max-width: 600px) {
  .home-right { grid-template-columns: 1fr; gap: 10px; }
  .trending-panel { max-height: 300px; }
  .schedule-section { max-height: 360px; }
  /* smaller preview on mobile */
  .t-preview-slot.active { height: 105px; }
  .t-preview-card img { width: 60px; height: 80px; }
  .t-preview-title { font-size: .74rem; }
  .card-grid { grid-template-columns: repeat(auto-fill, minmax(82px, 1fr)); gap: 7px; }
  .card-info { height: 44px; }
  .card-title { font-size: .65rem; }
  .day-tab { min-width: 34px; padding: 3px 0; }
  .day-label { font-size: .65rem; }
  .day-badge { font-size: .58rem; }
}

@media (max-width: 480px) {
  .home-root { height: auto; }
  .home-main, .home-left, .home-right { gap: 8px; }
  .card-grid { grid-template-columns: repeat(3, 1fr); gap: 6px; }
  .card-info { height: 40px; padding: 4px 5px 5px; }
  .card-title { font-size: .62rem; }
  .card-meta { font-size: .58rem; padding: 1px 5px; }
  .poster-score { font-size: .6rem; padding: 1px 5px; top: 4px; left: 4px; }
  .section-header { padding: 6px 10px; }
  .section-body   { padding: 6px 10px; }
  .day-tabs { gap: 2px; }
  .day-tab { min-width: 30px; padding: 3px 0; border-radius: 6px; }
  .day-badge { display: none; }
  .trending-panel { max-height: 260px; }
  .schedule-section { max-height: 300px; }
  .t-link { padding: 4px 6px; gap: 6px; }
  .t-title { font-size: .74rem; }
  .t-thumb, .t-thumb-ph { width: 24px; height: 32px; }
  .t-rank { width: 18px; height: 18px; font-size: .62rem; }
  .t-score { font-size: .7rem; min-width: 30px; }
  .t-heat { display: none; }
}

@media (max-width: 380px) {
  .card-grid { grid-template-columns: repeat(3, 1fr); gap: 5px; }
  .card-title { font-size: .6rem; }
  .card-info { height: 38px; }
}
</style>
