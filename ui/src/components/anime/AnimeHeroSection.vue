<template>
  <div class="anime-hero">
    <div class="anime-poster">
      <img :src="animeData.imageUrl" :alt="animeData.animeTitle" loading="lazy" />
    </div>
    <div class="anime-info">
      <h1 class="anime-title-main">{{ titleInfo.main }}</h1>
      <div class="anime-title-sub">{{ titleInfo.sub }}</div>

      <div class="anime-rating-block">
        <div class="anime-rating-main">
          <span class="anime-score">{{ ratingMain }}</span>
          <span class="anime-rating-label">弹弹评分</span>
        </div>
        <div class="anime-rating-others">
          <span>🍙 Bangumi {{ ratingBangumi }}</span>
          <span>📀 AniDB {{ ratingAnidb }}</span>
        </div>
      </div>

      <div class="anime-status-badge">
        <span class="anime-badge"><i class="mdi mdi-bell"></i> {{ airingStatusText }}</span>
        <span class="anime-badge anime-badge-air">更新 {{ mainEpisodes.length }}/{{ totalEpisodes }}</span>

        <!-- 追番按钮 + 级联状态选择 -->
        <span v-if="showFollowBtn" ref="btnWrap" class="anime-follow-btn-wrap">
          <span
            class="anime-follow-btn"
            :class="{ following: isFollowing, loading: followLoading }"
            :title="isFollowing ? '修改追番状态' : '添加想看'"
            @click="handleFollowClick"
          >
            <i class="mdi" :class="isFollowing ? 'mdi-bookmark-check' : 'mdi-bookmark-outline'"></i>
            {{ isFollowing ? statusLabel(followStatus) : '想看' }}
          </span>
          <!-- 级联状态面板 -->
          <Transition name="follow-menu">
            <div v-if="menuOpen" class="follow-status-panel">
              <div class="follow-status-title">{{ isFollowing ? '修改状态' : '添加追番' }}</div>
              <div class="follow-status-cascade">
                <button
                  v-for="s in statusOptions"
                  :key="s.value"
                  class="follow-status-cascade-item"
                  :class="{ active: followStatus === s.value }"
                  :style="{ '--status-color': s.color }"
                  @click="selectStatus(s.value)"
                >
                  <span class="cascade-dot" :style="{ background: s.color }"></span>
                  <span class="cascade-label">{{ s.label }}</span>
                  <i v-if="followStatus === s.value" class="mdi mdi-check cascade-check"></i>
                </button>
              </div>
              <button v-if="isFollowing" class="follow-status-unfollow" @click="selectStatus(null)">
                <i class="mdi mdi-close"></i> 取消追番
              </button>
            </div>
          </Transition>
        </span>
      </div>

      <!-- 简介摘要 -->
      <div class="anime-summary-block">
        <div class="anime-summary" :class="{ expanded: isSummaryExpanded }">
          <p v-html="sanitizeHtml(formattedSummary)"></p>
        </div>
        <button class="anime-expand-btn" @click="toggleSummary">
          {{ isSummaryExpanded ? '收起' : '展开' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { FOLLOW_STATUS as STATUS_MAP, FOLLOW_STATUS_OPTIONS as statusOptions } from '../../utils/followStatus';
import { sanitizeHtml } from '../../utils/sanitize';
import { useAuth } from '../../composables/useAuth';

const props = defineProps({
  animeData: {
    type: Object,
    required: true
  },
  titleInfo: {
    type: Object,
    required: true
  },
  isOnAir: {
    type: Boolean,
    required: true
  },
  airDayText: {
    type: String,
    required: true
  },
  ratingMain: {
    type: [String, Number],
    required: true
  },
  ratingBangumi: {
    type: [String, Number],
    required: true
  },
  ratingAnidb: {
    type: [String, Number],
    required: true
  },
  mainEpisodes: {
    type: Array,
    required: true
  },
  totalEpisodes: {
    type: [String, Number],
    required: true
  },
  formattedSummary: {
    type: String,
    required: true
  },
  isSummaryExpanded: {
    type: Boolean,
    required: true
  },
  isFavorited: {
    type: Boolean,
    required: true
  },
  isFollowing: {
    type: Boolean,
    default: false
  },
  followStatus: {
    type: String,
    default: 'wish'
  },
  followLoading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:isSummaryExpanded', 'toggleFavorite', 'toggleFollow', 'setFollowStatus']);

const menuOpen = ref(false);

const statusLabel = (s) => STATUS_MAP[s]?.label || '想看';

const handleFollowClick = (e) => {
  e.stopPropagation();
  if (!props.isFollowing) {
    // 首次追番：直接标记想看
    emit('setFollowStatus', 'wish');
  } else {
    menuOpen.value = !menuOpen.value;
  }
};

const selectStatus = (status) => {
  menuOpen.value = false;
  if (status === null) {
    emit('toggleFollow');
  } else {
    emit('setFollowStatus', status);
  }
};

const { isLoggedIn: showFollowBtn } = useAuth();

const airingStatusText = computed(() => {
  if (props.isOnAir) {
    return props.airDayText ? `放送中 · ${props.airDayText}` : '放送中';
  }
  return '未在更新';
});

const toggleSummary = () => {
  emit('update:isSummaryExpanded', !props.isSummaryExpanded);
};

const toggleFavorite = () => {
  emit('toggleFavorite');
};

const toggleFollow = () => {
  emit('toggleFollow');
};

// 点击外部关闭面板
const btnWrap = ref(null);
const closeMenu = (e) => {
  if (btnWrap.value && !btnWrap.value.contains(e.target)) {
    menuOpen.value = false;
  }
};
onMounted(() => document.addEventListener('click', closeMenu));
onBeforeUnmount(() => document.removeEventListener('click', closeMenu));
</script>

<style scoped>


/* 追番按钮包裹 */
.anime-follow-btn-wrap {
  position: relative;
  display: inline-flex;
}

/* 级联状态面板 */
.follow-status-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 100;
  background: var(--al-bg);
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(0,0,0,0.18), 0 2px 6px rgba(0,0,0,0.08);
  padding: 10px 6px;
  min-width: 168px;
}

.follow-status-title {
  font-size: 0.78rem;
  color: var(--al-text-muted);
  padding: 4px 12px 8px;
  border-bottom: 1px solid var(--al-bg-beige-6);
  margin-bottom: 4px;
}

/* 级联按钮组 */
.follow-status-cascade {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.follow-status-cascade-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: none;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  color: var(--al-text-brown-21);
  transition: background 0.15s, color 0.15s;
  text-align: left;
}

.follow-status-cascade-item:hover {
  background: rgba(0,0,0,0.04);
}

.follow-status-cascade-item.active {
  background: var(--al-bg-active-soft);
  color: var(--al-accent);
  font-weight: 600;
}

.cascade-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cascade-label {
  flex: 1;
}

.cascade-check {
  color: var(--al-accent);
  font-size: 1rem;
}

/* 取消追番 */
.follow-status-unfollow {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: none;
  padding: 9px 12px;
  margin-top: 4px;
  border-top: 1px solid var(--al-bg-beige-6);
  border-radius: 0;
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--al-text-muted-2);
  transition: color 0.15s;
}

.follow-status-unfollow:hover {
  color: var(--al-danger-coral);
}

/* 过渡动画 */
.follow-menu-enter-active,
.follow-menu-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.follow-menu-enter-from,
.follow-menu-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
