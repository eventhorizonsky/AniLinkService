// 字幕相关逻辑：字幕获取、libass/native 轨道切换、字幕偏移调整与持久化。
// 此前集中在 Player.vue 的"上帝组件"中，统一收敛到这里。
import { ref } from 'vue'
import SubtitlesOctopus from 'libass-wasm'
import { API_BASE, isSuperAdmin } from '../utils/constants'
import { getSubtitles, setSubtitleOffset } from '../api/subtitle'

const LIBASS_SUPPORTED_SUBTITLE_FORMATS = new Set(['ass', 'ssa'])
const NATIVE_ARTPLAYER_SUBTITLE_FORMATS = new Set(['srt', 'vtt'])

// 资源为部署相对路径（相对站点根目录），拼接 BASE_URL 以兼容子路径部署。
const withBase = (path) => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.BASE_URL) {
    return import.meta.env.BASE_URL.replace(/\/+$/, '') + path
  }
  return path
}

const SUBTITLES_OCTOPUS_WORK_JS_PATH = withBase('/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.js')
const SUBTITLES_OCTOPUS_WORK_WASM_PATH = withBase('/js/JavascriptSubtitlesOctopus/subtitles-octopus-worker.wasm')
const SUBTITLES_OCTOPUS_FONTS = [withBase('/static/SourceHanSansCN-Bold.woff2')]

/**
 * 字幕轨道逻辑。
 * @param {Object} options
 * @param {import('vue').ShallowRef} options.art 播放器实例引用
 * @param {import('vue').Ref} options.userInfo 用户信息
 * @param {import('vue-router').Router} options.router
 * @param {import('vue-router').RouteLocationNormalizedLoaded} options.route
 * @param {() => boolean} options.isMobileViewport
 * @param {() => string} options.getVideoId
 * @param {() => string} options.getAnimeId
 * @param {() => string} options.getEpisodeId
 * @param {() => string} options.getSubtitleTrackId
 */
export function useSubtitleTracks({ art, userInfo, router, route, isMobileViewport, getVideoId, getAnimeId, getEpisodeId, getSubtitleTrackId }) {
  const subtitleOctopus = ref(null)
  const tmpSubtitleOctopusSubUrl = ref('')
  const selectedSubtitleTrack = ref(null)
  let subtitleOffsetPersistTimer = null

  /**
   * 根据videoId获取字幕文件列表
   */
  const fetchSubtitles = async (videoId) => {
    try {
      if (!videoId) {
        console.warn('videoId为空，无法获取字幕')
        return []
      }

      const data = await getSubtitles(videoId)

      // 处理后端ApiResponseVO格式返回
      if (data.code === 200 && Array.isArray(data.data)) {
        // 转换字幕数据为插件需要的格式
        return data.data
          .filter(subtitle => subtitle.subtitleFormat && subtitle.filePath)
          .map(subtitle => {
            const format = String(subtitle.subtitleFormat || '').toLowerCase()
            return {
              id: subtitle.id,
              name: subtitle.trackName || `${subtitle.language || '未知语言'}`,
              url: `${API_BASE}/subtitles/${subtitle.id}/download`,
              format,
              timeOffset: Number(subtitle.timeOffset || 0),
              isLibassSupported: LIBASS_SUPPORTED_SUBTITLE_FORMATS.has(format),
            }
          })
      }

      console.warn('字幕数据格式未知', data)
      return []
    } catch (error) {
      console.error('获取字幕失败:', error)
      return []
    }
  }

  const toSubtitleOffsetSeconds = (subtitle) => {
    const offsetMs = Number(subtitle?.timeOffset || 0)
    return Number.isFinite(offsetMs) ? offsetMs / 1000 : 0
  }

  const isLibassSubtitleTrack = (subtitle) => {
    const format = String(subtitle?.format || '').toLowerCase()
    return LIBASS_SUPPORTED_SUBTITLE_FORMATS.has(format)
  }

  const isNativeArtplayerSubtitleTrack = (subtitle) => {
    const format = String(subtitle?.format || '').toLowerCase()
    return NATIVE_ARTPLAYER_SUBTITLE_FORMATS.has(format)
  }

  const getNativeSubtitleOffsetMs = () => {
    if (!art.value) {
      return 0
    }
    return Math.round(Number(art.value.subtitleOffset || 0) * 1000)
  }

  const setNativeSubtitleOffsetMs = (offsetMs) => {
    if (!art.value) {
      return
    }
    art.value.subtitleOffset = Number(offsetMs || 0) / 1000
  }

  const getCurrentSubtitleOffsetMs = (track = selectedSubtitleTrack.value) => {
    if (!track) {
      return 0
    }
    if (isNativeArtplayerSubtitleTrack(track)) {
      return getNativeSubtitleOffsetMs()
    }
    return Number(track.timeOffset || 0)
  }

  const switchSubtitleTrack = async (track) => {
    if (!track?.id) {
      return
    }

    const targetSubtitleId = String(track.id)
    if (targetSubtitleId === String(getSubtitleTrackId() || '')) {
      if (isLibassSubtitleTrack(track) && subtitleOctopus.value) {
        applySubtitleTrack(track)
      }
      return
    }

    try {
      await router.replace({
        name: 'Player',
        params: { videoId: String(getVideoId() || '') },
        query: {
          ...route.query,
          animeId: String(getAnimeId() || ''),
          episodeId: String(getEpisodeId() || ''),
          subtitleId: targetSubtitleId,
        },
      })
    } catch (error) {
      console.warn('切换字幕轨道失败:', error)
    }
  }

  const syncSubtitleOffset = (subtitle) => {
    const octopus = subtitleOctopus.value
    if (!octopus) {
      return
    }

    const offsetSeconds = toSubtitleOffsetSeconds(subtitle)
    octopus.timeOffset = offsetSeconds

    if (octopus.video && typeof octopus.setCurrentTime === 'function') {
      octopus.setCurrentTime(octopus.video.currentTime + offsetSeconds)
    }
  }

  /**
   * 判断当前登录用户是否为超级管理员
   */
  const isCurrentUserAdmin = () => isSuperAdmin(userInfo.value)

  /**
   * 将字幕偏移量持久化到后端（仅超管可用）
   */
  const persistSubtitleOffset = async (subtitleId, offsetMs) => {
    try {
      await setSubtitleOffset(subtitleId, offsetMs)
    } catch (e) {
      console.warn('保存字幕偏移量失败:', e)
    }
  }

  const stopSubtitleOffsetPersistTimer = () => {
    if (subtitleOffsetPersistTimer) {
      clearTimeout(subtitleOffsetPersistTimer)
      subtitleOffsetPersistTimer = null
    }
  }

  const queuePersistSubtitleOffset = (track, offsetMs) => {
    if (!isCurrentUserAdmin() || !track?.id) {
      return
    }

    stopSubtitleOffsetPersistTimer()
    subtitleOffsetPersistTimer = setTimeout(() => {
      persistSubtitleOffset(track.id, offsetMs)
    }, 280)
  }

  /**
   * 调整当前字幕延迟（单位毫秒）。
   * 快捷键：[ 减少 500ms，] 增加 500ms
   * 若当前用户为超管，同时将偏移量入库。
   */
  const adjustSubtitleDelay = async (deltaMs) => {
    const track = selectedSubtitleTrack.value
    if (!track) return

    if (isNativeArtplayerSubtitleTrack(track)) {
      const nextOffsetMs = getNativeSubtitleOffsetMs() + deltaMs
      setNativeSubtitleOffsetMs(nextOffsetMs)

      const currentOffsetSec = Number(art.value?.subtitleOffset || 0).toFixed(1)
      if (art.value?.notice) {
        art.value.notice.show = `字幕延迟: ${Number(currentOffsetSec) >= 0 ? '+' : ''}${currentOffsetSec}s`
      }
      queuePersistSubtitleOffset(track, getNativeSubtitleOffsetMs())
      return
    }

    track.timeOffset = (track.timeOffset || 0) + deltaMs
    syncSubtitleOffset(track)

    const offsetSec = (track.timeOffset / 1000).toFixed(1)
    if (art.value?.notice) {
      art.value.notice.show = `字幕延迟: ${Number(offsetSec) >= 0 ? '+' : ''}${offsetSec}s`
    }
    queuePersistSubtitleOffset(track, track.timeOffset)
  }

  const applySubtitleTrack = (subtitle) => {
    const octopus = subtitleOctopus.value
    if (!octopus) {
      return
    }

    try {
      if (!subtitle?.url) {
        if (typeof octopus.freeTrack === 'function') {
          octopus.freeTrack()
        }
        selectedSubtitleTrack.value = null
        tmpSubtitleOctopusSubUrl.value = ''
        return
      }

      selectedSubtitleTrack.value = subtitle
      tmpSubtitleOctopusSubUrl.value = subtitle.url
      syncSubtitleOffset(subtitle)

      // 先释放旧轨道，避免切集后沿用旧字幕
      if (typeof octopus.freeTrack === 'function') {
        octopus.freeTrack()
      }
      if (typeof octopus.setTrackByUrl === 'function') {
        octopus.setTrackByUrl(subtitle.url)
      }
      if (typeof octopus.setSubUrl === 'function') {
        octopus.setSubUrl(subtitle.url)
      }
    } catch (error) {
      console.warn('字幕轨切换失败（可能实例已销毁）:', error)
    }
  }

  const artplayerPluginAss = (options) => {
    return (player) => {
      const instance = new SubtitlesOctopus({
        ...options,
        video: player.template.$video,
      })

      if (instance.canvasParent) {
        instance.canvasParent.style.zIndex = 20
      }

      player.on('destroy', () => {
        instance.dispose()
        subtitleOctopus.value = null
      })

      subtitleOctopus.value = instance

      return {
        name: 'artplayerPluginAss',
        instance,
      }
    }
  }

  const buildSubtitlePlugin = (subtitles, preferredTrack = null) => {
    if (subtitles.length === 0) {
      selectedSubtitleTrack.value = null
      return null
    }

    const initialTrack = preferredTrack && isLibassSubtitleTrack(preferredTrack)
      ? preferredTrack
      : subtitles[0]

    selectedSubtitleTrack.value = initialTrack
    tmpSubtitleOctopusSubUrl.value = initialTrack.url
    return artplayerPluginAss({
      fonts: SUBTITLES_OCTOPUS_FONTS,
      subUrl: initialTrack.url,
      fallbackFont: SUBTITLES_OCTOPUS_FONTS[0],
      workerUrl: SUBTITLES_OCTOPUS_WORK_JS_PATH,
      wasmUrl: SUBTITLES_OCTOPUS_WORK_WASM_PATH,
      timeOffset: toSubtitleOffsetSeconds(initialTrack),
    })
  }

  const buildSubtitleSettings = (subtitles, activeSubtitleId = '') => {
    if (subtitles.length === 0) {
      tmpSubtitleOctopusSubUrl.value = ''
      return []
    }

    const subtitleTrackSetting = {
      width: 220,
      html: '字幕',
      tooltip: '选择',
      icon: '<span style="font-size:16px">CC</span>',
      selector: [
        {
          html: '开启',
          tooltip: '显示',
          switch: true,
          onSwitch: (item) => {
            if (!subtitleOctopus.value) {
              return item.switch
            }

            item.tooltip = item.switch ? '隐藏' : '显示'
            if (item.switch) {
              tmpSubtitleOctopusSubUrl.value = tmpSubtitleOctopusSubUrl.value || subtitles[0]?.url || ''
              subtitleOctopus.value.freeTrack()
            } else if (selectedSubtitleTrack.value?.url || tmpSubtitleOctopusSubUrl.value) {
              applySubtitleTrack(selectedSubtitleTrack.value || subtitles.find(subtitle => subtitle.url === tmpSubtitleOctopusSubUrl.value) || subtitles[0])
            }
            return !item.switch
          },
        },
        ...subtitles.map((subtitle, index) => ({
          default: String(subtitle.id || '') === String(activeSubtitleId || '') || (index === 0 && !activeSubtitleId),
          html: subtitle.name || `字幕 ${index + 1}`,
          subtitle,
          url: subtitle.url,
        })),
      ],
      onSelect: (item) => {
        if (!item.subtitle) {
          return item.html
        }
        switchSubtitleTrack(item.subtitle)
        return item.html
      },
    }

    return [subtitleTrackSetting]
  }

  const buildNativeSubtitleOption = (track) => {
    if (!track?.url) {
      return null
    }

    const mobile = isMobileViewport()
    const nativeSubtitleFontSize = mobile ? '28px' : '34px'

    return {
      url: track.url,
      type: track.format,
      encoding: 'utf-8',
      escape: true,
      style: {
        color: '#FFFFFF',
        'font-size': nativeSubtitleFontSize,
        'text-shadow': '0 2px 4px rgba(0, 0, 0, 0.65)',
      },
    }
  }

  return {
    subtitleOctopus,
    tmpSubtitleOctopusSubUrl,
    selectedSubtitleTrack,
    fetchSubtitles,
    toSubtitleOffsetSeconds,
    isLibassSubtitleTrack,
    isNativeArtplayerSubtitleTrack,
    getNativeSubtitleOffsetMs,
    setNativeSubtitleOffsetMs,
    getCurrentSubtitleOffsetMs,
    switchSubtitleTrack,
    syncSubtitleOffset,
    adjustSubtitleDelay,
    applySubtitleTrack,
    artplayerPluginAss,
    buildSubtitlePlugin,
    buildSubtitleSettings,
    buildNativeSubtitleOption,
    stopSubtitleOffsetPersistTimer,
    queuePersistSubtitleOffset,
  }
}
