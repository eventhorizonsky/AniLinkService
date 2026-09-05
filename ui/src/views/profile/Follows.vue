<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import { showAppMessage, askAppConfirm } from '../../utils/ui-feedback'
import {
  FOLLOW_STATUS_ORDER_MAP as STATUS_ORDER,
  FOLLOW_STATUS_LABEL as STATUS_LABEL,
  FOLLOW_STATUS_COLORS as STATUS_COLORS,
} from '../../utils/followStatus'
import { usePagination } from '../../composables/usePagination'
import { getFollows, getActiveFollowsPage, getFollowsByStatusPage, setFollowStatus, removeFollow } from '../../api/follows'
import { pullBangumiCollections } from '../../api/bangumi'

const route = useRoute()
const router = useRouter()

const list = ref([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const statusFilter = ref('active')
const keyword = ref('')
const updatingId = ref(null)
const menuId = ref(null) // 当前展开状态菜单的 follow.id
const pulling = ref(false)

const statusOptions = [
  { label: '活跃', value: 'active' },
  { label: '全部', value: '' },
  { label: '想看', value: 'wish' },
  { label: '在看', value: 'watching' },
  { label: '看过', value: 'watched' },
  { label: '搁置', value: 'on_hold' },
  { label: '抛弃', value: 'dropped' }
]

const fetchData = async () => {
  loading.value = true; error.value = ''
  try {
    const params = { page: page.value, pageSize: pageSize.value, keyword: keyword.value.trim() }
    const res = statusFilter.value === 'active'
      ? await getActiveFollowsPage(params)
      : statusFilter.value
        ? await getFollowsByStatusPage(statusFilter.value, params)
        : await getFollows(params)
    if (res?.code !== 200) throw new Error(res?.msg || '加载追番失败')

    const data = res.data || {}
    const items = [...(data.content || [])]
    total.value = Number(data.totalElements || 0)
    // 当前页超出总页数（如末页仅剩的条目被移除/改状态）时，回退到最后一页重取
    if (!items.length && total.value > 0 && page.value > totalPages.value) {
      page.value = totalPages.value
      syncQuery()
      return fetchData()
    }
    // 活跃视图保持接口的更新时间倒序（新更新的在前），不按状态重排
    if (statusFilter.value !== 'active') {
      items.sort((a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99))
    }
    list.value = items
  } catch (e) {
    error.value = e?.response?.data?.msg || e?.message || '加载追番失败'
    list.value = []
  } finally { loading.value = false }
}

const { page, pageSize, totalPages, pages, changePage } = usePagination({
  pageSize: 24,
  getTotal: () => total.value,
  onPageChange: () => { syncQuery(); fetchData() },
})

// 视图状态（tab/页码/搜索词）写入 URL query：进入详情后返回、或刷新页面都能还原
// query 中「全部」用 all 表示（避免空值），其余与 statusFilter 值一致
const statusFromQuery = (q) => {
  if (q === 'all') return ''
  return statusOptions.some((o) => o.value === q) ? q : 'active'
}
const pageFromQuery = (q) => {
  const n = parseInt(q, 10)
  return Number.isFinite(n) && n >= 1 ? n : 1
}
const buildQuery = () => {
  const q = {}
  if (statusFilter.value !== 'active') q.status = statusFilter.value === '' ? 'all' : statusFilter.value
  if (page.value > 1) q.page = String(page.value)
  const kw = keyword.value.trim()
  if (kw) q.kw = kw
  return q
}
const syncQuery = () => {
  const next = buildQuery()
  const keys = Object.keys(next)
  const same = keys.length === Object.keys(route.query).length
    && keys.every((k) => String(route.query[k]) === next[k])
  if (!same) router.replace({ query: next })
}

// 首次挂载从 URL 还原视图状态（默认视图无 query）
statusFilter.value = statusFromQuery(route.query.status)
keyword.value = typeof route.query.kw === 'string' ? route.query.kw : ''
page.value = pageFromQuery(route.query.page)

const applyFilter = (value) => {
  statusFilter.value = value
  page.value = 1
  syncQuery()
  fetchData()
}

const doSearch = () => {
  page.value = 1
  syncQuery()
  fetchData()
}

// 返回本页时恢复离开前的滚动位置：点击卡片离开前记录 {路径, 滚动位置}（sessionStorage 一次），
// 挂载后路径一致（从详情返回/刷新）才恢复，从菜单重新进入则不恢复
const RETURN_KEY = 'follows:return'
const currentScrollTop = () => document.querySelector('.app-content')?.scrollTop || 0
const saveReturnView = () => {
  try {
    sessionStorage.setItem(RETURN_KEY, JSON.stringify({ path: route.fullPath, top: currentScrollTop() }))
  } catch (e) { /* 存储不可用时跳过 */ }
}
const restoreReturnView = async () => {
  let saved = null
  try { saved = JSON.parse(sessionStorage.getItem(RETURN_KEY) || 'null') } catch (e) { saved = null }
  sessionStorage.removeItem(RETURN_KEY)
  if (!saved || saved.path !== route.fullPath || !saved.top) return
  await nextTick()
  const el = document.querySelector('.app-content')
  if (el) el.scrollTop = saved.top
}

// 进入详情：已绑定本地番剧直接看；仅有 Bangumi 关联时走 bgmMode 详情路由，
// 由详情页按 bgmid 查询弹弹并（找到时）自动绑定到本追番记录；
// 两者都没有（老数据或后端未返回 bangumiSubjectId）时兜底跳资料库按标题搜索
const goToAnime = (follow) => {
  saveReturnView()
  if (follow?.animeId) {
    router.push(`/anime/${follow.animeId}`)
    return
  }
  if (follow?.bangumiSubjectId) {
    router.push({
      path: `/anime/bgm/${follow.bangumiSubjectId}`,
      query: { follow: follow.id, name: follow.animeTitle || '' },
    })
    return
  }
  router.push({ path: '/search', query: { tab: 'database', dbq: follow.animeTitle || '' } })
}

const isBgmOnly = (follow) => !follow?.animeId && Boolean(follow?.bangumiSubjectId)
const navIcon = (follow) => (follow?.animeId ? 'mdi-play-circle-outline' : follow?.bangumiSubjectId ? 'mdi-open-in-new' : 'mdi-magnify')

const statusLabel = (s) => STATUS_LABEL[s] || s || '-'
const statusColor = (s) => STATUS_COLORS[s] || '#9e8c7e'

const toggleMenu = (id) => { menuId.value = menuId.value === id ? null : id }

const setStatus = async (follow, status) => {
  menuId.value = null
  if (!follow.animeId || follow.status === status) return
  updatingId.value = follow.animeId
  try {
    const res = await setFollowStatus(follow.animeId, status)
    if (res?.code === 200) await fetchData()
    else showAppMessage(res?.msg || '更新状态失败', 'error')
  } catch (e) {
    showAppMessage(e.response?.data?.msg || '更新状态失败', 'error')
  } finally { updatingId.value = null }
}

const unfollow = async (follow) => {
  menuId.value = null
  const ok = await askAppConfirm({ title: '取消追番', message: `确定要取消追番《${follow.animeTitle}》吗？`, confirmText: '取消追番' })
  if (!ok) return
  try {
    const res = await removeFollow(follow.animeId)
    if (res?.code === 200) await fetchData()
    else showAppMessage(res?.msg || '取消追番失败', 'error')
  } catch (e) { showAppMessage('取消追番失败', 'error') }
}

const pullBangumi = async () => {
  const ok = await askAppConfirm({
    title: '拉取 Bangumi 追番',
    message: '将从 Bangumi 拉取你的所有动画收藏并同步到本地追番列表。以 Bangumi 数据为准，同名番剧的状态将被覆盖。是否继续？',
    confirmText: '开始拉取'
  })
  if (!ok) return
  pulling.value = true
  try {
    const res = await pullBangumiCollections()
    if (res?.code === 200 && res?.data) {
      const d = res.data
      showAppMessage(`同步完成：共 ${d.total} 条，新增 ${d.created}，更新 ${d.updated}，跳过 ${d.skipped}`, 'success')
      await fetchData()
    } else showAppMessage(res?.msg || '拉取失败', 'error')
  } catch (e) { showAppMessage('拉取 Bangumi 追番失败', 'error') }
  finally { pulling.value = false }
}

// 点击外部关闭状态菜单
const closeMenu = (e) => {
  if (menuId.value && !e.target.closest('.follow-menu-wrap')) menuId.value = null
}
onMounted(async () => {
  document.addEventListener('click', closeMenu)
  await fetchData()
  restoreReturnView()
})
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
  <div class="follows-page">
    <div class="page-head">
      <h2><i class="mdi mdi-bookmark-multiple"></i> 我的追番</h2>
      <div class="page-head-actions">
        <button class="btn btn-ghost" :disabled="pulling" @click="pullBangumi">
          <i class="mdi mdi-sync"></i> {{ pulling ? '同步中...' : '拉取 Bangumi' }}
        </button>
      </div>
    </div>

    <!-- 筛选 + 搜索 -->
    <div class="follow-toolbar">
      <div class="filter-pills">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="pill"
          :class="{ active: statusFilter === opt.value }"
          @click="applyFilter(opt.value)"
        >{{ opt.label }}</button>
      </div>
      <div class="search-box">
        <i class="mdi mdi-magnify"></i>
        <input
          v-model="keyword"
          placeholder="搜索番剧..."
          @keyup.enter="doSearch"
        />
        <button v-if="keyword" class="clear-btn" @click="keyword = ''; doSearch()"><i class="mdi mdi-close"></i></button>
      </div>
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="br-sk-grid">
      <div v-for="i in 12" :key="i" class="br-sk-card"></div>
    </div>
    <div v-else-if="error" class="br-empty error"><i class="mdi mdi-alert-circle-outline"></i> {{ error }}</div>
    <div v-else-if="!list.length" class="empty-state">
      <i class="mdi mdi-bookmark-off-outline"></i>
      <p>还没有追番记录</p>
      <router-link to="/search" class="btn btn-primary"><i class="mdi mdi-compass"></i> 去发现番剧</router-link>
    </div>
    <div v-else class="br-grid">
      <div
        v-for="follow in list"
        :key="follow.id"
        class="br-card follow-card"
        :class="{ 'menu-open': menuId === follow.id }"
      >
        <div class="br-card-image" :class="{ 'bgm-only': isBgmOnly(follow) }" @click="goToAnime(follow)">
          <img v-if="follow.imageUrl" :src="follow.imageUrl" :alt="follow.animeTitle" loading="lazy" />
          <div v-else class="poster-ph"><i class="mdi mdi-image-off-outline"></i></div>
          <div class="poster-hover"><i class="mdi" :class="navIcon(follow)"></i></div>
          <span v-if="follow.unreadEpisodeCount > 0" class="follow-unread" title="未读新剧集">{{ follow.unreadEpisodeCount }}</span>
          <span v-if="isBgmOnly(follow)" class="unbound-tag" title="仅关联了 Bangumi，点击进入详情将自动查询并绑定弹弹片源">Bangumi</span>
        </div>
        <div class="br-card-body">
          <h4 :title="follow.animeTitle">{{ follow.animeTitle }}</h4>
          <div class="br-card-meta">
            <span class="genre" :style="{ background: statusColor(follow.status) + '22', color: statusColor(follow.status), fontWeight: 600 }">
              {{ statusLabel(follow.status) }}
            </span>
            <div v-if="follow.animeId" class="follow-menu-wrap" @click.stop>
              <button class="more-btn" :disabled="updatingId === follow.animeId" @click="toggleMenu(follow.id)">
                <i class="mdi mdi-dots-horizontal"></i>
              </button>
              <div v-if="menuId === follow.id" class="menu-panel">
                <button
                  v-for="s in statusOptions.slice(2)"
                  :key="s.value"
                  class="menu-item"
                  :class="{ selected: follow.status === s.value }"
                  :style="{ color: STATUS_COLORS[s.value] }"
                  @click="setStatus(follow, s.value)"
                >
                  <span class="dot" :style="{ background: STATUS_COLORS[s.value] }"></span>{{ s.label }}
                </button>
                <div class="menu-divider"></div>
                <button class="menu-item danger" @click="unfollow(follow)"><i class="mdi mdi-close"></i> 取消追番</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <PaginationBar :page="page" :total-pages="totalPages" :pages="pages" :total-text="`共 ${total} 部`" @change="changePage" />
  </div>
</template>

<style scoped>
.follows-page { animation: in 0.35s ease-out; }
@keyframes in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.follow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-pills { display: flex; gap: 8px; flex-wrap: wrap; }
.pill {
  border: 1px solid var(--al-border-input);
  background: var(--al-bg);
  color: var(--anime-text-secondary);
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.pill:hover { border-color: var(--anime-accent-red); color: var(--anime-accent-red); }
.pill.active { background: rgba(var(--al-accent-rgb), 0.1); border-color: var(--anime-accent-red); color: var(--anime-accent-red); font-weight: 600; }

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--al-bg-soft);
  border: 1.5px solid var(--al-border-input);
  border-radius: 999px;
  padding: 0 14px;
  min-width: 220px;
  transition: all 0.2s;
}
.search-box:focus-within { border-color: var(--anime-accent-red); background: var(--al-bg); box-shadow: 0 0 0 4px rgba(var(--al-accent-rgb), 0.12); }
.search-box i { color: var(--al-text-placeholder); }
.search-box input { flex: 1; border: none; outline: none; background: transparent; padding: 9px 0; font-size: 13px; font-family: inherit; color: var(--anime-text-main); }
.search-box .clear-btn { border: none; background: none; color: var(--al-text-placeholder); cursor: pointer; padding: 0; display: flex; }
.search-box .clear-btn:hover { color: var(--anime-accent-red); }

.follow-card { overflow: visible; }
.follow-card.menu-open {
  z-index: 30;
  position: relative;
}
.follow-card .br-card-image {
  cursor: pointer;
  border-radius: 14px 14px 0 0;
}
.follow-card .br-card-image.bgm-only { opacity: 0.85; }
.poster-ph {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: var(--al-gray-muted); font-size: 2rem;
  background: linear-gradient(135deg, var(--al-bg-beige), var(--al-bg-beige-13));
}
.unbound-tag {
  position: absolute; top: 12px; right: 12px;
  background: rgba(107, 114, 128, 0.85);
  color: #fff; font-size: 10px; font-weight: 600;
  padding: 2px 10px; border-radius: 999px;
}

/* 未读新剧集角标 */
.follow-unread {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 3;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--al-danger-hot);
  color: var(--al-text-on-accent);
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(229, 57, 53, 0.45);
}

.follow-menu-wrap { position: relative; display: flex; }
.more-btn {
  width: 28px; height: 28px;
  border: none; background: transparent;
  color: var(--anime-text-secondary);
  border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s;
}
.more-btn:hover:not(:disabled) { background: var(--al-border-neutral); color: var(--anime-accent-red); }
.more-btn:disabled { opacity: 0.5; }

.menu-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 60;
  background: var(--al-bg);
  border: 1px solid var(--al-border-panel);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14);
  padding: 6px;
  min-width: 150px;
  animation: pop 0.15s ease;
}
@keyframes pop { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }
.menu-item {
  display: flex; align-items: center; gap: 8px;
  width: 100%; border: none; background: none;
  padding: 8px 10px; border-radius: 8px;
  font-size: 13px; cursor: pointer; transition: background 0.15s;
  text-align: left;
}
.menu-item:hover { background: var(--al-border-hover-2); }
.menu-item.selected { font-weight: 700; }
.menu-item .dot { width: 9px; height: 9px; border-radius: 50%; }
.menu-item.danger { color: var(--al-danger); }
.menu-divider { height: 1px; background: var(--al-border-neutral); margin: 4px 0; }

.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 70px 20px; color: var(--anime-text-secondary);
}
.empty-state i { font-size: 3rem; opacity: 0.35; color: var(--anime-accent-red); }
.empty-state p { margin: 0; font-size: 14px; }
</style>
