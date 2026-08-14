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
 * 弹幕颜色整数 → 十六进制色值字符串（如 #FF8800）。
 */
export function formatDanmakuColor(color, fallback = '#FFFFFF') {
  if (color == null) return fallback
  return '#' + color.toString(16).padStart(6, '0').toUpperCase()
}
