/*!
 * index.js —— artplayer-proxy-mediabunny（vendored fork, MIT）
 * 上游: https://github.com/zhw2590582/ArtPlayer/tree/master/packages/artplayer-proxy-mediabunny
 * 上游许可: MIT, (c) 2017-2026 Harvey Zhao (zhw2590582)
 * AniLinkService 本地改动（详见本目录 README.md）:
 *   - 不再内联 mediabunny：mediabunny 改为运行时 import，复用 ui 顶层统一依赖实例，
 *     使 @mediabunny/ac3 的 registerAc3Decoder() 能注册进同一解码器注册表（AC3/EAC3 可解）；
 *   - 移除 m3u8/HLS 专属控制 UI（m3u8.js 未随附）。
 */
/**
 * ArtPlayer MediaBunny Proxy
 * Main entry point
 */
import VideoShim from './VideoShim.js'

export default function artplayerProxyMediabunny(option = {}) {
  return (art) => {
    const { constructor } = art
    const { createElement } = constructor.utils

    // Create canvas element
    const canvas = createElement('canvas')
    const ctx = canvas.getContext('2d')

    // Create video shim
    const shim = new VideoShim({
      art,
      canvas,
      ctx,
      option,
    })
    art.mediabunny = shim

    // Proxy canvas methods to shim
    const originalCanvasMethods = {}
    for (const prop in canvas) {
      if (typeof canvas[prop] === 'function') {
        originalCanvasMethods[prop] = canvas[prop].bind(canvas)
      }
    }

    // Get all properties from shim instance and prototype
    const propertyNames = new Set([
      ...Object.getOwnPropertyNames(shim),
      ...Object.getOwnPropertyNames(Object.getPrototypeOf(shim)),
    ])

    // Add shim properties to canvas
    for (const prop of propertyNames) {
      if (prop === 'constructor')
        continue
      if (!(prop in canvas)) {
        Object.defineProperty(canvas, prop, {
          get() {
            const value = shim[prop]
            return typeof value === 'function' ? value.bind(shim) : value
          },
          set(v) {
            shim[prop] = v
          },
          configurable: true,
          enumerable: true,
        })
      }
    }

    // Restore original canvas methods
    for (const prop in originalCanvasMethods) {
      canvas[prop] = (...args) => originalCanvasMethods[prop](...args)
    }

    // Handle resize
    function resize() {
      const player = art.template?.$player
      if (!player || art.option.autoSize)
        return

      Object.assign(canvas.style, {
        width: '100%',
        height: '100%',
        objectFit: 'contain',
      })
    }

    art.on('resize', resize)
    art.on('video:loadedmetadata', resize)

    // Cleanup on destroy
    art.on('destroy', () => {
      delete art.mediabunny
      shim.destroy()
    })

    return canvas
  }
}
