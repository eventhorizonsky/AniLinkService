// Web 播放器能力检测与播放模式决策。
//
// 目标：浏览器原生 <video> 只支持有限的容器与编码。番剧资源常见 MKV+HEVC(10bit)+FLAC/AC3/DTS。
// 这里依据后端返回的编码/容器事实 + 浏览器 canPlayType/MSE 能力，决定：
//   - direct    直出原始文件流
//   - remux     秒转封装 HLS（编码可解、仅容器不支持，服务端 -c copy）
//   - transcode 转码 HLS（编码不可解，服务端转 H.264/AAC）
//   - null      完全不可播（交由外部播放器兜底，如弹弹play）
//
// 容器判定直接用浏览器 canPlayType 探测真实 MIME（Chrome 117+ 原生支持 MKV），
// 而不是硬编码白名单，避免误伤本可直出的文件。

import Hls from 'hls.js'

// 编码名 → canPlayType 可识别的 codec 串
const VIDEO_CODEC_STRINGS = {
  h264: 'avc1.640028',
  hevc: 'hvc1.1.6.L93.B0',
  h265: 'hvc1.1.6.L93.B0',
  av1: 'av01.0.04M.08',
  vp9: 'vp09.00.10.08',
}

const AUDIO_CODEC_STRINGS = {
  aac: 'mp4a.40.2',
  mp3: 'mp4a.40.34',
  ac3: 'ac-3',
  eac3: 'ec-3',
  dts: 'dtsc',
  opus: 'opus',
  vorbis: 'vorbis',
  flac: 'flac',
}

// ffprobe 的 format_name 片段 → MIME 容器
const CONTAINER_MIME_MAP = {
  mp4: 'video/mp4',
  m4v: 'video/mp4',
  m4a: 'video/mp4',
  mov: 'video/mp4',
  '3gp': 'video/mp4',
  webm: 'video/webm',
  matroska: 'video/x-matroska',
}

let _probeVideo = null

function probeVideo() {
  if (!_probeVideo && typeof document !== 'undefined') {
    _probeVideo = document.createElement('video')
  }
  return _probeVideo
}

/** 探测指定 MIME 是否可播，返回 '' | 'maybe' | 'probably' */
export function canPlayMime(mime) {
  const video = probeVideo()
  if (!video || typeof video.canPlayType !== 'function') {
    return ''
  }
  try {
    return video.canPlayType(mime)
  } catch (e) {
    return ''
  }
}

const normalize = (v) => (v == null ? '' : String(v).trim().toLowerCase())

/**
 * 将 ffprobe 的容器串映射为浏览器 MIME 容器。
 * 支持逗号分隔列表（如 "mov,mp4,m4a,3gp,3g2,mj2" / "matroska,webm"），
 * 任一中一个片段命中即可。
 */
export function mapContainerMime(container) {
  if (!container) return null
  for (const token of String(container).split(',')) {
    const mime = CONTAINER_MIME_MAP[token.trim().toLowerCase()]
    if (mime) return mime
  }
  return null
}

/** 组装视频+音频的 codec 串列表 */
function buildCodecStrings(playInfo) {
  const codecs = []
  const video = VIDEO_CODEC_STRINGS[normalize(playInfo?.videoCodec)]
  const audio = AUDIO_CODEC_STRINGS[normalize(playInfo?.audioCodec)]
  if (video) codecs.push(video)
  if (audio) codecs.push(audio)
  return codecs
}

/**
 * 带变体探测：HEVC 需分别尝试 hvc1/hev1（Safari 认 hvc1，Chrome 部分认 hev1）。
 */
function probeContainerMime(mimeContainer, codecs, videoCodec) {
  const base = `${mimeContainer}; codecs="`
  const normalizedVideo = normalize(videoCodec)
  if (normalizedVideo === 'hevc' || normalizedVideo === 'h265') {
    const rest = codecs.filter((c) => c !== 'hvc1.1.6.L93.B0' && c !== 'hev1.1.6.L93.B0')
    for (const v of ['hvc1.1.6.L93.B0', 'hev1.1.6.L93.B0']) {
      if (canPlayMime(base + [v, ...rest].join(', ') + '"') !== '') return true
    }
    return false
  }
  if (codecs.length === 0) return false
  return canPlayMime(base + codecs.join(', ') + '"') !== ''
}

/**
 * 判断浏览器能否解码给定的视频编码（与容器无关）。
 * 用于 remux 决策：编码本身能不能解（hls.js/MSE 的 TS 流同样依赖该解码能力）。
 */
export function canDecodeVideo(videoCodec, colorDepth) {
  const codec = normalize(videoCodec)
  if (!codec) return false
  if (codec === 'h264' && /10[\s-]?bit/i.test(colorDepth || '')) return false
  const codecStr = VIDEO_CODEC_STRINGS[codec]
  if (!codecStr) return false
  if (codec === 'hevc' || codec === 'h265') {
    return (
      canPlayMime(`video/mp4; codecs="${VIDEO_CODEC_STRINGS.hevc}"`) !== '' ||
      canPlayMime(`video/mp4; codecs="hev1.1.6.L93.B0"`) !== ''
    )
  }
  return canPlayMime(`video/mp4; codecs="${codecStr}"`) !== ''
}

/** 判断浏览器能否解码给定的音频编码（与容器无关） */
export function canDecodeAudio(audioCodec) {
  const codec = normalize(audioCodec)
  if (!codec) return false
  const codecStr = AUDIO_CODEC_STRINGS[codec]
  if (!codecStr) return false
  if (codec === 'opus') {
    return canPlayMime('audio/webm; codecs="opus"') !== ''
  }
  return canPlayMime(`video/mp4; codecs="${codecStr}"`) !== ''
}

/**
 * 原始文件能否被浏览器原生直接播放。
 * 用真实容器 MIME + 编码串探测浏览器，容器命中即直接播放（如 Chrome 117+ 的 MKV）。
 */
export function canPlayDirect(playInfo) {
  const videoCodec = normalize(playInfo?.videoCodec)
  if (!videoCodec) return false
  // 10-bit H.264 浏览器普遍不支持
  if (videoCodec === 'h264' && /10[\s-]?bit/i.test(playInfo?.colorDepth || '')) return false
  const mimeContainer = mapContainerMime(playInfo?.containerFormat)
  if (!mimeContainer) return false
  return probeContainerMime(mimeContainer, buildCodecStrings(playInfo), videoCodec)
}

/**
 * 浏览器是否具备 HLS 播放能力（MSE + hls.js，或 iOS 原生 HLS）。
 */
export function supportsHlsPlayback() {
  try {
    if (Hls && typeof Hls.isSupported === 'function' && Hls.isSupported()) {
      return true
    }
  } catch (e) {
    // ignore
  }
  // iOS Safari / 部分原生 HLS 设备
  return canPlayMime('application/vnd.apple.mpegurl') !== ''
}

/**
 * 根据 play-info 决策最终播放方式。
 *
 * @returns {{mode: 'direct'|'remux'|'mixed'|'transcode'|null, url: string, isHls: boolean}}
 */
export function resolvePlayMode(playInfo) {
  const info = playInfo || {}
  const streamUrl = info.streamUrl || ''
  const remuxUrl = info.remuxUrl || ''
  const mixedUrl = info.mixedUrl || ''
  const transcodeUrl = info.transcodeUrl || ''

  // 1. 原始文件直出（最省资源）
  if (streamUrl && canPlayDirect(info)) {
    return { mode: 'direct', url: streamUrl, isHls: false }
  }

  const videoOk = canDecodeVideo(info.videoCodec, info.colorDepth)
  const audioOk = canDecodeAudio(info.audioCodec)
  const hlsOk = supportsHlsPlayback()

  // 2. 秒转封装：视频+音频都可解、且都能进 TS → 服务端 -c copy，零损耗
  if (remuxUrl && videoOk && audioOk && hlsOk) {
    return { mode: 'remux', url: remuxUrl, isHls: true }
  }

  // 3. 混合：视频可解且能直通 TS，仅音频不支持 → 视频原样封装（-c:v copy）+ 音频转 AAC，避免视频重编码
  if (mixedUrl && videoOk && hlsOk) {
    return { mode: 'mixed', url: mixedUrl, isHls: true }
  }

  // 4. 全转码：视频编码不可解或进不了 TS → 服务端转 H.264/AAC
  if (transcodeUrl && hlsOk) {
    return { mode: 'transcode', url: transcodeUrl, isHls: true }
  }

  // 5. 完全不可播（外部播放器兜底）
  return { mode: null, url: null, isHls: false }
}
