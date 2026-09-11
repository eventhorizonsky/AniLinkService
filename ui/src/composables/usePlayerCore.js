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
import { decidePlaybackMode } from '../utils/playbackMode'
import { createWasmAudioEngine } from '../utils/wasmAudioEngine'

// --- WASM(mediabunny) 播放后端：仅在需要时动态加载 ---
// ArtPlayer 的 option.proxy 播放后端为仓库内 vendored fork（上游 MIT：
// artplayer-proxy-mediabunny，见 ui/src/vendor/artplayer-proxy-mediabunny/README.md），
// 它运行时复用顶层统一的 mediabunny 实例；AC3/EAC3 通过 @mediabunny/ac3(MPL-2.0,
// libavcodec WASM) 官方 registerAc3Decoder() 注册到同一解码器注册表。
// 本项目只作为使用方，不修改 mediabunny 本体（第三方声明见仓库根 THIRD_PARTY_NOTICES）。
let wasmPlaybackModulesPromise = null
const loadWasmPlaybackModules = () => {
  if (!wasmPlaybackModulesPromise) {
    wasmPlaybackModulesPromise = (async () => {
      const [{ default: artplayerProxyMediabunny }, { registerAc3Decoder }] = await Promise.all([
        import('../vendor/artplayer-proxy-mediabunny/index.js'),
        import('@mediabunny/ac3'),
      ])
      registerAc3Decoder() // 注册后 mediabunny 解码 ac3/eac3 音轨自动走 WASM
      return { artplayerProxyMediabunny }
    })()
  }
  return wasmPlaybackModulesPromise
}

const MOBILE_VIEWPORT_MAX_WIDTH = 768
const EPISODE_SELECTOR_TITLE_MAX_LEN = 28

// 播放器控件随容器宽度自适应的阈值（与 Player.vue 中 .player-controls-* 规则对应）：
// - 容器宽度 < CONTROLS_NARROW_MAX_WIDTH：启用紧凑控件尺寸
// - 容器宽度 < CONTROLS_EMITTER_OFF_MAX_WIDTH：折叠弹幕输入框
// - 容器宽度 < CONTROLS_MINI_MAX_WIDTH：隐藏上/下集快捷按钮
// - 容器宽度 < CONTROLS_TINY_MAX_WIDTH：隐藏时间显示与音量按钮
const CONTROLS_NARROW_MAX_WIDTH = 971
const CONTROLS_EMITTER_OFF_MAX_WIDTH = 800
const CONTROLS_MINI_MAX_WIDTH = 750
const CONTROLS_TINY_MAX_WIDTH = 730

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
  // WASM 播放提示去重：同一番剧只提示一次
  let _wasmInfoAnimeId = null
  // 代理模式事件桥清理函数（libass 字幕等依赖 $video 上的 DOM 事件）
  let _proxyVideoEventBridgeCleanup = null
  // hybrid(原生画面 + WASM AC3 音频) 引擎清理函数
  let _hybridAudioCleanup = null
  // 媒体编码元数据缓存：videoId -> { videoCodec, audioCodec, containerFormat }
  const _codecsCache = new Map()
  const isDesktopViewport = ref(true)

  const showDdplayButton = computed(() => {
    return isDesktopViewport.value && Boolean(getVideoId())
  })

  const isAndroidDevice = () =>
    typeof navigator !== 'undefined' && /android/i.test(navigator.userAgent || '')

  /**
   * 是否为非 PC 设备（平板/手机等以触摸为主要输入的设备）。
   * 依据主指针类型判断：`(pointer: coarse)` 表示触摸为主要输入；
   * 触屏笔记本的主指针仍是鼠标（fine），不会被误判为平板。
   */
  const isNonPcDevice = () => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return typeof navigator !== 'undefined' && (navigator.maxTouchPoints || 0) > 0
    }
    return window.matchMedia('(pointer: coarse)').matches
  }

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

  /**
   * 弹窗引导使用弹弹play（"判定为 external"与"hybrid 原生画面运行时失败"共用）。
   * 手机端/PC 展示各自适用的链接；Android 额外提供"通过其他视频软件打开"平级按钮。
   */
  const showDdplayGuidance = (message, title = '浏览器可能无法播放该视频') => {
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
      title,
      message,
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

  /**
   * 依据播放器【容器实际宽度】切换控件自适应 class：
   * - player-controls-narrow：紧凑控件尺寸（控件栏完整显示需要约 971px）
   * - player-controls-emitter-off：折叠弹幕输入框（800px 以下放不下）
   * - player-controls-mini：隐藏上/下集快捷按钮（约 750px 以下）
   * - player-controls-tiny：隐藏时间显示与音量按钮（约 730px 以下）
   * 视口宽度无法直接描述播放器宽度（侧边栏/选集面板会吃掉大量宽度），
   * 因此直接测量容器宽度，与视口无关。
   * 全屏时宽度充足且必须保留完整控件（尤其发送弹幕的输入框），不应用任何折叠 class。
   */
  const syncNarrowClasses = () => {
    const container = artRef.value
    if (!container) return
    const playerEl = container.querySelector('.art-video-player')
    if (!playerEl) return
    const fullscreen = Boolean(art.value && (art.value.fullscreen || art.value.fullscreenWeb))
    const width = fullscreen ? Number.MAX_SAFE_INTEGER : (container.clientWidth || 0)
    playerEl.classList.toggle('player-controls-narrow', width > 0 && width < CONTROLS_NARROW_MAX_WIDTH)
    playerEl.classList.toggle('player-controls-emitter-off', width > 0 && width < CONTROLS_EMITTER_OFF_MAX_WIDTH)
    playerEl.classList.toggle('player-controls-mini', width > 0 && width < CONTROLS_MINI_MAX_WIDTH)
    playerEl.classList.toggle('player-controls-tiny', width > 0 && width < CONTROLS_TINY_MAX_WIDTH)
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
    syncNarrowClasses()
    installMobileTapHandler()
  }

  /**
   * 原生/hybrid 共用兜底：
   *  - 运行时 error(MEDIA_ERR_SRC_NOT_SUPPORTED)：直接弹引导；
   *  - 画面帧看门狗：Edge 等对 MKV+HEVC 会"静默失败"（error=null、readyState=4 但
   *    解码不输出任何帧 → 黑屏）。用 requestVideoFrameCallback 探测：播放中超过阈值
   *    没有新帧且时间在前进，视为解码无输出，弹引导。正常浏览器每帧都会回调，不受影响。
   */
  const showNativeVideoUnsupportedGuidance = () => {
    const animeKey = String(getAnimeId() || '')
    if (_codecPromptAnimeId === animeKey) return
    _codecPromptAnimeId = animeKey
    showDdplayGuidance(
      '当前视频无法在当前浏览器中直接解码播放（可能为黑屏）。\n建议使用弹弹play客户端播放，以获得最佳画质与流畅度。',
    )
  }

  let _nativeVideoGuardCleanup = null
  const uninstallNativeVideoGuard = () => {
    if (_nativeVideoGuardCleanup) {
      const cleanup = _nativeVideoGuardCleanup
      _nativeVideoGuardCleanup = null
      try {
        cleanup()
      } catch (error) {
        console.warn('移除 video 兜底监听失败:', error)
      }
    }
  }
  const installNativeVideoGuard = ({ frameWatchdog = false } = {}) => {
    uninstallNativeVideoGuard()
    const el = art.value?.template?.$video
    if (!el || typeof el.addEventListener !== 'function') {
      return
    }

    const removers = []
    let watchdogTimer = 0
    const clearWatchdog = () => {
      if (watchdogTimer) {
        clearTimeout(watchdogTimer)
        watchdogTimer = 0
      }
    }
    const cleanup = () => {
      clearWatchdog()
      removers.forEach((fn) => fn())
      removers.length = 0
    }

    // 1) 显式解码失败：所有原生/hybrid 场景都监听
    const onError = () => {
      if (el.error?.code === 4) {
        showNativeVideoUnsupportedGuidance()
      }
    }
    el.addEventListener('error', onError)
    removers.push(() => el.removeEventListener('error', onError))

    // 2) 画面帧看门狗：默认关闭。仅 matroska 容器启用（Chromium 对 MKV+HEVC 存在
    //    "不报错但解码无输出"的静默失败）；且每段播放只在首次 playing 后探测一次，
    //    不随 seek/暂停重复——运行时成本近零。
    if (frameWatchdog && typeof el.requestVideoFrameCallback === 'function') {
      let armedOnce = false
      const onPlaying = () => {
        if (armedOnce || el.paused || el.ended) return
        armedOnce = true
        let sawFrame = false
        try {
          el.requestVideoFrameCallback(() => {
            sawFrame = true
          })
        } catch {
          return // 该实现不可用则不再探测
        }
        watchdogTimer = setTimeout(() => {
          watchdogTimer = 0
          if (!sawFrame && !el.paused && !el.ended && el.readyState >= 3 && el.currentTime > 0.2) {
            showNativeVideoUnsupportedGuidance()
          }
        }, 2500)
      }
      el.addEventListener('playing', onPlaying)
      removers.push(() => el.removeEventListener('playing', onPlaying))
    }

    _nativeVideoGuardCleanup = cleanup
  }

  /**
   * hybrid 模式：原生 <video> 出画面，wasmAudioEngine(@mediabunny/ac3 WASM) 从动出音轨。
   * 异步启动：失败/不支持时只回退为原生画面播放（不阻塞、不打扰）。
   */
  const uninstallHybridAudio = () => {
    if (_hybridAudioCleanup) {
      const cleanup = _hybridAudioCleanup
      _hybridAudioCleanup = null
      try {
        cleanup()
      } catch (error) {
        console.warn('[hybrid-audio] 清理失败:', error)
      }
    }
  }

  const startHybridAudioEngine = async (videoId, seq, { audioCodecNeedsWasm = false } = {}) => {
    const animeKey = String(getAnimeId() || '')
    const notifyOnce = (message, kind) => {
      if (_wasmInfoAnimeId === animeKey) return
      _wasmInfoAnimeId = animeKey
      showAppMessage(message, kind)
    }
    // 原生画面运行时失败（如 Edge 无 HEVC 能力）→ 恢复改前的弹窗引导弹弹play（按番剧去重）
    const showUnsupportedDdplay = () => {
      if (_codecPromptAnimeId !== animeKey) {
        _codecPromptAnimeId = animeKey
        showDdplayGuidance(
          '当前视频编码无法在浏览器中直接播放。\n建议使用弹弹play客户端播放，以获得最佳画质与流畅度。',
        )
      }
    }

    try {
      const artInstance = art.value
      const videoEl = artInstance?.template?.$video
      if (!videoEl || typeof videoEl.addEventListener !== 'function') {
        return
      }

      // 同步先挂 error 监听：引擎是异步加载（首次含 ~1MB wasm chunk），若 <video> 在其
      // 完成前就报 MEDIA_ERR_SRC_NOT_SUPPORTED(4)，引擎自身的监听会错过 → 提前兜底弹窗
      const earlyErrorHandler = () => {
        if (videoEl.error?.code === 4) {
          showUnsupportedDdplay()
        }
      }
      videoEl.addEventListener('error', earlyErrorHandler)

      // 统一清理：无论引擎是否启动成功，都要移除上面的提前监听
      const cleanups = [() => videoEl.removeEventListener('error', earlyErrorHandler)]

      const audioEngine = await createWasmAudioEngine({
        url: getStreamUrl(),
        video: videoEl,
        getVolume: () => Number(art.value?.volume ?? 0.5),
        isMuted: () => Boolean(art.value?.muted),
        getPlaybackRate: () => Number(art.value?.playbackRate ?? 1),
        onVideoError: showUnsupportedDdplay,
      })

      if (!audioEngine || seq !== playerRecreateSeq || !art.value) {
        audioEngine?.destroy?.()
        if (audioCodecNeedsWasm && seq === playerRecreateSeq) {
          notifyOnce('该音轨的 WASM 解码引擎未能启用，当前保持原生画面播放（音轨可能无声）', 'warning')
        }
        _hybridAudioCleanup = () => cleanups.forEach((fn) => fn())
        return
      }

      cleanups.push(() => audioEngine.destroy())
      _hybridAudioCleanup = () => cleanups.forEach((fn) => fn())
      console.debug('[player] hybrid 音频引擎已启动')
      notifyOnce(
        audioCodecNeedsWasm
          ? '画面为原生播放，AC3/EAC3 音轨已启用浏览器内 WASM 解码'
          : '画面为原生播放，音轨已启用浏览器内解码',
        'info',
      )
    } catch (error) {
      console.warn('[hybrid-audio] 启动失败，保持原生画面:', error)
      if (audioCodecNeedsWasm && seq === playerRecreateSeq) {
        notifyOnce('该音轨的 WASM 解码引擎未能启用，当前保持原生画面播放（音轨可能无声）', 'warning')
      }
    }
  }

  /**
   * WASM(mediabunny) 代理模式的事件桥。
   * 代理模式下 art.$video 是被替换成的 canvas（携带 video 语义的属性），引擎事件以
   * 'video:*' 转发到 art，但不会触发 canvas 上的原生 DOM 事件。libass 字幕等消费者
   * 依赖 video 元素的 DOM 事件（timeupdate/playing/pause/…），这里把它们合成到 $video 上。
   */
  const uninstallProxyVideoEventBridge = () => {
    if (_proxyVideoEventBridgeCleanup) {
      _proxyVideoEventBridgeCleanup()
    }
  }

  const installProxyVideoEventBridge = () => {
    uninstallProxyVideoEventBridge()
    const artInstance = art.value
    const el = artInstance?.video
    if (!artInstance || !el || typeof el.dispatchEvent !== 'function') {
      return
    }

    // 重入守卫：ArtPlayer 已在 $video(canvas) 上绑定"DOM 事件 → art.emit('video:*')"的
    // 原生监听，若在 art 'video:*' 处理器里无条件 dispatchEvent，会形成
    // emit → dispatchEvent → ArtPlayer 监听 → emit → … 的无限递归（栈溢出）。
    // dispatchEvent 是同步的，ArtPlayer 的回发只发生在本次派发期间，故用同步标志截断。
    let synthesizing = false
    const dispatch = (name) => () => {
      if (synthesizing) return
      try {
        synthesizing = true
        el.dispatchEvent(new Event(name))
      } catch {
        /* 忽略合成事件异常 */
      } finally {
        synthesizing = false
      }
    }
    const DOM_EVENTS = [
      'loadstart', 'durationchange', 'loadedmetadata', 'progress', 'loadeddata',
      'canplay', 'canplaythrough', 'playing', 'waiting', 'seeking', 'seeked',
      'ended', 'emptied', 'stalled', 'suspend', 'ratechange', 'volumechange',
      'pause', 'play',
    ]
    const handlers = {}
    DOM_EVENTS.forEach((name) => {
      handlers[name] = dispatch(name)
      artInstance.on(`video:${name}`, handlers[name])
    })

    // 播放期间以 rAF 高频派发 timeupdate，弥补引擎默认 250ms 间隔，保证字幕平滑
    let rafId = 0
    let ticking = false
    const tick = () => {
      if (!ticking || !art.value) return
      dispatch('timeupdate')()
      rafId = requestAnimationFrame(tick)
    }
    const startTick = () => {
      if (!ticking) {
        ticking = true
        rafId = requestAnimationFrame(tick)
      }
    }
    const stopTick = () => {
      ticking = false
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = 0
      }
    }
    handlers._play = startTick
    handlers._pause = stopTick
    artInstance.on('video:play', startTick)
    artInstance.on('video:playing', startTick)
    artInstance.on('video:pause', stopTick)
    artInstance.on('video:ended', stopTick)

    _proxyVideoEventBridgeCleanup = () => {
      stopTick()
      DOM_EVENTS.forEach((name) => artInstance.off(`video:${name}`, handlers[name]))
      artInstance.off('video:play', startTick)
      artInstance.off('video:playing', startTick)
      artInstance.off('video:pause', stopTick)
      artInstance.off('video:ended', stopTick)
      _proxyVideoEventBridgeCleanup = null
    }
  }

  /**
   * 播放能力探测与降级提示。
   * 三种模式（见 utils/playbackMode.js 的 decidePlaybackMode）：
   *  - native：原 ArtPlayer <video> 直出，本函数不打扰；
   *  - wasm：mediabunny+WASM 代理接管（AC3/EAC3/MKV 等可解），通常不打扰；
   *    仅当浏览器连视频轨都无法解码（如 Edge 无 HEVC 能力）时提示一次"已降级仅音频"；
   *  - external：mediabunny 也覆盖不了（如 DTS/TrueHD、AVI 等），弹窗引导弹弹play。
   */
  const checkUnsupportedCodec = async (videoId, seq, { playbackMode = null, codecsMeta = null } = {}) => {
    try {
      const animeKey = String(getAnimeId() || '')

      if (playbackMode?.kind === 'wasm') {
        if (!playbackMode.videoPlayable && _wasmInfoAnimeId !== animeKey) {
          _wasmInfoAnimeId = animeKey
          const videoName = codecsMeta?.videoCodec ? `（${codecsMeta.videoCodec}）` : ''
          const containerName = codecsMeta?.containerFormat ? `，容器 ${codecsMeta.containerFormat}` : ''
          // 区分两种"视频不可解"：非安全上下文(http 访问局域网 IP)下 WebCodecs 被禁用，
          // 与浏览器本身缺 HEVC 解码能力，给用户不同的指引
          const insecureContext = typeof window !== 'undefined' && window.isSecureContext === false
          const guidance = insecureContext
            ? '当前为 http 非安全页面，浏览器禁用了 WebCodecs 视频解码；请改用 https 访问本站点。'
            : '如需完整画面请使用弹弹play。'
          showAppMessage(
            `当前浏览器无法解码该视频编码${videoName}${containerName}，已降级为仅音频播放；${guidance}`,
            'warning',
          )
        }
        return
      }
      // hybrid：画面由原生 <video> 承载（startHybridAudioEngine 负责音轨与运行时失败提示）。
      // 若原生探测判否（nativeVideoUncertain，如 Edge 无 HEVC），先弹一次引导——但 hybrid
      // 仍会"真的尝试"原生画面，用户可点"继续播放"；可解却误报的浏览器不受影响。
      if (playbackMode?.kind === 'hybrid') {
        if (playbackMode.nativeVideoUncertain && _codecPromptAnimeId !== animeKey) {
          _codecPromptAnimeId = animeKey
          showDdplayGuidance(
            '当前视频编码可能无法在当前浏览器中直接播放。\n将先尝试原生播放；若画面异常，建议使用弹弹play客户端。',
          )
        }
        return
      }
      if (playbackMode?.kind === 'native') return

      // external / 兜底路径：需要元数据来判定并提示
      let d = codecsMeta
      if (!d) {
        const res = await getMediaFileCodecs(videoId)
        if (seq !== playerRecreateSeq) return
        d = res?.data
      }
      if (!d) return

      const { supported, unsupportedParts } = checkCodecSupport({
        videoCodec: d.videoCodec,
        audioCodec: d.audioCodec,
      })
      const containerUnsupportedMsg = (playbackMode?.reasons || []).find((r) => r.includes('容器')) || ''
      if (supported && !containerUnsupportedMsg) return

      if (_codecPromptAnimeId === animeKey) return
      _codecPromptAnimeId = animeKey

      const reasonParts = [...unsupportedParts]
      if (containerUnsupportedMsg) {
        reasonParts.push(containerUnsupportedMsg)
      }
      const reasonText = reasonParts.join('、')

      showDdplayGuidance(
        `当前视频/音频编码（${reasonText}）可能无法在浏览器中直接播放。\n建议使用弹弹play客户端播放，以获得最佳画质与流畅度。`,
      )
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
    uninstallNativeVideoGuard()
    uninstallHybridAudio()
    uninstallProxyVideoEventBridge()
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
    // 非 PC 设备（平板/手机）上不展示"通过弹弹play播放"与"分集"控件：
    // - ddplay: 协议仅桌面客户端可用，平板上意义不大
    // - 分集选择器与页面上的选集面板/选集 TAB 重复
    // 同时也能为控件栏腾出宽度，避免溢出裁切。
    const nonPc = !mobile && isNonPcDevice()

    if (!mobile && showDdplayButton.value && !nonPc) {
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
    )

    if (!nonPc) {
      controls.push({
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
      })
    }
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

      // 优先获取播放器必需数据：字幕 + 编码/容器元数据（并行，命中缓存则不再请求）；
      // 弹幕改为异步注入，避免阻塞首帧播放
      const [subtitles, codecsBody] = await Promise.all([
        subtitle.fetchSubtitles(targetVideoId),
        _codecsCache.has(targetVideoId)
          ? Promise.resolve(null)
          : getMediaFileCodecs(targetVideoId)
              .then((r) => r?.data ?? null)
              .catch(() => null),
      ])
      let codecsMeta = _codecsCache.get(targetVideoId) || null
      if (!codecsMeta) {
        const fetchedMeta = codecsBody && (codecsBody.videoCodec || codecsBody.audioCodec || codecsBody.containerFormat)
          ? {
              videoCodec: codecsBody.videoCodec,
              audioCodec: codecsBody.audioCodec,
              containerFormat: codecsBody.containerFormat,
            }
          : null
        if (fetchedMeta) {
          _codecsCache.set(targetVideoId, fetchedMeta)
          codecsMeta = fetchedMeta
        }
      }
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

      // 播放模式决策：native(原 ArtPlayer <video>) / wasm(mediabunny+WASM 代理) / external(引导弹弹play)
      let playbackMode = null
      if (codecsMeta) {
        try {
          playbackMode = await decidePlaybackMode(codecsMeta)
        } catch (error) {
          console.debug('[player] 播放模式判定失败，按原生播放处理:', error)
        }
      }
      if (seq !== playerRecreateSeq) {
        return
      }

      // wasm 代理：动态加载插件与 AC3 WASM 解码器（仅使用方调用其官方 API）
      let wasmProxyFactory = null
      if (playbackMode?.kind === 'wasm') {
        try {
          wasmProxyFactory = (await loadWasmPlaybackModules()).artplayerProxyMediabunny
        } catch (error) {
          console.error('[player] WASM 播放后端加载失败，回退原生播放:', error)
          showAppMessage('WASM 解码后端加载失败，已回退原生播放', 'error')
          // 保留容器原因：原生 <video> 覆盖不了的资源（MKV/TS 等）此时会静默黑屏，
          // 不能让 checkUnsupportedCodec 因"编码全支持"而跳过引导弹弹play
          playbackMode = {
            kind: 'external',
            reasons: codecsMeta?.containerFormat
              ? [`容器格式 ${codecsMeta.containerFormat} 浏览器无法直接播放`]
              : [],
          }
        }
      }
      if (seq !== playerRecreateSeq) {
        return
      }

      destroyPlayerInstance()

      // wasm 代理模式下 ArtPlayer 原生字幕（依赖 textTracks）不可渲染：仅保留 libass(ASS/SSA)
      const proxyActive = Boolean(wasmProxyFactory)
      const effectiveUseNativeSubtitle = useNativeSubtitle && !proxyActive
      const effectiveNativeSubtitleOption = proxyActive ? null : nativeSubtitleOption
      let effectiveLibassTracks = subtitlesForLibass
      let effectiveActiveSubtitle = activeSubtitleTrack
      if (proxyActive && useNativeSubtitle) {
        effectiveActiveSubtitle = subtitlesForLibass[0] || null
        if (!effectiveActiveSubtitle && subtitles.length > 0) {
          console.warn('[subtitle] WASM 代理模式不支持 SRT/VTT 原生字幕渲染:', subtitles.map((s) => s.format))
          showAppMessage('该资源字幕为 SRT/VTT，WASM 播放模式下暂不支持渲染；可选用弹弹play，或将字幕转为 ASS 格式', 'warning')
        }
      }

      const danmakuOptions = danmaku.buildDanmakuOptions([], mobile)
      const subtitlePlugin = effectiveUseNativeSubtitle
        ? null
        : subtitle.buildSubtitlePlugin(effectiveLibassTracks, effectiveActiveSubtitle)
      const subtitleSettings = subtitle.buildSubtitleSettings(subtitles, String(effectiveActiveSubtitle?.id || ''))
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
        ...(effectiveUseNativeSubtitle ? { subtitleOffset: true } : {}),
        ...(effectiveNativeSubtitleOption ? { subtitle: effectiveNativeSubtitleOption } : {}),
        // 原生 <video> 无法覆盖（MKV 容器 / AC3/EAC3 / HEVC 等）时切到 mediabunny(WASM) 播放后端
        ...(wasmProxyFactory
          ? {
              proxy: wasmProxyFactory({
                volume: 0.5,
                autoplay: false,
                poster: '',
                loadTimeout: 60000,
              }),
            }
          : {}),
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

      // 创建后立即按容器宽度应用控件自适应 class，避免首帧闪变
      syncNarrowClasses()

      if (wasmProxyFactory) {
        // 代理模式：把 art 的 video:* 事件桥接为 $video(canvas) 上的合成 DOM 事件（libass 字幕等依赖）
        installProxyVideoEventBridge()
      } else {
        // 原生/hybrid：真实 <video>，运行时失败(error/黑屏无帧)时引导弹弹play（探测不可靠的兜底）。
        // 帧看门狗只对 matroska 容器启用（Chromium 对 MKV+HEVC 有"静默黑屏"缺陷）
        const isMatroska = /matroska|\bmkv\b|mka/i.test(codecsMeta?.containerFormat || '')
        installNativeVideoGuard({ frameWatchdog: isMatroska })
      }

      // 诊断：模式决策结果与元数据（排查播放模式问题时临时开启）
      console.debug('[player] 播放模式决策:', {
        kind: playbackMode?.kind ?? null,
        nativeVideoUncertain: playbackMode?.nativeVideoUncertain ?? false,
        audioCodecNeedsWasm: playbackMode?.audioCodecNeedsWasm ?? false,
        container: codecsMeta?.containerFormat ?? null,
        videoCodec: codecsMeta?.videoCodec ?? null,
        audioCodec: codecsMeta?.audioCodec ?? null,
        secure: typeof window !== 'undefined' ? window.isSecureContext : null,
      })

      if (playbackMode?.kind === 'hybrid') {
        // hybrid：原生画面 + WASM AC3 音频从动引擎（异步，失败不影响画面）
        startHybridAudioEngine(targetVideoId, seq, {
          audioCodecNeedsWasm: Boolean(playbackMode?.audioCodecNeedsWasm),
        })
      }

      // 播放能力降级提示：wasm 代理已接管的不再打扰；无法覆盖的才弹窗引导弹弹play
      checkUnsupportedCodec(targetVideoId, seq, { playbackMode, codecsMeta })

      // wasm 代理已生效的信息提示（同番剧一次）
      if (wasmProxyFactory && playbackMode?.audioCodecNeedsWasm) {
        const animeKey = String(getAnimeId() || '')
        if (_wasmInfoAnimeId !== animeKey) {
          _wasmInfoAnimeId = animeKey
          showAppMessage('检测到 AC3/EAC3 音轨，已启用浏览器内 WASM 解码播放', 'info')
        }
      }

      // 监听播放器事件
      art.value.on('ready', async () => {
        placeEpisodeControlBeforeScreenshot()
        syncMobileClass()
        syncNarrowClasses()
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

      // 播放器容器尺寸变化（如跨布局断点、侧边栏切换）时刷新控件自适应 class
      art.value.on('resize', () => {
        syncNarrowClasses()
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
