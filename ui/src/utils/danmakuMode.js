// 弹幕模式标签（弹弹play：1=普通 4=底部 5=顶部）。
// 此前 Danmaku.vue 与 AdminDanmaku.vue 各自定义，统一收敛到这里。

export const DANMAKU_MODE_LABELS = { 1: '普通', 4: '底部', 5: '顶部' }

export const danmakuModeLabel = (mode) => DANMAKU_MODE_LABELS[mode] || String(mode ?? '')

// 弹幕模式双向映射（Player.vue 发送/渲染弹幕时使用）。
// Artplayer：0=滚动 1=顶部 2=底部；弹弹play：1=普通(滚动) 4=底部 5=顶部
export const ARTPLAYER_TO_DANDAN_MODE = { 0: 1, 1: 5, 2: 4 }
export const DANDAN_TO_ARTPLAYER_MODE = { 1: 0, 4: 2, 5: 1 }
