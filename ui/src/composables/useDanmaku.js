// 弹幕相关逻辑：设置持久化、弹弹→Artplayer 格式转换、获取/发送弹幕。
// 此前集中在 Player.vue 的"上帝组件"中，统一收敛到这里。
import { formatDanmakuColor } from '../utils/format'
import { getDanmakuComments, sendDanmakuComment } from '../api/danmaku'
import { ARTPLAYER_TO_DANDAN_MODE, DANDAN_TO_ARTPLAYER_MODE } from '../utils/danmakuMode'

export const DANMAKU_SETTINGS_STORAGE_KEY = 'anilink:danmaku:settings:v1'

export const DEFAULT_DANMAKU_SETTINGS = {
  speed: 7,
  opacity: 1,
  fontSize: 25,
  color: '#FFFFFF',
  mode: 0,
  margin: [10, '25%'],
  antiOverlap: true,
  synchronousPlayback: false,
  lockTime: 5,
  maxLength: 200,
  theme: 'dark',
  emitter: true,
  visible: true,
}

export const normalizeDanmakuSettings = (raw) => {
  if (!raw || typeof raw !== 'object') {
    return {}
  }

  const margin = Array.isArray(raw.margin) && raw.margin.length === 2
    ? [raw.margin[0], raw.margin[1]]
    : undefined

  return {
    speed: typeof raw.speed === 'number' ? raw.speed : undefined,
    opacity: typeof raw.opacity === 'number' ? raw.opacity : undefined,
    fontSize: typeof raw.fontSize === 'number' || typeof raw.fontSize === 'string' ? raw.fontSize : undefined,
    color: typeof raw.color === 'string' ? raw.color : undefined,
    mode: typeof raw.mode === 'number' ? raw.mode : undefined,
    margin,
    antiOverlap: typeof raw.antiOverlap === 'boolean' ? raw.antiOverlap : undefined,
    synchronousPlayback: typeof raw.synchronousPlayback === 'boolean' ? raw.synchronousPlayback : undefined,
    lockTime: typeof raw.lockTime === 'number' ? raw.lockTime : undefined,
    maxLength: typeof raw.maxLength === 'number' ? raw.maxLength : undefined,
    theme: raw.theme === 'light' || raw.theme === 'dark' ? raw.theme : undefined,
    emitter: typeof raw.emitter === 'boolean' ? raw.emitter : undefined,
    visible: typeof raw.visible === 'boolean' ? raw.visible : undefined,
  }
}

const loadDanmakuSettings = () => {
  try {
    const cache = localStorage.getItem(DANMAKU_SETTINGS_STORAGE_KEY)
    if (!cache) {
      return {}
    }
    const parsed = JSON.parse(cache)
    return normalizeDanmakuSettings(parsed)
  } catch (e) {
    console.warn('读取弹幕配置缓存失败:', e)
    return {}
  }
}

const saveDanmakuSettings = (option) => {
  try {
    const normalized = normalizeDanmakuSettings(option)
    localStorage.setItem(DANMAKU_SETTINGS_STORAGE_KEY, JSON.stringify(normalized))
  } catch (e) {
    console.warn('保存弹幕配置缓存失败:', e)
  }
}

/**
 * 将弹弹play弹幕格式转换为Artplayer格式
 * @param {Array} chats - 弹弹play返回的弹幕数组
 * @returns {Array} 转换后的弹幕数组
 */
const convertDandanToArtplayer = (chats) => {
  if (!Array.isArray(chats)) {
    console.warn('弹幕数据不是数组:', typeof chats, chats)
    return []
  }

  if (chats.length === 0) {
    return []
  }

  return chats
    .filter((chat) => {
      // 检查弹幕对象是否包含必要字段
      return chat && (chat.p || chat.mode || chat.mode !== undefined) && (chat.m || chat.text)
    })
    .map((chat) => {
      try {
        // 如果已经是转换后的格式，直接返回
        if (chat.text && (chat.mode !== undefined || chat.time !== undefined)) {
          return {
            text: chat.text,
            time: chat.time || 0,
            mode: chat.mode || 0,
            color: chat.color || '#FFFFFF',
            border: chat.border || false,
          }
        }

        // 解析 p 字段: "出现时间,模式,颜色,用户ID"
        if (!chat.p || !chat.m) {
          console.warn('弹幕缺少必要字段 p 或 m:', chat)
          return null
        }

        const pParts = chat.p.split(',')
        if (pParts.length < 3) {
          console.warn('弹幕 p 字段格式不正确:', chat.p)
          return null
        }

        const time = parseFloat(pParts[0]) || 0
        const dandanMode = parseInt(pParts[1]) || 1
        const colorInt = parseInt(pParts[2]) || 16777215 // 白色

        // 模式转换
        // 弹弹play: 1=普通, 4=底部, 5=顶部 → Artplayer: 0=滚动, 1=顶部, 2=底部
        const artplayerMode = DANDAN_TO_ARTPLAYER_MODE[dandanMode] ?? 0

        // 颜色转换: 十进制RGB到十六进制
        const color = formatDanmakuColor(colorInt, '#FFFFFF')

        return {
          text: chat.m,
          time,
          mode: artplayerMode,
          color,
          border: false,
        }
      } catch (error) {
        console.error('弹幕转换失败:', chat, error)
        return null
      }
    })
    .filter((item) => item !== null)
}

/**
 * 弹幕逻辑。
 * @param {Object} options
 * @param {import('vue').ShallowRef} options.art 播放器实例引用
 * @param {import('vue').Ref} options.token 登录令牌
 * @param {() => string} options.getEpisodeId
 * @param {() => string} options.getAnimeId
 * @param {() => string} options.getVideoId
 * @param {() => string|null} options.getAnimeTitle
 * @param {() => string|null} options.getEpisodeTitle
 */
export function useDanmaku({ art, token, getEpisodeId, getAnimeId, getVideoId, getAnimeTitle, getEpisodeTitle }) {
  /**
   * 根据episodeId获取弹幕数据
   */
  const fetchDanmaku = async (episodeId) => {
    try {
      if (!episodeId) {
        console.warn('episodeId为空，无法获取弹幕')
        return []
      }

      const data = await getDanmakuComments(episodeId)

      // 处理后端自定义格式返回
      if (data.code === 200 && data.data) {
        // 优先使用 comments 字段，其次使用 chats 字段
        const comments = data.data.comments || data.data.chats || []
        return convertDandanToArtplayer(comments)
      }

      // 处理标准ApiResponseVO格式
      if (data.success && data.data) {
        const comments = data.data.chats || data.data.comments || []
        return convertDandanToArtplayer(comments)
      }

      // 处理直接返回弹幕数组
      if (data.chats) {
        return convertDandanToArtplayer(data.chats)
      }

      if (data.comments) {
        return convertDandanToArtplayer(data.comments)
      }

      console.warn('弹幕数据格式未知', data)
      return []
    } catch (error) {
      console.error('获取弹幕失败:', error)
      return []
    }
  }

  /**
   * 发送弹幕到弹弹play API
   * @param {Object} danmu - 弹幕数据 { text, time, mode, color }
   */
  const sendDanmaku = async (danmu) => {
    const targetEpisodeId = getEpisodeId()
    if (!targetEpisodeId) {
      throw new Error('缺少弹幕库ID')
    }

    // Artplayer mode: 0=滚动, 1=顶部, 2=底部
    // 弹弹play mode: 1=普通(滚动), 4=底部, 5=顶部
    const dandanMode = ARTPLAYER_TO_DANDAN_MODE[danmu.mode] || 1

    // 颜色转换: #HEX -> 十进制整数 RGB
    let colorInt = 16777215 // 默认白色
    if (danmu.color) {
      const hex = danmu.color.replace('#', '')
      colorInt = parseInt(hex, 16)
      if (isNaN(colorInt)) {
        colorInt = 16777215
      }
    }

    const requestBody = {
      time: danmu.time || 0,
      mode: dandanMode,
      color: colorInt,
      comment: danmu.text || '',
      animeId: getAnimeId() || null,
      animeTitle: getAnimeTitle() || null,
      videoId: getVideoId() || null,
      episodeTitle: getEpisodeTitle() || null,
    }

    await sendDanmakuComment(targetEpisodeId, requestBody)
  }

  /**
   * 构建 artplayer-plugin-danmuku 插件配置
   * @param {Array} danmakuData 已转换的弹幕数组
   * @param {boolean} mobile 是否移动端
   */
  const buildDanmakuOptions = (danmakuData, mobile) => {
    const persistedDanmakuSettings = loadDanmakuSettings()

    // 仅在没有历史设置时降低移动端默认字号，避免覆盖用户已调过的配置。
    const mobileDefaults = mobile && persistedDanmakuSettings.fontSize === undefined
      ? { fontSize: 19 }
      : {}

    return {
      ...DEFAULT_DANMAKU_SETTINGS,
      ...mobileDefaults,
      ...persistedDanmakuSettings,
      danmuku: danmakuData,
      useWorker: true,
      minWidth: mobile ? 140 : 200,
      maxWidth: mobile ? 320 : 500,
      filter: (danmu) => danmu.text && danmu.text.length < 200,
      beforeEmit: async (danmu) => {
        // 先校验文本非空
        if (!danmu.text || !danmu.text.trim()) {
          return false
        }

        // 检查登录状态
        if (!token.value) {
          if (art.value?.notice) {
            art.value.notice.show = '请先登录后再发送弹幕'
          }
          return false
        }

        try {
          await sendDanmaku(danmu)
          if (art.value?.notice) {
            art.value.notice.show = '弹幕发送成功'
          }
          return true
        } catch (err) {
          console.error('弹幕发送失败:', err)
          if (art.value?.notice) {
            art.value.notice.show = '弹幕发送失败'
          }
          return false
        }
      },
    }
  }

  return {
    fetchDanmaku,
    sendDanmaku,
    buildDanmakuOptions,
    saveDanmakuSettings,
  }
}
