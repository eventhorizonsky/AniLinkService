/*!
 * AudioEngine.js —— artplayer-proxy-mediabunny（vendored fork, MIT）
 * 上游: https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-proxy-mediabunny
 * 上游许可: MIT, (c) 2017-2026 Harvey Zhao (zhw2590582)
 * AniLinkService 本地改动（详见本目录 README.md）:
 *   - 不再内联 mediabunny：mediabunny 改为运行时 import，复用 ui 顶层统一依赖实例，
 *     使 @mediabunny/ac3 的 registerAc3Decoder() 能注册进同一解码器注册表（AC3/EAC3 可解）；
 *   - 移除 m3u8/HLS 专属控制 UI（m3u8.js 未随附）；
 *   - 音量曲线与原生 <video>.volume 对齐（上游 updateGain() 用 v * v，会让 WASM 音轨在
 *     同一滑块位置比原生音轨低 20*log10(v) dB，50% 处即 -6 dB；详见 updateGain() 注释）。
 */
/**
 * Audio Engine for MediaBunny
 * Handles audio playback using Web Audio API
 */
import {
  AudioBufferSink,
} from 'mediabunny'

export default class AudioEngine {
  constructor(events) {
    this.events = events

    // MediaBunny instances
    this.input = null
    this.audioSink = null
    this.audioIterator = null

    // Web Audio API
    this.audioContext = null
    this.gainNode = null

    // Playback state
    this.audioContextStartTime = 0
    this.playbackTimeAtStart = 0
    this.latestScheduledEndTime = 0
    this.duration = Number.NaN
    this.paused = true

    // Audio settings
    this.volume = 0.7
    this.muted = false
    this.playbackRate = 1

    // Async control
    this.asyncId = 0
    this.queuedNodes = new Set()
  }

  get currentTime() {
    if (this.paused)
      return this.playbackTimeAtStart

    return (
      (this.audioContext.currentTime - this.audioContextStartTime) * this.playbackRate
      + this.playbackTimeAtStart
    )
  }

  ensureAudioContext(sampleRate) {
    if (this.audioContext)
      return

    const AudioContext = window.AudioContext || window.webkitAudioContext

    try {
      this.audioContext = new AudioContext({ sampleRate })
    }
    catch {
      this.audioContext = new AudioContext()
    }

    this.gainNode = this.audioContext.createGain()
    this.gainNode.connect(this.audioContext.destination)
    this.updateGain()
  }

  updateGain() {
    if (!this.gainNode)
      return

    const v = this.muted ? 0 : this.volume

    // 本地改动：增益取线性音量，与原生 <video>.volume 的语义一致。
    // 上游这里是 `gain.value = v * v`（感知型曲线），而播放器音量滑块驱动的是
    // 原生的 video.volume（线性），于是同一条音轨只要走 WASM（AC3/EAC3/DTS 等）就会
    // 比原生播放低 20*log10(v) dB——默认音量 0.5 时正好低 6 dB，听感即"AC3 音量偏小"。
    // 这里顺带用短时间常数做斜坡，避免拖动音量条/静音切换时的阶跃爆音。
    const gain = this.gainNode.gain
    const now = this.audioContext.currentTime
    gain.cancelScheduledValues(now)
    if (v === 0) {
      gain.setValueAtTime(0, now) // 静音要立即生效，不做淡出尾巴
    }
    else {
      gain.setTargetAtTime(v, now, 0.02)
    }
  }

  stopQueuedNodes() {
    this.queuedNodes.forEach(node => node.stop())
    this.queuedNodes.clear()
  }

  async stopIterator() {
    await this.audioIterator?.return()
    this.audioIterator = null
  }

  handleNoAudioTrack() {
    this.audioSink = null
    this.ensureAudioContext()
  }

  async load(media, onMetadata) {
    ++this.asyncId

    await this.stopIterator()
    this.stopQueuedNodes()

    this.paused = true
    this.playbackTimeAtStart = 0
    this.audioContextStartTime = 0

    const { input, audioTrack, duration } = media
    this.input = input
    this.duration = duration

    if (!audioTrack) {
      this.handleNoAudioTrack()
      onMetadata?.()
      return
    }

    if (audioTrack.codec === null || !(await audioTrack.canDecode())) {
      this.audioSink = null
      this.ensureAudioContext()
      onMetadata?.()
      return
    }

    this.ensureAudioContext(audioTrack.sampleRate)
    this.audioSink = new AudioBufferSink(audioTrack)

    onMetadata?.()
  }

  async runIterator(localId) {
    if (!this.audioSink)
      return

    await this.stopIterator()
    this.audioIterator = this.audioSink.buffers(this.currentTime)

    // Batch size: read multiple audio buffers per iteration to reduce
    // IPC round-trip overhead. 16 buffers ≈ 340ms of audio.
    const BATCH_SIZE = 16

    while (true) {
      if (localId !== this.asyncId || this.paused)
        return

      const batch = []
      let batchDone = false
      try {
        for (let i = 0; i < BATCH_SIZE; i++) {
          const result = await this.audioIterator.next()
          if (result.done) {
            batchDone = true
            break
          }
          batch.push(result.value)
        }
      }
      catch (e) {
        console.error('Audio iterator error:', e)
        batchDone = true
      }

      if (localId !== this.asyncId || this.paused)
        return

      // Resume if was suspended
      if (batch.length > 0 && this.audioContext.state === 'suspended') {
        await this.audioContext.resume()
        this.events.emit('canplay')
        this.events.emit('playing')
      }

      // Schedule all buffers in the batch
      for (const { buffer, timestamp } of batch) {
        const node = this.audioContext.createBufferSource()
        node.buffer = buffer
        node.connect(this.gainNode)
        node.playbackRate.value = this.playbackRate

        const startAt
          = this.audioContextStartTime
            + (timestamp - this.playbackTimeAtStart) / this.playbackRate

        const duration = buffer.duration
        const endAt = startAt + duration / this.playbackRate

        const endMediaTime = (endAt - this.audioContextStartTime) * this.playbackRate + this.playbackTimeAtStart
        if (endMediaTime > this.latestScheduledEndTime) {
          this.latestScheduledEndTime = endMediaTime
        }

        if (startAt >= this.audioContext.currentTime) {
          node.start(startAt)
        }
        else {
          node.start(
            this.audioContext.currentTime,
            (this.audioContext.currentTime - startAt) * this.playbackRate,
          )
        }

        this.queuedNodes.add(node)
        node.onended = () => this.queuedNodes.delete(node)
      }

      if (batchDone)
        break

      // Yield main thread after each batch so video rAF callbacks can run.
      // Without this, the audio pump's tight loop starves the render loop.
      await new Promise(resolve => setTimeout(resolve, 0))

      // Backpressure: if we've scheduled too far ahead, wait for playback
      // to catch up before decoding more. This prevents the audio pump from
      // consuming all WebCodecs resources and starving the video decoder.
      const BUFFER_AHEAD = 1 // seconds
      while (this.latestScheduledEndTime - this.currentTime > BUFFER_AHEAD) {
        if (localId !== this.asyncId || this.paused)
          return
        await new Promise(resolve => setTimeout(resolve, 50))
      }
    }
  }

  async play() {
    if (!this.paused)
      return

    if (!this.audioContext) {
      this.ensureAudioContext()
    }

    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume()
    }

    this.audioContextStartTime = this.audioContext.currentTime
    this.latestScheduledEndTime = this.playbackTimeAtStart
    this.paused = false

    const id = ++this.asyncId
    this.runIterator(id)
  }

  pause() {
    if (this.paused)
      return

    this.playbackTimeAtStart = this.currentTime
    this.paused = true

    this.stopIterator()
    this.stopQueuedNodes()
  }

  async seek(time) {
    this.playbackTimeAtStart = Math.max(0, time)
    this.audioContextStartTime = this.audioContext.currentTime
    this.latestScheduledEndTime = this.playbackTimeAtStart

    const id = ++this.asyncId
    if (!this.paused) {
      this.runIterator(id)
    }
  }

  setVolume(volume, muted) {
    this.volume = volume
    this.muted = muted
    this.updateGain()
  }

  setPlaybackRate(rate) {
    if (rate === this.playbackRate)
      return

    if (!this.paused) {
      this.playbackTimeAtStart = this.currentTime
      this.audioContextStartTime = this.audioContext.currentTime
    }

    this.playbackRate = rate

    if (!this.paused) {
      const id = ++this.asyncId
      this.runIterator(id)
    }
  }

  destroy() {
    this.asyncId++
    this.pause()
    this.audioContext?.close()
    this.audioContext = null
    this.input = null
    this.audioSink = null
  }
}
