<template>
  <!-- 加载中 -->
  <div class="player-page" v-if="loading && !animeData" key="loading">
    <div class="loading-state">加载中...</div>
  </div>

  <!-- 发生错误 -->
  <div class="player-page" v-else-if="error" key="error">
    <div class="error-state">{{ error }}</div>
  </div>

  <!-- 正常显示 -->
  <div class="player-page" v-else key="loaded">
    <div class="player-layout">
      <!-- 左侧：播放器和主要内容 -->
      <div class="player-main">
        <!-- 播放器容器 -->
        <div class="player-card">
          <div ref="artRef" class="artplayer-container"></div>
          <div v-if="isSwitching" class="player-switching-overlay">
            <div class="player-switching-content">
              <div class="player-switching-spinner"></div>
              <div class="player-switching-text">正在切换分集...</div>
            </div>
          </div>
        </div>

        <div v-if="showDdplayButton" class="player-actions">
          <button class="ddplay-btn" @click="openWithDdplay">
            <i class="mdi mdi-play-network-outline"></i>
            <span>通过弹弹play播放</span>
          </button>
        </div>

        <!-- 番剧信息部分 -->
        <div v-if="animeData" class="anime-info-section">
          <!-- 头部信息 -->
          <AnimeHeroSection 
            :anime-data="animeData"
            :title-info="titleInfo"
            :is-on-air="isOnAir"
            :air-day-text="airDayText"
            :rating-main="ratingMain"
            :rating-bangumi="ratingBangumi"
            :rating-anidb="ratingAnidb"
            :main-episodes="mainEpisodes"
            :total-episodes="totalEpisodes"
            :formatted-summary="formattedSummary"
            :is-summary-expanded="isSummaryExpanded"
            :is-favorited="isFavorited"
            :is-following="isFollowing"
            :follow-loading="followLoading"
            @update:is-summary-expanded="isSummaryExpanded = $event"
            @toggleFavorite="toggleFavorite"
            @toggleFollow="toggleFollow"
          />

          <!-- 分集列表 -->
          <EpisodeListSection
            :episodes="animeData.episodes"
            :main-count="mainEpisodes.length"
            :total-count="animeData.episodes.length"
            :playable-episode-keys="playableEpisodeKeys"
            :current-episode-id="episodeId"
            @playEpisode="playEpisode"
          />

          <!-- 预告片 -->
          <TrailerCarousel :trailers="animeData.trailers" />

          <!-- 外部链接 -->
          <FooterLinks
            :databases="animeData.onlineDatabases"
            :copyright-text="copyrightText"
          />
        </div>
      </div>

      <!-- 右侧边栏 -->
      <div v-if="animeData" class="player-sidebar">
        <!-- 制作信息 -->
        <MetadataCard
          :staff-list="staffList"
          :tags="animeData.tags"
        />

        <!-- 相关作品 -->
        <RelatedWorksCarousel :relateds="animeData.relateds" />
      </div>
    </div>

    <!-- 资源选择对话框 -->
    <div v-if="showResourceDialog" class="resource-dialog-mask" @click.self="closeResourceDialog">
      <div class="resource-dialog">
        <h3 class="resource-dialog-title">选择播放资源</h3>
        <p class="resource-dialog-subtitle">{{ selectedEpisodeTitle }}</p>
        <div class="resource-list">
          <button
            v-for="(resource, index) in selectedResources"
            :key="resource.id"
            class="resource-item"
            @click="selectResource(resource)"
          >
            <span class="resource-name">{{ resource.fileName || `资源 ${index + 1}` }}</span>
            <span class="resource-meta">ID: {{ resource.id }}</span>
          </button>
        </div>
        <button class="resource-cancel-btn" @click="closeResourceDialog">取消</button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { computed, ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import SubtitlesOctopus from 'libass-wasm'
import { showAppMessage } from '../utils/ui-feedback'
import AnimeHeroSection from '../components/anime/AnimeHeroSection.vue'
import EpisodeListSection from '../components/anime/EpisodeListSection.vue'
import TrailerCarousel from '../components/anime/TrailerCarousel.vue'
import RelatedWorksCarousel from '../components/anime/RelatedWorksCarousel.vue'
import MetadataCard from '../components/anime/MetadataCard.vue'
import FooterLinks from '../components/anime/FooterLinks.vue'

const route = useRoute()
const router = useRouter()

const videoId = computed(() => String(route.params.videoId || ''))
const animeId = computed(() => String(route.query.animeId || ''))
const episodeId = computed(() => String(route.query.episodeId || ''))
const subtitleTrackId = computed(() => String(route.query.subtitleId || ''))

// Anime Data State
const animeData = ref(null)
const existingEpisodes = ref([])
const loading = ref(false)
const error = ref(null)
const isSummaryExpanded = ref(false)
const isFavorited = ref(false)
const isFollowing = ref(false)
const followLoading = ref(false)
const showResourceDialog = ref(false)
const isSwitching = ref(false)
const selectedResources = ref([])
const selectedEpisodeTitle = ref('')
const isDesktopViewport = ref(true)

const artRef = ref(null)
const art = shallowRef(null)
const subtitleOctopus = ref(null)
const tmpSubtitleOctopusSubUrl = ref('')
const selectedSubtitleTrack = ref(null)
let progressSaveTimer = null
let subtitleOffsetPersistTimer = null

const subtitlesOctopusWorkJsPath = '/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.js'
const subtitlesOctopusWorkWasmPath = '/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.wasm'
const subtitlesOctopusFonts = ['/static/SourceHanSansCN-Bold.woff2']
let playerRecreateSeq = 0

const DANMAKU_SETTINGS_STORAGE_KEY = 'anilink:danmaku:settings:v1'
const AIR_DAY_MAP = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
const STAFF_META_KEYS = ['原作', '导演', '音乐', '动画制作']
const EPISODE_SELECTOR_TITLE_MAX_LEN = 28
const RESOURCE_DIALOG_TITLE_MAX_LEN = 40
const LIBASS_SUPPORTED_SUBTITLE_FORMATS = new Set(['ass', 'ssa'])
const NATIVE_ARTPLAYER_SUBTITLE_FORMATS = new Set(['srt', 'vtt'])

const DEFAULT_DANMAKU_SETTINGS = {
  speed: 7,
  opacity: 1,
  fontSize: 25,
  color: '#FFFFFF',
  mode: 0,
  margin: [10, '25%'],
  antiOverlap: true,
  synchronousPlayback: false,
  lockTime: 5,
  maxLength: 200,
  theme: 'dark',
  emitter: true,
  visible: true,
}

const MOBILE_VIEWPORT_MAX_WIDTH = 768

const isMobileViewport = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia(`(max-width: ${MOBILE_VIEWPORT_MAX_WIDTH}px)`).matches
}

const normalizeDanmakuSettings = (raw) => {
  if (!raw || typeof raw !== 'object') {
    return {}
  }

  const margin = Array.isArray(raw.margin) && raw.margin.length === 2
    ? [raw.margin[0], raw.margin[1]]
    : undefined

  return {
    speed: typeof raw.speed === 'number' ? raw.speed : undefined,
    opacity: typeof raw.opacity === 'number' ? raw.opacity : undefined,
    fontSize: typeof raw.fontSize === 'number' || typeof raw.fontSize === 'string' ? raw.fontSize : undefined,
    color: typeof raw.color === 'string' ? raw.color : undefined,
    mode: typeof raw.mode === 'number' ? raw.mode : undefined,
    margin,
    antiOverlap: typeof raw.antiOverlap === 'boolean' ? raw.antiOverlap : undefined,
    synchronousPlayback: typeof raw.synchronousPlayback === 'boolean' ? raw.synchronousPlayback : undefined,
    lockTime: typeof raw.lockTime === 'number' ? raw.lockTime : undefined,
    maxLength: typeof raw.maxLength === 'number' ? raw.maxLength : undefined,
    theme: raw.theme === 'light' || raw.theme === 'dark' ? raw.theme : undefined,
    emitter: typeof raw.emitter === 'boolean' ? raw.emitter : undefined,
    visible: typeof raw.visible === 'boolean' ? raw.visible : undefined,
  }
}

const loadDanmakuSettings = () => {
  try {
    const cache = localStorage.getItem(DANMAKU_SETTINGS_STORAGE_KEY)
    if (!cache) {
      return {}
    }
    const parsed = JSON.parse(cache)
    return normalizeDanmakuSettings(parsed)
  } catch (e) {
    console.warn('读取弹幕配置缓存失败:', e)
    return {}
  }
}

const saveDanmakuSettings = (option) => {
  try {
    const normalized = normalizeDanmakuSettings(option)
    localStorage.setItem(DANMAKU_SETTINGS_STORAGE_KEY, JSON.stringify(normalized))
  } catch (e) {
    console.warn('保存弹幕配置缓存失败:', e)
  }
}

const fetchJson = async (url, options) => {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(`请求失败(${response.status}): ${url}`)
  }
  return response.json()
}

/**
 * 获取番剧数据
 */
const fetchAnimeData = async () => {
  try {
    if (!animeId.value) return

    loading.value = true
    error.value = null

    const [animeResp, episodesResp] = await Promise.allSettled([
      fetchJson(`/api/animes/${animeId.value}/raw-json`),
      fetchJson(`/api/animes/${animeId.value}/episodes?page=1&pageSize=9999`),
    ])

    if (animeResp.status === 'fulfilled' && animeResp.value.code === 200 && animeResp.value.data?.bangumi) {
      animeData.value = animeResp.value.data.bangumi
      await checkFollowStatus()
    } else {
      animeData.value = null
      isFollowing.value = false
    }

    if (episodesResp.status === 'fulfilled' && episodesResp.value.code === 200 && Array.isArray(episodesResp.value.data?.content)) {
      existingEpisodes.value = episodesResp.value.data.content
    } else {
      existingEpisodes.value = []
    }

    if (animeResp.status === 'rejected') {
      throw animeResp.reason
    }
    if (!animeData.value) {
      throw new Error('获取番剧信息失败')
    }
  } catch (err) {
    animeData.value = null
    error.value = err?.message || '获取番剧数据失败'
  } finally {
    loading.value = false
  }
}

const checkFollowStatus = async () => {
  const token = localStorage.getItem('token')
  if (!token || !animeId.value) {
    isFollowing.value = false
    return
  }

  try {
    const response = await axios.get(`/api/follows/check/${animeId.value}`)
    if (response.data?.code === 200) {
      isFollowing.value = Boolean(response.data.data)
    } else {
      isFollowing.value = false
    }
  } catch (e) {
    console.error('检查追番状态失败:', e)
    isFollowing.value = false
  }
}

const toggleFollow = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    showAppMessage('请先登录', 'warning')
    return
  }
  if (!animeId.value || !animeData.value) {
    showAppMessage('番剧数据未就绪，请稍后重试', 'warning')
    return
  }

  followLoading.value = true
  try {
    if (isFollowing.value) {
      await axios.delete(`/api/follows/${animeId.value}`)
      isFollowing.value = false
      showAppMessage('已取消追番', 'success')
    } else {
      await axios.post('/api/follows', {
        animeId: Number(animeId.value),
        animeTitle: animeData.value?.titles?.[0]?.title || '未知',
        imageUrl: animeData.value?.imageUrl || '',
      })
      isFollowing.value = true
      showAppMessage('追番成功', 'success')
    }
  } catch (e) {
    console.error('切换追番状态失败:', e)
    showAppMessage('操作失败，请重试', 'error')
  } finally {
    followLoading.value = false
  }
}

/**
 * Computed properties for anime display
 */
const isFuture = (ep) => new Date(ep.airDate) > new Date()

const getEpisodeType = (ep) => {
  const num = ep.episodeNumber
  if (/^\d+$/.test(num)) return 'main'
  if (num.startsWith('S')) return 'special'
  if (num.startsWith('C')) return 'credit'
  return 'other'
}

const mainEpisodes = computed(() => 
  animeData.value?.episodes.filter(ep => getEpisodeType(ep) === 'main') || []
)

const formatRating = (value, digits = 1) => {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '--'
}

const isOnAir = computed(() => Boolean(animeData.value?.isOnAir))
const ratingMain = computed(() => formatRating(animeData.value?.rating, 1))
const ratingBangumi = computed(() => animeData.value?.ratingDetails?.['Bangumi评分'] ?? '7.8')
const ratingAnidb = computed(() => animeData.value?.ratingDetails?.['Anidb连载中评分'] ?? '8.51')

const totalEpisodes = computed(() => {
  const meta = animeData.value?.metadata || []
  const epItem = meta.find(m => m.startsWith('话数'))
  return epItem ? epItem.split(':')[1]?.trim() : '10'
})

const formattedSummary = computed(() => {
  return animeData.value?.summary?.replace(/\n/g, '<br>') || ''
})

const playableEpisodeKeys = computed(() => {
  const set = new Set()
  existingEpisodes.value.forEach((ep) => {
    if (ep.episodeId !== undefined && ep.episodeId !== null) {
      set.add(String(ep.episodeId))
    }
  })
  return set
})

const playableEpisodes = computed(() => {
  const episodes = animeData.value?.episodes || []
  return episodes.filter((ep) => {
    if (!ep || isFuture(ep)) {
      return false
    }
    return getEpisodeResources(ep.episodeId).length > 0
  })
})

const currentEpisodeResource = computed(() => {
  const targetVideoId = String(videoId.value || '')
  if (!targetVideoId) {
    return null
  }
  return existingEpisodes.value.find((item) => String(item?.id || '') === targetVideoId) || null
})

const showDdplayButton = computed(() => {
  return isDesktopViewport.value && Boolean(videoId.value)
})

const ddplayLink = computed(() => {
  if (!videoId.value || typeof window === 'undefined') {
    return ''
  }

  const streamUrl = `${window.location.origin}/api/media-files/stream/${videoId.value}`
  const filePath = currentEpisodeResource.value?.filePath || currentEpisodeResource.value?.fileName || ''
  const withOptionalFilePath = filePath
    ? `${streamUrl}|filePath=${filePath}`
    : streamUrl

  return `ddplay:${encodeURIComponent(withOptionalFilePath)}`
})

const updateViewportState = () => {
  isDesktopViewport.value = !isMobileViewport()
}

const openWithDdplay = () => {
  if (!ddplayLink.value) {
    showAppMessage('未获取到可播放地址', 'warning')
    return
  }
  window.location.href = ddplayLink.value
}

const getCurrentPlayableEpisodeIndex = () => {
  const currentEpisodeKey = String(episodeId.value || '')
  return playableEpisodes.value.findIndex((ep) => String(ep.episodeId) === currentEpisodeKey)
}

const titleInfo = computed(() => {
  const titles = animeData.value?.titles || []
  if (titles.length === 0) return { main: '', sub: '' }
  return {
    main: titles[0]?.title || '',
    sub: titles[1]?.title || ''
  }
})

const airDayText = computed(() => {
  const day = animeData.value?.airDay
  return AIR_DAY_MAP[day] || ''
})

const staffList = computed(() => {
  const meta = animeData.value?.metadata || []
  return meta
    .filter(item => STAFF_META_KEYS.some(key => item.startsWith(key)))
    .map(item => {
      const parts = item.split(':')
      if (parts.length >= 2) {
        return `<strong>${parts[0]}:</strong>${parts.slice(1).join(':')}`
      }
      return item
    })
})

const copyrightText = computed(() => {
  const meta = animeData.value?.metadata || []
  return meta.find(m => m.startsWith('Copyright')) || ''
})

/**
 * 根据videoId获取字幕文件列表
 */
const fetchSubtitles = async (videoId) => {
  try {
    if (!videoId) {
      console.warn('videoId为空，无法获取字幕')
      return []
    }

    console.log('[subtitle] request list start, videoId =', videoId)
    const response = await fetch(`/api/media-files/${videoId}/subtitles?_ts=${Date.now()}`, {
      cache: 'no-store',
    })
    if (!response.ok) {
      throw new Error(`字幕接口返回状态: ${response.status}`)
    }

    const data = await response.json()
    console.log('字幕API返回数据:', data)

    // 处理后端ApiResponseVO格式返回
    if (data.code === 200 && Array.isArray(data.data)) {
      // 转换字幕数据为插件需要的格式
      return data.data
        .filter(subtitle => subtitle.subtitleFormat && subtitle.filePath)
        .map(subtitle => {
          const format = String(subtitle.subtitleFormat || '').toLowerCase()
          return {
            id: subtitle.id,
            name: subtitle.trackName || `${subtitle.language || '未知语言'}`,
            url: `/api/subtitles/${subtitle.id}/download`,
            format,
            timeOffset: Number(subtitle.timeOffset || 0),
            isLibassSupported: LIBASS_SUPPORTED_SUBTITLE_FORMATS.has(format),
          }
        })
    }

    console.warn('字幕数据格式未知', data)
    return []
  } catch (error) {
    console.error('获取字幕失败:', error)
    return []
  }
}

/**
 * 保存播放进度到后端
 */
const savePlayProgress = async () => {
  const token = localStorage.getItem('token')
  if (!token || !art.value) {
    return
  }
  
  try {
    const currentTime = Math.floor(art.value.currentTime || 0)
    const duration = Math.floor(art.value.duration || 0)
    
    if (!videoId.value || !animeId.value || duration === 0) {
      return
    }
    
    // 如果播放时间小于5秒或进度小于5%，则不保存
    if (currentTime < 5 || currentTime / duration < 0.05) {
      return
    }
    
    const isCompleted = currentTime / duration >= 0.9 // 播放超过90%认为已完成
    
    await axios.post('/api/play-history/progress', {
      videoId: videoId.value,
      videoName: `Episode ${episodeId.value || ''}`,
      animeId: animeId.value,
      episodeId: String(episodeId.value || ''),
      animeTitle: animeData.value?.titles?.[0]?.title || '未知',
      progressSeconds: currentTime,
      durationSeconds: duration,
      isCompleted: isCompleted
    })
    
    console.log('播放进度已保存:', currentTime, '/', duration)
  } catch (error) {
    console.error('保存播放进度失败:', error)
  }
}

/**
 * 加载播放进度
 */
const loadPlayProgress = async () => {
  const token = localStorage.getItem('token')
  if (!token || !animeId.value) {
    return null
  }
  
  try {
    const response = await axios.get(`/api/play-history/anime/${animeId.value}`)
    if (response.data.code === 200 && response.data.data) {
      // 仅在当前播放视频与历史视频一致时恢复秒数，避免跨分集误跳进度。
      if (String(response.data.data.videoId || '') !== String(videoId.value || '')) {
        return null
      }
      return response.data.data.progressSeconds || 0
    }
  } catch (error) {
    console.error('加载播放进度失败:', error)
  }
  return null
}

/**
 * 开始定时保存播放进度
 */
const startProgressSaveTimer = () => {
  stopProgressSaveTimer()
  // 每30秒保存一次
  progressSaveTimer = setInterval(() => {
    savePlayProgress()
  }, 30000)
}

/**
 * 停止定时保存播放进度
 */
const stopProgressSaveTimer = () => {
  if (progressSaveTimer) {
    clearInterval(progressSaveTimer)
    progressSaveTimer = null
  }
}

const toSubtitleOffsetSeconds = (subtitle) => {
  const offsetMs = Number(subtitle?.timeOffset || 0)
  return Number.isFinite(offsetMs) ? offsetMs / 1000 : 0
}

const isLibassSubtitleTrack = (subtitle) => {
  const format = String(subtitle?.format || '').toLowerCase()
  return LIBASS_SUPPORTED_SUBTITLE_FORMATS.has(format)
}

const isNativeArtplayerSubtitleTrack = (subtitle) => {
  const format = String(subtitle?.format || '').toLowerCase()
  return NATIVE_ARTPLAYER_SUBTITLE_FORMATS.has(format)
}

const getNativeSubtitleOffsetMs = () => {
  if (!art.value) {
    return 0
  }
  return Math.round(Number(art.value.subtitleOffset || 0) * 1000)
}

const setNativeSubtitleOffsetMs = (offsetMs) => {
  if (!art.value) {
    return
  }
  art.value.subtitleOffset = Number(offsetMs || 0) / 1000
}

const getCurrentSubtitleOffsetMs = (track = selectedSubtitleTrack.value) => {
  if (!track) {
    return 0
  }
  if (isNativeArtplayerSubtitleTrack(track)) {
    return getNativeSubtitleOffsetMs()
  }
  return Number(track.timeOffset || 0)
}

const switchSubtitleTrack = async (track) => {
  if (!track?.id) {
    return
  }

  const targetSubtitleId = String(track.id)
  if (targetSubtitleId === String(subtitleTrackId.value || '')) {
    if (isLibassSubtitleTrack(track) && subtitleOctopus.value) {
      applySubtitleTrack(track)
    }
    return
  }

  try {
    await router.replace({
      name: 'Player',
      params: { videoId: String(videoId.value || '') },
      query: {
        ...route.query,
        animeId: String(animeId.value || ''),
        episodeId: String(episodeId.value || ''),
        subtitleId: targetSubtitleId,
      },
    })
  } catch (error) {
    console.warn('切换字幕轨道失败:', error)
  }
}

const syncSubtitleOffset = (subtitle) => {
  const octopus = subtitleOctopus.value
  if (!octopus) {
    return
  }

  const offsetSeconds = toSubtitleOffsetSeconds(subtitle)
  octopus.timeOffset = offsetSeconds

  if (octopus.video && typeof octopus.setCurrentTime === 'function') {
    octopus.setCurrentTime(octopus.video.currentTime + offsetSeconds)
  }
}

/**
 * 判断当前登录用户是否为超级管理员
 */
const isCurrentUserAdmin = () => {
  try {
    const userInfoStr = localStorage.getItem('userInfo')
    if (!userInfoStr) return false
    const userInfo = JSON.parse(userInfoStr)
    return Array.isArray(userInfo?.roleCodeList) && userInfo.roleCodeList.includes('super-admin')
  } catch {
    return false
  }
}

/**
 * 将字幕偏移量持久化到后端（仅超管可用）
 */
const persistSubtitleOffset = async (subtitleId, offsetMs) => {
  try {
    const token = localStorage.getItem('token') || ''
    await fetch(`/api/subtitles/${subtitleId}/offset?offset=${offsetMs}`, {
      method: 'PUT',
      headers: { satoken: token },
    })
  } catch (e) {
    console.warn('保存字幕偏移量失败:', e)
  }
}

const stopSubtitleOffsetPersistTimer = () => {
  if (subtitleOffsetPersistTimer) {
    clearTimeout(subtitleOffsetPersistTimer)
    subtitleOffsetPersistTimer = null
  }
}

const queuePersistSubtitleOffset = (track, offsetMs) => {
  if (!isCurrentUserAdmin() || !track?.id) {
    return
  }

  stopSubtitleOffsetPersistTimer()
  subtitleOffsetPersistTimer = setTimeout(() => {
    persistSubtitleOffset(track.id, offsetMs)
  }, 280)
}

/**
 * 调整当前字幕延迟（单位毫秒）。
 * 快捷键：[ 减少 500ms，] 增加 500ms
 * 若当前用户为超管，同时将偏移量入库。
 */
const adjustSubtitleDelay = async (deltaMs) => {
  const track = selectedSubtitleTrack.value
  if (!track) return

  if (isNativeArtplayerSubtitleTrack(track)) {
    const nextOffsetMs = getNativeSubtitleOffsetMs() + deltaMs
    setNativeSubtitleOffsetMs(nextOffsetMs)

    const currentOffsetSec = Number(art.value?.subtitleOffset || 0).toFixed(1)
    if (art.value?.notice) {
      art.value.notice.show = `字幕延迟: ${Number(currentOffsetSec) >= 0 ? '+' : ''}${currentOffsetSec}s`
    }
    queuePersistSubtitleOffset(track, getNativeSubtitleOffsetMs())
    return
  }

  track.timeOffset = (track.timeOffset || 0) + deltaMs
  syncSubtitleOffset(track)

  const offsetSec = (track.timeOffset / 1000).toFixed(1)
  if (art.value?.notice) {
    art.value.notice.show = `字幕延迟: ${Number(offsetSec) >= 0 ? '+' : ''}${offsetSec}s`
  }
  queuePersistSubtitleOffset(track, track.timeOffset)
}

/**
 * 键盘快捷键：[ 减少字幕延迟，] 增加字幕延迟（每次 500ms）
 */
const handleSubtitleDelayKey = (e) => {
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return
  if (e.key === '[') {
    e.preventDefault()
    adjustSubtitleDelay(-500)
  } else if (e.key === ']') {
    e.preventDefault()
    adjustSubtitleDelay(500)
  }
}

const applySubtitleTrack = (subtitle) => {
  const octopus = subtitleOctopus.value
  if (!octopus) {
    return
  }

  try {
    if (!subtitle?.url) {
      if (typeof octopus.freeTrack === 'function') {
        octopus.freeTrack()
      }
      selectedSubtitleTrack.value = null
      tmpSubtitleOctopusSubUrl.value = ''
      return
    }

    selectedSubtitleTrack.value = subtitle
    tmpSubtitleOctopusSubUrl.value = subtitle.url
    syncSubtitleOffset(subtitle)

    // 先释放旧轨道，避免切集后沿用旧字幕
    if (typeof octopus.freeTrack === 'function') {
      octopus.freeTrack()
    }
    if (typeof octopus.setTrackByUrl === 'function') {
      octopus.setTrackByUrl(subtitle.url)
    }
    if (typeof octopus.setSubUrl === 'function') {
      octopus.setSubUrl(subtitle.url)
    }
  } catch (error) {
    console.warn('字幕轨切换失败（可能实例已销毁）:', error)
  }
}

const artplayerPluginAss = (options) => {
  return (player) => {
    const instance = new SubtitlesOctopus({
      ...options,
      video: player.template.$video,
    })

    if (instance.canvasParent) {
      instance.canvasParent.style.zIndex = 20
    }

    player.on('destroy', () => {
      instance.dispose()
      subtitleOctopus.value = null
    })

    subtitleOctopus.value = instance

    return {
      name: 'artplayerPluginAss',
      instance,
    }
  }
}

/**
 * 将弹弹play弹幕格式转换为Artplayer格式
 * @param {Array} chats - 弹弹play返回的弹幕数组
 * @returns {Array} 转换后的弹幕数组
 */
const convertDandanToArtplayer = (chats) => {
  if (!Array.isArray(chats)) {
    console.warn('弹幕数据不是数组:', typeof chats, chats)
    return []
  }

  if (chats.length === 0) {
    console.log('弹幕数组为空')
    return []
  }

  return chats
    .filter((chat) => {
      // 检查弹幕对象是否包含必要字段
      return chat && (chat.p || chat.mode || chat.mode !== undefined) && (chat.m || chat.text)
    })
    .map((chat) => {
      try {
        // 如果已经是转换后的格式，直接返回
        if (chat.text && (chat.mode !== undefined || chat.time !== undefined)) {
          return {
            text: chat.text,
            time: chat.time || 0,
            mode: chat.mode || 0,
            color: chat.color || '#FFFFFF',
            border: chat.border || false,
          }
        }

        // 解析 p 字段: "出现时间,模式,颜色,用户ID"
        if (!chat.p || !chat.m) {
          console.warn('弹幕缺少必要字段 p 或 m:', chat)
          return null
        }

        const pParts = chat.p.split(',')
        if (pParts.length < 3) {
          console.warn('弹幕 p 字段格式不正确:', chat.p)
          return null
        }

        const time = parseFloat(pParts[0]) || 0
        const dandanMode = parseInt(pParts[1]) || 1
        const colorInt = parseInt(pParts[2]) || 16777215 // 白色

        // 模式转换
        // 弹弹play: 1=普通, 4=底部, 5=顶部
        // Artplayer: 0=滚动, 1=顶部, 2=底部
        let artplayerMode = 0
        if (dandanMode === 5) {
          artplayerMode = 1 // 顶部
        } else if (dandanMode === 4) {
          artplayerMode = 2 // 底部
        } else {
          artplayerMode = 0 // 默认滚动
        }

        // 颜色转换: 十进制RGB到十六进制
        const color = '#' + colorInt.toString(16).padStart(6, '0').toUpperCase()

        return {
          text: chat.m,
          time,
          mode: artplayerMode,
          color,
          border: false,
        }
      } catch (error) {
        console.error('弹幕转换失败:', chat, error)
        return null
      }
    })
    .filter((item) => item !== null)
}

/**
 * 根据episodeId获取弹幕数据
 */
const fetchDanmaku = async (episodeId) => {
  try {
    if (!episodeId) {
      console.warn('episodeId为空，无法获取弹幕')
      return []
    }

    const response = await fetch(`/api/v2/comment/${episodeId}?withRelated=true`)
    if (!response.ok) {
      throw new Error(`弹幕接口返回状态: ${response.status}`)
    }

    const data = await response.json()
    console.log('弹幕API返回数据:', data)

    // 处理后端自定义格式返回
    if (data.code === 200 && data.data) {
      // 优先使用 comments 字段，其次使用 chats 字段
      const comments = data.data.comments || data.data.chats || []
      return convertDandanToArtplayer(comments)
    }

    // 处理标准ApiResponseVO格式
    if (data.success && data.data) {
      const comments = data.data.chats || data.data.comments || []
      return convertDandanToArtplayer(comments)
    }

    // 处理直接返回弹幕数组
    if (data.chats) {
      return convertDandanToArtplayer(data.chats)
    }

    if (data.comments) {
      return convertDandanToArtplayer(data.comments)
    }

    console.warn('弹幕数据格式未知', data)
    return []
  } catch (error) {
    console.error('获取弹幕失败:', error)
    return []
  }
}

/**
 * 根据episodeId获取可用资源
 */
const getEpisodeResources = (episodeId) => {
  if (episodeId === undefined || episodeId === null) {
    return []
  }
  const key = String(episodeId)
  return existingEpisodes.value.filter((item) => String(item.episodeId) === key && item.id !== undefined && item.id !== null)
}

const truncateText = (text, maxLen) => {
  const str = String(text || '')
  if (str.length <= maxLen) {
    return str
  }
  return `${str.slice(0, maxLen)}...`
}

/**
 * 选择资源并播放
 */
const goToPlayer = async (resource) => {
  showResourceDialog.value = false
  selectedResources.value = []

  const targetVideoId = String(resource.id)
  const targetEpisodeId = String(resource.episodeId ?? '')

  // 更新当前播放的视频
  try {
    await router.push({
      name: 'Player',
      params: { videoId: targetVideoId },
      query: {
        animeId: String(animeId.value),
        episodeId: targetEpisodeId
      }
    })
  } catch (error) {
    console.warn('router.push 异常，继续执行播放刷新:', error)
  }
}

/**
 * 关闭资源选择对话框
 */
const closeResourceDialog = () => {
  showResourceDialog.value = false
  selectedResources.value = []
  selectedEpisodeTitle.value = ''
}

/**
 * 选择资源
 */
const selectResource = (resource) => {
  goToPlayer(resource)
}

/**
 * 播放剧集
 */
const playEpisode = (ep) => {
  if (isFuture(ep)) return
  const resources = getEpisodeResources(ep.episodeId)
  if (resources.length === 0) return

  if (resources.length === 1) {
    goToPlayer(resources[0])
    return
  }

  selectedResources.value = resources
  selectedEpisodeTitle.value = truncateText(ep.episodeTitle || `第${ep.episodeNumber}话`, RESOURCE_DIALOG_TITLE_MAX_LEN)
  showResourceDialog.value = true
}

const jumpToEpisodeById = (targetEpisodeId) => {
  const target = playableEpisodes.value.find((ep) => String(ep.episodeId) === String(targetEpisodeId))
  if (!target) {
    return false
  }
  playEpisode(target)
  return true
}

const jumpToAdjacentEpisode = (delta) => {
  const list = playableEpisodes.value
  if (list.length === 0) {
    return null
  }

  const currentIndex = getCurrentPlayableEpisodeIndex()
  if (currentIndex === -1) {
    return null
  }

  const nextIndex = currentIndex + delta
  if (nextIndex < 0 || nextIndex >= list.length) {
    return null
  }

  playEpisode(list[nextIndex])
  return list[nextIndex]
}

/**
 * 切换收藏
 */
const toggleFavorite = () => {
  isFavorited.value = !isFavorited.value
}

const destroyPlayerInstance = () => {
  if (art.value) {
    try {
      // art.destroy 会触发 plugin destroy，内部会 dispose SubtitlesOctopus
      art.value.destroy(false)
    } catch (error) {
      console.warn('销毁播放器失败:', error)
    }
    art.value = null
    subtitleOctopus.value = null
    return
  }

  if (subtitleOctopus.value) {
    try {
      subtitleOctopus.value.dispose()
    } catch (error) {
      console.warn('销毁字幕实例失败:', error)
    }
    subtitleOctopus.value = null
  }
}

const placeEpisodeControlBeforeScreenshot = () => {
  if (!art.value?.template?.$controls) {
    return
  }

  const controlsRoot = art.value.template.$controls
  const rightGroup = controlsRoot.querySelector('.art-controls-right')
  const screenshotControl = controlsRoot.querySelector('.art-control-screenshot')
  const episodeLabel = controlsRoot.querySelector('.anilink-episode-control')
  const episodeControl = episodeLabel?.closest('.art-control')

  if (!rightGroup || !screenshotControl || !episodeControl) {
    return
  }

  if (episodeControl.parentElement !== rightGroup || episodeControl.nextElementSibling !== screenshotControl) {
    rightGroup.insertBefore(episodeControl, screenshotControl)
  }
}

const buildDanmakuOptions = (danmakuData, mobile) => {
  const persistedDanmakuSettings = loadDanmakuSettings()

  // 仅在没有历史设置时降低移动端默认字号，避免覆盖用户已调过的配置。
  const mobileDefaults = mobile && persistedDanmakuSettings.fontSize === undefined
    ? { fontSize: 19 }
    : {}

  return {
    ...DEFAULT_DANMAKU_SETTINGS,
    ...mobileDefaults,
    ...persistedDanmakuSettings,
    danmuku: danmakuData,
    useWorker: true,
    minWidth: mobile ? 140 : 200,
    maxWidth: mobile ? 320 : 500,
    filter: (danmu) => danmu.text && danmu.text.length < 200,
    beforeEmit: (danmu) => danmu.text && !!danmu.text.trim(),
  }
}

const loadDanmakuAsync = async (seq, targetEpisodeId) => {
  try {
    const danmakuData = await fetchDanmaku(targetEpisodeId)
    if (seq !== playerRecreateSeq || !art.value?.plugins?.artplayerPluginDanmuku) {
      return
    }
    await art.value.plugins.artplayerPluginDanmuku.load(danmakuData)
  } catch (error) {
    console.error('异步加载弹幕失败:', error)
  }
}

const buildSubtitlePlugin = (subtitles, preferredTrack = null) => {
  if (subtitles.length === 0) {
    selectedSubtitleTrack.value = null
    return null
  }

  const initialTrack = preferredTrack && isLibassSubtitleTrack(preferredTrack)
    ? preferredTrack
    : subtitles[0]

  selectedSubtitleTrack.value = initialTrack
  tmpSubtitleOctopusSubUrl.value = initialTrack.url
  return artplayerPluginAss({
    fonts: subtitlesOctopusFonts,
    subUrl: initialTrack.url,
    fallbackFont: '/static/SourceHanSansCN-Bold.woff2',
    workerUrl: subtitlesOctopusWorkJsPath,
    wasmUrl: subtitlesOctopusWorkWasmPath,
    timeOffset: toSubtitleOffsetSeconds(initialTrack),
  })
}

const buildSubtitleSettings = (subtitles, activeSubtitleId = '') => {
  if (subtitles.length === 0) {
    tmpSubtitleOctopusSubUrl.value = ''
    return []
  }

  const subtitleTrackSetting = {
    width: 220,
    html: '字幕',
    tooltip: '选择',
    icon: '<span style="font-size:16px">CC</span>',
    selector: [
      {
        html: '开启',
        tooltip: '显示',
        switch: true,
        onSwitch: (item) => {
          if (!subtitleOctopus.value) {
            return item.switch
          }

          item.tooltip = item.switch ? '隐藏' : '显示'
          if (item.switch) {
            tmpSubtitleOctopusSubUrl.value = tmpSubtitleOctopusSubUrl.value || subtitles[0]?.url || ''
            subtitleOctopus.value.freeTrack()
          } else if (selectedSubtitleTrack.value?.url || tmpSubtitleOctopusSubUrl.value) {
            applySubtitleTrack(selectedSubtitleTrack.value || subtitles.find(subtitle => subtitle.url === tmpSubtitleOctopusSubUrl.value) || subtitles[0])
          }
          return !item.switch
        },
      },
      ...subtitles.map((subtitle, index) => ({
        default: String(subtitle.id || '') === String(activeSubtitleId || '') || (index === 0 && !activeSubtitleId),
        html: subtitle.name || `字幕 ${index + 1}`,
        subtitle,
        url: subtitle.url,
      })),
    ],
    onSelect: (item) => {
      if (!item.subtitle) {
        return item.html
      }
      switchSubtitleTrack(item.subtitle)
      return item.html
    },
  }

  return [subtitleTrackSetting]
}

const buildNativeSubtitleOption = (track) => {
  if (!track?.url) {
    return null
  }

  const mobile = isMobileViewport()
  const nativeSubtitleFontSize = mobile ? '28px' : '34px'

  return {
    url: track.url,
    type: track.format,
    encoding: 'utf-8',
    escape: true,
    style: {
      color: '#FFFFFF',
      'font-size': nativeSubtitleFontSize,
      'text-shadow': '0 2px 4px rgba(0, 0, 0, 0.65)',
    },
  }
}

const buildEpisodeControls = (mobile) => {
  if (mobile) {
    return []
  }

  return [
  {
    position: 'left',
    index: 9,
    html: '<i class="mdi mdi-skip-previous" style="font-size:20px;line-height:1;"></i>',
    tooltip: '播放上一集',
    click: () => {
      const prev = jumpToAdjacentEpisode(-1)
      if (!prev && art.value?.notice) {
        art.value.notice.show = '已是第一集'
      }
    },
  },
  {
    position: 'left',
    index: 11,
    html: '<i class="mdi mdi-skip-next" style="font-size:20px;line-height:1;"></i>',
    tooltip: '播放下一集',
    click: () => {
      const next = jumpToAdjacentEpisode(1)
      if (!next && art.value?.notice) {
        art.value.notice.show = '已是最后一集'
      }
    },
  },
  {
    position: 'right',
    index: 100,
    html: '<span class="anilink-episode-control" style="font-size:13px;line-height:1">分集</span>',
    tooltip: '选择分集',
    selector: playableEpisodes.value.map((ep, index) => ({
      default: String(ep.episodeId) === String(episodeId.value),
      html: `第${ep.episodeNumber || index + 1}话 ${truncateText(ep.episodeTitle || '', EPISODE_SELECTOR_TITLE_MAX_LEN)}`.trim(),
      value: String(ep.episodeId || ''),
      episodeId: String(ep.episodeId || ''),
    })),
    onSelect: (item) => {
      const targetEpisodeId = item?.value || item?.episodeId || ''
      console.warn('[episode-selector] select:', targetEpisodeId, item)

      if (targetEpisodeId) {
        const ok = jumpToEpisodeById(targetEpisodeId)
        if (!ok && art.value?.notice) {
          art.value.notice.show = '该分集暂无可播放资源'
        }
      }
      return '分集'
    },
  },
]
}

const createPlayerInstance = async () => {
  const seq = ++playerRecreateSeq
  isSwitching.value = true
  const mobile = isMobileViewport()

  const targetVideoId = String(videoId.value || '')
  const targetEpisodeId = String(episodeId.value || '')

  if (!targetVideoId) {
    if (seq === playerRecreateSeq) {
      isSwitching.value = false
    }
    return
  }

  try {
    // 记录旧播放器的全屏状态，重建后自动恢复
    const prevArt = art.value
    const restoreFullscreenWeb = Boolean(prevArt && prevArt.fullscreenWeb)
    const restoreFullscreen = !restoreFullscreenWeb && Boolean(prevArt && prevArt.fullscreen)

    // 优先获取播放器必需数据：字幕；弹幕改为异步注入，避免阻塞首帧播放
    const subtitles = await fetchSubtitles(targetVideoId)
    const routeSelectedTrack = subtitles.find((item) => String(item?.id || '') === String(subtitleTrackId.value || '')) || null
    const subtitlesForLibass = subtitles.filter(isLibassSubtitleTrack)
    const subtitlesForNative = subtitles.filter(isNativeArtplayerSubtitleTrack)
    const fallbackTrack = subtitlesForLibass[0] || subtitlesForNative[0] || subtitles[0] || null
    const activeSubtitleTrack = routeSelectedTrack || fallbackTrack
    const useNativeSubtitle = Boolean(activeSubtitleTrack && isNativeArtplayerSubtitleTrack(activeSubtitleTrack))
    const nativeSubtitleOption = useNativeSubtitle ? buildNativeSubtitleOption(activeSubtitleTrack) : null

    if (useNativeSubtitle) {
      selectedSubtitleTrack.value = activeSubtitleTrack
      tmpSubtitleOctopusSubUrl.value = ''
      console.info('[subtitle] 使用 Artplayer 原生字幕渲染', subtitlesForNative.map(item => item?.format))
    }

    if (subtitles.length > 0 && subtitlesForLibass.length === 0) {
      console.warn('[subtitle] 当前资源无ASS/SSA字幕，已跳过libass字幕渲染', subtitles.map(item => item?.format))
    }
    if (seq !== playerRecreateSeq) {
      return
    }

    destroyPlayerInstance()

    const danmakuOptions = buildDanmakuOptions([], mobile)
    const subtitlePlugin = useNativeSubtitle
      ? null
      : buildSubtitlePlugin(subtitlesForLibass, activeSubtitleTrack)
    const subtitleSettings = buildSubtitleSettings(subtitles, String(activeSubtitleTrack?.id || ''))
    const episodeControls = buildEpisodeControls(mobile)

    // 初始化 Artplayer
    art.value = new Artplayer({
      container: artRef.value,
      url: `/api/media-files/stream/${targetVideoId}`,
      poster: '',
      volume: 0.5,
      isLive: false,
      muted: false,
      autoplay: false,
      pip: !mobile,
      autoSize: false,
      autoMini: true,
      screenshot: !mobile,
      setting: true,
      loop: false,
      // 保留移动端设置菜单中的功能项（镜像、倍速、画面比例）
      flip: true,
      playbackRate: true,
      aspectRatio: true,
      fullscreen: true,
      fullscreenWeb: !mobile,
      miniProgressBar: true,
      mutex: true,
      backdrop: true,
      playsInline: true,
      autoPlayback: false,
      airplay: !mobile,
      theme: '#c45d2b',
      lang: 'zh-cn',
      ...(useNativeSubtitle ? { subtitleOffset: true } : {}),
      ...(nativeSubtitleOption ? { subtitle: nativeSubtitleOption } : {}),
      moreVideoAttr: {
        crossOrigin: 'anonymous',
      },
      plugins: [
        artplayerPluginDanmuku(danmakuOptions),
        ...(subtitlePlugin ? [subtitlePlugin] : []),
      ],
      controls: episodeControls,
      settings: subtitleSettings,
      contextmenu: [
        {
          html: '字幕延迟 −0.5s &nbsp;<kbd>快捷键：[</kbd>',
          click: () => { adjustSubtitleDelay(-500) },
        },
        {
          html: '字幕延迟 +0.5s &nbsp;<kbd>快捷键：]</kbd>',
          click: () => { adjustSubtitleDelay(500) },
        },
        {
          html: '重置字幕延迟',
          click: () => {
            const track = selectedSubtitleTrack.value
            if (!track) return
            const delta = -getCurrentSubtitleOffsetMs(track)
            if (delta !== 0) adjustSubtitleDelay(delta)
          },
        },
      ],
    })
    if (seq !== playerRecreateSeq) {
      destroyPlayerInstance()
      return
    }

    // 监听播放器事件
    art.value.on('ready', async () => {
      console.log('播放器已就绪')
      placeEpisodeControlBeforeScreenshot()

      // 加载并恢复播放进度
      try {
        const savedProgress = await loadPlayProgress()
        if (savedProgress && savedProgress > 5) {
          art.value.currentTime = savedProgress
          console.log('已恢复播放进度:', savedProgress)
        }
      } catch (error) {
        console.warn('恢复播放进度失败:', error)
      }

      // 切集后自动恢复全屏状态
      try {
        if (restoreFullscreenWeb) {
          art.value.fullscreenWeb = true
        } else if (restoreFullscreen) {
          art.value.fullscreen = true
        }
      } catch (error) {
        console.warn('恢复全屏状态失败:', error)
      }
    })

    art.value.on('play', () => {
      console.log('开始播放')
      startProgressSaveTimer()
    })

    art.value.on('pause', () => {
      console.log('暂停播放')
      savePlayProgress()
      stopProgressSaveTimer()
    })

    art.value.on('video:ended', () => {
      console.log('播放结束')
      savePlayProgress()
      stopProgressSaveTimer()
    })

    art.value.on('error', (error) => {
      console.error('播放器错误:', error)
      stopProgressSaveTimer()
    })

    art.value.on('subtitleOffset', (offsetSec) => {
      const track = selectedSubtitleTrack.value
      if (!track || !isNativeArtplayerSubtitleTrack(track)) {
        return
      }
      const offsetMs = Math.round(Number(offsetSec || 0) * 1000)
      track.timeOffset = offsetMs
      queuePersistSubtitleOffset(track, offsetMs)
    })

    // 弹幕事件
    art.value.on('artplayerPluginDanmuku:visible', () => {

    })

    art.value.on('artplayerPluginDanmuku:loaded', (danmus) => {
      const count = Array.isArray(danmus) ? danmus.length : 0
      console.log('已加载弹幕数:', count)
      if (art.value?.notice) {
        // 延迟一小段时间，避免被播放器初始化阶段的其他状态提示覆盖
        setTimeout(() => {
          if (!art.value?.notice) {
            return
          }
          art.value.notice.show = count > 0 ? `弹幕已加载 ${count} 条` : '未加载到弹幕'
        }, 180)
      }
    })

    art.value.on('artplayerPluginDanmuku:config', (option) => {
      saveDanmakuSettings(option)
    })

    art.value.on('artplayerPluginDanmuku:show', () => {
      const option = art.value?.plugins?.artplayerPluginDanmuku?.option
      if (option) {
        saveDanmakuSettings({ ...option, visible: true })
      }
    })

    art.value.on('artplayerPluginDanmuku:hide', () => {
      const option = art.value?.plugins?.artplayerPluginDanmuku?.option
      if (option) {
        saveDanmakuSettings({ ...option, visible: false })
      }
    })

    art.value.on('artplayerPluginDanmuku:error', (error) => {
      console.error('弹幕加载错误:', error)
    })

    // 播放器已可用后再异步加载弹幕，避免阻塞播放启动
    loadDanmakuAsync(seq, targetEpisodeId)
  } finally {
    if (seq === playerRecreateSeq) {
      isSwitching.value = false
    }
  }
}

onMounted(async () => {
  updateViewportState()
  window.addEventListener('resize', updateViewportState)
  document.addEventListener('keydown', handleSubtitleDelayKey)
  // 获取番剧数据
  await fetchAnimeData()
  await createPlayerInstance()
})

/**
 * 监听路由变化：videoId 或 episodeId 任一变化都刷新播放态。
 * - videoId 变化：切换视频源 + 刷新弹幕/字幕
 * - episodeId 变化：刷新弹幕/字幕（即便视频源不变）
 */
watch(
  () => [String(route.params.videoId || ''), String(route.query.episodeId || ''), String(route.query.subtitleId || '')],
  async ([newVideoId, newEpisodeId, newSubtitleId], [oldVideoId, oldEpisodeId, oldSubtitleId]) => {
    closeResourceDialog()
    isSummaryExpanded.value = false

    if (newVideoId === oldVideoId && newEpisodeId === oldEpisodeId && newSubtitleId === oldSubtitleId) {
      return
    }

    try {
      await createPlayerInstance()
    } catch (error) {
      console.error('重建播放器失败:', error)
    }
  }
)

watch(() => animeId.value, async () => {
  closeResourceDialog()
  isSummaryExpanded.value = false
  await fetchAnimeData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewportState)
  document.removeEventListener('keydown', handleSubtitleDelayKey)
  stopProgressSaveTimer()
  stopSubtitleOffsetPersistTimer()
  savePlayProgress() // 组件销毁前保存最后一次进度
  destroyPlayerInstance()
})
</script>

<style scoped>
/* Loading and Error States */
.loading-state,
.error-state {
  text-align: center;
  font-size: 1.1rem;
  color: #6b5f55;
  padding: 60px 20px;
}

.error-state {
  color: #d32f2f;
}

/* Main Layout */

.player-layout {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  gap: 28px;
  background: white;
  border-radius: 32px;
  overflow: hidden;
  padding: 32px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
}

.player-main {
  flex: 1;
  min-width: 0;
}

.player-sidebar {
  width: 280px;
  flex-shrink: 0;
}

/* Player Card */
.player-card {
  position: relative;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  margin-bottom: 28px;
}

.player-actions {
  margin: -12px 0 20px;
  display: flex;
  justify-content: flex-end;
}

.ddplay-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #1f8f67;
  background: #e8f7f1;
  color: #116449;
  border-radius: 999px;
  padding: 9px 16px;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ddplay-btn i {
  font-size: 1.05rem;
}

.ddplay-btn:hover {
  background: #d8f0e7;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(17, 100, 73, 0.18);
}

.ddplay-btn:active {
  transform: translateY(0);
}

.player-switching-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 25;
  backdrop-filter: blur(2px);
}

.player-switching-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #ffffff;
}

.player-switching-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(255, 255, 255, 0.35);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: anilink-spin 0.9s linear infinite;
}

.player-switching-text {
  font-size: 0.95rem;
  letter-spacing: 0.02em;
}

@keyframes anilink-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .player-actions {
    display: none;
  }

  /* Artplayer 移动端默认会把左右控件组外扩（负 margin），全屏时需要覆盖并叠加安全区 */
  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen-web .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile:fullscreen .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile:-webkit-full-screen .art-bottom) {
    padding-left: max(12px, env(safe-area-inset-left, 0px));
    padding-right: max(12px, env(safe-area-inset-right, 0px));
    padding-bottom: max(10px, env(safe-area-inset-bottom, 0px));
    box-sizing: border-box;
  }

  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen .art-controls-left),
  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen-web .art-controls-left),
  .artplayer-container :deep(.art-video-player.art-mobile:fullscreen .art-controls-left),
  .artplayer-container :deep(.art-video-player.art-mobile:-webkit-full-screen .art-controls-left) {
    margin-left: 0 !important;
  }

  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen .art-controls-right),
  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen-web .art-controls-right),
  .artplayer-container :deep(.art-video-player.art-mobile:fullscreen .art-controls-right),
  .artplayer-container :deep(.art-video-player.art-mobile:-webkit-full-screen .art-controls-right) {
    margin-right: 0 !important;
  }

  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile.art-fullscreen-web .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile:fullscreen .art-bottom),
  .artplayer-container :deep(.art-video-player.art-mobile:-webkit-full-screen .art-bottom) {
    padding-left: max(12px, env(safe-area-inset-left, 0px)) !important;
    padding-right: max(12px, env(safe-area-inset-right, 0px)) !important;
    padding-bottom: max(10px, env(safe-area-inset-bottom, 0px)) !important;
  }
}

.artplayer-container {
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 280px;
  height: auto;
  background: #000;
}

.artplayer-container :deep(.art-video-player) {
  width: 100% !important;
  height: 100% !important;
}

.artplayer-container :deep(.art-notice) {
  top: 12px;
  right: 12px;
  left: auto;
  bottom: auto;
  transform: none;
  pointer-events: none;
}

.artplayer-container :deep(.art-notice-inner) {
  background: rgba(26, 26, 26, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  font-size: 12px;
  padding: 6px 10px;
}

/* Info Section */
.anime-info-section {
  margin-top: 0;
}

/* Resource Dialog */
.resource-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  padding: 20px;
}

.resource-dialog {
  width: min(560px, 100%);
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.25);
  padding: 20px;
}

.resource-dialog-title {
  margin: 0;
  font-size: 1.2rem;
  color: #2e241e;
}

.resource-dialog-subtitle {
  margin: 8px 0 14px;
  color: #6b5f55;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.resource-list {
  display: grid;
  gap: 10px;
  max-height: 50vh;
  overflow: auto;
}

.resource-item {
  border: 1px solid #e9e2dc;
  background: #fffaf6;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-item:hover {
  border-color: #c45d2b;
  background: #fff1e8;
}

.resource-name {
  color: #2e241e;
  font-weight: 600;
}

.resource-meta {
  color: #8b7e74;
  font-size: 0.85rem;
}

.resource-cancel-btn {
  margin-top: 14px;
  width: 100%;
  border: 1px solid #dfd2c8;
  background: #ffffff;
  color: #5f5148;
  border-radius: 10px;
  height: 40px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-cancel-btn:hover {
  background: #f9f4ef;
}

/* Responsive Design */
@media (max-width: 1199px) {
  .player-layout {
    flex-direction: column;
    padding: 20px;
  }

  .player-sidebar {
    width: 100%;
  }
}

@media (max-width: 799px) {
  .player-layout {
    padding: 16px;
  }
}

@media (max-width: 479px) {
  .artplayer-container {
    min-height: 200px;
  }
}
</style>
