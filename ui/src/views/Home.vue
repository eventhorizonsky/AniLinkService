<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const API_BASE = '/api'

const STATUS_LABEL_MAP = { wish: '想看', watching: '在看', watched: '看过', on_hold: '搁置', dropped: '抛弃' }
const statusLabel = (s) => STATUS_LABEL_MAP[s] || s

const defaultPoster = 'https://assets.anixplayer.net/image/poster/default.jpg'

// ===================== Follow List =====================
const followList = ref([])
const followLoading = ref(false)

// ===================== Trending =====================
const trendingHot = ref([])
const trendingNewAnime = ref([])
const trendingLoading = ref(false)
const trendingError = ref('')

// ===================== User =====================
const userInfo = ref(null)
try {
  const stored = localStorage.getItem('userInfo')
  if (stored) userInfo.value = JSON.parse(stored)
} catch (e) { /* ignore */ }

const isLoggedIn = computed(() => !!localStorage.getItem('token') && !!userInfo.value)

// ===================== Hero Carousel =====================
const heroSlides = computed(() => trendingHot.value.slice(0, 5))
const heroTags = ['🔥 热播中', '✨ 新作', '🏆 霸权', '🎬 热门', '⭐ 推荐']
const heroTag = (i) => heroTags[i % heroTags.length]
const heroBg = (slide) => slide.imageUrl || defaultPoster

const currentSlide = ref(0)
let autoPlayTimer = null

const goToSlide = (index) => { currentSlide.value = index }
const selectSlide = (index) => { goToSlide(index); resetAutoPlay() }
const nextSlide = () => {
  if (heroSlides.value.length > 0) goToSlide((currentSlide.value + 1) % heroSlides.value.length)
}
const prevSlide = () => {
  if (heroSlides.value.length > 0) goToSlide((currentSlide.value - 1 + heroSlides.value.length) % heroSlides.value.length)
}
const resetAutoPlay = () => {
  if (autoPlayTimer) clearInterval(autoPlayTimer)
  if (heroSlides.value.length > 1) autoPlayTimer = setInterval(nextSlide, 5500)
}
const pauseAutoPlay = () => {
  if (autoPlayTimer) clearInterval(autoPlayTimer)
}

// ===================== Hero touch swipe =====================
// 移动端横向滑动切换，滑动后抑制随后的 click 避免误跳转
let touchStartX = null
let suppressClick = false
const onTouchStart = (e) => { touchStartX = e.touches[0].clientX }
const onTouchEnd = (e) => {
  if (touchStartX === null) return
  const dx = e.changedTouches[0].clientX - touchStartX
  touchStartX = null
  if (Math.abs(dx) < 50) return
  if (dx < 0) nextSlide(); else prevSlide()
  resetAutoPlay()
  suppressClick = true
  setTimeout(() => { suppressClick = false }, 400)
}

// ===================== Navigation =====================
const goToDetail = (a) => {
  if (suppressClick) { suppressClick = false; return }
  if (a?.animeId) router.push('/anime/' + a.animeId)
}
const goToSearch = () => router.push('/search')
const goToFollows = () => router.push('/profile/follows')

// ===================== Follow horizontal scroll =====================
// Vue 的 @wheel 默认 passive，preventDefault 无效，需手动挂非 passive 监听
const followScrollRef = ref(null)
let followWheelHandler = null

const detachFollowWheel = () => {
  if (followWheelHandler && followScrollRef.value) {
    followScrollRef.value.removeEventListener('wheel', followWheelHandler)
    followWheelHandler = null
  }
}
const attachFollowWheel = () => {
  detachFollowWheel()
  const el = followScrollRef.value
  if (!el) return
  followWheelHandler = (e) => {
    e.preventDefault()
    el.scrollLeft += e.deltaY
  }
  el.addEventListener('wheel', followWheelHandler, { passive: false })
}

watch(() => followList.value.length, () => {
  if (followList.value.length) nextTick(attachFollowWheel)
  else detachFollowWheel()
})

// ===================== Formatting =====================
const fmtScore = (v) => {
  if (v == null || v === '') return '-'
  const n = Number(v); return Number.isNaN(n) ? '-' : n.toFixed(1)
}
const fmtHeat = (v) => {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (n >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, '') + '亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1).replace(/\.0$/, '') + '万'
  return String(n)
}
const genreLabel = (a, kind) => {
  if (kind === 'hot') return '本周热播'
  if (kind === 'new') return '热门新番'
  if (kind === 'follow') return statusLabel(a.status)
  return a.type || '动漫'
}

// ===================== Fetch =====================
const fetchFollowList = async () => {
  if (!isLoggedIn.value) return
  followLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/follows/active`)
    if (res.data?.code === 200) {
      // 首页只展示已绑定本地番剧的追番，animeId 为空（未匹配）的不展示
      followList.value = (Array.isArray(res.data.data) ? res.data.data : []).filter((item) => item.animeId)
    }
  } catch (e) { console.error('Fetch follow list failed:', e) }
  finally { followLoading.value = false }
}

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

// ===================== Lifecycle =====================
watch(() => trendingHot.value.length, () => {
  currentSlide.value = 0
  resetAutoPlay()
})

onMounted(() => {
  fetchTrending()
  if (isLoggedIn.value) fetchFollowList()
})

onBeforeUnmount(() => {
  detachFollowWheel()
  if (autoPlayTimer) clearInterval(autoPlayTimer)
})
</script>

<template>
  <div class="home-root">
    <!-- ===== Hero Carousel ===== -->
    <section
      v-if="heroSlides.length"
      class="hero-section"
      @mouseenter="pauseAutoPlay"
      @mouseleave="resetAutoPlay"
      @touchstart.passive="onTouchStart"
      @touchend.passive="onTouchEnd"
    >
      <div
        v-for="(slide, i) in heroSlides"
        :key="slide.animeId || i"
        class="hero-slide"
        :class="{ active: currentSlide === i }"
        @click="goToDetail(slide)"
      >
        <!-- 模糊封面作为背景 -->
        <div class="hero-bg" :style="{ backgroundImage: `url(${heroBg(slide)})` }"></div>
        <div class="hero-overlay"></div>

        <!-- 左侧文案 -->
        <div class="hero-content">
          <span class="tag">{{ heroTag(i) }}</span>
          <h2>{{ slide.animeTitle }}</h2>
          <div class="meta">
            <span><i class="mdi mdi-star" style="color:#fbbf24;"></i> {{ fmtScore(slide.rating) }}</span>
            <span v-if="slide.heat" class="meta-heat"><i class="mdi mdi-fire"></i> {{ fmtHeat(slide.heat) }} 热度</span>
            <span><i class="mdi mdi-play-circle-outline"></i> 立即观看</span>
          </div>
        </div>

        <!-- 右侧封面卡片（原比例） -->
        <div class="hero-poster">
          <img :src="slide.imageUrl || defaultPoster" :alt="slide.animeTitle" loading="lazy" />
          <div class="poster-hover"><i class="mdi mdi-play-circle-outline"></i></div>
        </div>
      </div>

      <div class="hero-controls">
        <button
          v-for="(slide, i) in heroSlides"
          :key="'c' + i"
          :class="{ active: currentSlide === i }"
          :aria-label="`切换到第 ${i + 1} 张`"
          @click="selectSlide(i)"
        ></button>
      </div>

      <button class="hero-arrow hero-arrow--prev" aria-label="上一张" @click="prevSlide(); resetAutoPlay()">
        <i class="mdi mdi-chevron-left"></i>
      </button>
      <button class="hero-arrow hero-arrow--next" aria-label="下一张" @click="nextSlide(); resetAutoPlay()">
        <i class="mdi mdi-chevron-right"></i>
      </button>
    </section>
    <div v-else-if="trendingLoading" class="hero-section hero-skeleton"></div>

    <!-- ===== 我的追番 ===== -->
    <template v-if="isLoggedIn">
      <div class="br-section-header">
        <h3><i class="mdi mdi-bookmark-multiple"></i> 我的追番</h3>
        <span class="br-view-all" @click="goToFollows">查看全部 <i class="mdi mdi-chevron-right"></i></span>
      </div>

      <div v-if="followLoading" class="br-sk-grid">
        <div v-for="i in 6" :key="'f' + i" class="br-sk-card"></div>
      </div>
      <div v-else-if="!followList.length" class="br-empty">
        <i class="mdi mdi-movie-open-outline"></i> 还没有追番，去
        <button class="br-link" @click="goToSearch">发现番剧</button>
      </div>
      <div v-else ref="followScrollRef" class="br-follow-scroll">
        <div
          v-for="a in followList.slice(0, 10)"
          :key="a.id"
          class="br-card br-follow-card"
          @click="goToDetail(a)"
        >
          <div class="br-card-image">
            <img :src="a.imageUrl || defaultPoster" :alt="a.animeTitle" loading="lazy" />
            <div class="poster-hover"><i class="mdi mdi-play-circle-outline"></i></div>
            <span v-if="a.unreadEpisodeCount > 0" class="br-follow-unread" title="未读新剧集">{{ a.unreadEpisodeCount }}</span>
          </div>
          <div class="br-card-body">
            <h4>{{ a.animeTitle }}</h4>
            <div class="br-card-meta">
              <span class="genre" :class="a.status === 'watching' ? 'genre-active' : 'genre-done'">
                {{ genreLabel(a, 'follow') }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== 本周热门推荐 ===== -->
    <div class="br-section-header">
      <h3><i class="mdi mdi-fire"></i> 本周热门推荐</h3>
      <span class="br-view-all" @click="goToSearch">查看全部 <i class="mdi mdi-chevron-right"></i></span>
    </div>

    <div v-if="trendingLoading" class="br-sk-grid">
      <div v-for="i in 8" :key="'h' + i" class="br-sk-card"></div>
    </div>
    <div v-else-if="!trendingHot.length" class="br-empty"><i class="mdi mdi-fire-off"></i> {{ trendingError || '暂无热门数据' }}</div>
    <div v-else class="br-grid">
      <div v-for="a in trendingHot.slice(0, 10)" :key="a.animeId" class="br-card" @click="goToDetail(a)">
        <div class="br-card-image">
          <img :src="a.imageUrl || defaultPoster" :alt="a.animeTitle" loading="lazy" />
          <span class="br-badge-new">热播</span>
          <span v-if="a.heat" class="br-badge-ep"><i class="mdi mdi-fire"></i> {{ fmtHeat(a.heat) }}</span>
        </div>
        <div class="br-card-body">
          <h4>{{ a.animeTitle }}</h4>
          <div class="br-card-meta">
            <span class="genre">{{ genreLabel(a, 'hot') }}</span>
            <span class="rating"><i class="mdi mdi-star"></i> {{ fmtScore(a.rating) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 热门新番 ===== -->
    <div class="br-section-header">
      <h3><i class="mdi mdi-rocket-launch-outline"></i> 热门新番</h3>
      <span class="br-view-all" @click="goToSearch">查看全部 <i class="mdi mdi-chevron-right"></i></span>
    </div>

    <div v-if="trendingLoading" class="br-sk-grid">
      <div v-for="i in 8" :key="'n' + i" class="br-sk-card"></div>
    </div>
    <div v-else-if="!trendingNewAnime.length" class="br-empty"><i class="mdi mdi-rocket-launch-outline"></i> 暂无新作数据</div>
    <div v-else class="br-grid">
      <div v-for="a in trendingNewAnime.slice(0, 10)" :key="a.animeId" class="br-card" @click="goToDetail(a)">
        <div class="br-card-image">
          <img :src="a.imageUrl || defaultPoster" :alt="a.animeTitle" loading="lazy" />
          <span class="br-badge-new br-badge-new--sparkle">新作</span>
          <span v-if="a.heat" class="br-badge-ep"><i class="mdi mdi-fire"></i> {{ fmtHeat(a.heat) }}</span>
        </div>
        <div class="br-card-body">
          <h4>{{ a.animeTitle }}</h4>
          <div class="br-card-meta">
            <span class="genre">{{ genreLabel(a, 'new') }}</span>
            <span class="rating"><i class="mdi mdi-star"></i> {{ fmtScore(a.rating) }}</span>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.home-root {
  --accent: #c45d2b;
  --accent-soft: rgba(196, 93, 43, 0.12);
  --bg-beige: #f4eee7;
  --radius-lg: 20px;
  --radius-md: 14px;
  --radius-full: 9999px;
  --shadow-md: 0 4px 20px rgba(0, 0, 0, 0.06);
  --transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  animation: home-in 0.35s ease-out;
}
@keyframes home-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

/* ===================== Hero ===================== */
.hero-section {
  position: relative;
  width: 100%;
  height: clamp(260px, 30vh, 360px);
  margin: 4px 0 8px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-beige);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  touch-action: pan-y;
}
.hero-skeleton {
  background: linear-gradient(135deg, var(--bg-beige) 25%, #ede3d8 50%, var(--bg-beige) 75%);
  background-size: 200% 100%;
  animation: shim 1.4s ease-in-out infinite;
}
@keyframes shim { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.hero-slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.9s ease;
  /* 非激活的透明 slide 仍在 DOM 中叠在上层，会拦截点击（例如 B 显示时点到 C），需禁用指针事件 */
  pointer-events: none;
}
.hero-slide.active { opacity: 1; pointer-events: auto; }

/* 模糊封面作为背景 */
.hero-bg {
  position: absolute;
  inset: -32px;
  background-size: cover;
  background-position: center 30%;
  filter: blur(26px) saturate(1.3) brightness(0.9);
}

/* 左侧加深渐变保证文字可读，右侧保留亮部衬托封面 */
.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 12, 8, 0.82) 0%, rgba(18, 12, 8, 0.55) 42%, rgba(18, 12, 8, 0.18) 72%, rgba(18, 12, 8, 0.42) 100%);
}

.hero-content {
  position: absolute;
  left: 48px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  max-width: 52%;
  color: #fff;
}
.hero-content .tag {
  display: inline-block;
  background: rgba(196, 93, 43, 0.9);
  backdrop-filter: blur(4px);
  padding: 3px 14px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  margin-bottom: 14px;
  color: #fff;
}
.hero-content h2 {
  font-size: clamp(22px, 3vw, 38px);
  font-weight: 700;
  line-height: 1.15;
  margin-bottom: 14px;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.35);
}
.hero-content .meta {
  display: flex;
  gap: 18px;
  font-size: 13px;
  opacity: 0.92;
}
.hero-content .meta span { display: flex; align-items: center; gap: 6px; }
.hero-content .meta i { font-size: 13px; }

/* 右侧封面卡片（原比例 283:400） */
.hero-poster {
  position: absolute;
  right: 88px;
  top: 50%;
  transform: translateY(-50%);
  width: 172px;
  aspect-ratio: 283 / 400;
  border-radius: 16px;
  overflow: hidden;
  background: #e6e0d6;
  border: 2px solid rgba(255, 255, 255, 0.28);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5), 0 4px 16px rgba(0, 0, 0, 0.25);
  z-index: 2;
}
.hero-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
}
.hero-poster:hover .poster-hover { opacity: 1; }

/* hover 播放遮罩 */
.poster-hover {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
}
.poster-hover i { font-size: 2rem; color: #fff; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3)); }

/* 追番卡片未读新剧集角标 */
.br-follow-unread {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #e53935;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(229, 57, 53, 0.45);
}

/* 轮播圆点 */
.hero-controls {
  position: absolute;
  bottom: 18px;
  left: 48px;
  z-index: 5;
  display: flex;
  gap: 8px;
}
.hero-controls button {
  width: 10px;
  height: 10px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(4px);
  cursor: pointer;
  transition: var(--transition);
  padding: 0;
}
.hero-controls button.active { background: #fff; width: 28px; border-radius: 6px; }
.hero-controls button:hover { background: rgba(255, 255, 255, 0.75); }

/* 上一张 / 下一张 箭头 */
.hero-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 6;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.32);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, background 0.2s ease;
  padding: 0;
}
.hero-section:hover .hero-arrow { opacity: 1; }
.hero-arrow:hover { background: rgba(196, 93, 43, 0.9); }
.hero-arrow:active { transform: translateY(-50%) scale(0.92); }
.hero-arrow--prev { right: calc(88px + 172px + 14px); }
.hero-arrow--next { right: 22px; }

/* ===================== Responsive ===================== */
@media (max-width: 820px) {
  .hero-section { height: 230px; }
  .hero-poster { width: 120px; right: 64px; }
  .hero-content { left: 26px; max-width: calc(100% - 230px); }
  .hero-content h2 { font-size: 20px; }
  .hero-controls { left: 26px; }
  .hero-arrow { width: 38px; height: 38px; font-size: 20px; opacity: 1; }
  .hero-arrow--prev { right: calc(64px + 120px + 12px); }
  .hero-arrow--next { right: 13px; }
}

@media (max-width: 480px) {
  .hero-section { height: 190px; border-radius: var(--radius-md); }
  .hero-poster { width: 96px; right: 52px; border-radius: 12px; border-width: 1.5px; }
  .hero-content { left: 18px; max-width: calc(100% - 178px); }
  .hero-content .tag { font-size: 10px; padding: 2px 10px; margin-bottom: 8px; }
  .hero-content h2 { font-size: 16px; margin-bottom: 8px; }
  .hero-content .meta { gap: 10px; font-size: 11px; }
  .hero-content .meta .meta-heat { display: none; }
  .hero-controls { bottom: 12px; left: 18px; }
  .hero-arrow { width: 32px; height: 32px; font-size: 18px; }
  .hero-arrow--prev { right: calc(52px + 96px + 10px); }
  .hero-arrow--next { right: 10px; }
}
</style>
