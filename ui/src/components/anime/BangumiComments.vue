<template>
  <div class="bgm-comments">
    <!-- 加载中 -->
    <div v-if="loading && comments.length === 0" class="bgm-comments-loading">
      <span class="bgm-loading-dot"></span>
      <span class="bgm-loading-dot"></span>
      <span class="bgm-loading-dot"></span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!loading && comments.length === 0" class="bgm-comments-empty">
      暂无评论
    </div>

    <!-- 评论列表 -->
    <template v-else>
      <div v-for="item in comments" :key="item.id" class="bgm-comment-item">
        <a
          :href="`https://bgm.tv/user/${item.user?.username}`"
          target="_blank"
          rel="noopener noreferrer"
          class="bgm-comment-avatar"
        >
          <img
            :src="item.user?.avatar?.medium || item.user?.avatar?.large"
            :alt="item.user?.nickname"
            loading="lazy"
            @error="onAvatarError"
          />
        </a>
        <div class="bgm-comment-body">
          <div class="bgm-comment-header">
            <a
              :href="`https://bgm.tv/user/${item.user?.username}`"
              target="_blank"
              rel="noopener noreferrer"
              class="bgm-comment-name"
            >{{ item.user?.nickname || item.user?.username }}</a>
            <div v-if="item.rate" class="bgm-comment-stars" :aria-label="`${item.rate} 分`">
              <span
                v-for="n in 5"
                :key="n"
                class="bgm-star"
                :class="{ filled: n <= Math.round(item.rate / 2) }"
              >★</span>
              <span class="bgm-comment-rate">{{ item.rate }}</span>
            </div>
            <span class="bgm-comment-date">{{ formatDate(item.updatedAt) }}</span>
          </div>
          <p class="bgm-comment-text">{{ item.comment }}</p>
        </div>
      </div>

      <!-- 加载更多 -->
      <div class="bgm-comments-more">
        <button
          v-if="hasMore"
          class="bgm-load-more-btn"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
        <span v-else-if="comments.length > 0" class="bgm-no-more">已加载全部评论</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';

const props = defineProps({
  subjectId: {
    type: [String, Number],
    required: true
  }
});

const emit = defineEmits(['unavailable']);

const PAGE_SIZE = 20;

const comments = ref([]);
const total = ref(0);
const loading = ref(false);
const offset = ref(0);

const hasMore = computed(() => comments.value.length < total.value);

import { computed } from 'vue';

const fetchComments = async (reset = false) => {
  if (loading.value) return;
  loading.value = true;
  try {
    const currentOffset = reset ? 0 : offset.value;
    const res = await fetch(
      `/api/bangumi/subjects/${props.subjectId}/comments?limit=${PAGE_SIZE}&offset=${currentOffset}`
    );
    if (!res.ok) {
      emit('unavailable');
      return;
    }
    const json = await res.json();
    if (json.code !== 200 || !json.data) {
      emit('unavailable');
      return;
    }
    const data = json.data;
    total.value = data.total ?? 0;

    const newItems = (data.data ?? []).filter(item => item.comment && item.comment.trim() !== '');
    if (reset) {
      comments.value = newItems;
    } else {
      comments.value.push(...newItems);
    }
    offset.value = currentOffset + PAGE_SIZE;

    if (reset && newItems.length === 0 && total.value === 0) {
      emit('unavailable');
    }
  } catch {
    emit('unavailable');
  } finally {
    loading.value = false;
  }
};

const loadMore = () => fetchComments(false);

const formatDate = (ts) => {
  if (!ts) return '';
  const d = new Date(ts * 1000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const onAvatarError = (e) => {
  e.target.src = 'https://lain.bgm.tv/img/no_icon_subject.png';
};

watch(() => props.subjectId, () => {
  offset.value = 0;
  comments.value = [];
  total.value = 0;
  fetchComments(true);
});

onMounted(() => {
  fetchComments(true);
});
</script>

<style scoped>
.bgm-comments {
  padding: 4px 0;
}

.bgm-comments-loading {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 36px 0;
}

.bgm-loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--anime-accent-red, var(--al-accent));
  animation: bgm-bounce 1.2s ease-in-out infinite;
}

.bgm-loading-dot:nth-child(2) { animation-delay: 0.2s; }
.bgm-loading-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bgm-bounce {
  0%, 80%, 100% { transform: scale(0.7); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.bgm-comments-empty {
  text-align: center;
  color: var(--anime-text-secondary, var(--al-text-secondary));
  padding: 36px 0;
  font-size: 0.95rem;
}

.bgm-comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--anime-border-light, var(--al-border));
}

.bgm-comment-item:last-of-type {
  border-bottom: none;
}

.bgm-comment-avatar {
  flex-shrink: 0;
}

.bgm-comment-avatar img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--anime-bg-beige, var(--al-bg-beige));
}

.bgm-comment-body {
  flex: 1;
  min-width: 0;
}

.bgm-comment-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.bgm-comment-name {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--anime-primary-dark, var(--al-text-strong));
  text-decoration: none;
}

.bgm-comment-name:hover {
  color: var(--anime-accent-red, var(--al-accent));
}

.bgm-comment-stars {
  display: flex;
  align-items: center;
  gap: 1px;
}

.bgm-star {
  font-size: 0.85rem;
  color: var(--al-star-2);
}

.bgm-star.filled {
  color: var(--al-star-3);
}

.bgm-comment-rate {
  font-size: 0.78rem;
  color: var(--anime-text-secondary, var(--al-text-secondary));
  margin-left: 4px;
}

.bgm-comment-date {
  font-size: 0.78rem;
  color: var(--anime-accent-brown, var(--al-accent-brown));
  margin-left: auto;
}

.bgm-comment-text {
  margin: 0;
  font-size: 0.92rem;
  color: var(--anime-text-main, var(--al-text));
  line-height: 1.6;
  word-break: break-word;
}

.bgm-comments-more {
  text-align: center;
  padding: 16px 0 4px;
}

.bgm-load-more-btn {
  background: none;
  border: 1.5px solid var(--anime-accent-red, var(--al-accent));
  color: var(--anime-accent-red, var(--al-accent));
  border-radius: 20px;
  padding: 6px 24px;
  font-size: 0.88rem;
  cursor: pointer;
  transition: 0.2s;
}

.bgm-load-more-btn:hover:not(:disabled) {
  background: var(--anime-accent-red, var(--al-accent));
  color: var(--al-text-on-accent);
}

.bgm-load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.bgm-no-more {
  font-size: 0.82rem;
  color: var(--anime-accent-brown, var(--al-accent-brown));
}
</style>
