// 播放模式决策：原生 <video> 直出 / mediabunny(WebCodecs + WASM) 代理 / hybrid(原生画面
// + WASM 音轨) / 引导外部播放器。
//
// 背景：服务端不转码、仅提供文件流。浏览器原生 <video> 能放"容器 + 编码"都可解的媒体；
// MKV(matroska) 的 demuxer 与 WebM 同族，Chromium 系浏览器常能直接播放其中的常见编码
// （h264/hevc+aac/ac3 视平台而定），因此按"原生尝试 + 运行时 error 兜底"处理，不做一刀切。
// 原生解不了的编码（AC3/EAC3/DTS/TrueHD…）由 mediabunny 补充：WebCodecs 可用的安全上下文
// 走整段 wasm 代理；WebCodecs 不可用（http 非安全上下文/Firefox）但原生能放画面时走
// hybrid（原生画面 + @mediabunny/ac3 WASM 音轨从动）。彻底覆盖不了的走"引导外部播放器"。
//
// 说明：
//  - 本项目只做使用方：不修改 mediabunny/@mediabunny/ac3 源码，
//    AC3/EAC3 解码通过其官方扩展 registerAc3Decoder() 接入。
//  - containerFormat 来自后端 FFprobe 的 format_name（如 matroska,webm），需先归一化。

import { checkCodecSupport } from './codecSupport'

const norm = (v) => String(v || '').trim().toLowerCase()

// ffprobe format_name → 归类（native=尝试交给原生 <video>；mb=mediabunny 可解复用）
function classifyContainer(raw) {
  const s = norm(raw)
  if (!s) return { key: '', native: true, mb: true } // 未知容器按原生尝试（保持旧行为）

  // matroska/webm demuxer 同族：Chromium 常可原生播放 MKV 内常见编码（如 h264/hevc+aac/ac3，
  // 视平台与解码器而定），标记 native 后由播放层"尝试 + error 兜底"，避免把能放的画面降级
  if (s.includes('matroska') || s === 'mkv' || s === 'mka') return { key: 'mkv', native: true, mb: true }
  if (s.includes('webm')) return { key: 'webm', native: true, mb: true }
  if (s.includes('mpegts')) return { key: 'ts', native: false, mb: true }
  // Ogg(Theora/Vorbis)：Chrome/Firefox 原生 <video> 可直接播放，保持原生直出（避免误路由到
  // mediabunny——其视频软解不支持 theora，会把本可正常播放的内容退化成"仅音频"）；
  // Safari 等播不了的场景维持旧行为（原生尝试 + 后续提示）。
  if (s.includes('ogg') || s === 'ogv') return { key: 'ogg', native: true, mb: true }
  if (
    s.includes('mp4') || s.includes('quicktime') || s.includes('m4a')
    || s.includes('3gp') || s.includes('mov') || s.includes('mj2') || s === 'm4v'
  ) {
    return { key: 'mp4', native: true, mb: true }
  }
  if (s === 'flac' || s.includes('flac')) return { key: 'flac', native: true, mb: true }
  if (s === 'mp3' || s.includes('mp3')) return { key: 'mp3', native: true, mb: true }
  if (s === 'wav' || s === 'wave' || s.includes('wav')) return { key: 'wav', native: true, mb: true }
  if (s === 'aac' || s === 'adts' || s.includes('aac')) return { key: 'aac', native: true, mb: true }
  if (s.includes('avi')) return { key: 'avi', native: false, mb: false }
  // 其他（flv/rm/wmv/…）：浏览器与 mediabunny 都不能覆盖
  return { key: s, native: false, mb: false }
}

// @mediabunny/ac3（libavcodec WASM）可解的音频编码
export const WASM_AUDIO_CODECS = new Set(['ac3', 'eac3'])

// ffprobe codec_name → WebCodecs 视频配置串（用于探测浏览器能否解视频轨）
const FF_TO_WEBCODECS_VIDEO = {
  h264: ['avc1.42E01E', 'avc1.4D401F', 'avc1.64001F'],
  hevc: ['hvc1.1.6.L93.B0', 'hev1.1.6.L93.B0'],
  h265: ['hvc1.1.6.L93.B0', 'hev1.1.6.L93.B0'],
  av1: ['av01.0.08M.08'],
  vp9: ['vp09.00.10.08'],
  vp8: ['vp8'],
}
// 已知 WebCodecs 不能解的视频编码（mediabunny 也没有自定义视频解码器）
const WEBCODECS_UNSUPPORTED_VIDEO = new Set(['mpeg2video', 'mpeg4', 'vc1', 'wmv3', 'msmpeg4', 'rv30', 'rv40'])

const hasWebCodecsVideo = () => typeof window !== 'undefined' && 'VideoDecoder' in window

/**
 * 探测当前浏览器 WebCodecs 能否解码该视频轨（mediabunny 代理路径的视频解码前提）。
 * 无视频轨返回 true；WebCodecs 不可用时（如 Firefox）不可解。
 */
export async function probeVideoDecodableByWebCodecs(ffCodec) {
  const codec = norm(ffCodec)
  if (!codec) return true
  if (!hasWebCodecsVideo()) return false
  if (WEBCODECS_UNSUPPORTED_VIDEO.has(codec)) return false
  const codecStrs = FF_TO_WEBCODECS_VIDEO[codec]
  if (!codecStrs) return false // 未知视频编码：不承诺可解
  try {
    const results = await Promise.all(
      codecStrs.map((codecStr) =>
        VideoDecoder.isConfigSupported({ codec: codecStr, codedWidth: 1920, codedHeight: 1080 })
          .then((r) => r.supported)
          .catch(() => false),
      ),
    )
    return results.some(Boolean)
  } catch {
    return false
  }
}

// 浏览器原生/WebCodecs 音频可解的常见编码（mediabunny 代理路径靠 WebCodecs 解）
const AUDIO_WEBCODECS_CODECS = new Set(['aac', 'mp3', 'flac', 'opus', 'vorbis'])
const hasWebCodecsAudio = () => typeof window !== 'undefined' && 'AudioDecoder' in window

/**
 * 判断音频轨能否在 mediabunny 代理路径解码：
 *  - ac3/eac3 → WASM（@mediabunny/ac3）
 *  - 常见音频 → WebCodecs（需要 AudioDecoder）
 *  - dts/truehd 等当前没有解码器 → 不可解
 */
export async function probeAudioDecodableInProxy(ffCodec) {
  const codec = norm(ffCodec)
  if (!codec) return true
  if (WASM_AUDIO_CODECS.has(codec)) return true
  if (codec.startsWith('pcm_') || codec === 'pcm' || codec === 'fltp') return true
  if (AUDIO_WEBCODECS_CODECS.has(codec)) return hasWebCodecsAudio()
  return false // dts/truehd/未知音频：当前无法在浏览器内解
}

/**
 * 决策入口。返回：
 * @returns {{
 *   kind: 'native' | 'wasm' | 'hybrid' | 'external',
 *   container: {key: string, native: boolean, mb: boolean},
 *   videoPlayable: boolean,   // wasm 路径下视频轨能否（WebCodecs）解码
 *   audioPlayable: boolean,   // 音轨能否在浏览器内解码（原生或 WASM/WebAudio）
 *   audioCodecNeedsWasm: boolean,
 *   nativeVideoUncertain: boolean, // hybrid 时原生探测判否（如 Edge 无 HEVC），需先弹引导
 *   reasons: string[],        // 人类可读的判定原因（供提示文案）
 * }}
 */
export async function decidePlaybackMode({ containerFormat, videoCodec, audioCodec } = {}) {
  const container = classifyContainer(containerFormat)
  const reasons = []

  const { supported } = checkCodecSupport({ videoCodec, audioCodec })

  // 1) 原生直出：容器浏览器可解 且 编码浏览器可解（未知容器沿用旧逻辑，按原生尝试）
  if (container.native && supported) {
    return { kind: 'native', container, videoPlayable: true, audioPlayable: true, audioCodecNeedsWasm: false, reasons }
  }

  // 2) 尝试 mediabunny(WASM) 代理
  if (!container.mb) {
    reasons.push(`容器格式 ${containerFormat || '未知'} 浏览器无法直接播放`)
    return { kind: 'external', container, videoPlayable: false, audioPlayable: false, audioCodecNeedsWasm: false, reasons }
  }

  const video = norm(videoCodec)
  const audio = norm(audioCodec)
  // 画面能力判定优先于 AC3/降级逻辑：
  //  - videoNativeSupported：原生 <video> 对该视频编码的支持（canPlayType，能如实反映
  //    Edge 等无 HEVC 能力的环境）；注意这里只看视频轨，容器按原生容器(mp4/mov/mkv/webm…)尝试；
  //  - videoOk：mediabunny 路径（WebCodecs）能否解视频轨。
  const videoNativeSupported = container.native && checkCodecSupport({ videoCodec: video }).supported
  const audioOk = await probeAudioDecodableInProxy(audio)
  const videoOk = await probeVideoDecodableByWebCodecs(video)
  const audioCodecNeedsWasm = WASM_AUDIO_CODECS.has(audio)

  if (!audioOk) {
    reasons.push(`音频编码 ${audioCodec} 当前浏览器无法解码`)
    return { kind: 'external', container, videoPlayable: videoOk, audioPlayable: false, audioCodecNeedsWasm, reasons }
  }

  // 画面两条路都判否时的处理分两类：
  //  - 容器可原生尝试（mp4/mov/mkv…）：hybrid 仍要"真的尝试"原生画面——canPlayType 对
  //    HEVC 存在误报（某些 Chrome 实际可解但返回 ''），不能因探测判否就放弃播放；
  //    探测判否（如 Edge 无 HEVC）通过 nativeVideoUncertain 交给播放层弹一次引导弹窗。
  //  - 非原生容器（TS 等）且 WebCodecs 不可用：浏览器内确实没有画面路径 → external 引导。
  if (!videoOk) {
    if (container.native) {
      if (!videoNativeSupported) {
        reasons.push(`视频编码 ${videoCodec} 可能无法在当前浏览器直接播放`)
      }
      if (audioCodecNeedsWasm) {
        reasons.push(`音频 ${audioCodec} 使用 WASM(mediabunny) 解码，画面由原生播放`)
      }
      return {
        kind: 'hybrid',
        container,
        videoPlayable: false,
        audioPlayable: audioOk,
        audioCodecNeedsWasm,
        nativeVideoUncertain: !videoNativeSupported,
        reasons,
      }
    }
    reasons.push(`视频编码 ${videoCodec} 当前浏览器无法解码`)
    return { kind: 'external', container, videoPlayable: false, audioPlayable: audioOk, audioCodecNeedsWasm, reasons }
  }

  if (audioCodecNeedsWasm) {
    reasons.push(`音频 ${audioCodec} 使用 WASM(mediabunny+libavcodec) 解码`)
  }
  if (reasons.length === 0) {
    reasons.push('容器或编码超出原生能力，改用 mediabunny 播放')
  }

  return { kind: 'wasm', container, videoPlayable: true, audioPlayable: true, audioCodecNeedsWasm, reasons }
}
