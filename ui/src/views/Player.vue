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
import { markEpisodeMessagesRead as apiMarkEpisodeMessagesRead } from '../api/messages'
import { syncEpisodeWatched } from '../api/bangumi'
import { useAnimeDerived } from '../composables/useAnimeDerived'
import { useAnimeData } from '../composables/useAnimeData'
import { useAuth } from '../composables/useAuth'
import { useFollow } from '../composables/useFollow'
import { useResourceSelection } from '../composables/useResourceSelection'
import { useDanmaku } from '../composables/useDanmaku'
import { useSubtitleTracks } from '../composables/useSubtitleTracks'
import { usePlayProgress } from '../composables/usePlayProgress'
import { usePlayerCore, isMobileViewport } from '../composables/usePlayerCore'
import {
  isFuture,
  filterMainEpisodes,
  filterSpecialEpisodes,
  episodeNumberDisplay,
  formatEpisodeDate,
  buildPlayableEpisodeKeys,
  getEpisodeResources,
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
const isSwitching = ref(false)

const artRef = ref(null)
const art = shallowRef(null)
const RESOURCE_DIALOG_TITLE_MAX_LEN = 40

const { isFollowing, followStatus, checkFollowStatus, upgradeFollowWishToWatching } = useFollow()
const { showResourceDialog, selectedResources, selectedEpisodeTitle, closeResourceDialog, selectResource, playEpisode } =
  useResourceSelection({
    router,
    getAnimeId: () => animeId.value,
    getExistingEpisodes: () => existingEpisodes.value,
    titleMaxLen: RESOURCE_DIALOG_TITLE_MAX_LEN,
  })

/**
 * 获取番剧数据
 */
const { animeData, existingEpisodes, loading, error, fetchAnimeData } = useAnimeData({
  getAnimeId: () => animeId.value,
  onDataLoaded: (id) => {
    checkFollowStatus(id)
  },
  onError: () => {
    isFollowing.value = false
  },
  emptyDataMessage: '获取番剧信息失败',
})

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

// ===== 弹幕 =====
const danmaku = useDanmaku({
  art,
  token,
  getEpisodeId: () => episodeId.value,
  getAnimeId: () => animeId.value,
  getVideoId: () => videoId.value,
  getAnimeTitle: () => animeData.value?.titles?.[0]?.title || null,
  getEpisodeTitle: () => currentEpisodeResource.value?.episodeTitle || null,
})

// ===== 字幕 =====
const subtitle = useSubtitleTracks({
  art,
  userInfo,
  router,
  route,
  isMobileViewport,
  getVideoId: () => videoId.value,
  getAnimeId: () => animeId.value,
  getEpisodeId: () => episodeId.value,
  getSubtitleTrackId: () => subtitleTrackId.value,
})

// ===== 播放进度 =====
const progress = usePlayProgress({
  art,
  token,
  getVideoId: () => videoId.value,
  getAnimeId: () => animeId.value,
  getEpisodeId: () => episodeId.value,
  getEpisodeTitle: () => currentEpisodeResource.value?.episodeTitle || null,
  getAnimeTitle: () => animeData.value?.titles?.[0]?.title || null,
  onProgressComplete: () => syncEpisodeWatchedToBangumi(),
})

// ===== 播放器核心（创建/重建、快捷键、移动端手势）=====
const {
  isDesktopViewport,
  updateViewportState,
  handleSubtitleDelayKey,
  destroyPlayerInstance,
  createPlayerInstance,
} = usePlayerCore({
  artRef,
  art,
  route,
  router,
  isSwitching,
  seekTime,
  danmaku,
  subtitle,
  progress,
  getVideoId: () => videoId.value,
  getEpisodeId: () => episodeId.value,
  getAnimeId: () => animeId.value,
  getPlayableEpisodes: () => playableEpisodes.value,
  playEpisode,
  upgradeFollowWishToWatching,
  syncEpisodeWatchedToBangumi,
  getDdplayFilePath: () => currentEpisodeResource.value?.filePath || currentEpisodeResource.value?.fileName || '',
})

onMounted(async () => {
  // 从详情页跳转时可能保留了滚动位置，重置到顶部
  const appContent = document.querySelector('.app-content')
  if (appContent) appContent.scrollTop = 0
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
  progress.stopProgressSaveTimer()
  subtitle.stopSubtitleOffsetPersistTimer()
  progress.savePlayProgress() // 组件销毁前保存最后一次进度
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
