// 通用格式化工具。
// 此前日期/字节/时长/颜色等格式化函数在十余个文件中各自重复实现，统一收敛到这里。

/**
 * 完整日期时间：如 2025/01/02 13:04。
 * @param {*} value 时间值（Date/时间戳/ISO 字符串）
 * @param {string} fallback 空值兜底
 */
export function formatDateTime(value, fallback = '--') {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/**
 * 仅日期（取 ISO 字符串前 10 位，如 2025-01-02）。
 */
export function formatDate(iso, fallback = '') {
  return iso ? String(iso).slice(0, 10) : fallback
}

/**
 * 相对时间（刚刚/X分钟前/X小时前/X天前）。
 * 此前在 MainLayout.vue 中局部实现。
 */
export function formatRelativeTime(value, fallback = '') {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const diffMs = Date.now() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN')
}

/**
 * 月-日 时间：如 01/02 13:04（无年份）。
 * 此前 History.vue / Messages.vue 各自复制了相同实现。
 */
export function formatMonthDayTime(value, fallback = '--') {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/**
 * 时长（毫秒）→ H:MM:SS 或 M:SS。
 */
export function formatDuration(ms) {
  if (!ms) return '未知'
  const seconds = Math.floor(Number(ms) / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${minutes}:${String(secs).padStart(2, '0')}`
}

/**
 * 文件大小（字节）→ 如 1.5 MB（保留两位小数）。
 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(Number(bytes)) / Math.log(k)), sizes.length - 1)
  return `${Math.round((Number(bytes) / Math.pow(k, i)) * 100) / 100} ${sizes[i]}`
}

/**
 * 字节数（用于“已下载 / 总量”展示，短格式，无空格）。
 */
export function formatBytes(value, fallback = '-') {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return fallback
  const num = Number(value)
  if (num < 1024) return `${num} B`
  const kb = num / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}

/**
 * 速率（字节/秒）。
 */
export function formatSpeed(bps) {
  if (!bps || bps <= 0) return '0 B/s'
  if (bps < 1024) return `${Math.round(bps)} B/s`
  const kb = bps / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB/s`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB/s`
  return `${(mb / 1024).toFixed(2)} GB/s`
}

/**
 * 评分 → 保留一位小数，非法值返回 '-'。
 */
export function formatScore(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? '-' : n.toFixed(1)
}

/**
 * 大数字紧凑显示（中文单位）：12000 → "1.2万"，3e8 → "3亿"。
 * 此前在 Home.vue 中局部实现（fmtHeat）。
 */
export function formatCompactNumber(v, fallback = '') {
  if (v == null || v === '') return fallback
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (n >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, '') + '亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1).replace(/\.0$/, '') + '万'
  return String(n)
}

/**
 * 弹幕颜色整数 → 十六进制色值字符串（如 #FF8800）。
 */
export function formatDanmakuColor(color, fallback = '#FFFFFF') {
  if (color == null) return fallback
  return '#' + color.toString(16).padStart(6, '0').toUpperCase()
}

/**
 * 运行时长（秒）→ "X 天 X 小时 X 分钟"。
 * 此前 SystemInfo.vue / InstallStep1.vue 各自实现了一份。
 */
export function formatUptime(seconds) {
  if (seconds == null) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  let text = ''
  if (days > 0) text += `${days} 天 `
  if (hours > 0 || days > 0) text += `${hours} 小时 `
  text += `${minutes} 分钟`
  return text
}

/**
 * 运行时长（秒）→ 紧凑格式 "1d 2h 3m"（短展示用）。
 */
export function formatUptimeShort(seconds) {
  if (seconds == null) return '-'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
}

/**
 * 时长（毫秒）→ "1h 2m 3s" 风格（人类可读）。
 * 此前 VideoFileManager.vue / ScheduledTasks.vue 各自实现了一份。
 */
export function formatDurationHuman(ms) {
  if (!ms) return '-'
  const seconds = Math.floor(Number(ms) / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  if (hours > 0) {
    return `${hours}h ${minutes}m ${secs}s`
  }
  return `${minutes}m ${secs}s`
}
