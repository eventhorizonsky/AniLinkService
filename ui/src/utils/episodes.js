// 集数分类与展示工具。
// 此前 isFuture/getEpisodeType/episodeNumberDisplay/formatEpisodeDate/playableEpisodeKeys 等
// 在 Player.vue / AnimeDetail.vue / EpisodeListSection.vue 中逐字重复，统一收敛到这里。

export const isFuture = (ep) => new Date(ep.airDate) > new Date()

export const getEpisodeType = (ep) => {
  const num = ep?.episodeNumber
  if (/^\d+$/.test(num)) return 'main'
  if (String(num).startsWith('S')) return 'special'
  if (String(num).startsWith('C')) return 'credit'
  return 'other'
}

export const filterMainEpisodes = (eps) => (eps || []).filter((ep) => getEpisodeType(ep) === 'main')

export const filterSpecialEpisodes = (eps) =>
  (eps || []).filter((ep) => ['special', 'credit'].includes(getEpisodeType(ep)))

export const episodeNumberDisplay = (ep) => {
  const type = getEpisodeType(ep)
  if (type === 'main') return `第${ep.episodeNumber}话`
  if (type === 'special') return '特典'
  if (type === 'credit') return '主题'
  return ep.episodeNumber
}

export const formatEpisodeDate = (iso) => {
  if (!iso) return ''
  return String(iso).slice(5, 10)
}

export const buildPlayableEpisodeKeys = (existingEpisodes) => {
  const set = new Set()
  ;(existingEpisodes || []).forEach((ep) => {
    if (ep.episodeId !== undefined && ep.episodeId !== null) {
      set.add(String(ep.episodeId))
    }
  })
  return set
}

export const getEpisodeResources = (existingEpisodes, episodeId) => {
  if (episodeId === undefined || episodeId === null) {
    return []
  }
  const key = String(episodeId)
  return (existingEpisodes || []).filter(
    (item) => String(item.episodeId) === key && item.id !== undefined && item.id !== null
  )
}

export const truncateText = (text, maxLen) => {
  const str = String(text || '')
  if (str.length <= maxLen) {
    return str
  }
  return `${str.slice(0, maxLen)}...`
}

/**
 * 计算某集在其"正片"序列中的位置（1-based）。
 * <p>
 * 弹弹的 episodeNumber 未必从 1 开始（如某季标注为第 11 集起），而 Bangumi 的
 * episodes 数组是按 sort 排序的序列。两者都以"正片"的排列顺序对应，因此用
 * 该集在正片数组中的位置作为统一标识，而非把 episodeNumber 直接当索引。
 * 这样同步"已看"与拉取单集吐槽时都能对齐到 Bangumi 的同一集。
 *
 * @param {object|null} ep 剧集对象
 * @param {Array} episodes 番剧的全部 episodes 数组
 * @returns {number|null} 1-based 位置；不是正片或找不到时返回 null
 */
export const mainEpisodePosition = (ep, episodes) => {
  if (!ep) return null
  const mains = filterMainEpisodes(episodes || [])
    .slice()
    .sort((a, b) => Number(a?.episodeNumber) - Number(b?.episodeNumber))
  const idx = mains.findIndex((e) => String(e?.episodeId) === String(ep?.episodeId))
  return idx >= 0 ? idx + 1 : null
}
