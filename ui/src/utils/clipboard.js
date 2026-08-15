import { showAppMessage } from './ui-feedback'

/**
 * 复制文本到剪贴板。
 * 此前 RemoteAccess.vue / McpAccess.vue 各自实现了相同的 try/catch + 提示逻辑。
 * @param {string} text 要复制的文本
 * @param {string} [label='已复制'] 成功提示文案
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyText(text, label = '已复制') {
  if (!text) {
    showAppMessage('无可复制内容', 'warning')
    return false
  }
  try {
    await navigator.clipboard.writeText(text)
    showAppMessage(label, 'success')
    return true
  } catch (e) {
    console.error('复制失败:', e)
    showAppMessage('复制失败，请手动复制', 'error')
    return false
  }
}
