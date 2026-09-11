// Hybrid 播放：原生 <video> 画面 + mediabunny(@mediabunny/ac3 WASM) 音频从动引擎。
//
// 背景：WebCodecs 只在安全上下文（https/localhost）可用，因此当视频轨需要原生 <video>
// 播放（如 http 访问 NAS 的 HEVC、或 Firefox 无 WebCodecs 的 h264），而音轨又是浏览器
// 原生解不了的 AC3/EAC3 时，单一 wasm 代理链路（画面依赖 WebCodecs）会退化成"只有声音"。
// 本模块为这种场景提供第二路音频：用 @mediabunny/ac3（libavcodec WASM，不依赖 WebCodecs）
// 只解音轨并输出到 WebAudio，以 <video> 元素作为唯一主时钟做从动同步。
//
// 说明：
//  - 只做使用方：mediabunny/@mediabunny/ac3 经官方 registerAc3Decoder() 注册；
//  - 音轨引擎复用仓库内 vendored AudioEngine（artplayer-proxy-mediabunny fork，MIT），
//    不在本文件内复制解码/调度逻辑；
//  - 引擎初始化失败/不支持时返回 null，调用方保持原生画面播放（不阻塞、不弹错误）。

let ac3ModulesPromise = null
const loadAc3Modules = () => {
  if (!ac3ModulesPromise) {
    ac3ModulesPromise = (async () => {
      const [mediabunny, ac3, { default: AudioEngine }] = await Promise.all([
        import('mediabunny'),
        import('@mediabunny/ac3'),
        import('../vendor/artplayer-proxy-mediabunny/AudioEngine.js'),
      ])
      ac3.registerAc3Decoder() // 注册后 mediabunny 的 ac3/eac3 音轨可解（WASM）
      return { mediabunny, AudioEngine }
    })()
  }
  return ac3ModulesPromise
}

const clamp01 = (v) => Math.max(0, Math.min(1, Number(v) || 0))

async function resolveAudioDuration(input, tracks) {
  try {
    const fromMeta = await input.getDurationFromMetadata(tracks, { skipLiveWait: true })
    if (fromMeta !== null && Number.isFinite(fromMeta)) {
      return fromMeta
    }
    const computed = await input.computeDuration(tracks, { skipLiveWait: true })
    return computed ?? Number.NaN
  } catch {
    return Number.NaN
  }
}

/**
 * 启动"原生画面 + WASM 音频"组合的从动音频引擎。
 * @param {Object} options
 * @param {string} options.url 与 <video> 相同的媒体流地址（服务端需支持 HTTP Range）
 * @param {HTMLVideoElement} options.video 正在播放画面的 <video> 元素（主时钟）
 * @param {() => number} [options.getVolume] 当前音量 0..1
 * @param {() => boolean} [options.isMuted] 是否静音
 * @param {() => number} [options.getPlaybackRate] 当前倍速
 * @returns {Promise<{destroy: () => void} | null>} 不支持/失败返回 null
 */
export async function createWasmAudioEngine({
  url,
  video,
  getVolume = () => 0.5,
  isMuted = () => false,
  getPlaybackRate = () => 1,
  onVideoError = () => {},
} = {}) {
  if (!url || !video || typeof video.addEventListener !== 'function') {
    return null
  }

  let mediabunny = null
  let AudioEngine = null
  let input = null
  let engine = null
  let disposed = false

  const disposeResources = () => {
    if (disposed) return
    disposed = true
    try {
      engine?.destroy?.()
    } catch {
      /* 忽略 */
    }
    try {
      input?.dispose?.()
    } catch {
      /* 忽略 */
    }
    engine = null
    input = null
  }

  try {
    const mods = await loadAc3Modules()
    mediabunny = mods.mediabunny
    AudioEngine = mods.AudioEngine

    // 只解音轨：demux 后取主音轨（AC3/EAC3 走 WASM，注册后 canDecode()=true）
    input = new mediabunny.Input({
      source: new mediabunny.UrlSource(url),
      formats: mediabunny.ALL_FORMATS,
    })
    const audioTrack = await input.getPrimaryAudioTrack()
    if (!audioTrack || audioTrack.codec === null || !(await audioTrack.canDecode())) {
      disposeResources()
      return null
    }
    const duration = await resolveAudioDuration(input, [audioTrack])

    // 无 ops 事件源：vendored AudioEngine 只在内部 emit canplay/playing，我们不需要
    const noopEmitter = { addEventListener() {}, removeEventListener() {}, emit() {} }
    engine = new AudioEngine(noopEmitter)
    await engine.load({ input, audioTrack, duration })
    engine.setVolume(clamp01(getVolume()), Boolean(isMuted()))
    engine.setPlaybackRate(Math.max(0.1, Number(getPlaybackRate()) || 1))
  } catch (error) {
    console.warn('[hybrid-audio] 引擎初始化失败，保持原生画面:', error)
    disposeResources()
    return null
  }

  // ---- 主从同步：<video> 是唯一时钟 ----
  const on = (type, fn) => video.addEventListener(type, fn)
  const off = (type, fn) => video.removeEventListener(type, fn)

  const seekTo = (time) => {
    if (!engine || disposed) return
    const t = Number(time)
    if (!Number.isFinite(t) || t < 0) return
    try {
      // vendored AudioEngine.seek 为 async：消化拒绝避免 unhandledrejection
      const result = engine.seek(t)
      result?.catch?.(() => {})
    } catch {
      /* 忽略 */
    }
  }

  const alignAndPlay = () => {
    if (disposed || !engine) return
    seekTo(video.currentTime)
    try {
      engine.play().catch(() => {})
    } catch {
      /* 忽略 */
    }
  }

  const pauseAudio = () => {
    if (disposed || !engine) return
    try {
      engine.pause()
    } catch {
      /* 忽略 */
    }
  }

  // play 事件在用户手势内同步派发：先在此 resume AudioContext 拿到播放许可并预对齐，
  // 但真正开始排程等到 playing（此时画面已真正起播，避免"视频根本放不了"时先出声）
  const grantAudioOnPlay = () => {
    if (disposed || !engine) return
    seekTo(video.currentTime)
    try {
      const ctx = engine.audioContext
      if (ctx && ctx.state === 'suspended') {
        ctx.resume().catch(() => {})
      }
    } catch {
      /* 忽略 */
    }
  }

  const handlers = {
    play: grantAudioOnPlay,
    playing: alignAndPlay,
    pause: pauseAudio,
    waiting: pauseAudio,
    stalled: pauseAudio,
    seeking: pauseAudio,
    ended: pauseAudio,
    seeked: () => seekTo(video.currentTime),
  }
  Object.keys(handlers).forEach((type) => on(type, handlers[type]))

  // 引擎异步加载期间用户可能已抢先开始播放：挂载时若已在播放则补一次对齐
  if (!video.paused && !video.ended) {
    alignAndPlay()
  }

  // 播放中周期校正：漂移 > 阈值才重读对齐；顺带跟随音量/静音/倍速
  let tickTimer = 0
  let lastAligned = -1
  const tick = () => {
    if (disposed) return
    try {
      if (engine && !video.paused && !video.ended) {
        engine.setVolume(clamp01(getVolume()), Boolean(isMuted()))
        engine.setPlaybackRate(Math.max(0.1, Number(getPlaybackRate()) || 1))

        const master = Number(video.currentTime)
        if (Number.isFinite(master)) {
          // 听感时间 ≈ 内部时钟 − WebAudio 输出延迟，避免恒定滞后被误判为漂移
          const ctx = engine.audioContext
          const latency = ctx ? ctx.outputLatency || ctx.baseLatency || 0 : 0
          const heard = Number.isFinite(engine.currentTime) ? engine.currentTime - latency : Number.NaN
          const drift = master - heard
          if (!Number.isNaN(drift) && Math.abs(drift) > 0.35 && Math.abs(master - lastAligned) > 0.5) {
            lastAligned = master
            seekTo(master)
          }
        }
      }
    } catch {
      /* 忽略单次校正异常 */
    }
    tickTimer = setTimeout(tick, 500)
  }
  tickTimer = setTimeout(tick, 500)

  // 原生 <video> 真正解不了（如浏览器完全没有 HEVC 解码能力）时触发
  // MEDIA_ERR_SRC_NOT_SUPPORTED(4)：停掉音频引擎并通知上层，避免"有声音黑屏"挂着。
  // 网络(2)/解码(3)错误不在此列，交给播放器自身错误处理。
  const errorListener = () => {
    if (disposed) return
    if (video.error?.code !== 4) return
    if (tickTimer) {
      clearTimeout(tickTimer)
      tickTimer = 0
    }
    Object.keys(handlers).forEach((type) => off(type, handlers[type]))
    disposeResources()
    try {
      onVideoError()
    } catch {
      /* 忽略 */
    }
  }
  on('error', errorListener)

  return {
    destroy() {
      if (disposed) return
      if (tickTimer) {
        clearTimeout(tickTimer)
        tickTimer = 0
      }
      Object.keys(handlers).forEach((type) => off(type, handlers[type]))
      off('error', errorListener)
      disposeResources()
    },
  }
}
