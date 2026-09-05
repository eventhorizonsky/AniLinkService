// 全局共享常量。
// 此前 API_BASE、defaultPoster、角色等级等在 30+ 个文件中各自重复声明，统一收敛到这里。

export const API_BASE = '/api'

export const DEFAULT_POSTER = 'https://assets.anixplayer.net/image/poster/default.jpg'

export const DEFAULT_SITE_NAME = 'AniLink'

export const BANGUMI_BASE_URL = 'https://bgm.tv/'

// Bangumi 官方 Access Token 申请入口（next 客户端开发用 Demo 应用，登录后即可生成）
export const BANGUMI_ACCESS_TOKEN_URL = 'https://next.bgm.tv/demo/access-token'

// 「猜你喜欢」推荐算法参考实现（czy0729/Bangumi 客户端）
export const BANGUMI_RECOMMEND_ALGO_URL = 'https://github.com/czy0729/Bangumi'

export const DEFAULT_DANDAN_BASE_URL = 'https://api.dandanplay.net'

// 弹弹play 官网 / Android 客户端开源仓库（编码不支持时引导用户安装使用）
export const DANDPANPLAY_OFFICIAL_URL = 'https://www.dandanplay.com/'
export const DANDPANPLAY_ANDROID_URL = 'https://gitee.com/xyoye/DanDanPlayForAndroid/releases'
// Android 端弹弹play 应用包名（intent:// 精确唤起用）
export const DANDPANPLAY_ANDROID_PACKAGE = 'com.xyoye.dandanplay'

// 星期标签（0=周日，7 也代表周日），用于放送日展示。
export const WEEKDAY_LABELS = {
  0: '周日',
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

// 角色优先级：数字越大权限越高，供“满足某角色即可”的鉴权判断使用。
export const ROLE_LEVEL = {
  user: 1,
  admin: 2,
  'super-admin': 3,
}

/**
 * 是否为超级管理员（拥有 super-admin 角色码）。
 * 之前该判断散落在 Admin.vue / MainLayout.vue / Player.vue 中。
 */
export const isSuperAdmin = (userInfo) =>
  Array.isArray(userInfo?.roleCodeList) && userInfo.roleCodeList.includes('super-admin')

/**
 * 判断用户角色是否满足 requiredRole（支持 user/admin/super-admin 的等级比较，
 * 也兼容“直接包含角色码”的情况）。与 router 守卫、MainLayout 中的逻辑保持一致。
 */
export const hasRoleLevel = (userInfo, requiredRoleRaw) => {
  const requiredRole = (requiredRoleRaw || 'user').toString().trim()
  const requiredLevel = ROLE_LEVEL[requiredRole]
  const roleCodes = Array.isArray(userInfo?.roleCodeList) ? userInfo.roleCodeList : []

  if (!requiredLevel) {
    return roleCodes.includes(requiredRole)
  }
  return roleCodes.some((role) => {
    const level = ROLE_LEVEL[role]
    return typeof level === 'number' && level >= requiredLevel
  })
}
