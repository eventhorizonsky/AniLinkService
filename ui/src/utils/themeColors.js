// ============================================================
// 主题色预设
// 前端本地保存（localStorage），不上服务端。
// 每个预设提供浅色 / 深色两套色板，key 稳定不变用于持久化：
//   - primary：Vuetify 的 primary（按钮 / 输入框 / 进度条等）
//   - accent：对应 CSS 变量 --al-accent（品牌强调色）
//   - accentStrong / accentStrong2 / accentStrong3：hover / 激活档
//   - accentDeep：导航选中文字等深一档
//   - accentGold：品牌渐变第二色
//   - accentBrown / accentBrownDark：描边 / 观看按钮等次要强调
// 深色模式取值整体提亮，保证对比度。
// ============================================================

export const DEFAULT_THEME_COLOR_KEY = 'ember'

export const THEME_COLOR_PRESETS = [
  {
    key: 'ember',
    name: '赤陶',
    light: {
      primary: '#c45d2b',
      accent: '#c45d2b',
      accentStrong: '#a65628',
      accentStrong2: '#a85628',
      accentStrong3: '#a84e24',
      accentDeep: '#8a3d12',
      accentGold: '#e0a050',
      accentBrown: '#b99a7e',
      accentBrownDark: '#b3815b',
    },
    dark: {
      primary: '#e0804e',
      accent: '#e0804e',
      accentStrong: '#ef9360',
      accentStrong2: '#ef9360',
      accentStrong3: '#ef9360',
      accentDeep: '#f0a478',
      accentGold: '#e8a85f',
      accentBrown: '#c8a98c',
      accentBrownDark: '#d6a07c',
    },
  },
  {
    key: 'ocean',
    name: '青碧',
    light: {
      primary: '#1e7b6b',
      accent: '#1e7b6b',
      accentStrong: '#17685b',
      accentStrong2: '#176b5c',
      accentStrong3: '#156052',
      accentDeep: '#0f5a4d',
      accentGold: '#3fa88f',
      accentBrown: '#7fb3a4',
      accentBrownDark: '#5f9c8b',
    },
    dark: {
      primary: '#4ec9b5',
      accent: '#4ec9b5',
      accentStrong: '#62d8c4',
      accentStrong2: '#62d8c4',
      accentStrong3: '#62d8c4',
      accentDeep: '#7ee2d0',
      accentGold: '#6fd0b8',
      accentBrown: '#7cc4b2',
      accentBrownDark: '#8fd4c3',
    },
  },
  {
    key: 'indigo',
    name: '靛蓝',
    light: {
      primary: '#4f5bd5',
      accent: '#4f5bd5',
      accentStrong: '#4550c2',
      accentStrong2: '#4752c6',
      accentStrong3: '#404bb8',
      accentDeep: '#3944b0',
      accentGold: '#8f97f5',
      accentBrown: '#a5a9d8',
      accentBrownDark: '#8f94c9',
    },
    dark: {
      primary: '#7c86f0',
      accent: '#7c86f0',
      accentStrong: '#8f97f5',
      accentStrong2: '#8f97f5',
      accentStrong3: '#8f97f5',
      accentDeep: '#a5acf8',
      accentGold: '#9da4f5',
      accentBrown: '#a5a9d8',
      accentBrownDark: '#b3b6e0',
    },
  },
  {
    key: 'rose',
    name: '樱花',
    light: {
      primary: '#d94f70',
      accent: '#d94f70',
      accentStrong: '#c74363',
      accentStrong2: '#cb4667',
      accentStrong3: '#bd3f5e',
      accentDeep: '#b03a56',
      accentGold: '#e88aa0',
      accentBrown: '#d8a0ac',
      accentBrownDark: '#c98b98',
    },
    dark: {
      primary: '#f07a97',
      accent: '#f07a97',
      accentStrong: '#f58ca5',
      accentStrong2: '#f58ca5',
      accentStrong3: '#f58ca5',
      accentDeep: '#f9a2b7',
      accentGold: '#f0a0b2',
      accentBrown: '#d8a0ac',
      accentBrownDark: '#e0b0bc',
    },
  },
  {
    key: 'violet',
    name: '暮紫',
    light: {
      primary: '#7c5cd6',
      accent: '#7c5cd6',
      accentStrong: '#6d4fc9',
      accentStrong2: '#7153cd',
      accentStrong3: '#6547c0',
      accentDeep: '#5f43b5',
      accentGold: '#a58cf0',
      accentBrown: '#b3a3d8',
      accentBrownDark: '#a08fc9',
    },
    dark: {
      primary: '#a78bfa',
      accent: '#a78bfa',
      accentStrong: '#b49bfb',
      accentStrong2: '#b49bfb',
      accentStrong3: '#b49bfb',
      accentDeep: '#c2acfc',
      accentGold: '#b3a0f5',
      accentBrown: '#b3a3d8',
      accentBrownDark: '#c0b2e0',
    },
  },
  {
    key: 'sky',
    name: '海蓝',
    light: {
      primary: '#0e86c8',
      accent: '#0e86c8',
      accentStrong: '#0d77b4',
      accentStrong2: '#0d7ab8',
      accentStrong3: '#0c6fa8',
      accentDeep: '#0b66a0',
      accentGold: '#59b2e0',
      accentBrown: '#8fb8d0',
      accentBrownDark: '#74a6c2',
    },
    dark: {
      primary: '#5cb8ef',
      accent: '#5cb8ef',
      accentStrong: '#74c5f5',
      accentStrong2: '#74c5f5',
      accentStrong3: '#74c5f5',
      accentDeep: '#8fd2fa',
      accentGold: '#7cc0e8',
      accentBrown: '#8fb8d0',
      accentBrownDark: '#a0c6de',
    },
  },
]

/** 查找预设，未找到返回 null */
export function getThemeColorPreset(key) {
  return THEME_COLOR_PRESETS.find((p) => p.key === key) || null
}

/** '#c45d2b' -> '196, 93, 43'（供 rgba(var(--al-accent-rgb), x) 使用） */
export function hexToRgb(hex) {
  const raw = (hex || '').replace('#', '')
  if (raw.length !== 6) {
    return '0, 0, 0'
  }
  const n = parseInt(raw, 16)
  return `${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}`
}

/**
 * 生成主题色 CSS 变量样式。
 * 注入为 <style> 置于文档末尾，与 theme.css 选择器同等优先级但后声明，
 * 因此可覆盖 :root / .dark 中的默认强调色；模式切换仍由 .dark 类驱动。
 */
export function buildAccentStyleCss(preset) {
  const { light, dark } = preset
  const token = (c) => c

  const lightCss = [
    `--al-accent: ${token(light.accent)};`,
    `--al-accent-strong: ${token(light.accentStrong)};`,
    `--al-accent-strong-2: ${token(light.accentStrong2)};`,
    `--al-accent-strong-3: ${token(light.accentStrong3)};`,
    `--al-accent-deep: ${token(light.accentDeep)};`,
    `--al-accent-gold: ${token(light.accentGold)};`,
    `--al-accent-brown: ${token(light.accentBrown)};`,
    `--al-accent-brown-dark: ${token(light.accentBrownDark)};`,
    `--al-accent-rgb: ${hexToRgb(light.accent)};`,
    `--al-accent-brown-rgb: ${hexToRgb(light.accentBrownDark)};`,
  ].join('\n  ')

  const darkCss = [
    `--al-accent: ${token(dark.accent)};`,
    `--al-accent-strong: ${token(dark.accentStrong)};`,
    `--al-accent-strong-2: ${token(dark.accentStrong2)};`,
    `--al-accent-strong-3: ${token(dark.accentStrong3)};`,
    `--al-accent-deep: ${token(dark.accentDeep)};`,
    `--al-accent-gold: ${token(dark.accentGold)};`,
    `--al-accent-brown: ${token(dark.accentBrown)};`,
    `--al-accent-brown-dark: ${token(dark.accentBrownDark)};`,
    `--al-accent-rgb: ${hexToRgb(dark.accent)};`,
    `--al-accent-brown-rgb: ${hexToRgb(dark.accentBrownDark)};`,
  ].join('\n  ')

  return `:root {\n  ${lightCss}\n}\n.dark {\n  ${darkCss}\n}\n`
}
