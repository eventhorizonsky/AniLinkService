<template>
  <div class="rank-panel">
    <!-- ===== 筛选工具条（参照 Bangumi 客户端「排行榜」：排序/类型/年份/月份 + 更多筛选项） ===== -->
    <div class="rk-toolbar">
      <div class="rk-row">
        <div class="rk-sel">
          <label>排序</label>
          <select v-model="filters.sort" @change="onFiltersChange">
            <option v-for="o in sortOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
        <div class="rk-sel">
          <label>类型</label>
          <select v-model="filters.platform" @change="onFiltersChange">
            <option v-for="o in platformOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
        <div class="rk-sel">
          <label>年份</label>
          <select v-model="filters.year" @change="onFiltersChange">
            <option value="">全部</option>
            <option v-for="y in yearOptions" :key="y" :value="y">{{ y }}</option>
          </select>
        </div>
        <div class="rk-sel" :class="{ disabled: !filters.year }">
          <label>月份</label>
          <select v-model="filters.month" :disabled="!filters.year" @change="onFiltersChange">
            <option value="">全部</option>
            <option v-for="m in monthOptions" :key="m" :value="m">{{ m }}月</option>
          </select>
        </div>
        <button class="rk-more-btn" :class="{ active: expanded }" @click="expanded = !expanded">
          <i class="mdi" :class="expanded ? 'mdi-chevron-up' : 'mdi-chevron-down'"></i>
          {{ expanded ? '收起筛选' : '更多筛选' }}
        </button>
        <button class="rk-reset-btn" :disabled="!hasActiveFilter" @click="onReset">
          <i class="mdi mdi-restore"></i>重置
        </button>
        <button class="rk-reset-btn" :disabled="loading" title="重新加载" @click="onRefresh">
          <i class="mdi mdi-refresh"></i>刷新
        </button>
      </div>

      <div v-if="expanded" class="rk-row rk-row-more">
        <div class="rk-sel">
          <label>来源</label>
          <select v-model="filters.source" @change="onFiltersChange">
            <option v-for="o in sourceOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
        <div class="rk-sel">
          <label>题材</label>
          <select v-model="filters.tag" @change="onFiltersChange">
            <option v-for="o in tagOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
        <div class="rk-sel">
          <label>地区</label>
          <select v-model="filters.area" @change="onFiltersChange">
            <option v-for="o in areaOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
        <div class="rk-sel">
          <label>受众</label>
          <select v-model="filters.target" @change="onFiltersChange">
            <option v-for="o in targetOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
      </div>
    </div>

    <div class="rk-body">
      <!-- 加载中骨架 -->
      <div v-if="loading && !items.length" class="rk-grid-skel">
        <div v-for="i in 12" :key="i" class="rk-skel-card"></div>
      </div>

      <!-- 加载失败（数据源不可达等） -->
      <div v-else-if="errorText" class="rk-center">
        <div class="rk-sync-box error">
          <i class="mdi mdi-alert-circle"></i>
          <p>排行榜获取失败</p>
          <p class="rk-sync-hint">{{ errorText }}</p>
          <button class="rk-retry" @click="onRefresh">重试</button>
        </div>
      </div>

      <!-- 空结果 -->
      <div v-else-if="!items.length && !loading" class="rk-center">
        <div class="rk-sync-box">
          <i class="mdi mdi-filmstrip-off"></i>
          <p>没有符合条件的动画</p>
          <p class="rk-sync-hint">换个筛选条件试试看</p>
        </div>
      </div>

      <!-- 列表 -->
      <template v-else-if="items.length">
        <div class="rk-meta">
          第 <strong>{{ page }}</strong> / <strong>{{ totalPages }}</strong> 页 · 数据来源
          <a :href="BANGUMI_BASE_URL" target="_blank" rel="noopener noreferrer">Bangumi</a>
        </div>
        <div class="br-grid">
          <AnimeCard
            v-for="a in items"
            :key="a.subjectId"
            :to="'/anime/bgm/' + a.subjectId"
            :image-url="a.cover"
            :alt="a.nameCn || a.name"
            :title="a.nameCn || a.name || '未命名动画'"
            :title-attr="a.nameCn || a.name"
            hover
          >
            <template #badges>
              <span v-if="showRankNo && a.rank" class="rk-rank-badge">#{{ a.rank }}</span>
            </template>
            <template #meta>
              <span v-if="a.score" class="rk-star"><i class="mdi mdi-star"></i>{{ formatScore(a.score) }}</span>
              <span v-if="a.date" class="rk-chip">{{ a.date.slice(0, 4) }}</span>
            </template>
          </AnimeCard>
        </div>

        <div class="rk-loadmore">
          <button v-if="hasMore && !loadingMore" class="rk-more-btn" @click="loadMore">
            <i class="mdi mdi-arrow-down"></i>加载更多（第 {{ page }}/{{ totalPages }} 页）
          </button>
          <span v-else-if="loadingMore" class="rk-loading-text"><i class="mdi mdi-loading mdi-spin"></i> 加载中…</span>
          <span v-else-if="!hasMore" class="rk-loading-text done">— 已加载至最后一页 —</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { getBgmRankSubjects } from '../../api/bangumi'
import { formatScore } from '../../utils/format'
import { BANGUMI_BASE_URL } from '../../utils/constants'
import AnimeCard from '../AnimeCard.vue'

// ===== 筛选项（参照 czy0729/Bangumi「排行榜」，标签字典与 Bangumi 公共标签一致） =====
const sortOptions = [
  { label: '排名', value: 'rank' },
  { label: '热度', value: 'trends' },
  { label: '收藏', value: 'collects' },
  { label: '日期', value: 'date' },
  { label: '名称', value: 'title' }
]
const platformOptions = ['全部', 'TV', 'WEB', 'OVA', '剧场版', '其他']
const sourceOptions = ['全部', '原创', '漫画改', '游戏改', '小说改', '影视改']
const tagOptions = [
  '全部', '科幻', '喜剧', '百合', '校园', '惊悚', '后宫', '机战', '悬疑', '恋爱', '奇幻',
  '推理', '运动', '耽美', '音乐', '战斗', '冒险', '萌系', '穿越', '玄幻', '乙女', '恐怖',
  '历史', '日常', '剧情', '武侠', '美食', '职场'
]
const areaOptions = ['全部', '日本', '欧美', '美国', '中国', '法国', '韩国', '英国', '俄罗斯', '苏联', '香港', '台湾', '捷克']
const targetOptions = ['全部', '子供向', '少年向', '少女向', '青年向', '女性向', 'BL', 'GL']

const currentYear = new Date().getFullYear()
const yearOptions = []
for (let y = currentYear; y >= 1960; y--) yearOptions.push(String(y))
const monthOptions = Array.from({ length: 12 }, (_, i) => String(i + 1))

const filters = ref({
  sort: 'rank',
  platform: '全部',
  year: '',
  month: '',
  source: '全部',
  tag: '全部',
  area: '全部',
  target: '全部'
})
const expanded = ref(false)

const items = ref([])
const page = ref(1)
const totalPages = ref(1)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const errorText = ref('')
let fetchSeq = 0

// ===== 会话兜底（跨整页刷新）：只保留筛选/页码/列表数据；滚动位置由发现页父组件统一记录与恢复 =====
const STATE_KEY = 'anilink.rank.state'
const persist = () => {
  try {
    sessionStorage.setItem(
      STATE_KEY,
      JSON.stringify({
        filters: filters.value,
        expanded: expanded.value,
        page: page.value,
        totalPages: totalPages.value,
        hasMore: hasMore.value,
        items: items.value.slice(0, 800)
      })
    )
  } catch {
    /* 存储不可用时忽略 */
  }
}
watch(filters, persist, { deep: true })

const showRankNo = computed(() => filters.value.sort === 'rank')
const hasActiveFilter = computed(() =>
  filters.value.platform !== '全部' ||
  filters.value.year !== '' ||
  filters.value.source !== '全部' ||
  filters.value.tag !== '全部' ||
  filters.value.area !== '全部' ||
  filters.value.target !== '全部'
)

const buildParams = (pageNo) => {
  const f = filters.value
  const params = { sort: f.sort, page: pageNo }
  if (f.platform !== '全部') params.platform = f.platform
  if (f.year) params.year = Number(f.year)
  if (f.year && f.month) params.month = Number(f.month)
  if (f.source !== '全部') params.source = f.source
  if (f.tag !== '全部') params.tag = f.tag
  if (f.area !== '全部') params.area = f.area
  if (f.target !== '全部') params.target = f.target
  return params
}

const fetchRank = async (reset) => {
  const seq = ++fetchSeq
  if (reset) {
    page.value = 1
    items.value = []
    totalPages.value = 1
    hasMore.value = false
    errorText.value = ''
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const res = await getBgmRankSubjects(buildParams(reset ? 1 : page.value))
    if (seq !== fetchSeq) return
    if (res?.code !== 200) {
      errorText.value = res?.msg || '排行榜获取失败'
      return
    }
    const payload = res.data || {}
    const list = Array.isArray(payload.list) ? payload.list : []
    items.value = reset ? list : items.value.concat(list)
    if (reset) page.value = Number(payload.page || 1)
    totalPages.value = Number(payload.totalPages || 1)
    hasMore.value = !!payload.hasMore
    persist()
  } catch (e) {
    if (seq !== fetchSeq) return
    errorText.value = e?.response?.data?.msg || e?.message || '排行榜获取失败'
    if (!reset) {
      page.value = Math.max(1, page.value - 1)
    }
  } finally {
    if (seq === fetchSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

const loadMore = () => {
  if (loadingMore.value || !hasMore.value) return
  page.value += 1
  fetchRank(false)
}

const onFiltersChange = () => {
  // 未选年份时清空月份
  if (!filters.value.year) filters.value.month = ''
  fetchRank(true)
}

const onReset = () => {
  filters.value = { ...filters.value, platform: '全部', year: '', month: '', source: '全部', tag: '全部', area: '全部', target: '全部' }
  fetchRank(true)
}

// 重试 / 手动刷新当前页
const onRefresh = () => {
  fetchRank(true)
}

onMounted(() => {
  // 还原上次会话的筛选/页码与列表（不含滚动，滚动位置由发现页父组件恢复）；无快照则重新加载第一页
  let snap = null
  try {
    snap = JSON.parse(sessionStorage.getItem(STATE_KEY) || 'null')
  } catch {
    snap = null
  }
  if (snap && snap.filters && Array.isArray(snap.items) && snap.items.length) {
    filters.value = Object.assign({}, filters.value, snap.filters)
    if (!filters.value.year) filters.value.month = ''
    expanded.value = !!snap.expanded
    page.value = Number(snap.page) || 1
    totalPages.value = Number(snap.totalPages) || 1
    hasMore.value = !!snap.hasMore
    items.value = snap.items
    errorText.value = ''
    loading.value = false
  } else {
    fetchRank(true)
  }
})

onBeforeUnmount(() => {
  fetchSeq++
  persist()
})

</script>

<style scoped>
/* ========================= ROOT ========================= */
.rank-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 10px;
}

/* ========================= 工具条 ========================= */
.rk-toolbar {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--al-border-extra, rgba(128, 128, 128, 0.08));
  border-radius: 12px;
  padding: 10px 12px;
}
.rk-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
.rk-sel {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 110px;
}
.rk-sel.disabled {
  opacity: 0.55;
}
.rk-sel label {
  font-size: 11px;
  color: var(--anime-text-secondary, #8a8a8a);
  letter-spacing: 0.5px;
  padding-left: 2px;
}
.rk-sel select {
  appearance: auto;
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.25));
  background: var(--al-bg, #fff);
  color: var(--anime-text-main, #222);
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  cursor: pointer;
  min-width: 110px;
}
.rk-sel select:focus {
  border-color: var(--anime-accent-red, #e0544d);
}
.rk-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.25));
  background: var(--al-bg, #fff);
  color: var(--anime-text-main, #222);
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.rk-more-btn:hover,
.rk-more-btn.active {
  color: var(--anime-accent-red, #e0544d);
  border-color: rgba(224, 84, 77, 0.4);
}
.rk-reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--anime-text-secondary, #8a8a8a);
  padding: 7px 6px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
}
.rk-reset-btn:hover {
  color: var(--anime-accent-red, #e0544d);
}
.rk-reset-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
.rk-row-more {
  padding-top: 8px;
  border-top: 1px dashed var(--anime-border-light, rgba(128, 128, 128, 0.2));
}

/* ========================= 主体滚动区 ========================= */
.rk-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 2px;
}

/* ========================= 失败 / 空状态 ========================= */
.rk-center {
  flex: 1;
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rk-sync-box {
  text-align: center;
  color: var(--anime-text-secondary, #8a8a8a);
  max-width: 420px;
  padding: 24px;
}
.rk-sync-box i {
  font-size: 3rem;
  color: var(--anime-accent-red, #e0544d);
  opacity: 0.75;
  display: inline-block;
  margin-bottom: 10px;
}
.rk-sync-box.error i {
  color: #e53935;
}
.rk-sync-box p {
  margin: 4px 0;
  font-size: 14px;
  color: var(--anime-text-main, #222);
  font-weight: 600;
}
.rk-sync-box .rk-sync-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--anime-text-secondary, #8a8a8a);
  margin-top: 6px;
  line-height: 1.7;
}
.rk-retry {
  margin-top: 14px;
  border: 1px solid var(--anime-accent-red, #e0544d);
  color: var(--anime-accent-red, #e0544d);
  background: transparent;
  border-radius: 999px;
  padding: 7px 22px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.rk-retry:hover {
  background: var(--anime-accent-red, #e0544d);
  color: #fff;
}

/* ========================= 骨架 ========================= */
.rk-grid-skel {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 18px;
}
.rk-skel-card {
  aspect-ratio: 2 / 2.9;
  border-radius: 14px;
  background: linear-gradient(100deg,
    var(--anime-bg-beige, #f1ece4) 40%,
    var(--anime-bg-beige-13, #e6dfd4) 50%,
    var(--anime-bg-beige, #f1ece4) 60%);
  background-size: 200% 100%;
  animation: rk-shimmer 1.4s infinite;
}
@keyframes rk-shimmer {
  0% { background-position: 120% 0; }
  100% { background-position: -80% 0; }
}

/* ========================= 卡片信息 ========================= */
.rk-meta {
  font-size: 12.5px;
  color: var(--anime-text-secondary, #8a8a8a);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.rk-meta strong {
  color: var(--anime-accent-red, #e0544d);
}
.rk-meta a {
  color: var(--anime-text-secondary, #8a8a8a);
}
.rk-rank-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.62);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  color: #ffd54f;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.3px;
  padding: 3px 9px;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.rk-star {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #f5a623;
  font-weight: 700;
  font-size: 12.5px;
}
.rk-chip {
  font-size: 11px;
  color: var(--anime-text-secondary, #8a8a8a);
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.2));
  border-radius: 999px;
  padding: 1px 8px;
}

/* ========================= 加载更多 ========================= */
.rk-loadmore {
  display: flex;
  justify-content: center;
  padding: 6px 0 12px;
}
.rk-loadmore .rk-more-btn {
  border-color: transparent;
  background: var(--al-border-extra, rgba(128, 128, 128, 0.1));
}
.rk-loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--anime-text-secondary, #8a8a8a);
  font-size: 13px;
  padding: 8px 0;
}
.rk-loading-text.done {
  opacity: 0.7;
}
</style>
