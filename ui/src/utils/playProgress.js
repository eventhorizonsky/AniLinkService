// 播放进度展示工具。
// 此前 History.vue 的 progressText 与 AnimeDetail.vue 的 resumeProgressText 各自实现，统一收敛到这里。

/**
 * 生成播放进度文本：如 "125s / 1500s · 8%"，无时长时仅显示已播秒数。
 * @param {Object} item 含 progressSeconds / durationSeconds / progressPercentage 的对象
 */
export function formatPlayProgressText(item) {
  const progress = Number(item?.progressSeconds || 0)
  const duration = Number(item?.durationSeconds || 0)
  const percent = Number(item?.progressPercentage || 0)
  if (!duration) return `${progress}s`
  return `${progress}s / ${duration}s · ${Math.min(100, Math.max(0, percent))}%`
}

/**
 * 归一化进度百分比（0-100）。
 */
export function clampProgressPercent(value) {
  return Math.min(100, Math.max(0, Number(value || 0)))
}
