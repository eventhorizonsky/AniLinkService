<template>
  <div class="bgm-ep-comments">
    <div v-if="loading && comments.length === 0" class="bgm-ep-loading">
      <span class="bgm-ep-loading-dot"></span>
      <span class="bgm-ep-loading-dot"></span>
      <span class="bgm-ep-loading-dot"></span>
    </div>

    <template v-else-if="available">
      <div v-if="episode" class="bgm-ep-header">
        <a
          :href="`https://bgm.tv/ep/${episode.id}`"
          target="_blank"
          rel="noopener noreferrer"
          class="bgm-ep-link"
        >
          Bangumi 吐槽箱
        </a>
        <span v-if="episode.sort" class="bgm-ep-num">第 {{ episode.sort }} 话</span>
      </div>

      <div v-if="comments.length === 0" class="bgm-ep-empty">暂无吐槽</div>

      <div v-else ref="listRef" class="bgm-ep-list" @scroll="onListScroll">
        <div
          v-for="c in visibleItems"
          :key="c.id"
          class="bgm-ep-item"
        >
          <a
            :href="userUrl(c)"
            target="_blank"
            rel="noopener noreferrer"
            class="bgm-ep-avatar"
          >
            <img
              :src="avatarUrl(c)"
              :alt="userName(c)"
              loading="lazy"
              @error="onAvatarError"
            />
          </a>
          <div class="bgm-ep-body">
            <div class="bgm-ep-header-row">
              <a
                :href="userUrl(c)"
                target="_blank"
                rel="noopener noreferrer"
                class="bgm-ep-name"
              >{{ userName(c) }}</a>
              <span class="bgm-ep-date">{{ formatDate(c.createdAt) }}</span>
            </div>

            <div
              class="bgm-ep-content"
              v-html="renderBBCode(c.content)"
              @click="onContentClick"
            ></div>

            <!-- 子回复 -->
            <template v-if="c.replies && c.replies.length">
              <button
                type="button"
                class="bgm-ep-replies-toggle"
                @click="toggleReplies(c.id)"
              >
                {{ replyOpen[c.id] ? '收起回复' : `查看 ${c.replies.length} 条回复` }}
              </button>
              <div v-if="replyOpen[c.id]" class="bgm-ep-replies">
                <div v-for="r in c.replies" :key="r.id" class="bgm-ep-reply">
                  <a
                    :href="userUrl(r)"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="bgm-ep-avatar"
                  >
                    <img
                      :src="avatarUrl(r)"
                      :alt="userName(r)"
                      loading="lazy"
                      @error="onAvatarError"
                    />
                  </a>
                  <div class="bgm-ep-reply-body">
                    <div class="bgm-ep-header-row">
                      <a
                        :href="userUrl(r)"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="bgm-ep-name"
                      >{{ userName(r) }}</a>
                      <span class="bgm-ep-date">{{ formatDate(r.createdAt) }}</span>
                    </div>
                    <div
                      class="bgm-ep-content"
                      v-html="renderBBCode(r.content)"
                      @click="onContentClick"
                    ></div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="bgm-ep-empty">
      {{ message || '暂无吐槽' }}
    </div>

    <!-- 图片灯箱（挂载到 body，避免被面板 overflow/transform 影响） -->
    <Teleport to="body">
      <div v-if="lightboxUrl" class="bgm-ep-lightbox" @click.self="closeLightbox">
        <button
          type="button"
          class="bgm-ep-lightbox-close"
          aria-label="关闭"
          @click="closeLightbox"
        >×</button>
        <img
          :src="lightboxUrl"
          alt="图片预览"
          class="bgm-ep-lightbox-img"
          @click.stop
        />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { renderBBCode as renderBBCodeBase } from '../../utils/bbcode.js'
import { getEpisodeComments } from '../../api/anime'

const props = defineProps({
  animeId: {
    type: [String, Number],
    required: true
  },
  episodeNumber: {
    type: [String, Number],
    default: ''
  }
})

const comments = ref([])
const episode = ref(null)
const loading = ref(false)
const available = ref(false)
const message = ref('')
const imageBaseUrl = ref('')
const replyOpen = ref({})
const lightboxUrl = ref('')

// ===== 虚拟滚动（防止评论过多导致 DOM 过大） =====
const renderedCount = ref(10)
const listRef = ref(null)
const BATCH_SIZE = 10
const LOAD_MORE_THRESHOLD = 200

const visibleItems = computed(() => comments.value.slice(0, renderedCount.value))

const loadMore = () => {
  if (renderedCount.value >= comments.value.length) return
  renderedCount.value = Math.min(comments.value.length, renderedCount.value + BATCH_SIZE)
}

const onListScroll = (e) => {
  const el = e.target
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - LOAD_MORE_THRESHOLD) {
    loadMore()
  }
}

// 内容不足一屏时（前几批太短无法触发滚动），自动继续加载直到填满或加载完
watch(visibleItems, async () => {
  await nextTick()
  const el = listRef.value
  if (!el) return
  if (el.scrollHeight <= el.clientHeight && renderedCount.value < comments.value.length) {
    loadMore()
  }
})

const fetchComments = async () => {
  const epNum = String(props.episodeNumber ?? '').trim()
  replyOpen.value = {}
  lightboxUrl.value = ''
  renderedCount.value = 10

  if (!props.animeId || !epNum) {
    available.value = false
    message.value = ''
    comments.value = []
    episode.value = null
    return
  }

  loading.value = true
  try {
    const json = await getEpisodeComments(props.animeId, epNum)
    if (json.code !== 200) {
      throw new Error('bad response')
    }
    const data = json.data || {}
    available.value = data.available === true
    message.value = data.message || ''
    imageBaseUrl.value = data.imageBaseUrl || ''
    comments.value = Array.isArray(data.comments) ? data.comments : []
    episode.value = data.episode || null
  } catch (e) {
    available.value = false
    message.value = '加载失败'
    imageBaseUrl.value = ''
    comments.value = []
    episode.value = null
  } finally {
    loading.value = false
  }
}

const userName = (c) => c.user?.nickname || c.user?.username || '匿名'

const userUrl = (c) => {
  if (c.user?.username) return `https://bgm.tv/user/${c.user.username}`
  return 'https://bgm.tv'
}

const avatarUrl = (c) => {
  return c.user?.avatar?.medium
    || c.user?.avatar?.large
    || c.user?.avatar?.small
    || 'https://lain.bgm.tv/img/no_icon_subject.png'
}

// 表情图片 CDN：优先使用后端按 api.bgm.tv 镜像配置派生的基地址，
// 其次复用评论头像的域名（如 lain.bangumi.lol），最后回退官方 lain.bgm.tv
const smileCdnBase = computed(() => {
  if (imageBaseUrl.value) {
    return imageBaseUrl.value.replace(/\/+$/, '')
  }
  for (const c of comments.value) {
    const u = avatarUrl(c)
    const m = typeof u === 'string' ? u.match(/^https:\/\/[^/]+/) : null
    if (m) return m[0]
  }
  return 'https://lain.bgm.tv'
})

const formatDate = (ts) => {
  if (!ts) return ''
  const d = new Date(ts * 1000)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const onAvatarError = (e) => {
  e.target.src = 'https://lain.bgm.tv/img/no_icon_subject.png'
}

// ===== 子回复展开/收起 =====
const toggleReplies = (id) => {
  replyOpen.value[id] = !replyOpen.value[id]
}

// ===== 图片灯箱 =====
const onContentClick = (e) => {
  const img = e.target.closest('.bgm-ep-img')
  if (img && img.src) {
    lightboxUrl.value = img.src
  }
}

const closeLightbox = () => {
  lightboxUrl.value = ''
}

const onKeydown = (e) => {
  if (e.key === 'Escape') {
    closeLightbox()
  }
}

watch(lightboxUrl, (val) => {
  if (val) {
    document.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})

// ===== Bangumi BBCode 渲染 =====
// 白名单渲染逻辑统一收敛到 utils/bbcode.js，这里仅注入表情 CDN 基础地址。
const renderBBCode = (content) => renderBBCodeBase(content, { smileBaseUrl: smileCdnBase.value })

watch(
  () => [props.animeId, props.episodeNumber],
  fetchComments
)

onMounted(fetchComments)
</script>

<style>
.bgm-ep-comments {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 4px 2px;
}

.bgm-ep-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-anchor: none;
}

.bgm-ep-content .bgm-ep-smile {
  display: inline-block;
  height: 1.3em;
  width: auto;
  vertical-align: -0.3em;
  margin: 0 2px;
  border-radius: 2px;
}

.bgm-ep-content .bgm-ep-align-right {
  text-align: right;
}

.bgm-ep-content .bgm-ep-align-center {
  text-align: center;
}

.bgm-ep-content .bgm-ep-align-left {
  text-align: left;
}

.bgm-ep-content .bgm-ep-mask {
  background: var(--al-text-brown-23);
  color: transparent;
  border-radius: 3px;
  padding: 0 4px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.bgm-ep-content .bgm-ep-mask img {
  visibility: hidden;
}

.bgm-ep-content .bgm-ep-mask:hover {
  background: var(--al-bg-beige-12);
  color: var(--al-text-brown-22);
}

.bgm-ep-content .bgm-ep-mask:hover img {
  visibility: visible;
}

.bgm-ep-loading {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 36px 0;
}

.bgm-ep-loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--al-accent);
  animation: bgm-ep-bounce 1.2s ease-in-out infinite;
}

.bgm-ep-loading-dot:nth-child(2) { animation-delay: 0.2s; }
.bgm-ep-loading-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bgm-ep-bounce {
  0%, 80%, 100% { transform: scale(0.7); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.bgm-ep-empty {
  text-align: center;
  color: var(--al-text-muted);
  padding: 32px 12px;
  font-size: 0.85rem;
  line-height: 1.6;
}

.bgm-ep-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--al-bg-beige-6);
  margin-bottom: 4px;
}

.bgm-ep-link {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--al-accent);
  text-decoration: none;
}

.bgm-ep-link:hover {
  text-decoration: underline;
}

.bgm-ep-num {
  font-size: 0.75rem;
  color: var(--al-text-muted-2);
  font-variant-numeric: tabular-nums;
}

.bgm-ep-item {
  display: flex;
  gap: 10px;
  padding: 12px 10px;
  border-bottom: 1px solid var(--al-bg-beige-5);
}

.bgm-ep-item:last-of-type {
  border-bottom: none;
}

.bgm-ep-avatar {
  flex-shrink: 0;
}

.bgm-ep-avatar img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--al-bg-beige-12);
}

.bgm-ep-body {
  flex: 1;
  min-width: 0;
}

.bgm-ep-header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.bgm-ep-name {
  font-weight: 600;
  font-size: 0.8rem;
  color: var(--al-text-brown-23);
  text-decoration: none;
}

.bgm-ep-name:hover {
  color: var(--al-accent);
}

.bgm-ep-date {
  font-size: 0.7rem;
  color: var(--al-text-muted-3);
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.bgm-ep-content {
  margin: 0;
  font-size: 0.82rem;
  color: var(--al-text-brown-22);
  line-height: 1.55;
  word-break: break-word;
}

/* v-html 生成的内容；全局样式确保动态内容也能命中 */
.bgm-ep-content .bgm-ep-img {
  display: block;
  max-width: 100%;
  max-height: 160px;
  width: auto;
  height: auto;
  object-fit: contain;
  margin: 6px 0;
  border-radius: 8px;
  background: var(--al-bg-beige-5);
  cursor: zoom-in;
}

.bgm-ep-content a {
  color: var(--al-accent);
  word-break: break-all;
}

.bgm-ep-content .bgm-ep-quote {
  margin: 6px 0;
  padding: 6px 10px;
  border-left: 3px solid var(--al-border-soft-10);
  background: var(--al-bg-comment);
  color: var(--al-text-brown-14);
  font-size: 0.78rem;
}

.bgm-ep-replies-toggle {
  display: inline-block;
  margin-top: 6px;
  border: none;
  background: none;
  padding: 2px 0;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--al-text-brown-24);
  cursor: pointer;
  text-decoration: none;
}

.bgm-ep-replies-toggle:hover {
  color: var(--al-accent);
  text-decoration: underline;
}

.bgm-ep-replies {
  margin: 6px 0 2px;
  padding: 4px 0 2px 8px;
  border-left: 2px solid var(--al-border-soft-12);
}

.bgm-ep-reply {
  display: flex;
  gap: 8px;
  padding: 8px 4px;
}

.bgm-ep-reply .bgm-ep-avatar img {
  width: 26px;
  height: 26px;
}

.bgm-ep-reply-body {
  flex: 1;
  min-width: 0;
}

/* 灯箱 */
.bgm-ep-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.82);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.bgm-ep-lightbox-img {
  max-width: 90vw;
  max-height: 85vh;
  object-fit: contain;
  border-radius: 6px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5);
}

.bgm-ep-lightbox-close {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 1.6rem;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s;
}

.bgm-ep-lightbox-close:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
