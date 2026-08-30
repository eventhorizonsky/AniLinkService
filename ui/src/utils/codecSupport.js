// 浏览器编码能力探测（无服务端转码、直出播放场景）。
// 后端 FFprobe 给出的 codec_name 为小写 FFmpeg 名称（h264/hevc/aac/ac3/flac...），
// 这里映射到浏览器 canPlayType 可识别的 codec 串逐一探测。
// 未知编码一律视为可支持，避免误提示。

function probe(mime) {
  if (typeof document === 'undefined') return ''
  try {
    const v = document.createElement('video')
    return v.canPlayType(mime)
  } catch {
    return ''
  }
}

function probeCodec(container, codec) {
  return probe(`${container}; codecs="${codec}"`) !== ''
}

// 常见视频编码 → 各容器下的 codec 串
const VIDEO_CODEC_MIMES = {
  h264: ['avc1.42E01E', 'avc1.4D401F', 'avc1.64001F'],
  hevc: ['hvc1.1.6.L93.B0', 'hev1.1.6.L93.B0'],
  h265: ['hvc1.1.6.L93.B0', 'hev1.1.6.L93.B0'],
  av1: ['av01.0.08M.08'],
  vp9: ['vp09.00.10.08'],
  vp8: ['vp8'],
  mpeg2video: ['mp4v.6.2'],
  vc1: ['vc-1'],
}

// 常见音频编码 → 各容器下的 codec 串
const AUDIO_CODEC_MIMES = {
  aac: ['mp4a.40.2'],
  mp3: ['mp4a.69'],
  ac3: ['ac-3'],
  eac3: ['ec-3'],
  flac: ['flac'],
  opus: ['opus'],
  vorbis: ['vorbis'],
  dts: ['dtsc', 'dtsh', 'dtsl'],
  truehd: ['mlpa'],
}

function isSupported(codecName, table) {
  if (!codecName) return true
  const key = String(codecName).toLowerCase()
  const codecStrs = table[key]
  if (!codecStrs) return true // 未知编码：不误报

  // mp3 用 audio/mpeg 单独探测
  if (key === 'mp3') {
    return probe('audio/mpeg') !== ''
  }
  if (key === 'flac') {
    return probe('audio/flac') !== ''
  }

  const containers = ['video/mp4', 'video/webm', 'audio/mp4', 'audio/webm']
  return codecStrs.some((c) => containers.some((container) => probeCodec(container, c)))
}

/**
 * 探测浏览器能否解码给定的视频/音频编码。
 * @param {{videoCodec?: string, audioCodec?: string}} param0 媒体文件编码信息
 * @returns {{supported: boolean, unsupportedParts: string[]}}
 */
export function checkCodecSupport({ videoCodec, audioCodec } = {}) {
  const unsupportedParts = []
  if (!isSupported(videoCodec, VIDEO_CODEC_MIMES)) {
    unsupportedParts.push(`视频编码 ${videoCodec}`)
  }
  if (!isSupported(audioCodec, AUDIO_CODEC_MIMES)) {
    unsupportedParts.push(`音频编码 ${audioCodec}`)
  }
  return { supported: unsupportedParts.length === 0, unsupportedParts }
}
