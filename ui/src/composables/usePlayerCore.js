// 播放器核心逻辑：播放器实例创建/重建（含 playerRecreateSeq 竞态守卫）、
// 快捷键、移动端手势、选集控制栏。此前集中在 Player.vue 的"上帝组件"中，统一收敛到这里。
import { computed, ref } from 'vue'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import artplayerPluginVttThumbnail from 'artplayer-plugin-vtt-thumbnail'
import Hls from 'hls.js'
import { showAppMessage } from '../utils/ui-feedback'
import { API_BASE, ACCENT_COLOR } from '../utils/constants'
import { truncateText } from '../utils/episodes'
import { getMediaPlayInfo } from '../api/media'
import { resolvePlayMode, canPlayMime } from '../utils/playback'

const MOBILE_VIEWPORT_MAX_WIDTH = 768
const EPISODE_SELECTOR_TITLE_MAX_LEN = 28

export const isMobileViewport = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia(`(max-width: ${MOBILE_VIEWPORT_MAX_WIDTH}px)`).matches
}

/**
 * 播放器核心逻辑。
 * @param {Object} options
 * @param {import('vue').Ref} options.artRef 播放器容器元素
 * @param {import('vue').ShallowRef} options.art 播放器实例引用
 * @param {import('vue-router').RouteLocationNormalizedLoaded} options.route
 * @param {import('vue-router').Router} options.router
 * @param {import('vue').Ref} options.isSwitching 切换分集遮罩状态
 * @param {import('vue').ComputedRef} options.seekTime URL 传入的时间跳转参数
 * @param {object} options.danmaku useDanmaku 实例
 * @param {object} options.subtitle useSubtitleTracks 实例
 * @param {object} options.progress usePlayProgress 实例
 * @param {() => string} options.getVideoId
 * @param {() => string} options.getEpisodeId
 * @param {() => string} options.getAnimeId
 * @param {() => Array} options.getPlayableEpisodes
 * @param {(ep, fromPlayerChrome) => void} options.playEpisode
 * @param {(animeId) => void} options.upgradeFollowWishToWatching
 * @param {() => void} options.syncEpisodeWatchedToBangumi
 * @param {() => string} options.getDdplayFilePath 当前资源文件路径
 */
export function usePlayerCore({
  artRef,
  art,
  route,
  router,
  isSwitching,
  seekTime,
  danmaku,
  subtitle,
  progress,
  getVideoId,
  getEpisodeId,
  getAnimeId,
  getPlayableEpisodes,
  playEpisode,
  upgradeFollowWishToWatching,
  syncEpisodeWatchedToBangumi,
  getDdplayFilePath,
}) {
  let playerRecreateSeq = 0
  let _mobileTapHandler = null
  let _hlsInstance = null
  let _playbackWarnShown = false
  const isDesktopViewport = ref(true)

  const showDdplayButton = computed(() => {
    return isDesktopViewport.value && Boolean(getVideoId())
  })

  const ddplayLink = computed(() => {
    if (!getVideoId() || typeof window === 'undefined') {
      return ''
    }

    const streamUrl = `${window.location.origin}${API_BASE}/media-files/stream/${getVideoId()}`
    const filePath = getDdplayFilePath()
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

  /**
   * 根据 play-info 响应决策播放源。
   * - 有可靠播放方式（direct/remux/mixed/transcode）时按浏览器能力选择；
   * - 无法可靠播放时（如服务端转码已关闭），仍回退直出原始流，仅通过 warn 提示
   *   引导用户在播放异常时使用弹弹play，而不是直接失败或跳转。
   */
  const resolvePlaySource = (playInfoRes) => {
    const info = playInfoRes?.code === 200 ? playInfoRes.data : null
    if (info) {
      const resolved = resolvePlayMode(info)
      if (resolved.mode) {
        return resolved
      }
      return {
        mode: 'direct',
        url: info.streamUrl || `${API_BASE}/media-files/stream/${getVideoId()}`,
        isHls: false,
        degraded: true,
      }
    }
    return {
      mode: 'direct',
      url: `${API_BASE}/media-files/stream/${getVideoId()}`,
      isHls: false,
    }
  }

  /**
   * 降级播放时的 warn 提醒（每个播放实例最多一次）：
   * - transcode：视频被重编码，画质/音质可能下降
   * - mixed：仅音频被重编码，音质可能下降
   * - degraded（直出但浏览器可能不支持）：引导使用弹弹play
   */
  const warnDegradedPlayback = (playSource) => {
    if (_playbackWarnShown || !playSource) return
    let message = ''
    if (playSource.mode === 'transcode') {
      message = '当前视频编码浏览器不支持，已由服务端转码播放，画质/音质可能有所下降'
    } else if (playSource.mode === 'mixed') {
      message = '当前音频编码浏览器不支持，音频已由服务端转码播放，音质可能有所下降'
    } else if (playSource.degraded) {
      message = '浏览器可能无法正常播放该视频编码，如播放异常可尝试通过弹弹play播放'
    }
    if (message) {
      _playbackWarnShown = true
      showAppMessage(message, 'warning')
    }
  }

  const getCurrentPlayableEpisodeIndex = () => {
    const currentEpisodeKey = String(getEpisodeId() || '')
    return getPlayableEpisodes().findIndex((ep) => String(ep.episodeId) === currentEpisodeKey)
  }

  const jumpToEpisodeById = (targetEpisodeId) => {
    const target = getPlayableEpisodes().find((ep) => String(ep.episodeId) === String(targetEpisodeId))
    if (!target) {
      return false
    }
    playEpisode(target, true)
    return true
  }

  const jumpToAdjacentEpisode = (delta) => {
    const list = getPlayableEpisodes()
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

  const destroyHls = () => {
    if (_hlsInstance) {
      try {
        _hlsInstance.destroy()
      } catch (error) {
        console.warn('销毁 HLS 实例失败:', error)
      }
      _hlsInstance = null
    }
  }

  /**
   * 为视频元素加载 HLS 转码/秒转流（优先 MSE + hls.js，iOS 回退原生 HLS）。
   */
  const attachHlsSource = (url) => {
    const video = art.value?.video
    if (!video) {
      return
    }
    if (Hls && typeof Hls.isSupported === 'function' && Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true })
      _hlsInstance = hls
      hls.loadSource(url)
      hls.attachMedia(video)
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data || !data.fatal) {
          return
        }
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          hls.startLoad()
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls.recoverMediaError()
        } else {
          console.error('HLS 播放错误:', data)
          destroyHls()
          if (art.value?.notice) {
            art.value.notice.show = '播放失败，可尝试通过弹弹play等外部播放器播放'
          }
        }
      })
    } else if (canPlayMime('application/vnd.apple.mpegurl')) {
      video.src = url
    }
  }

  const destroyPlayerInstance = () => {
    destroyHls()

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
      subtitle.subtitleOctopus.value = null
      return
    }

    if (subtitle.subtitleOctopus.value) {
      try {
        subtitle.subtitleOctopus.value.dispose()
      } catch (error) {
        console.warn('销毁字幕实例失败:', error)
      }
      subtitle.subtitleOctopus.value = null
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
        selector: getPlayableEpisodes().map((ep, index) => ({
          default: String(ep.episodeId) === String(getEpisodeId()),
          html: `第${ep.episodeNumber || index + 1}话 ${truncateText(ep.episodeTitle || '', EPISODE_SELECTOR_TITLE_MAX_LEN)}`.trim(),
          value: String(ep.episodeId || ''),
          episodeId: String(ep.episodeId || ''),
        })),
        onSelect: (item) => {
          const targetEpisodeId = item?.value || item?.episodeId || ''

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

  /**
   * 键盘快捷键：[ 减少字幕延迟，] 增加字幕延迟（每次 500ms）
   */
  const handleSubtitleDelayKey = (e) => {
    const tag = document.activeElement?.tagName?.toLowerCase()
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return
    if (e.key === '[') {
      e.preventDefault()
      subtitle.adjustSubtitleDelay(-500)
    } else if (e.key === ']') {
      e.preventDefault()
      subtitle.adjustSubtitleDelay(500)
    }
  }

  /**
   * 播放器已可用后再异步加载弹幕，避免阻塞播放启动
   */
  const loadDanmakuAsync = async (seq, targetEpisodeId) => {
    try {
      const danmakuData = await danmaku.fetchDanmaku(targetEpisodeId)
      if (seq !== playerRecreateSeq || !art.value?.plugins?.artplayerPluginDanmuku) {
        return
      }
      await art.value.plugins.artplayerPluginDanmuku.load(danmakuData)
    } catch (error) {
      console.error('异步加载弹幕失败:', error)
    }
  }

  const createPlayerInstance = async () => {
    const seq = ++playerRecreateSeq
    isSwitching.value = true
    const mobile = isMobileViewport()

    const targetVideoId = String(getVideoId() || '')
    const targetEpisodeId = String(getEpisodeId() || '')

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

      // 优先获取播放器必需数据：字幕 + 播放信息（用于能力决策）；弹幕改为异步注入
      const [playInfoRes, subtitles] = await Promise.all([
        getMediaPlayInfo(targetVideoId).catch(() => null),
        subtitle.fetchSubtitles(targetVideoId),
      ])
      const routeSelectedTrack = subtitles.find((item) => String(item?.id || '') === String(route.query.subtitleId || '')) || null
      const subtitlesForLibass = subtitles.filter(subtitle.isLibassSubtitleTrack)
      const subtitlesForNative = subtitles.filter(subtitle.isNativeArtplayerSubtitleTrack)
      const fallbackTrack = subtitlesForLibass[0] || subtitlesForNative[0] || subtitles[0] || null
      const activeSubtitleTrack = routeSelectedTrack || fallbackTrack
      const useNativeSubtitle = Boolean(activeSubtitleTrack && subtitle.isNativeArtplayerSubtitleTrack(activeSubtitleTrack))
      const nativeSubtitleOption = useNativeSubtitle ? subtitle.buildNativeSubtitleOption(activeSubtitleTrack) : null

      if (useNativeSubtitle) {
        subtitle.selectedSubtitleTrack.value = activeSubtitleTrack
        subtitle.tmpSubtitleOctopusSubUrl.value = ''
        console.info('[subtitle] 使用 Artplayer 原生字幕渲染', subtitlesForNative.map(item => item?.format))
      }

      if (subtitles.length > 0 && subtitlesForLibass.length === 0) {
        console.warn('[subtitle] 当前资源无ASS/SSA字幕，已跳过libass字幕渲染', subtitles.map(item => item?.format))
      }
      if (seq !== playerRecreateSeq) {
        return
      }

      destroyPlayerInstance()

      // 依据浏览器能力决定播放方式（直出 / 秒转封装 / 音频转码 / 全转码）。
      // 无法可靠播放时也会回退直出原始流（degraded），仅提示引导使用弹弹play。
      const playSource = resolvePlaySource(playInfoRes)
      _playbackWarnShown = false

      const danmakuOptions = danmaku.buildDanmakuOptions([], mobile)
      const subtitlePlugin = useNativeSubtitle
        ? null
        : subtitle.buildSubtitlePlugin(subtitlesForLibass, activeSubtitleTrack)
      const subtitleSettings = subtitle.buildSubtitleSettings(subtitles, String(activeSubtitleTrack?.id || ''))
      const episodeControls = buildEpisodeControls(mobile)

      // 初始化 Artplayer
      art.value = new Artplayer({
        container: artRef.value,
        url: playSource.isHls ? '' : playSource.url,
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
        theme: ACCENT_COLOR,
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
            click: () => { subtitle.adjustSubtitleDelay(-500) },
          },
          {
            html: '字幕延迟 +0.5s &nbsp;<kbd>快捷键：]</kbd>',
            click: () => { subtitle.adjustSubtitleDelay(500) },
          },
          {
            html: '重置字幕延迟',
            click: () => {
              const track = subtitle.selectedSubtitleTrack.value
              if (!track) return
              const delta = -subtitle.getCurrentSubtitleOffsetMs(track)
              if (delta !== 0) subtitle.adjustSubtitleDelay(delta)
            },
          },
        ],
      })
      if (seq !== playerRecreateSeq) {
        destroyPlayerInstance()
        return
      }

      // 转码/秒转流走 HLS（MSE/hls.js 或 iOS 原生）
      if (playSource.isHls) {
        attachHlsSource(playSource.url)
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
            const savedProgress = await progress.loadPlayProgress()
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
        progress.startProgressSaveTimer()
        // 追番"想看" → 自动升级为"在看"
        upgradeFollowWishToWatching(getAnimeId())
        // 降级播放（转码/直出但可能不支持）时给出 warn 提醒
        warnDegradedPlayback(playSource)
      })

      art.value.on('pause', () => {
        progress.savePlayProgress()
        progress.stopProgressSaveTimer()
      })

      art.value.on('video:ended', () => {
        progress.savePlayProgress()
        progress.stopProgressSaveTimer()
        // 自动同步 Bangumi 剧集已看状态
        syncEpisodeWatchedToBangumi()
      })

      art.value.on('error', (error) => {
        console.error('播放器错误:', error)
        progress.stopProgressSaveTimer()
      })

      art.value.on('subtitleOffset', (offsetSec) => {
        const track = subtitle.selectedSubtitleTrack.value
        if (!track || !subtitle.isNativeArtplayerSubtitleTrack(track)) {
          return
        }
        const offsetMs = Math.round(Number(offsetSec || 0) * 1000)
        track.timeOffset = offsetMs
        subtitle.queuePersistSubtitleOffset(track, offsetMs)
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
        danmaku.saveDanmakuSettings(option)
      })

      art.value.on('artplayerPluginDanmuku:show', () => {
        const option = art.value?.plugins?.artplayerPluginDanmuku?.option
        if (option) {
          danmaku.saveDanmakuSettings({ ...option, visible: true })
        }
      })

      art.value.on('artplayerPluginDanmuku:hide', () => {
        const option = art.value?.plugins?.artplayerPluginDanmuku?.option
        if (option) {
          danmaku.saveDanmakuSettings({ ...option, visible: false })
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

  return {
    isDesktopViewport,
    updateViewportState,
    handleSubtitleDelayKey,
    destroyPlayerInstance,
    createPlayerInstance,
  }
}
