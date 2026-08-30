import DOMPurify from 'dompurify'

/**
 * 使用 DOMPurify 清洗可能包含不可信内容的 HTML 片段，
 * 供 v-html 渲染前统一调用，避免 XSS。
 * 返回字符串；空值返回空字符串。
 */
export function sanitizeHtml(dirty) {
  if (dirty == null) return ''
  return DOMPurify.sanitize(String(dirty))
}
