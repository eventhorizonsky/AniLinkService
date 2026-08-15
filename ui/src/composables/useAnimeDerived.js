import { computed } from 'vue'
import { WEEKDAY_LABELS } from '../utils/constants'
import { sanitizeHtml } from '../utils/sanitize'

// 从原始 anime 数据（ref）派生的展示字段。
// 此前这些 computed 在 AnimeDetail.vue / Player.vue 中几乎逐字重复，统一收敛到这里。

const STAFF_META_KEYS = ['原作', '导演', '音乐', '动画制作']

const DEFAULT_RATING_BANGUMI = '7.8'
const DEFAULT_RATING_ANIDB = '8.51'
const DEFAULT_TOTAL_EPISODES = '10'

function formatRating(value, digits = 1) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '--'
}

export function useAnimeDerived(animeData) {
  const isOnAir = computed(() => Boolean(animeData.value?.isOnAir))
  const ratingMain = computed(() => formatRating(animeData.value?.rating, 1))
  const ratingBangumi = computed(() => animeData.value?.ratingDetails?.['Bangumi评分'] ?? DEFAULT_RATING_BANGUMI)
  const ratingAnidb = computed(() => animeData.value?.ratingDetails?.['Anidb连载中评分'] ?? DEFAULT_RATING_ANIDB)

  const totalEpisodes = computed(() => {
    const meta = animeData.value?.metadata || []
    const epItem = meta.find((m) => m.startsWith('话数'))
    return epItem ? epItem.split(':')[1]?.trim() : DEFAULT_TOTAL_EPISODES
  })

  // 直接产出 HTML 片段（换行转 <br>），在此处统一清洗，避免调用方忘记 sanitize 造成 XSS 面
  const formattedSummary = computed(() => {
    return sanitizeHtml(animeData.value?.summary?.replace(/\n/g, '<br>') || '')
  })

  const titleInfo = computed(() => {
    const titles = animeData.value?.titles || []
    if (titles.length === 0) return { main: '', sub: '' }
    return {
      main: titles[0]?.title || '',
      sub: titles[1]?.title || '',
    }
  })

  const airDayText = computed(() => {
    const day = animeData.value?.airDay
    return WEEKDAY_LABELS[day] || ''
  })

  // staffList 元素本身可能含 <strong> 标签，统一在此清洗
  const staffList = computed(() => {
    const meta = animeData.value?.metadata || []
    return meta
      .filter((item) => STAFF_META_KEYS.some((key) => item.startsWith(key)))
      .map((item) => {
        const parts = item.split(':')
        if (parts.length >= 2) {
          return sanitizeHtml(`<strong>${parts[0]}:</strong>${parts.slice(1).join(':')}`)
        }
        return sanitizeHtml(item)
      })
  })

  const copyrightText = computed(() => {
    const meta = animeData.value?.metadata || []
    return meta.find((m) => m.startsWith('Copyright')) || ''
  })

  return {
    isOnAir,
    ratingMain,
    ratingBangumi,
    ratingAnidb,
    totalEpisodes,
    formattedSummary,
    titleInfo,
    airDayText,
    staffList,
    copyrightText,
  }
}
