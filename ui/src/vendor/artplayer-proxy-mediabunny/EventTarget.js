/*!
 * EventTarget.js —— artplayer-proxy-mediabunny（vendored fork, MIT）
 * 上游: https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-proxy-mediabunny
 * 上游许可: MIT, (c) 2017-2026 Harvey Zhao (zhw2590582)
 * AniLinkService 本地改动（详见本目录 README.md）:
 *   - 不再内联 mediabunny：mediabunny 改为运行时 import，复用 ui 顶层统一依赖实例，
 *     使 @mediabunny/ac3 的 registerAc3Decoder() 能注册进同一解码器注册表（AC3/EAC3 可解）；
 *   - 移除 m3u8/HLS 专属控制 UI（m3u8.js 未随附）。
 */
/**
 * Event Target Implementation
 * Simple event system for video events
 */
export default class EventTarget {
  constructor() {
    this.listeners = new Map()
  }

  addEventListener(type, fn) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type).push(fn)
  }

  removeEventListener(type, fn) {
    const list = this.listeners.get(type)
    if (!list)
      return

    const index = list.indexOf(fn)
    if (index >= 0) {
      list.splice(index, 1)
    }
  }

  emit(type, detail) {
    const evt = new Event(type)
    evt.detail = detail

    const list = this.listeners.get(type)
    if (list) {
      list.forEach(fn => fn(evt))
    }
  }
}
