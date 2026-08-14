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
      <!-- 播放器 + 选集卡片行（撑满视口） -->
      <div class="player-top-row">
        <!-- 播放器区域 -->
        <div class="player-main">
          <div class="player-card">
            <div ref="artRef" class="artplayer-container"></div>
            <div v-if="isSwitching" class="player-switching-overlay">
              <div class="player-switching-content">
                <div class="player-switching-spinner"></div>
                <div class="player-switching-text">正在切换分集...</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选集卡片（桌面端，移动端改用下方 B 站式 TAB 组件） -->
        <div v-if="animeData && isDesktopViewport" class="player-episode-panel">
          <!-- 顶部番剧信息 -->
          <div class="episode-panel-header">
            <img
              v-if="animeData.imageUrl"
              :src="animeData.imageUrl"
              class="episode-panel-poster"
              alt=""
            />
            <div class="episode-panel-meta">
              <div class="episode-panel-title">{{ titleInfo.main }}</div>
              <div class="episode-panel-sub" v-if="titleInfo.sub">{{ titleInfo.sub }}</div>
            </div>
            <router-link
              v-if="animeId"
              :to="{ name: 'AnimeDetail', params: { animeId } }"
              class="episode-panel-back"
            >
              <i class="mdi mdi-arrow-left"></i>详情
            </router-link>
          </div>

          <!-- Tab 切换 -->
          <div class="episode-panel-tabs">
            <button
              v-for="tab in episodeTabs"
              :key="tab.value"
              class="episode-panel-tab"
              :class="{ active: episodeTab === tab.value }"
              @click="episodeTab = tab.value"
            >{{ tab.label }}</button>
            <span class="episode-panel-count">共{{ totalEpisodes }}话</span>
          </div>

          <!-- 分集列表 -->
          <div v-if="episodeTab !== 'comments'" class="episode-panel-list">
            <button
              v-for="ep in displayedEpisodes"
              :key="ep.episodeId"
              class="episode-panel-item"
              :class="{
                'is-current': String(ep.episodeId) === String(episodeId),
                'is-unavailable': !canPlayEpisode(ep)
              }"
              :disabled="!canPlayEpisode(ep)"
              @click="playEpisode(ep)"
            >
              <span class="episode-item-num">{{ episodeNumberDisplay(ep) }}</span>
              <span class="episode-item-title">{{ ep.episodeTitle || '' }}</span>
              <span class="episode-item-date">{{ formatEpisodeDate(ep.airDate) }}</span>
            </button>
          </div>

          <!-- Bangumi 单集吐槽 -->
          <div v-else class="episode-panel-comments">
            <EpisodeComments
              :anime-id="animeId"
              :episode-number="currentEpisodeNumber"
            />
          </div>
        </div>

        <!-- 移动端 B 站式 TAB 布局：简介 / 选集 / 吐槽 -->
        <MobilePlayerTabs
          v-if="animeData && !isDesktopViewport"
          :anime="animeData"
          :anime-id="animeId"
          :title-info="titleInfo"
          :rating-main="ratingMain"
          :total-episodes="totalEpisodes"
          :is-on-air="animeData.isOnAir"
          :air-day-text="airDayText"
          :summary="formattedSummary"
          :staff-list="staffList"
          :copyright-text="copyrightText"
          :current-episode-id="episodeId"
          :current-episode-number="currentEpisodeNumber"
          :main-episodes="mainEpisodes"
          :special-episodes="specialEpisodes"
          :play-episode="playEpisode"
          :can-play-episode="canPlayEpisode"
          :episode-number-display="episodeNumberDisplay"
          :format-episode-date="formatEpisodeDate"
        />
      </div>

    </div>

    <!-- 资源选择对话框 -->
    <ResourceSelectDialog
      :open="showResourceDialog"
      :resources="selectedResources"
      :title="selectedEpisodeTitle"
      @select="selectResource"
      @close="closeResourceDialog"
    />

  </div>
</template>

<script setup>
import { computed, ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import artplayerPluginVttThumbnail from 'artplayer-plugin-vtt-thumbnail'
import SubtitlesOctopus from 'libass-wasm'
import { showAppMessage } from '../utils/ui-feedback'
import { API_BASE, isSuperAdmin } from '../utils/constants'
import { formatDanmakuColor } from '../utils/format'
import { getAnimeRawJson, getAnimeEpisodes } from '../api/anime'
import { savePlayProgress as postPlayProgress, getPlayResume } from '../api/playHistory'
import { markEpisodeMessagesRead as apiMarkEpisodeMessagesRead } from '../api/messages'
import { getSubtitles, setSubtitleOffset } from '../api/subtitle'
import { getDanmakuComments, sendDanmakuComment } from '../api/danmaku'
import { syncEpisodeWatched } from '../api/bangumi'
import { useAnimeDerived } from '../composables/useAnimeDerived'
import { useAuth } from '../composables/useAuth'
import { useFollow } from '../composables/useFollow'
import { useResourceSelection } from '../composables/useResourceSelection'
import {
  isFuture,
  filterMainEpisodes,
  filterSpecialEpisodes,
  episodeNumberDisplay,
  formatEpisodeDate,
  buildPlayableEpisodeKeys,
  getEpisodeResources,
  truncateText,
} from '../utils/episodes'
import EpisodeComments from '../components/anime/EpisodeComments.vue'
import MobilePlayerTabs from '../components/anime/MobilePlayerTabs.vue'
import ResourceSelectDialog from '../components/anime/ResourceSelectDialog.vue'


const route = useRoute()
const router = useRouter()
const { token, userInfo } = useAuth()

const videoId = computed(() => String(route.params.videoId || ''))
const animeId = computed(() => String(route.query.animeId || ''))
const episodeId = computed(() => String(route.query.episodeId || ''))
const subtitleTrackId = computed(() => String(route.query.subtitleId || ''))
const seekTime = computed(() => {
  const t = route.query.t
  if (t !== undefined && t !== null && t !== '') {
    const n = parseFloat(t)
    return isNaN(n) ? null : n
  }
  return null
})

// Anime Data State
const animeData = ref(null)
const existingEpisodes = ref([])
const loading = ref(false)
const error = ref(null)
const isSwitching = ref(false)
const isDesktopViewport = ref(true)

const artRef = ref(null)
const art = shallowRef(null)
const subtitleOctopus = ref(null)
const tmpSubtitleOctopusSubUrl = ref('')
const selectedSubtitleTrack = ref(null)
let progressSaveTimer = null
let subtitleOffsetPersistTimer = null
let _mobileTapHandler = null

const subtitlesOctopusWorkJsPath = '/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.js'
const subtitlesOctopusWorkWasmPath = '/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.wasm'
const subtitlesOctopusFonts = ['/static/SourceHanSansCN-Bold.woff2']
let playerRecreateSeq = 0

const DANMAKU_SETTINGS_STORAGE_KEY = 'anilink:danmaku:settings:v1'
const EPISODE_SELECTOR_TITLE_MAX_LEN = 28
const RESOURCE_DIALOG_TITLE_MAX_LEN = 40
const LIBASS_SUPPORTED_SUBTITLE_FORMATS = new Set(['ass', 'ssa'])
const NATIVE_ARTPLAYER_SUBTITLE_FORMATS = new Set(['srt', 'vtt'])

const { isFollowing, followStatus, checkFollowStatus, upgradeFollowWishToWatching } = useFollow()
const { showResourceDialog, selectedResources, selectedEpisodeTitle, closeResourceDialog, selectResource, playEpisode } =
  useResourceSelection({
    router,
    getAnimeId: () => animeId.value,
    getExistingEpisodes: () => existingEpisodes.value,
    titleMaxLen: RESOURCE_DIALOG_TITLE_MAX_LEN,
  })

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

/**
 * 获取番剧数据
 */
const fetchAnimeData = async () => {
  try {
    if (!animeId.value) return

    loading.value = true
    error.value = null

    const [animeResp, episodesResp] = await Promise.allSettled([
      getAnimeRawJson(animeId.value),
      getAnimeEpisodes(animeId.value, { page: 1, pageSize: 9999 }),
    ])

    if (animeResp.status === 'fulfilled' && animeResp.value.code === 200 && animeResp.value.data?.bangumi) {
      animeData.value = animeResp.value.data.bangumi
      await checkFollowStatus(animeId.value)
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

/**
 * Computed properties for anime display
 */
const mainEpisodes = computed(() => filterMainEpisodes(animeData.value?.episodes))

const specialEpisodes = computed(() => filterSpecialEpisodes(animeData.value?.episodes))

// ===== 选集面板 =====
const episodeTab = ref('main')
const episodeTabs = [
  { label: '正片', value: 'main' },
  { label: '特典', value: 'special' },
  { label: '全部', value: 'all' },
  { label: '吐槽', value: 'comments' }
]

const displayedEpisodes = computed(() => {
  const eps = animeData.value?.episodes || []
  if (episodeTab.value === 'main') return mainEpisodes.value
  if (episodeTab.value === 'special') return specialEpisodes.value
  return [...eps].sort((a, b) => new Date(a.airDate) - new Date(b.airDate))
})

const currentEpisodeNumber = computed(() => {
  const currentEp = animeData.value?.episodes?.find(
    ep => String(ep.episodeId) === String(episodeId.value)
  )
  return currentEp?.episodeNumber != null ? String(currentEp.episodeNumber) : ''
})

const canPlayEpisode = (ep) => {
  if (!ep || ep.episodeId === undefined || ep.episodeId === null) return false
  return playableEpisodeKeys.value.has(String(ep.episodeId)) && !isFuture(ep)
}

const {
  isOnAir,
  ratingMain,
  ratingBangumi,
  ratingAnidb,
  totalEpisodes,
  formattedSummary,
  titleInfo,
  airDayText,
  staffList,
  copyrightText,
} = useAnimeDerived(animeData)

const playableEpisodeKeys = computed(() => buildPlayableEpisodeKeys(existingEpisodes.value))

const playableEpisodes = computed(() => {
  const episodes = animeData.value?.episodes || []
  return episodes.filter((ep) => {
    if (!ep || isFuture(ep)) {
      return false
    }
    return getEpisodeResources(existingEpisodes.value, ep.episodeId).length > 0
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

  const streamUrl = `${window.location.origin}${API_BASE}/media-files/stream/${videoId.value}`
  const filePath = currentEpisodeResource.value?.filePath || currentEpisodeResource.value?.fileName || ''
  const withOptionalFilePath = filePath
    ? `${streamUrl}|filePath=${filePath}`
    : streamUrl

  return `ddplay:${encodeURIComponent(withOptionalFilePath)}`
})

const syncMobileClass = () => {
  if (!artRef.value) return
  const playerEl = artRef.value.querySelector('.art-video-player')
  if (!playerEl) return
  if (isMobileViewport()) {
    playerEl.classList.add('art-mobile')
  } else {
    playerEl.classList.remove('art-mobile')
  }
}

const installMobileTapHandler = () => {
  if (_mobileTapHandler) {
    const video = art.value?.video
    if (video) video.removeEventListener('click', _mobileTapHandler, true)
    _mobileTapHandler = null
  }

  if (!art.value?.video) return

  _mobileTapHandler = (e) => {
    if (!isMobileViewport() || !art.value) return

    const target = e.target
    if (target.closest('.art-control, .art-settings, .art-selector, .art-contextmenu, .art-notice, .art-loading, .art-danmaku')) {
      return
    }

    e.stopPropagation()
    if (art.value.playing) {
      art.value.pause()
    } else {
      art.value.play()
    }
  }

  art.value.video.addEventListener('click', _mobileTapHandler, true)
}

const updateViewportState = () => {
  isDesktopViewport.value = !isMobileViewport()
  syncMobileClass()
  installMobileTapHandler()
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



/**
 * 根据videoId获取字幕文件列表
 */
const fetchSubtitles = async (videoId) => {
  try {
    if (!videoId) {
    console.warn('videoId为空，无法获取字幕')
    return []
  }

  const data = await getSubtitles(videoId)

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
            url: `${API_BASE}/subtitles/${subtitle.id}/download`,
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
  if (!token.value || !art.value) {
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
    
    const isCompleted = currentTime / duration >= 0.8 // 播放超过80%认为已完成
    if (isCompleted) syncEpisodeWatchedToBangumi()    // 达到80%即时同步

    await postPlayProgress({
      videoId: videoId.value,
      videoName: `Episode ${episodeId.value || ''}`,
      animeId: animeId.value,
      episodeId: String(episodeId.value || ''),
      animeTitle: animeData.value?.titles?.[0]?.title || '未知',
      progressSeconds: currentTime,
      durationSeconds: duration,
      isCompleted: isCompleted
    })
  } catch (error) {
    console.error('保存播放进度失败:', error)
  }
}

/**
 * 加载播放进度
 */
const loadPlayProgress = async () => {
  if (!token.value || !animeId.value) {
    return null
  }
  
  try {
    const body = await getPlayResume(animeId.value)
    if (body.code === 200 && body.data) {
      // 仅在当前播放视频与历史视频一致时恢复秒数，避免跨分集误跳进度。
      if (String(body.data.videoId || '') !== String(videoId.value || '')) {
        return null
      }
      return body.data.progressSeconds || 0
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

/**
 * 自动同步 Bangumi 剧集"已看"状态。
 * 仅在用户已登录、已绑定 Bangumi、且当前剧集确实看完（>=80%）时触发。
 * 失败静默，不影响用户观看体验。
 */
let _lastSyncedKey = ''

const syncEpisodeWatchedToBangumi = async () => {
  if (!token.value || !animeId.value || !episodeId.value) return

  // 去重：同集不重复同步
  const key = `${animeId.value}:${episodeId.value}`
  if (key === _lastSyncedKey) return

  // 找到当前播放的剧集
  const currentEp = animeData.value?.episodes?.find(
    ep => String(ep.episodeId) === String(episodeId.value)
  )
  if (!currentEp?.episodeNumber) return

  // 播放 >= 80% 才同步
  const currentTime = art.value?.currentTime || 0
  const duration = art.value?.duration || 0
  if (duration > 0 && currentTime / duration < 0.8) return

  _lastSyncedKey = key
  try {
    await syncEpisodeWatched({
      animeId: animeId.value,
      episodeNumber: String(currentEp.episodeNumber)
    })
  } catch (e) {
    // 静默失败 — 同步是最大努力，不应打扰用户
    console.debug('Bangumi 剧集同步跳过:', e)
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
const isCurrentUserAdmin = () => isSuperAdmin(userInfo.value)

/**
 * 将字幕偏移量持久化到后端（仅超管可用）
 */
const persistSubtitleOffset = async (subtitleId, offsetMs) => {
  try {
    await setSubtitleOffset(subtitleId, offsetMs)
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
        const color = formatDanmakuColor(colorInt, '#FFFFFF')

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

    const data = await getDanmakuComments(episodeId)

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
 * 发送弹幕到弹弹play API
 * @param {Object} danmu - 弹幕数据 { text, time, mode, color }
 */
const sendDanmaku = async (danmu) => {
  const targetEpisodeId = episodeId.value
  if (!targetEpisodeId) {
    throw new Error('缺少弹幕库ID')
  }

  // Artplayer mode: 0=滚动, 1=顶部, 2=底部
  // 弹弹play mode: 1=普通(滚动), 4=底部, 5=顶部
  const modeMap = { 0: 1, 2: 4, 1: 5 }
  const dandanMode = modeMap[danmu.mode] || 1

  // 颜色转换: #HEX -> 十进制整数 RGB
  let colorInt = 16777215 // 默认白色
  if (danmu.color) {
    const hex = danmu.color.replace('#', '')
    colorInt = parseInt(hex, 16)
    if (isNaN(colorInt)) {
      colorInt = 16777215
    }
  }

  const requestBody = {
    time: danmu.time || 0,
    mode: dandanMode,
    color: colorInt,
    comment: danmu.text || '',
    animeId: animeId.value || null,
    animeTitle: animeData.value?.titles?.[0]?.title || null,
    videoId: videoId.value || null,
    episodeTitle: currentEpisodeResource.value?.episodeTitle || null,
  }

  await sendDanmakuComment(targetEpisodeId, requestBody)
}

const jumpToEpisodeById = (targetEpisodeId) => {
  const target = playableEpisodes.value.find((ep) => String(ep.episodeId) === String(targetEpisodeId))
  if (!target) {
    return false
  }
  playEpisode(target, true)
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

  playEpisode(list[nextIndex], true)
  return list[nextIndex]
}

const destroyPlayerInstance = () => {
  if (_mobileTapHandler) {
    const video = art.value?.video
    if (video) video.removeEventListener('click', _mobileTapHandler, true)
    _mobileTapHandler = null
  }

  if (art.value) {
    try {
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
    beforeEmit: async (danmu) => {
      // 先校验文本非空
      if (!danmu.text || !danmu.text.trim()) {
        return false
      }

      // 检查登录状态
      if (!token.value) {
        if (art.value?.notice) {
          art.value.notice.show = '请先登录后再发送弹幕'
        }
        return false
      }

      try {
        await sendDanmaku(danmu)
        if (art.value?.notice) {
          art.value.notice.show = '弹幕发送成功'
        }
        return true
      } catch (err) {
        console.error('弹幕发送失败:', err)
        if (art.value?.notice) {
          art.value.notice.show = '弹幕发送失败'
        }
        return false
      }
    },
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
  const controls = []

  if (!mobile && showDdplayButton.value) {
    controls.push({
      position: 'right',
      index: 5,
      html: '<i class="mdi mdi-open-in-new" style="font-size:20px;line-height:1;"></i>',
      tooltip: '通过弹弹play播放',
      click: () => { openWithDdplay() },
    })
  }

  if (mobile) {
    return controls
  }

  controls.push(
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
    index: 6,
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
  )
  return controls
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
      url: `${API_BASE}/media-files/stream/${targetVideoId}`,
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
        artplayerPluginVttThumbnail({
          vtt: `${API_BASE}/media-files/${targetVideoId}/thumbnails.vtt`,
        }),
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
      placeEpisodeControlBeforeScreenshot()
      syncMobileClass()
      installMobileTapHandler()

      // 优先处理 URL 传入的时间跳转参数
      const t = seekTime.value
      if (t !== null && t >= 0) {
        const doSeek = () => {
          if (art.value) {
            art.value.currentTime = t
          }
          // 跳转完成后清除 URL 中的 t 参数
          const { t: _, ...restQuery } = route.query
          router.replace({ query: restQuery }).catch(() => {})
        }

        // 检查视频是否已经可以播放（避免 canplay 已触发过的竞态）
        const video = art.value?.video
        if (video && video.readyState >= 2) {
          doSeek()
        } else {
          art.value.on('video:canplay', function onCanPlay() {
            art.value.off('video:canplay', onCanPlay)
            doSeek()
          })
        }
      } else {
        // 加载并恢复播放进度
        try {
          const savedProgress = await loadPlayProgress()
          if (savedProgress && savedProgress > 5) {
            art.value.currentTime = savedProgress
          }
        } catch (error) {
          console.warn('恢复播放进度失败:', error)
        }
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
      startProgressSaveTimer()
      // 追番"想看" → 自动升级为"在看"
      upgradeFollowWishToWatching(animeId.value)
    })

    art.value.on('pause', () => {
      savePlayProgress()
      stopProgressSaveTimer()
    })

    art.value.on('video:ended', () => {
      savePlayProgress()
      stopProgressSaveTimer()
      // 自动同步 Bangumi 剧集已看状态
      syncEpisodeWatchedToBangumi()
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
    art.value.on('artplayerPluginDanmuku:loaded', (danmus) => {
      const count = Array.isArray(danmus) ? danmus.length : 0
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
  // 从详情页跳转时可能保留了滚动位置，重置到顶部
  const mainContent = document.querySelector('.main-content')
  if (mainContent) mainContent.scrollTop = 0
  updateViewportState()
  window.addEventListener('resize', updateViewportState)
  document.addEventListener('keydown', handleSubtitleDelayKey)
  // 进入播放页即消除当前剧集的未读消息
  markEpisodeMessagesRead()
  // 获取番剧数据
  await fetchAnimeData()
  await createPlayerInstance()
})

/**
 * 消除当前剧集的未读消息（进入播放页 / 切换分集时调用）。
 * 失败静默，不影响观看。
 */
const markEpisodeMessagesRead = async () => {
  if (!token.value || !episodeId.value) return
  try {
    await apiMarkEpisodeMessagesRead(episodeId.value)
  } catch (e) {
    console.debug('标记剧集消息已读失败:', e)
  }
}

/**
 * 监听路由变化：videoId 或 episodeId 任一变化都刷新播放态。
 * - videoId 变化：切换视频源 + 刷新弹幕/字幕
 * - episodeId 变化：刷新弹幕/字幕（即便视频源不变）
 */
watch(
  () => [String(route.params.videoId || ''), String(route.query.episodeId || ''), String(route.query.subtitleId || '')],
  async ([newVideoId, newEpisodeId, newSubtitleId], [oldVideoId, oldEpisodeId, oldSubtitleId]) => {
    closeResourceDialog()

    if (newVideoId === oldVideoId && newEpisodeId === oldEpisodeId && newSubtitleId === oldSubtitleId) {
      return
    }

    // 切换分集后消除该集的未读消息
    markEpisodeMessagesRead()

    try {
      await createPlayerInstance()
    } catch (error) {
      console.error('重建播放器失败:', error)
    }
  }
)

watch(() => animeId.value, async () => {
  closeResourceDialog()
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

<style>
/* Player 页禁止外层滚动：用 flex 让播放器精确撑满剩余高度，避免出现滚动条 */
.app-content:has(.player-page) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (max-width: 768px) {
  /* 移动端恢复滚动，内容自然排布 */
  .app-content:has(.player-page) {
    display: block;
    overflow-y: auto;
  }
}
</style>

<style scoped>
/* Loading and Error States */
.loading-state,
.error-state {
  text-align: center;
  font-size: 1.1rem;
  color: var(--al-text-secondary);
  padding: 60px 20px;
}

.error-state {
  color: var(--al-danger-strong);
}

/* Player page — 播放器撑满剩余高度，无滚动 */
.player-page {
  flex: 1;
  min-height: 0;
  height: auto;
  display: flex;
  flex-direction: column;
}

/* Main Layout */
.player-layout {
  display: flex;
  flex-direction: column;
  background: var(--al-bg);
  border-radius: 32px;
  overflow: hidden;
  padding: 24px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
  flex: 1;
  min-height: 0;
}

/* 播放器 + 选集行 */
.player-top-row {
  display: flex;
  gap: 20px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

/* 播放器区域 */
.player-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.player-card {
  flex: 1;
  min-height: 0;
}

/* Player Card */
.player-card {
  position: relative;
  background: var(--al-bg);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 选集卡片面板 — 固定高度对齐播放器，不滚动 */
.player-episode-panel {
  flex: 0 0 360px;
  width: 360px;
  display: flex;
  flex-direction: column;
  background: var(--al-bg-offwhite);
  border: 1px solid var(--al-border-soft-2);
  border-radius: 16px;
  overflow: hidden;
}

/* 面板顶部 — 番剧信息 + 返回 */
.episode-panel-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--al-bg-beige-6);
}

.episode-panel-poster {
  width: 54px;
  height: 76px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--al-bg-beige-12);
}

.episode-panel-meta {
  flex: 1;
  min-width: 0;
}

.episode-panel-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--al-text-strong);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-panel-sub {
  font-size: 0.78rem;
  color: var(--al-text-muted);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-panel-back {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 0.78rem;
  color: var(--al-text-muted-2);
  text-decoration: none;
  padding: 3px 8px;
  border: 1px solid var(--al-border-soft-2);
  border-radius: 6px;
  flex-shrink: 0;
  transition: color 0.15s, border-color 0.15s;
}

.episode-panel-back:hover {
  color: var(--al-accent);
  border-color: var(--al-accent);
}

/* Tab 切换 — 底部下划线 + 右侧计数 */
.episode-panel-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 16px;
  border-bottom: 1px solid var(--al-bg-beige-6);
}

.episode-panel-tab {
  border: none;
  background: none;
  padding: 8px 14px;
  font-size: 0.8rem;
  color: var(--al-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.episode-panel-tab:hover {
  color: var(--al-text-brown-21);
}

.episode-panel-tab.active {
  color: var(--al-accent);
  border-bottom-color: var(--al-accent);
  font-weight: 600;
}

.episode-panel-count {
  margin-left: auto;
  font-size: 0.72rem;
  color: var(--al-text-muted-3);
  padding-right: 4px;
}

/* 分集列表 — 撑满剩余高度，溢出滚动 */
.episode-panel-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 6px 10px;
}

.episode-panel-comments {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 6px 10px;
}

.episode-panel-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: none;
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  font-size: 0.8rem;
  color: var(--al-text-brown-21);
  transition: background 0.1s;
  flex-shrink: 0;
}

.episode-panel-item:hover {
  background: rgba(196, 93, 43, 0.06);
}

.episode-panel-item.is-current {
  background: var(--al-bg-hover-warm);
  color: var(--al-accent);
  font-weight: 600;
  border-radius: 8px;
}

.episode-panel-item.is-unavailable {
  opacity: 0.4;
  cursor: default;
}

.episode-item-num {
  flex-shrink: 0;
  min-width: 4.2em;
  font-variant-numeric: tabular-nums;
}

.episode-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-item-date {
  flex-shrink: 0;
  font-size: 0.72rem;
  color: var(--al-text-muted-2);
  font-variant-numeric: tabular-nums;
}

.episode-panel-item.is-current .episode-item-date {
  color: var(--al-accent);
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
  /* Artplayer 移动端全屏安全区 */
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
  height: 100%;
  min-height: 280px;
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

/* 平板/移动端控件约束：防止控件栏宽度溢出。
   注意：.art-controls-left/.art-controls-right 不能设置 overflow:hidden，
   否则会裁掉向上展开的音量面板、分集面板与 hover 提示气泡。
   水平方向的溢出由 artplayer 自带 .art-bottom 的 overflow:hidden 兜底裁切。 */
.artplayer-container :deep(.art-controls) {
  max-width: 100%;
  flex-wrap: nowrap;
}

.artplayer-container :deep(.art-controls-left),
.artplayer-container :deep(.art-controls-right) {
  flex-shrink: 0;
}

/* .art-mobile 同步后的非全屏安全区适配 */
.artplayer-container :deep(.art-video-player.art-mobile:not(.art-fullscreen):not(.art-fullscreen-web):not(:fullscreen):not(:-webkit-full-screen) .art-bottom) {
  padding-left: max(10px, env(safe-area-inset-left, 0px));
  padding-right: max(10px, env(safe-area-inset-right, 0px));
  padding-bottom: max(10px, env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}

/* 平板中等尺寸 (769px–1199px)：缩小控件尺寸，防止溢出 */
@media (min-width: 769px) and (max-width: 1199px) {
  .artplayer-container :deep(.art-video-player) {
    --art-control-height: 38px;
    --art-control-icon-size: 28px;
    --art-bottom-height: 76px;
    --art-bottom-offset: 14px;
    --art-padding: 8px;
    --art-bottom-gap: 3px;
    --art-state-size: 60px;
    --art-settings-icon-size: 20px;
  }
}

/* Responsive */
@media (max-width: 1199px) {
  .player-layout {
    padding: 16px;
    gap: 12px;
    min-height: 0;
    border-radius: 24px;
  }

  .player-top-row {
    flex-direction: column;
  }

  .player-main {
    flex: 0 0 auto;
    width: 100%;
  }

  .player-episode-panel {
    flex: 0 0 auto;
    width: 100%;
    max-height: 420px;
  }
}

@media (max-width: 799px) {
  .player-layout {
    padding: 12px;
    gap: 10px;
    border-radius: 20px;
  }
}

/* 手机端：右侧选集面板改为 B 站式 TAB 组件，页面可滚动 */
@media (max-width: 768px) {
  .player-page {
    flex: none;
    height: auto;
    min-height: calc(100vh - 76px);
  }

  .player-top-row {
    gap: 12px;
  }

  .player-main {
    flex: 0 0 auto;
    width: 100%;
  }

  .player-card {
    flex: 0 0 auto;
    aspect-ratio: 16 / 9;
  }

  .artplayer-container {
    min-height: 0;
  }

  .player-episode-panel {
    display: none;
  }
}

@media (max-width: 479px) {
  .artplayer-container {
    min-height: 200px;
  }
}
</style>
