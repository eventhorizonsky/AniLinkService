<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { API_BASE } from '../utils/constants'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { userInfo, setUserInfo } = useAuth()

const loading = ref(true)
const stats = ref({ follows: null, history: null, danmaku: null, unread: null })

const initial = computed(() => (userInfo.value?.username || '用').charAt(0).toUpperCase())
const roleLabel = computed(() => {
  const roles = userInfo.value?.roleCodeList || []
  if (roles.includes('super-admin')) return '超级管理员'
  if (roles.includes('admin')) return '管理员'
  return '普通用户'
})

const statCards = computed(() => [
  { label: '追番', icon: 'mdi-bookmark-multiple', value: stats.value.follows, to: '/profile/follows', color: '#c45d2b' },
  { label: '观看历史', icon: 'mdi-history', value: stats.value.history, to: '/profile/history', color: '#1e7b6b' },
  { label: '弹幕', icon: 'mdi-comment-text-multiple', value: stats.value.danmaku, to: '/profile/danmaku', color: '#8b5cf6' },
  { label: '未读消息', icon: 'mdi-bell-outline', value: stats.value.unread, to: '/profile/messages', color: '#ef4444' }
])

const quickLinks = [
  { label: '观看历史', icon: 'mdi-history', desc: '继续上次的播放', to: '/profile/history' },
  { label: '我的追番', icon: 'mdi-bookmark-multiple', desc: '管理你的收藏', to: '/profile/follows' },
  { label: '我的弹幕', icon: 'mdi-comment-text-multiple', desc: '查看发过的弹幕', to: '/profile/danmaku' },
  { label: '消息中心', icon: 'mdi-bell-outline', desc: '剧集更新与系统通知', to: '/profile/messages' },
  { label: '账号绑定', icon: 'mdi-link-variant', desc: '绑定 Bangumi 账号', to: '/profile/binding' }
]

const fmt = (v) => (v == null ? '--' : Number(v).toLocaleString('zh-CN'))

const fetchStats = async () => {
  const [u, f, h, d, m] = await Promise.allSettled([
    axios.post(`${API_BASE}/auth/currentUser`),
    axios.get(`${API_BASE}/follows`, { params: { page: 1, pageSize: 1 } }),
    axios.get(`${API_BASE}/play-history`, { params: { page: 1, pageSize: 1 } }),
    axios.get(`${API_BASE}/v2/danmaku-records/mine`, { params: { page: 1, pageSize: 1 } }),
    axios.get(`${API_BASE}/messages/unread-count`)
  ])
  if (u.status === 'fulfilled' && u.value.data?.code === 200 && u.value.data.data) {
    setUserInfo(u.value.data.data)
  }
  if (f.status === 'fulfilled') stats.value.follows = Number(f.value.data?.data?.totalElements ?? f.value.data?.data?.length ?? 0)
  if (h.status === 'fulfilled') stats.value.history = Number(h.value.data?.data?.totalElements ?? 0)
  if (d.status === 'fulfilled') stats.value.danmaku = Number(d.value.data?.data?.totalElements ?? 0)
  if (m.status === 'fulfilled') stats.value.unread = Number(m.value.data?.data?.unreadCount ?? 0)
  loading.value = false
}

onMounted(fetchStats)
</script>

<template>
  <div class="profile-page">
    <div class="page-head">
      <h2><i class="mdi mdi-account-circle-outline"></i> 个人中心</h2>
    </div>

    <div v-if="loading" class="profile-skeleton">
      <div class="sk-user"></div>
      <div class="sk-grid-row"><div v-for="i in 4" :key="i" class="sk-stat"></div></div>
    </div>

    <template v-else>
      <!-- 用户卡片 -->
      <div class="user-card">
        <div class="user-avatar">{{ initial }}</div>
        <div class="user-info">
          <div class="user-name-row">
            <h3>{{ userInfo?.username || '用户' }}</h3>
            <span class="role-chip">{{ roleLabel }}</span>
            <span v-if="userInfo?.bangumiBound" class="bangumi-chip">
              <i class="mdi mdi-link-variant"></i> Bangumi 已绑定
            </span>
          </div>
          <p class="user-email">{{ userInfo?.email || '未设置邮箱' }}</p>
          <p v-if="userInfo?.bangumiNickname" class="user-bangumi">Bangumi：{{ userInfo.bangumiNickname }}</p>
        </div>
        <router-link to="/profile/binding" class="btn btn-ghost">
          <i class="mdi mdi-cog-outline"></i> 账号设置
        </router-link>
      </div>

      <!-- 数据总览 -->
      <div class="stat-grid">
        <router-link v-for="card in statCards" :key="card.label" :to="card.to" class="stat-card">
          <span class="stat-icon" :style="{ color: card.color, background: card.color + '1a' }">
            <i class="mdi" :class="card.icon"></i>
          </span>
          <div class="stat-body">
            <span class="stat-value">{{ fmt(card.value) }}</span>
            <span class="stat-label">{{ card.label }}</span>
          </div>
          <i class="mdi mdi-chevron-right stat-arrow"></i>
        </router-link>
      </div>

      <!-- 快捷入口 -->
      <div class="page-head" style="margin-top: 26px;">
        <h2 style="font-size: 17px;"><i class="mdi mdi-view-grid-outline"></i> 功能入口</h2>
      </div>
      <div class="quick-grid">
        <router-link v-for="q in quickLinks" :key="q.to" :to="q.to" class="quick-card">
          <i class="mdi quick-icon" :class="q.icon"></i>
          <div class="quick-info">
            <span class="quick-label">{{ q.label }}</span>
            <span class="quick-desc">{{ q.desc }}</span>
          </div>
          <i class="mdi mdi-chevron-right quick-arrow"></i>
        </router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.profile-skeleton { display: flex; flex-direction: column; gap: 18px; }
.sk-user, .sk-stat {
  background: linear-gradient(135deg, var(--anime-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--anime-bg-beige) 75%);
  background-size: 200% 100%;
  animation: br-shim 1.4s ease-in-out infinite;
}
.sk-user { height: 120px; border-radius: 16px; }
.sk-grid-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.sk-stat { height: 90px; border-radius: 14px; }

/* 用户卡片 */
.user-card {
  display: flex; align-items: center; gap: 18px; flex-wrap: wrap;
  background: linear-gradient(120deg, var(--al-bg) 0%, var(--al-bg-gradient-1) 60%, var(--al-bg-gradient-2) 100%);
  border: 1px solid var(--al-border-panel); border-radius: 16px;
  padding: 22px 26px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 6px 24px rgba(0, 0, 0, 0.05);
}
.user-avatar {
  width: 68px; height: 68px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(196, 93, 43, 0.2), rgba(179, 129, 91, 0.3));
  color: var(--anime-accent-red);
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; font-weight: 700;
  border: 2px solid rgba(196, 93, 43, 0.2);
  flex-shrink: 0;
}
.user-info { flex: 1; min-width: 200px; }
.user-name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.user-name-row h3 { margin: 0; font-size: 1.25rem; color: var(--anime-text-main); }
.role-chip {
  font-size: 11px; font-weight: 600;
  background: rgba(196, 93, 43, 0.12); color: var(--anime-accent-red);
  padding: 2px 10px; border-radius: 999px;
}
.bangumi-chip {
  font-size: 11px; font-weight: 600;
  background: rgba(30, 123, 107, 0.1); color: var(--al-accent-teal);
  padding: 2px 10px; border-radius: 999px;
  display: inline-flex; align-items: center; gap: 4px;
}
.user-email { margin: 4px 0 0; font-size: 13px; color: var(--anime-text-secondary); }
.user-bangumi { margin: 2px 0 0; font-size: 12px; color: var(--anime-text-secondary); opacity: 0.85; }

/* 数据总览 */
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-top: 18px; }
.stat-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--al-bg); border: 1px solid var(--al-border-panel); border-radius: 14px;
  padding: 16px; text-decoration: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s, box-shadow 0.25s, border-color 0.25s;
}
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); border-color: rgba(196, 93, 43, 0.2); }
.stat-icon {
  width: 42px; height: 42px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; flex-shrink: 0;
}
.stat-body { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.stat-value { font-size: 1.3rem; font-weight: 700; color: var(--anime-text-main); line-height: 1.2; }
.stat-label { font-size: 12px; color: var(--anime-text-secondary); }
.stat-arrow { color: var(--al-gray-arrow); }

/* 快捷入口 */
.quick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.quick-card {
  display: flex; align-items: center; gap: 14px;
  background: var(--al-bg); border: 1px solid var(--al-border-panel); border-radius: 14px;
  padding: 16px; text-decoration: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: transform 0.25s, box-shadow 0.25s, border-color 0.25s;
}
.quick-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); border-color: rgba(196, 93, 43, 0.2); }
.quick-icon {
  width: 44px; height: 44px; border-radius: 12px;
  background: var(--anime-bg-beige);
  color: var(--anime-accent-red);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; flex-shrink: 0;
}
.quick-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.quick-label { font-size: 14px; font-weight: 600; color: var(--anime-text-main); }
.quick-desc { font-size: 12px; color: var(--anime-text-secondary); margin-top: 2px; }
.quick-arrow { color: var(--al-gray-arrow); }

@media (max-width: 900px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .sk-grid-row { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 480px) {
  .stat-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .quick-grid { grid-template-columns: 1fr; }
}
</style>
