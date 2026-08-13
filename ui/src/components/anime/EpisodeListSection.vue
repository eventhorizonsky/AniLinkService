<template>
  <div class="anime-episode-section">
    <div class="anime-section-header">
      <div class="anime-tabs">
        <button 
          v-for="tab in tabs" 
          :key="tab.value"
          class="anime-tab-btn" 
          :class="{ active: activeTab === tab.value }" 
          @click="selectTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span class="anime-episode-count">共{{ totalCount }}集 (主线{{ mainCount }})</span>
    </div>
    <div class="anime-episode-list">
      <div 
        v-for="ep in displayedEpisodes" 
        :key="ep.episodeId" 
        class="anime-episode-card" 
        :class="{ 
          today: isToday(ep),
          'is-current': isCurrentEpisode(ep)
        }"
      >
        <div class="anime-episode-left">
          <span class="anime-episode-num">{{ episodeNumberDisplay(ep) }}</span>
          <span class="anime-episode-title" :title="ep.episodeTitle">{{ episodeTitleDisplay(ep) }}</span>
          <span v-if="isToday(ep)" class="anime-today-tag">今日更新</span>
          <span v-if="isCurrentEpisode(ep)" class="anime-current-tag">正在播放</span>
        </div>
        <div class="anime-episode-meta">
          <span>{{ formatDate(ep.airDate) }}</span>
          <a
            href="#"
            class="anime-watch-btn"
            :class="{ disabled: !canPlay(ep) }"
            :aria-disabled="!canPlay(ep)"
            :tabindex="canPlay(ep) ? 0 : -1"
            @click.prevent="playEpisode(ep)"
          >
            <i v-if="isCurrentEpisode(ep)" class="mdi mdi-volume-high"></i>
            <i v-else class="mdi mdi-play-circle"></i> 
            {{ isCurrentEpisode(ep) ? '播放中' : '观看' }}
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  episodes: {
    type: Array,
    required: true
  },
  mainCount: {
    type: Number,
    required: true
  },
  totalCount: {
    type: Number,
    required: true
  },
  playableEpisodeKeys: {
    type: Set,
    default: () => new Set()
  },
  currentEpisodeId: {
    type: [String, Number],
    default: null
  }
});

const emit = defineEmits(['playEpisode']);

const activeTab = ref('main');

const tabs = [
  { label: '正片', value: 'main' },
  { label: '特典/短片', value: 'special' },
  { label: '全部', value: 'all' }
];

const EPISODE_TITLE_MAX_LEN = 36;

const todayStr = new Date().toISOString().slice(0, 10);

const formatDate = (iso) => iso ? iso.slice(0, 10) : '';

const isToday = (ep) => formatDate(ep.airDate) === todayStr;

const isFuture = (ep) => new Date(ep.airDate) > new Date();

const isEpisodeExisting = (ep) => {
  if (!ep || ep.episodeId === undefined || ep.episodeId === null) {
    return false;
  }
  return props.playableEpisodeKeys.has(String(ep.episodeId));
};

const isCurrentEpisode = (ep) => {
  if (!ep || ep.episodeId === undefined || ep.episodeId === null) {
    return false;
  }
  return String(ep.episodeId) === String(props.currentEpisodeId);
};

const canPlay = (ep) => isEpisodeExisting(ep) && !isFuture(ep);

const getEpisodeType = (ep) => {
  const num = ep.episodeNumber;
  if (/^\d+$/.test(num)) return 'main';
  if (num.startsWith('S')) return 'special';
  if (num.startsWith('C')) return 'credit';
  return 'other';
};

const mainEpisodes = computed(() => 
  props.episodes?.filter(ep => getEpisodeType(ep) === 'main') || []
);

const specialEpisodes = computed(() => 
  props.episodes?.filter(ep => ['special', 'credit'].includes(getEpisodeType(ep))) || []
);

const allEpisodesSorted = computed(() => 
  [...(props.episodes || [])].sort((a, b) => new Date(a.airDate) - new Date(b.airDate))
);

const displayedEpisodes = computed(() => {
  if (activeTab.value === 'main') return mainEpisodes.value;
  if (activeTab.value === 'special') return specialEpisodes.value;
  return allEpisodesSorted.value;
});

const episodeNumberDisplay = (ep) => {
  const type = getEpisodeType(ep);
  if (type === 'main') return `第${ep.episodeNumber}话`;
  if (type === 'special') return '特典';
  if (type === 'credit') return '主题';
  return ep.episodeNumber;
};

const truncateText = (text, maxLen) => {
  const str = String(text || '');
  if (str.length <= maxLen) {
    return str;
  }
  return `${str.slice(0, maxLen)}...`;
};

const episodeTitleDisplay = (ep) => {
  return truncateText(ep?.episodeTitle || '', EPISODE_TITLE_MAX_LEN);
};

const selectTab = (value) => {
  activeTab.value = value;
};

const playEpisode = (ep) => {
  if (!canPlay(ep)) return;
  emit('playEpisode', ep);
};
</script>

<style scoped>
@import '../../styles/anime.css';

/* 正在播放的剧集样式 */
.anime-episode-card.is-current {
  background: linear-gradient(135deg, var(--al-bg-highlight-1) 0%, var(--al-bg-highlight-2) 100%);
  border: 2px solid var(--anime-accent-red);
  box-shadow: 0 4px 16px rgba(196, 93, 43, 0.2);
  position: relative;
}

/* 正在播放标签样式 */
.anime-current-tag {
  display: inline-block;
  background: linear-gradient(135deg, var(--anime-accent-red) 0%, var(--anime-accent-orange) 100%);
  color: var(--al-text-on-accent);
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 600;
  animation: pulse 2s ease-in-out infinite;
  white-space: nowrap;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

/* 当前播放状态下的按钮样式 */
.anime-episode-card.is-current .anime-watch-btn {
  background: linear-gradient(135deg, var(--anime-accent-red) 0%, var(--anime-accent-orange) 100%) !important;
  color: var(--al-text-on-accent) !important;
  box-shadow: 0 4px 12px rgba(196, 93, 43, 0.3) !important;
}

.anime-episode-card.is-current .anime-watch-btn:hover {
  transform: translateY(-2px) !important;
  box-shadow: 0 6px 16px rgba(196, 93, 43, 0.4) !important;
}

.anime-watch-btn.disabled {
  background: var(--al-gray-chip);
  color: var(--al-gray-chip-text);
  cursor: not-allowed;
  box-shadow: none;
}

.anime-watch-btn.disabled:hover {
  transform: none;
  filter: none;
}

.anime-episode-left {
  min-width: 0;
}

.anime-episode-title {
  max-width: min(52vw, 520px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .anime-episode-title {
    max-width: min(64vw, 340px);
  }
}
</style>
