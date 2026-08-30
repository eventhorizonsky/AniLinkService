// 追番状态统一常量与映射。
// 此前“想看/在看/看过/搁置/抛弃”的 label、color、排序散落在
// Home.vue / Follows.vue / AnimeHeroSection.vue / AnimeDetail.vue / Player.vue 中，统一到这里。

export const FOLLOW_STATUS = {
  wish: { label: '想看', color: '#42a5f5' },
  watching: { label: '在看', color: '#ff9800' },
  watched: { label: '看过', color: '#4caf50' },
  on_hold: { label: '搁置', color: '#ffc107' },
  dropped: { label: '抛弃', color: '#ef5350' },
}

export const FOLLOW_STATUS_ORDER = ['wish', 'watching', 'watched', 'on_hold', 'dropped']

// 扁平映射，方便作为既有 STATUS_LABEL / STATUS_COLORS / STATUS_ORDER 的 drop-in 替换。
export const FOLLOW_STATUS_LABEL = Object.fromEntries(
  FOLLOW_STATUS_ORDER.map((s) => [s, FOLLOW_STATUS[s].label])
)
export const FOLLOW_STATUS_COLORS = Object.fromEntries(
  FOLLOW_STATUS_ORDER.map((s) => [s, FOLLOW_STATUS[s].color])
)
export const FOLLOW_STATUS_ORDER_MAP = Object.fromEntries(
  FOLLOW_STATUS_ORDER.map((s, i) => [s, i])
)

export const FOLLOW_STATUS_OPTIONS = FOLLOW_STATUS_ORDER.map((value) => ({
  value,
  label: FOLLOW_STATUS[value].label,
  color: FOLLOW_STATUS[value].color,
}))

export const followStatusLabel = (s, fallback = '-') =>
  FOLLOW_STATUS[s]?.label || s || fallback

export const followStatusColor = (s, fallback = '#9e8c7e') =>
  FOLLOW_STATUS[s]?.color || fallback
