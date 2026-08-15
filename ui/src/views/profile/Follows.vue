<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import PaginationBar from '../../components/PaginationBar.vue'
import { showAppMessage, askAppConfirm } from '../../utils/ui-feedback'
import {
  FOLLOW_STATUS_ORDER_MAP as STATUS_ORDER,
  FOLLOW_STATUS_LABEL as STATUS_LABEL,
  FOLLOW_STATUS_COLORS as STATUS_COLORS,
} from '../../utils/followStatus'
import { usePagination } from '../../composables/usePagination'
import { getFollows, getActiveFollows, getFollowsByStatus, setFollowStatus, removeFollow, bindFollow, matchFollow } from '../../api/follows'
import { searchDandanAnimes } from '../../api/anime'
import { pullBangumiCollections } from '../../api/bangumi'

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

// 绑定 / 匹配
const bindDialog = ref({ show: false, follow: null, keyword: '', results: [], searched: false, searching: false })
const matchDialog = ref({ show: false, follow: null })

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
      ? await getActiveFollows(params)
      : statusFilter.value
        ? await getFollowsByStatus(statusFilter.value, params)
        : await getFollows(params)
    if (res?.code !== 200) throw new Error(res?.msg || '加载追番失败')

    let items
    if (Array.isArray(res.data)) {
      items = [...res.data]
      total.value = items.length
    } else {
      items = [...(res.data?.content || [])]
      total.value = Number(res.data?.totalElements || 0)
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
  onPageChange: fetchData,
})

const applyFilter = (value) => {
  statusFilter.value = value
  page.value = 1
  fetchData()
}

const doSearch = () => {
  page.value = 1
  fetchData()
}

const goToAnime = (follow) => {
  if (follow?.animeId) router.push(`/anime/${follow.animeId}`)
}

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

const openBindDialog = (follow) => {
  menuId.value = null
  bindDialog.value = { show: true, follow, keyword: follow.animeTitle || '', results: [], searched: false, searching: false }
}

const searchBindAnime = async () => {
  if (!bindDialog.value.keyword.trim()) return
  bindDialog.value.searching = true
  try {
    const res = await searchDandanAnimes(bindDialog.value.keyword)
    const raw = res?.data
    const listRaw = raw?.animes || raw?.data?.animes || []
    bindDialog.value.results = Array.isArray(listRaw) ? listRaw : []
  } catch (e) { bindDialog.value.results = [] }
  finally { bindDialog.value.searching = false; bindDialog.value.searched = true }
}

const bindAnime = async (follow, anime) => {
  try {
    const title = anime.animeTitle || anime.title
    await bindFollow(follow.id, { animeId: anime.animeId, animeTitle: title, imageUrl: anime.imageUrl })
    showAppMessage(`已绑定「${title}」`, 'success')
    bindDialog.value.show = false
    await fetchData()
  } catch (e) { showAppMessage('绑定失败', 'error') }
}

const autoMatch = async (follow) => {
  menuId.value = null
  matchDialog.value = { show: true, follow }
  try {
    const body = await matchFollow(follow.id)
    if (body?.code === 200 && body.data?.matched) {
      matchDialog.value.show = false
      showAppMessage(`已匹配并绑定「${body.data.animeTitle || follow.animeTitle}」`, 'success')
      await fetchData()
      return
    }
    matchDialog.value.show = false
    if (body?.code === 200) openBindDialog(follow)
    else showAppMessage(body?.msg || '自动匹配失败', 'error')
  } catch (err) {
    matchDialog.value.show = false
    showAppMessage(err.response?.data?.msg || '自动匹配失败', 'error')
  }
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
onMounted(() => {
  fetchData()
  document.addEventListener('click', closeMenu)
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
        <div class="br-card-image" :class="{ unbound: !follow.animeId }" @click="follow.animeId ? goToAnime(follow) : autoMatch(follow)">
          <img v-if="follow.imageUrl" :src="follow.imageUrl" :alt="follow.animeTitle" loading="lazy" />
          <div v-else class="poster-ph"><i class="mdi mdi-image-off-outline"></i></div>
          <div class="poster-hover"><i class="mdi" :class="follow.animeId ? 'mdi-play-circle-outline' : 'mdi-link-variant'"></i></div>
          <span v-if="follow.unreadEpisodeCount > 0" class="follow-unread" title="未读新剧集">{{ follow.unreadEpisodeCount }}</span>
          <span v-if="!follow.animeId" class="unbound-tag">未绑定</span>
        </div>
        <div class="br-card-body">
          <h4 :title="follow.animeTitle">{{ follow.animeTitle }}</h4>
          <div class="br-card-meta">
            <span class="genre" :style="{ background: statusColor(follow.status) + '22', color: statusColor(follow.status), fontWeight: 600 }">
              {{ statusLabel(follow.status) }}
            </span>
            <div class="follow-menu-wrap" @click.stop>
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
                <template v-if="follow.animeId">
                  <div class="menu-divider"></div>
                  <button class="menu-item danger" @click="unfollow(follow)"><i class="mdi mdi-close"></i> 取消追番</button>
                </template>
                <template v-else>
                  <div class="menu-divider"></div>
                  <button class="menu-item" @click="openBindDialog(follow)"><i class="mdi mdi-link-variant"></i> 手动绑定</button>
                  <button class="menu-item" @click="autoMatch(follow)"><i class="mdi mdi-auto-fix"></i> 自动匹配</button>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <PaginationBar :page="page" :total-pages="totalPages" :pages="pages" :total-text="`共 ${total} 部`" @change="changePage" />

    <!-- 手动绑定弹窗 -->
    <div v-if="bindDialog.show" class="dialog-overlay" @click.self="bindDialog.show = false">
      <div class="dialog">
        <div class="dialog-head">
          <h3>绑定番剧</h3>
          <button class="dialog-close" @click="bindDialog.show = false"><i class="mdi mdi-close"></i></button>
        </div>
        <div class="dialog-body">
          <p class="dialog-hint">「{{ bindDialog.follow?.animeTitle }}」尚未匹配到本地番剧，手动选择后即可播放。</p>
          <div class="bind-search">
            <input v-model="bindDialog.keyword" placeholder="输入番剧名搜索" @keyup.enter="searchBindAnime" />
            <button class="btn btn-primary" :disabled="bindDialog.searching" @click="searchBindAnime">
              {{ bindDialog.searching ? '搜索中...' : '搜索' }}
            </button>
          </div>
          <div v-if="bindDialog.results.length" class="bind-results">
            <button
              v-for="anime in bindDialog.results"
              :key="anime.animeId"
              class="bind-result"
              @click="bindAnime(bindDialog.follow, anime)"
            >
              <img v-if="anime.imageUrl" :src="anime.imageUrl" alt="" loading="lazy" />
              <div>
                <div class="bind-result-title">{{ anime.animeTitle || anime.title }}</div>
                <div class="bind-result-meta">ID: {{ anime.animeId }}</div>
              </div>
            </button>
          </div>
          <div v-else-if="bindDialog.searched" class="bind-empty">未找到相关番剧</div>
        </div>
      </div>
    </div>

    <!-- 自动匹配弹窗 -->
    <div v-if="matchDialog.show" class="dialog-overlay">
      <div class="dialog">
        <div class="dialog-body match-loading">
          <i class="mdi mdi-loading mdi-spin"></i>
          <p>正在自动匹配「{{ matchDialog.follow?.animeTitle }}」...</p>
        </div>
      </div>
    </div>
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
.pill.active { background: rgba(196, 93, 43, 0.1); border-color: var(--anime-accent-red); color: var(--anime-accent-red); font-weight: 600; }

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
.search-box:focus-within { border-color: var(--anime-accent-red); background: var(--al-bg); box-shadow: 0 0 0 4px rgba(196, 93, 43, 0.12); }
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
.follow-card .br-card-image.unbound { opacity: 0.82; }
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

/* 弹窗 */
.dialog-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 2000; animation: fade 0.2s ease;
}
@keyframes fade { from { opacity: 0; } to { opacity: 1; } }
.dialog {
  background: var(--al-bg); border-radius: 16px;
  width: 90%; max-width: 460px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  animation: rise 0.25s ease;
}
@keyframes rise { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
.dialog-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid var(--al-border-neutral);
}
.dialog-head h3 { margin: 0; font-size: 16px; }
.dialog-close { border: none; background: none; color: var(--anime-text-secondary); font-size: 20px; cursor: pointer; }
.dialog-body { padding: 20px; }
.confirm-message { margin: 0; font-size: 14px; color: var(--anime-text-secondary); line-height: 1.6; }
.dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--al-border-neutral);
}
.dialog-hint { margin: 0 0 14px; font-size: 13px; color: var(--anime-text-secondary); line-height: 1.6; }

.bind-search { display: flex; gap: 8px; }
.bind-search input {
  flex: 1; border: 1.5px solid var(--al-border-input); border-radius: 10px;
  padding: 9px 12px; font-size: 13px; outline: none; font-family: inherit;
}
.bind-search input:focus { border-color: var(--anime-accent-red); }

.bind-results { display: flex; flex-direction: column; gap: 8px; margin-top: 14px; max-height: 300px; overflow-y: auto; }
.bind-result {
  display: flex; align-items: center; gap: 10px;
  border: 1px solid var(--al-border-panel); border-radius: 12px;
  padding: 8px; background: var(--al-bg); cursor: pointer;
  transition: border-color 0.2s;
  text-align: left;
}
.bind-result:hover { border-color: var(--anime-accent-red); }
.bind-result img { width: 42px; height: 56px; object-fit: cover; border-radius: 8px; background: var(--al-border-neutral); }
.bind-result-title { font-size: 13px; font-weight: 600; color: var(--anime-text-main); }
.bind-result-meta { font-size: 12px; color: var(--anime-text-secondary); margin-top: 2px; }
.bind-empty { padding: 20px; text-align: center; color: var(--anime-text-secondary); font-size: 13px; }

.match-loading { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 30px; }
.match-loading i { font-size: 2rem; color: var(--anime-accent-red); }
.match-loading p { margin: 0; font-size: 13px; color: var(--anime-text-secondary); }
</style>
