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
