<template>
  <div class="rec-panel">
    <!-- ===== 工具条 ===== -->
    <div class="rec-toolbar">
      <div class="rec-title">
        <i class="mdi mdi-heart-outline"></i>
        <div>
          <h3>猜你喜欢</h3>
          <p class="rec-sub">
            基于你的 Bangumi 收藏与官方条目推荐实时计算 · 算法参考
            <a :href="BANGUMI_RECOMMEND_ALGO_URL" target="_blank" rel="noopener noreferrer">
              czy0729/Bangumi<i class="mdi mdi-open-in-new"></i>
            </a>
          </p>
        </div>
      </div>
      <button v-if="bound && !generating" class="rec-gen-btn" @click="openDialog">
        <i class="mdi" :class="hasResult ? 'mdi-refresh' : 'mdi-auto-fix'"></i>
        {{ hasResult ? '重新生成' : '生成推荐' }}
      </button>
      <button v-else-if="generating" class="rec-gen-btn" disabled>
        <i class="mdi mdi-loading mdi-spin"></i>生成中…
      </button>
    </div>

    <!-- ===== 主体（滚动区） ===== -->
    <div class="rec-body">
      <!-- 初始加载 -->
      <div v-if="pageLoading" class="rec-center">
        <span class="rec-loading-text"><i class="mdi mdi-loading mdi-spin"></i> 加载中…</span>
      </div>

      <!-- 未登录：引导登录 -->
      <div v-else-if="!isLoggedIn" class="rec-center">
        <div class="rec-box">
          <i class="mdi mdi-heart-outline"></i>
          <p>登录后解锁专属推荐</p>
          <p class="rec-hint">
            猜你喜欢会读取你的 Bangumi 收藏（想看 / 在看 / 看过等），
            结合官方条目推荐为你计算个性化的动画推荐列表。
          </p>
          <button class="rec-retry" @click="askLogin">
            <i class="mdi mdi-login"></i> 去登录 / 注册
          </button>
        </div>
      </div>

      <!-- 已登录但未绑定 Bangumi：引导绑定 -->
      <div v-else-if="!bound" class="rec-center">
        <div class="rec-box">
          <i class="mdi mdi-link-variant"></i>
          <p>需要先绑定 Bangumi 账号</p>
          <p class="rec-hint">
            绑定后即可基于你的收藏生成个性化推荐；
            每次生成都会实时读取最新的收藏数据，无需手动同步。
          </p>
          <router-link class="rec-retry" to="/profile/binding">前往账号绑定</router-link>
        </div>
      </div>

      <!-- 生成中：进度 -->
      <div v-else-if="generating" class="rec-center">
        <div class="rec-box progress">
          <i class="mdi mdi-loading mdi-spin"></i>
          <p>{{ progressText }}</p>
          <div class="rec-progress">
            <div class="rec-progress-bar" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <p class="rec-hint">{{ progress.phase }} · {{ progress.current }} / {{ progress.total || '…' }}</p>
          <p v-if="progress.message" class="rec-hint error">{{ progress.message }}</p>
        </div>
      </div>

      <!-- 生成失败 -->
      <div v-else-if="errorText" class="rec-center">
        <div class="rec-box error">
          <i class="mdi mdi-alert-circle"></i>
          <p>生成失败</p>
          <p class="rec-hint">{{ errorText }}</p>
          <button class="rec-retry" @click="openDialog">重试</button>
        </div>
      </div>

      <!-- 已绑定但从未生成：空状态引导 -->
      <div v-else-if="!hasResult" class="rec-center">
        <div class="rec-box">
          <i class="mdi mdi-auto-fix"></i>
          <p>还没有生成过推荐</p>
          <p class="rec-hint">
            每次生成都会重新从 Bangumi 拉取你的收藏并实时计算，
            大约需要几十秒，结果会保存下来供随时查看。
          </p>
          <button class="rec-retry" @click="openDialog">生成推荐</button>
        </div>
      </div>

      <!-- 结果列表：左封面 + 右侧详情 -->
      <template v-else>
        <div class="rec-meta">
          <template v-if="resultMeta.collectionCount">
            基于 <strong>{{ resultMeta.collectionCount }}</strong> 条收藏计算 ·
          </template>
          共 <strong>{{ resultMeta.resultCount || items.length }}</strong> 个推荐 ·
          生成于 {{ formatTime(resultMeta.generatedAt) }} ·
          数据来源 <a :href="BANGUMI_BASE_URL" target="_blank" rel="noopener noreferrer">Bangumi</a>
        </div>

        <div class="rec-list">
          <router-link
            v-for="(a, idx) in visibleItems"
            :key="a.id"
            :to="'/anime/bgm/' + a.id"
            class="rr-row"
          >
            <!-- 左：封面 -->
            <div class="rr-cover">
              <img
                v-if="a.cover"
                :src="a.cover"
                :alt="rowTitle(a)"
                loading="lazy"
                decoding="async"
              />
              <div v-else class="rr-no-cover"><i class="mdi mdi-image-off-outline"></i></div>
            </div>

            <!-- 右：详情 -->
            <div class="rr-main">
              <div class="rr-top">
                <span v-if="items.length > 1" class="rr-ord">{{ idx + 1 }}</span>
                <h4 class="rr-title" :title="rowTitle(a)">{{ rowTitle(a) }}</h4>
                <span class="rr-rate" :title="'综合推荐分 = 各维度贡献合计 + 相似度加成'">
                  <i class="mdi mdi-heart"></i>{{ fmtRate(a.rate) }}
                </span>
              </div>

              <div class="rr-line rr-meta">
                <span v-if="a.score > 0" class="rr-star" :title="'Bangumi 评分 ' + formatScore(a.score)">
                  <i class="mdi mdi-star"></i>{{ formatScore(a.score) }}
                </span>
                <span v-if="a.rank > 0" class="rr-chip rank" :title="'Bangumi 排名 #' + a.rank">
                  <i class="mdi mdi-trophy-outline"></i>#{{ a.rank }}
                </span>
                <span class="rr-src" :title="'被你的 ' + a.relates + ' 个收藏条目同时推荐'">
                  命中 {{ a.relates }} 部收藏
                </span>
              </div>

              <div v-if="a.tags && a.tags.length" class="rr-line rr-tags">
                <i class="mdi mdi-tag-outline"></i>
                <span v-for="t in a.tags" :key="t" class="rr-chip">{{ t }}</span>
              </div>

              <div class="rr-reasons">
                <span class="rr-reason-label">
                  <i class="mdi mdi-chart-line-variant"></i>推荐理由
                </span>
                <template v-if="a.reasons && a.reasons.length">
                  <span
                    v-for="r in a.reasons"
                    :key="r.key"
                    class="rr-reason"
                    :class="r.value > 0 ? 'pos' : 'neg'"
                    :title="dimDesc(r.key)"
                  >{{ dimLabel(r.key) }} {{ signed(r.value) }}</span>
                </template>
                <span v-else class="rr-reason-none">—</span>
              </div>
            </div>
          </router-link>
        </div>

        <div class="rec-loadmore" ref="recSentinel">
          <span v-if="visibleItems.length < items.length" class="rec-loading-text hint">
            <i class="mdi mdi-chevron-down"></i>继续下滑加载更多
          </span>
          <span v-else class="rec-loading-text done">— 已展示全部推荐 —</span>
        </div>
      </template>
    </div>

    <!-- ===== 维度开关弹窗 ===== -->
    <div v-if="dialogVisible" class="rec-dialog-mask" @click.self="closeDialog">
      <div class="rec-dialog">
        <div class="rec-dialog-head">
          <h3>生成猜你喜欢</h3>
          <button class="rec-dialog-close" @click="closeDialog"><i class="mdi mdi-close"></i></button>
        </div>
        <p class="rec-dialog-tip">
          将重新从 Bangumi 拉取你的全部收藏并实时计算，勾选参与计分的推荐维度（至少一项）：
        </p>
        <div class="rec-dims">
          <label v-for="(d, i) in DIMENSIONS" :key="d.label" class="rec-dim" :class="{ off: !dims[i] }">
            <input type="checkbox" v-model="dims[i]" />
            <div class="rec-dim-text">
              <span class="rec-dim-label">{{ d.label }}</span>
              <span class="rec-dim-desc">{{ d.desc }}</span>
            </div>
          </label>
        </div>
        <div class="rec-dialog-foot">
          <div class="rec-dim-ops">
            <button @click="setAllDims(true)">全选</button>
            <button @click="setAllDims(false)">全不选</button>
            <button @click="setAllDims(true, true)">恢复默认</button>
          </div>
          <div class="rec-dim-submit">
            <button class="cancel" @click="closeDialog">取消</button>
            <button class="ok" :disabled="!anyDimOn || submitting" @click="confirmGenerate">
              <i v-if="submitting" class="mdi mdi-loading mdi-spin"></i>
              {{ submitting ? '提交中…' : '开始生成' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, watch, nextTick, onMounted, onActivated, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  getBangumiRecommendStatus,
  generateBangumiRecommend,
  getBangumiRecommendResult
} from '../../api/bangumi'
import { formatScore } from '../../utils/format'
import { BANGUMI_BASE_URL, BANGUMI_RECOMMEND_ALGO_URL } from '../../utils/constants'
import { useAuth } from '../../composables/useAuth'

const router = useRouter()
const { isLoggedIn } = useAuth()

// 主布局注入的登录弹窗打开函数（见 MainLayout.vue）
const openLogin = inject('openLoginDialog', null)

// 10 个计分维度（顺序与后端 BangumiRecommendService.REASONS 一致）
const DIMENSIONS = [
  { label: '自己评分', desc: '已评分，高分多加分，低分多扣分' },
  { label: '收藏状态', desc: '想看加分，搁置、抛弃扣分' },
  { label: '条目排名', desc: '收藏条目自身高排名加分，低排名扣分' },
  { label: '条目分数', desc: '收藏条目自身高分加分，低分扣分' },
  { label: '已看集数', desc: '已看过集数多，稍微加分' },
  { label: '自己点评', desc: '进行过长评稍微加分' },
  { label: '私密收藏', desc: '私密收藏加分' },
  { label: '最近收藏', desc: '最近操作过稍微加分' },
  { label: '标签倾向', desc: '标签是你倾向打的，越多相对加越多分' },
  { label: '多次推荐', desc: '被多个收藏条目同时推荐到相对加分' }
]

const PAGE_STEP = 30

const pageLoading = ref(true)
const bound = ref(false)
const generating = ref(false)
const errorText = ref('')
const progress = ref({ state: 'RUNNING', phase: '', current: 0, total: 0, message: '' })

const hasResult = ref(false)
const resultMeta = ref({ collectionCount: 0, resultCount: 0, generatedAt: null })
const items = ref([])
const visibleCount = ref(PAGE_STEP)

const dialogVisible = ref(false)
const submitting = ref(false)
const dims = ref(DIMENSIONS.map(() => true))

// 列表底部哨兵：滚动触底（或无感临近底部）时自动追加下一批
const recSentinel = ref(null)
let recIo = null
let recIoEl = null

let pollTimer = null
let pollSeq = 0
let refreshSeq = 0
let refreshing = false
let fetchingResult = false

const visibleItems = computed(() => items.value.slice(0, visibleCount.value))
const anyDimOn = computed(() => dims.value.some(Boolean))

const progressPercent = computed(() => {
  const p = progress.value
  if (!p.total) return 8
  return Math.min(96, Math.max(4, Math.round((p.current / p.total) * 100)))
})

const progressText = computed(() => {
  const phase = progress.value.phase || '计算中'
  if (phase === '拉取 Bangumi 收藏') return '正在拉取你的 Bangumi 收藏…'
  if (phase === '获取条目推荐') return '正在获取每个收藏条目的官方推荐…'
  if (phase === '计算推荐结果') return '正在计算推荐结果…'
  return '正在准备…'
})

// ===== 请求 =====

const fetchStatus = async () => {
  const res = await getBangumiRecommendStatus()
  const data = res?.data || {}
  bound.value = !!data.bound
  hasResult.value = !!data.record?.hasResult
  resultMeta.value = {
    collectionCount: data.record?.collectionCount || 0,
    resultCount: data.record?.resultCount || 0,
    generatedAt: data.record?.generatedAt || null
  }
  const p = data.progress
  if (data.generating && p) {
    generating.value = true
    progress.value = p
    startPolling()
  } else if (p && p.state === 'ERROR') {
    errorText.value = p.message || '生成失败，请重试'
  }
  // 上次使用的维度作为弹窗初始值
  if (Array.isArray(data.record?.dimensions) && data.record.dimensions.length) {
    dims.value = DIMENSIONS.map((_, i) => data.record.dimensions[i] !== false)
  }
}

const fetchResultData = async () => {
  if (fetchingResult) return
  fetchingResult = true
  try {
    const res = await getBangumiRecommendResult()
    const data = res?.data || {}
    items.value = Array.isArray(data.list) ? data.list : []
    hasResult.value = true
    resultMeta.value = {
      collectionCount: data.collectionCount || 0,
      resultCount: data.resultCount || items.value.length,
      generatedAt: data.generatedAt || null
    }
    visibleCount.value = PAGE_STEP
  } finally {
    fetchingResult = false
  }
}

// 整体刷新（首次进入 / 切回本 Tab / 登录态变化时调用）
const refresh = async () => {
  if (refreshing) return
  refreshing = true
  const seq = ++refreshSeq
  try {
    if (!isLoggedIn.value) {
      resetToGuest()
      return
    }
    pageLoading.value = true
    await fetchStatus()
    if (seq !== refreshSeq) return
    errorText.value = ''
    if (!generating.value && hasResult.value && bound.value && !items.value.length) {
      await fetchResultData()
      if (seq !== refreshSeq) return
    }
  } catch (e) {
    if (seq !== refreshSeq) return
    // 401 由 http 拦截器处理（清 token / 弹会话过期）；这里仅兜底展示
    errorText.value = e?.response?.data?.msg || e?.message || '加载失败，请稍后重试'
  } finally {
    if (seq === refreshSeq) {
      pageLoading.value = false
      refreshing = false
    }
  }
}

// 登出 / 匿名态：清空并停止轮询，避免展示上一用户缓存数据
const resetToGuest = () => {
  stopPolling()
  pageLoading.value = false
  bound.value = false
  generating.value = false
  errorText.value = ''
  progress.value = { state: 'RUNNING', phase: '', current: 0, total: 0, message: '' }
  hasResult.value = false
  resultMeta.value = { collectionCount: 0, resultCount: 0, generatedAt: null }
  items.value = []
}

// ===== 轮询 =====

const startPolling = () => {
  stopPolling()
  const seq = ++pollSeq
  pollTimer = setInterval(async () => {
    if (seq !== pollSeq) return
    if (!isLoggedIn.value) {
      resetToGuest()
      return
    }
    try {
      const res = await getBangumiRecommendStatus()
      if (seq !== pollSeq) return
      const p = res?.data?.progress
      if (p) progress.value = p
      if (!res?.data?.generating) {
        stopPolling()
        if (p?.state === 'ERROR') {
          generating.value = false
          errorText.value = p.message || '生成失败，请重试'
          return
        }
        // 先拉取结果再结束“生成中”状态，避免页面闪回空状态
        try {
          await fetchResultData()
        } catch (e) {
          errorText.value = e?.response?.data?.msg || e?.message || '结果获取失败，请稍后重试'
        } finally {
          generating.value = false
        }
      }
    } catch {
      /* 单次轮询失败忽略，下个周期重试 */
    }
  }, 1200)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollSeq++
}

// ===== 弹窗与生成 =====

const openDialog = () => {
  errorText.value = ''
  dialogVisible.value = true
}

const closeDialog = () => {
  if (submitting.value) return
  dialogVisible.value = false
}

const setAllDims = (on, defaults) => {
  if (defaults) {
    dims.value = DIMENSIONS.map(() => true)
  } else {
    dims.value = DIMENSIONS.map(() => on)
  }
}

const confirmGenerate = async () => {
  if (!anyDimOn.value || submitting.value) return
  submitting.value = true
  try {
    const res = await generateBangumiRecommend({ dimensions: dims.value })
    if (res?.code !== 200) {
      errorText.value = res?.msg || '生成任务启动失败'
      return
    }
    dialogVisible.value = false
    generating.value = true
    errorText.value = ''
    hasResult.value = false
    progress.value = res.data || { state: 'RUNNING', phase: '准备中', current: 0, total: 0, message: '' }
    startPolling()
  } catch (e) {
    errorText.value = e?.response?.data?.msg || e?.message || '生成任务启动失败'
  } finally {
    submitting.value = false
  }
}

const askLogin = () => {
  if (openLogin) {
    openLogin()
  } else {
    router.push('/')
  }
}

// ===== 无感滚动加载（滚动临近列表底部自动追加下一批） =====

const observeMore = () => {
  const el = recSentinel.value
  if (!el) return
  if (recIo && recIoEl === el) return
  if (recIo) recIo.disconnect()
  recIoEl = el
  recIo = new IntersectionObserver((entries) => {
    if (!entries.some((e) => e.isIntersecting)) return
    if (visibleCount.value < items.value.length) {
      visibleCount.value += PAGE_STEP
    }
  }, { rootMargin: '200px 0px' })
  recIo.observe(el)
}

const unobserveMore = () => {
  if (recIo) {
    recIo.disconnect()
    recIo = null
    recIoEl = null
  }
}

// ===== 展示 =====

const rowTitle = (a) => a?.nameCn || a?.name || '未命名动画'

const fmtRate = (v) => {
  if (v == null || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? '-' : String(Math.round(n * 10) / 10)
}

const signed = (v) => (v > 0 ? '+' + v : String(v))

// 推荐理由只存维度 key（后端不再下发重复 label），此处按 key 映射展示文案
const dimLabel = (key) => {
  const d = DIMENSIONS[Number(key)]
  return d ? d.label : '维度 ' + key
}

const dimDesc = (key) => {
  const d = DIMENSIONS[Number(key)]
  return d ? `${d.label}：${d.desc}` : '推荐理由维度 #' + key
}

const formatTime = (t) => {
  if (!t) return ''
  try {
    const d = new Date(t)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return String(t)
  }
}

// ===== 生命周期 =====

watch(
  () => isLoggedIn.value,
  (loggedIn) => {
    if (loggedIn) {
      refresh()
    } else {
      resetToGuest()
      unobserveMore()
    }
  }
)

// 列表内容/分批状态变化后重新挂上底部哨兵（flush: post 保证模板已更新）
watch(
  () => [items.value.length, visibleCount.value, hasResult.value, generating.value],
  () => nextTick(observeMore),
  { flush: 'post' }
)

onMounted(refresh)

onActivated(() => {
  refresh()
  nextTick(observeMore)
})

onBeforeUnmount(() => {
  stopPolling()
  unobserveMore()
  refreshSeq++
  refreshing = false
})
</script>

<style scoped>
/* ========================= ROOT ========================= */
.rec-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 10px;
}

/* ========================= 工具条 ========================= */
.rec-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--al-border-extra, rgba(128, 128, 128, 0.08));
  border-radius: 12px;
  padding: 10px 14px;
}
.rec-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.rec-title > i {
  font-size: 1.5rem;
  color: var(--anime-accent-red, #e0544d);
}
.rec-title h3 {
  margin: 0;
  font-size: 15px;
  color: var(--anime-text-main, #222);
}
.rec-sub {
  margin: 1px 0 0;
  font-size: 12px;
  color: var(--anime-text-secondary, #8a8a8a);
}
.rec-sub a {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--anime-accent-red, #e0544d);
  font-weight: 600;
  text-decoration: none;
  margin: 0 2px;
}
.rec-sub a:hover {
  text-decoration: underline;
}
.rec-sub a i {
  font-size: 12px;
}
.rec-gen-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--anime-accent-red, #e0544d);
  color: #fff;
  background: var(--anime-accent-red, #e0544d);
  border-radius: 999px;
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.rec-gen-btn:hover {
  opacity: 0.88;
}
.rec-gen-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

/* ========================= 主体 ========================= */
.rec-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 2px;
  -webkit-overflow-scrolling: touch;
}
.rec-center {
  flex: 1;
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rec-loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--anime-text-secondary, #8a8a8a);
  font-size: 14px;
}
.rec-box {
  text-align: center;
  color: var(--anime-text-secondary, #8a8a8a);
  max-width: 460px;
  padding: 24px;
}
.rec-box i {
  font-size: 3rem;
  color: var(--anime-accent-red, #e0544d);
  opacity: 0.75;
  display: inline-block;
  margin-bottom: 10px;
}
.rec-box.error i {
  color: #e53935;
}
.rec-box p {
  margin: 4px 0;
  font-size: 15px;
  color: var(--anime-text-main, #222);
  font-weight: 600;
}
.rec-box .rec-hint {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--anime-text-secondary, #8a8a8a);
  margin-top: 8px;
  line-height: 1.8;
}
.rec-hint.error {
  color: #e53935;
}
.rec-retry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  border: 1px solid var(--anime-accent-red, #e0544d);
  color: var(--anime-accent-red, #e0544d);
  background: transparent;
  border-radius: 999px;
  padding: 7px 22px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
}
.rec-retry:hover {
  background: var(--anime-accent-red, #e0544d);
  color: #fff;
}

/* 进度条 */
.rec-box.progress {
  min-width: 320px;
}
.rec-progress {
  margin-top: 14px;
  height: 8px;
  border-radius: 999px;
  background: var(--al-border-extra, rgba(128, 128, 128, 0.15));
  overflow: hidden;
}
.rec-progress-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--anime-accent-red, #e0544d), #ff8a65);
  transition: width 0.6s ease;
}

/* ========================= 结果列表（竖排：左封面右详情） ========================= */
.rec-meta {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--anime-text-secondary, #8a8a8a);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  padding: 0 2px;
}
.rec-meta strong {
  color: var(--anime-accent-red, #e0544d);
}
.rec-meta a {
  color: var(--anime-text-secondary, #8a8a8a);
}

.rec-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rr-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  background: var(--al-bg, #fff);
  border: 1px solid var(--al-border-panel, rgba(128, 128, 128, 0.18));
  border-radius: 14px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.rr-row:hover {
  border-color: rgba(224, 84, 77, 0.45);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

/* 封面 */
.rr-cover {
  flex: 0 0 86px;
  width: 86px;
  aspect-ratio: 2 / 3;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, var(--al-bg-beige, #f1ece4), var(--al-bg-beige-13, #e6dfd4));
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.12));
}
.rr-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.rr-no-cover {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--al-gray-muted, #b9ab9d);
  font-size: 1.6rem;
}

/* 右侧详情 */
.rr-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 1px;
}
.rr-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rr-ord {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 800;
  color: var(--al-gray-faint, #c4b8ab);
  min-width: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.rr-title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--anime-text-main, #222);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rr-title:hover {
  color: var(--anime-accent-red, #e0544d);
}
.rr-rate {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: rgba(224, 84, 77, 0.12);
  color: var(--anime-accent-red, #e0544d);
  border: 1px solid rgba(224, 84, 77, 0.32);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.rr-rate i {
  font-size: 12px;
}

/* 元信息行 */
.rr-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--anime-text-secondary, #8a8a8a);
}
.rr-star {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #f5a623;
  font-weight: 700;
  font-size: 12.5px;
}
.rr-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  color: var(--anime-text-secondary, #8a8a8a);
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.22));
  border-radius: 999px;
  padding: 1px 8px;
  white-space: nowrap;
}
.rr-chip.rank {
  color: #b8860b;
  border-color: rgba(184, 134, 11, 0.35);
  font-weight: 600;
}
.rr-tags {
  gap: 6px;
}
.rr-tags > i {
  font-size: 13px;
  opacity: 0.6;
}
.rr-src {
  opacity: 0.85;
}

/* 推荐理由（评分维度明细） */
.rr-reasons {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 2px;
}
.rr-reason-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--anime-text-secondary, #8a8a8a);
  opacity: 0.9;
  margin-right: 2px;
  flex-shrink: 0;
}
.rr-reason {
  font-size: 11.5px;
  font-weight: 600;
  border-radius: 999px;
  padding: 1px 8px;
  white-space: nowrap;
}
.rr-reason.pos {
  color: #1b7a3d;
  background: rgba(27, 122, 61, 0.1);
  border: 1px solid rgba(27, 122, 61, 0.28);
}
.rr-reason.neg {
  color: #c0392b;
  background: rgba(192, 57, 43, 0.09);
  border: 1px solid rgba(192, 57, 43, 0.28);
}
.rr-reason-none {
  font-size: 12px;
  opacity: 0.5;
}

/* 加载更多（无感滚动加载：底部哨兵区） */
.rec-loadmore {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 6px 0 12px;
  min-height: 32px;
}
.rec-loading-text.hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--anime-text-secondary, #8a8a8a);
  opacity: 0.55;
}
.rec-loading-text.done {
  opacity: 0.7;
}

/* ========================= 维度开关弹窗 ========================= */
.rec-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.rec-dialog {
  width: min(560px, 100%);
  max-height: min(640px, 86vh);
  display: flex;
  flex-direction: column;
  background: var(--al-bg, #fff);
  border-radius: 16px;
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}
.rec-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 8px;
}
.rec-dialog-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--anime-text-main, #222);
}
.rec-dialog-close {
  border: none;
  background: transparent;
  color: var(--anime-text-secondary, #8a8a8a);
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
}
.rec-dialog-close:hover {
  color: var(--anime-accent-red, #e0544d);
}
.rec-dialog-tip {
  margin: 0;
  padding: 0 20px 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--anime-text-secondary, #8a8a8a);
}
.rec-dims {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px;
  padding: 4px 20px 12px;
}
.rec-dim {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.22));
  border-radius: 10px;
  padding: 9px 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.rec-dim:hover {
  border-color: rgba(224, 84, 77, 0.4);
}
.rec-dim.off {
  opacity: 0.55;
}
.rec-dim input {
  margin-top: 2px;
  accent-color: var(--anime-accent-red, #e0544d);
  cursor: pointer;
}
.rec-dim-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.rec-dim-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--anime-text-main, #222);
}
.rec-dim-desc {
  font-size: 11.5px;
  color: var(--anime-text-secondary, #8a8a8a);
  line-height: 1.5;
}
.rec-dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.18));
}
.rec-dim-ops {
  display: flex;
  gap: 4px;
}
.rec-dim-ops button {
  border: none;
  background: transparent;
  color: var(--anime-text-secondary, #8a8a8a);
  font-size: 12.5px;
  font-family: inherit;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.rec-dim-ops button:hover {
  color: var(--anime-accent-red, #e0544d);
  background: var(--al-border-extra, rgba(128, 128, 128, 0.08));
}
.rec-dim-submit {
  display: flex;
  gap: 8px;
}
.rec-dim-submit button {
  border-radius: 999px;
  padding: 8px 20px;
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.rec-dim-submit .cancel {
  border: 1px solid var(--anime-border-light, rgba(128, 128, 128, 0.3));
  background: transparent;
  color: var(--anime-text-secondary, #8a8a8a);
}
.rec-dim-submit .cancel:hover {
  color: var(--anime-text-main, #222);
}
.rec-dim-submit .ok {
  border: 1px solid var(--anime-accent-red, #e0544d);
  background: var(--anime-accent-red, #e0544d);
  color: #fff;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.rec-dim-submit .ok:hover {
  opacity: 0.88;
}
.rec-dim-submit .ok:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ========================= 响应式 ========================= */
@media (max-width: 640px) {
  .rec-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .rec-gen-btn {
    justify-content: center;
  }
  .rr-cover {
    flex-basis: 72px;
    width: 72px;
  }
  .rr-title {
    font-size: 14px;
  }
  .rec-dims {
    grid-template-columns: 1fr;
  }
  .rec-dialog-foot {
    flex-direction: column;
    align-items: stretch;
  }
  .rec-dim-submit button {
    flex: 1;
  }
}
</style>
