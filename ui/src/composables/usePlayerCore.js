// 播放器核心逻辑：播放器实例创建/重建（含 playerRecreateSeq 竞态守卫）、
// 快捷键、移动端手势、选集控制栏。此前集中在 Player.vue 的"上帝组件"中，统一收敛到这里。
import { computed, ref } from 'vue'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import artplayerPluginVttThumbnail from 'artplayer-plugin-vtt-thumbnail'
import { showAppMessage, askAppConfirm } from '../utils/ui-feedback'
import {
  API_BASE,
  DANDPANPLAY_OFFICIAL_URL,
  DANDPANPLAY_ANDROID_URL,
  DANDPANPLAY_ANDROID_PACKAGE,
} from '../utils/constants'
import { truncateText } from '../utils/episodes'
import { theme, accentKey } from './useTheme'
import { getThemeColorPreset } from '../utils/themeColors'
import { getMediaFileCodecs } from '../api/media'
import { checkCodecSupport } from '../utils/codecSupport'

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
  // 编码不支持弹窗去重：同一番剧只提示一次（跨播放器重建保留，切分集不重复打扰）
  let _codecPromptAnimeId = null
  const isDesktopViewport = ref(true)

  const showDdplayButton = computed(() => {
    return isDesktopViewport.value && Boolean(getVideoId())
  })

  const isAndroidDevice = () =>
    typeof navigator !== 'undefined' && /android/i.test(navigator.userAgent || '')

  const getStreamUrl = () => {
    if (!getVideoId() || typeof window === 'undefined') {
      return ''
    }
    return `${window.location.origin}${API_BASE}/media-files/stream/${getVideoId()}`
  }

  /**
   * 构建 Android intent:// 唤起 URL。
   * @param {string} streamUrl 直链地址
   * @param {string|null} pkg 目标应用包名；传 null 时不指定包名，由系统弹出应用选择器
   */
  const buildAndroidIntentUrl = (streamUrl, pkg) => {
    try {
      const u = new URL(streamUrl)
      const fallback = encodeURIComponent(DANDPANPLAY_OFFICIAL_URL)
      const pkgPart = pkg ? `;package=${pkg}` : ''
      return (
        `intent://${u.host}${u.pathname}${u.search}` +
        `#Intent;scheme=${u.protocol.replace(':', '')}${pkgPart};` +
        `action=android.intent.action.VIEW;type=video/mp4;` +
        `S.browser_fallback_url=${fallback};end`
      )
    } catch {
      return streamUrl
    }
  }

  /**
   * 生成"通过弹弹play播放"的唤起链接：
   * - Android：无 ddplay: 自定义协议，改用 intent:// 精确唤起弹弹play APP
   *   （该 APP 注册了 ACTION_VIEW + BROWSABLE + http/https + video/* 的
   *   intent-filter，入口直接把 URL 交给播放器）；未安装时回退到官网。
   * - 桌面端：使用 ddplay: 自定义协议。
   */
  const buildExternalPlayerLaunchUrl = () => {
    const streamUrl = getStreamUrl()
    if (!streamUrl) {
      return ''
    }
    if (isAndroidDevice()) {
      return buildAndroidIntentUrl(streamUrl, DANDPANPLAY_ANDROID_PACKAGE)
    }

    const filePath = getDdplayFilePath()
    const withOptionalFilePath = filePath
      ? `${streamUrl}|filePath=${filePath}`
      : streamUrl
    return `ddplay:${encodeURIComponent(withOptionalFilePath)}`
  }

  /**
   * Android 端"通过其他视频软件打开"：不带 package 的 intent://，
   * 由系统弹出应用选择器（弹弹play、MX Player、VLC 等已注册视频 intent-filter 的应用）。
   */
  const buildExternalPlayerChooserUrl = () => {
    const streamUrl = getStreamUrl()
    if (!streamUrl || !isAndroidDevice()) {
      return ''
    }
    return buildAndroidIntentUrl(streamUrl, null)
  }

  /**
   * 通过隐藏 iframe 触发 Android intent:// 唤起，避免页面跳转/闪白页。
   * （intent:// 唤起无需页面导航，iframe 导航即可触发系统 intent）
   */
  const fireAndroidIntent = (intentUrl) => {
    if (!intentUrl) return
    const iframe = document.createElement('iframe')
    iframe.style.display = 'none'
    iframe.setAttribute('aria-hidden', 'true')
    iframe.src = intentUrl
    document.body.appendChild(iframe)
    setTimeout(() => iframe.remove(), 1000)
  }

  const openWithDdplay = () => {
    const launchUrl = buildExternalPlayerLaunchUrl()
    if (!launchUrl) {
      showAppMessage('未获取到可播放地址', 'warning')
      return
    }
    if (isAndroidDevice()) {
      fireAndroidIntent(launchUrl)
    } else {
      window.location.href = launchUrl
    }
  }

  /** Android：通过其他视频软件打开（系统应用选择器，iframe 触发不跳转页面） */
  const openWithOtherPlayer = () => {
    const chooserUrl = buildExternalPlayerChooserUrl()
    if (!chooserUrl) {
      showAppMessage('未获取到可播放地址', 'warning')
      return
    }
    fireAndroidIntent(chooserUrl)
  }

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

  /**
   * 探测浏览器对当前视频编码的支持情况。
   * 直出播放时若视频/音频编码浏览器无法解码（如 HEVC、FLAC/AC3/DTS），
   * 弹窗引导用户使用【通过弹弹play播放】。同一番剧只提示一次。
   * 探测失败或编码未知时静默跳过，不影响播放。
   */
  const checkUnsupportedCodec = async (videoId, seq) => {
    try {
      const res = await getMediaFileCodecs(videoId)
      if (seq !== playerRecreateSeq) return
      const d = res?.data
      if (!d) return
      const { supported, unsupportedParts } = checkCodecSupport({
        videoCodec: d.videoCodec,
        audioCodec: d.audioCodec,
      })
      if (supported) return

      const animeKey = String(getAnimeId() || '')
      if (_codecPromptAnimeId === animeKey) return
      _codecPromptAnimeId = animeKey

      // 手机端/PC 展示各自适用的链接；Android 额外提供"通过其他视频软件打开"平级按钮
      const links = isAndroidDevice()
        ? [
            { text: '弹弹play 官网', href: DANDPANPLAY_OFFICIAL_URL },
            { text: 'Android 客户端（开源）', href: DANDPANPLAY_ANDROID_URL },
          ]
        : [{ text: '弹弹play 官网', href: DANDPANPLAY_OFFICIAL_URL }]
      const actions = isAndroidDevice()
        ? [{ text: '通过其他视频软件打开', value: 'other-player', color: 'primary' }]
        : []

      askAppConfirm({
        title: '浏览器可能无法播放该视频',
        message: `当前视频/音频编码（${unsupportedParts.join('、')}）可能无法在浏览器中直接播放。\n建议使用弹弹play客户端播放，以获得最佳画质与流畅度。`,
        confirmText: '通过弹弹play播放',
        cancelText: '继续播放',
        color: 'warning',
        links,
        actions,
      }).then((choice) => {
        if (choice === 'other-player') {
          openWithOtherPlayer()
        } else if (choice === true) {
          openWithDdplay()
        }
      })
    } catch (e) {
      // 静默失败：拿不到编码信息时不打扰用户
      console.debug('编码探测失败:', e)
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

      // 优先获取播放器必需数据：字幕；弹幕改为异步注入，避免阻塞首帧播放
      const subtitles = await subtitle.fetchSubtitles(targetVideoId)
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

      const danmakuOptions = danmaku.buildDanmakuOptions([], mobile)
      const subtitlePlugin = useNativeSubtitle
        ? null
        : subtitle.buildSubtitlePlugin(subtitlesForLibass, activeSubtitleTrack)
      const subtitleSettings = subtitle.buildSubtitleSettings(subtitles, String(activeSubtitleTrack?.id || ''))
      const episodeControls = buildEpisodeControls(mobile)

      // 播放器主题色跟随当前主题色预设
      const playerAccent =
        getThemeColorPreset(accentKey.value)?.[theme.value === 'dark' ? 'dark' : 'light'].accent ||
        '#c45d2b'

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
        theme: playerAccent,
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

      // 探测浏览器编码支持：不支持时弹窗引导使用弹弹play（异步，不阻塞播放）
      checkUnsupportedCodec(targetVideoId, seq)

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
