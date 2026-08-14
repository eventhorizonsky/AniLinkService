// 弹幕模式标签（弹弹play：1=普通 4=底部 5=顶部）。
// 此前 Danmaku.vue 与 AdminDanmaku.vue 各自定义，统一收敛到这里。

export const DANMAKU_MODE_LABELS = { 1: '普通', 4: '底部', 5: '顶部' }

export const danmakuModeLabel = (mode) => DANMAKU_MODE_LABELS[mode] || String(mode ?? '')
