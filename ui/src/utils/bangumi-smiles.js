// Bangumi 表情码 -> 图片路径映射。
// 数据来源：bgm.tv 编辑器资源（g=editor 中的 smiles_sets 与 editor.min.css 中的 background-image 规则），
// 图片基地址为 lain.bgm.tv（渲染时可用评论头像的 CDN 域名替换，见 EpisodeComments.vue）。

const pad2 = (n) => String(n).padStart(2, '0')
const pad3 = (n) => String(n).padStart(3, '0')

// (bgm500)-(bgm529) 中为 gif 的编号，其余为 png
const TV500_GIF = new Set([500, 501, 505, 515, 516, 517, 518, 519, 521, 522, 523])

/**
 * 将表情码（不含括号，如 "bgm207" / "musume_79"）映射为图片路径。
 * 未知表情返回空字符串，调用方应保留原文。
 */
export function bangumiSmilePath(code) {
  if (typeof code !== 'string') return ''
  const key = code.trim().toLowerCase()

  // (bgm01)-(bgm125)
  let m = /^bgm(\d+)$/.exec(key)
  if (m) {
    const n = parseInt(m[1], 10)
    if (n >= 1 && n <= 23) {
      const ext = n === 11 || n === 23 ? 'gif' : 'png'
      return `/img/smiles/bgm/${pad2(n)}.${ext}`
    }
    if (n >= 24 && n <= 125) {
      return `/img/smiles/tv/${pad2(n - 23)}.gif`
    }
    if (n >= 200 && n <= 238) {
      return `/img/smiles/tv_vs/bgm_${pad3(n)}.png`
    }
    if (n >= 500 && n <= 529) {
      const ext = TV500_GIF.has(n) ? 'gif' : 'png'
      return `/img/smiles/tv_500/bgm_${pad3(n)}.${ext}`
    }
    return ''
  }

  // (musume_NN) / (blake_NN)，支持 1-3 位编号（如 musume_100 / musume_118）
  m = /^(musume|blake)_(\d{1,3})$/.exec(key)
  if (m) {
    const n = parseInt(m[2], 10)
    if (n >= 1 && n <= 999) {
      return `/img/smiles/${m[1]}/${m[1]}_${pad2(n)}.gif`
    }
  }

  return ''
}
