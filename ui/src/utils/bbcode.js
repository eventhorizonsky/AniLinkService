// Bangumi BBCode 渲染器（仅白名单标签，内容先转义）。
// 此前内联在 EpisodeComments.vue 中，抽取为独立可测试模块。
// 任何新标签必须同时加入白名单，并经过 sanitizeUrl / sanitizeColor 钳制。

import { bangumiSmilePath } from './bangumi-smiles'

const escapeHtml = (s) =>
  String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const sanitizeUrl = (raw) => {
  const url = String(raw || '').trim()
  return /^https?:\/\//i.test(url) ? url : ''
}

const sanitizeColor = (raw) => {
  const v = String(raw || '').trim().toLowerCase()
  if (/^#[0-9a-f]{3,8}$/.test(v)) return v
  if (/^[a-z]{3,20}$/.test(v)) return v
  return ''
}

const unescapeAttr = (s) =>
  String(s || '')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')

/**
 * 渲染 Bangumi BBCode 为受控 HTML。
 * @param {string} content 原始评论内容
 * @param {Object} [options]
 * @param {string} [options.smileBaseUrl] 表情 CDN 基础地址，缺省时不渲染表情
 * @returns {string}
 */
export function renderBBCode(content, { smileBaseUrl = '' } = {}) {
  if (!content) return ''
  let html = escapeHtml(content)

  // [img]URL[/img] -> 受限尺寸的图片，点击查看大图
  html = html.replace(/\[img(?:=(\d{1,4})(?:,(\d{1,4}))?)?\]([^\[]*?)\[\/img\]/gi, (m, w, h, url) => {
    const clean = sanitizeUrl(unescapeAttr(url))
    if (!clean) return ''
    return `<img class="bgm-ep-img" src="${escapeHtml(clean)}" alt="图片" loading="lazy" referrerpolicy="no-referrer" />`
  })

  // 行内格式
  html = html
    .replace(/\[b\]([\s\S]*?)\[\/b\]/gi, '<b>$1</b>')
    .replace(/\[i\]([\s\S]*?)\[\/i\]/gi, '<i>$1</i>')
    .replace(/\[u\]([\s\S]*?)\[\/u\]/gi, '<u>$1</u>')
    .replace(/\[s\]([\s\S]*?)\[\/s\]/gi, '<s>$1</s>')
    .replace(/\[size=(\d{1,3})\]([\s\S]*?)\[\/size\]/gi, (m, size, inner) => {
      let px = parseInt(size, 10)
      if (!Number.isFinite(px)) return inner
      px = Math.min(32, Math.max(10, px))
      return `<span style="font-size:${px}px">${inner}</span>`
    })
    .replace(/\[color=([^\]]+)\]([\s\S]*?)\[\/color\]/gi, (m, color, inner) => {
      const clean = sanitizeColor(unescapeAttr(color))
      return clean ? `<span style="color:${clean}">${inner}</span>` : inner
    })

  // 链接
  html = html
    .replace(/\[url=([^\]]+)\]([\s\S]*?)\[\/url\]/gi, (m, url, text) => {
      const clean = sanitizeUrl(unescapeAttr(url))
      return clean ? `<a href="${escapeHtml(clean)}" target="_blank" rel="noopener noreferrer nofollow">${text}</a>` : text
    })
    .replace(/\[url\]([^\[]*?)\[\/url\]/gi, (m, url) => {
      const clean = sanitizeUrl(unescapeAttr(url))
      return clean ? `<a href="${escapeHtml(clean)}" target="_blank" rel="noopener noreferrer nofollow">${escapeHtml(clean)}</a>` : ''
    })

  // alignment tags: [left] / [center] / [right]
  html = html
    .replace(/\[right\]([\s\S]*?)\[\/right\]/gi, '<div class="bgm-ep-align-right">$1</div>')
    .replace(/\[center\]([\s\S]*?)\[\/center\]/gi, '<div class="bgm-ep-align-center">$1</div>')
    .replace(/\[left\]([\s\S]*?)\[\/left\]/gi, '<div class="bgm-ep-align-left">$1</div>')

  // markdown-style links [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, text, url) => {
    const clean = sanitizeUrl(unescapeAttr(url))
    return clean ? `<a href="${escapeHtml(clean)}" target="_blank" rel="noopener noreferrer nofollow">${text}</a>` : m
  })

  // mask spoiler tag: hidden until hover
  html = html.replace(/\[mask\]([\s\S]*?)\[\/mask\]/gi, '<span class="bgm-ep-mask">$1</span>')

  // 引用块
  html = html.replace(/\[quote\]([\s\S]*?)\[\/quote\]/gi, '<blockquote class="bgm-ep-quote">$1</blockquote>')

  // 表情：如 (bgm207)、(musume_79)，映射成功的替换为图片，未知的保留原文
  if (smileBaseUrl) {
    html = html.replace(/\(([a-z0-9_]{2,20})\)/gi, (m, code) => {
      const path = bangumiSmilePath(code)
      if (!path) return m
      return `<img class="bgm-ep-smile" src="${escapeHtml(smileBaseUrl)}${path}" alt="${m}" title="${m}" loading="lazy" />`
    })
  }

  // 换行
  html = html.replace(/\r?\n/g, '<br>')
  return html
}
