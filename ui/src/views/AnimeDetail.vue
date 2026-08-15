<template>
  <div class="page skeleton-page" v-if="loading">
    <div class="anime-detail-layout">
      <div class="anime-left-column">
        <div class="anime-skeleton-card">
          <div class="sk-img"></div>
          <div class="sk-line" style="width: 72%"></div>
          <div class="sk-line" style="width: 94%"></div>
          <div class="sk-line" style="width: 60%"></div>
        </div>

        <div class="anime-skeleton-card">
          <div class="sk-line" style="width: 40%"></div>
          <div class="sk-line" style="width: 92%"></div>
          <div class="sk-line" style="width: 88%"></div>
          <div class="sk-line" style="width: 90%"></div>
          <div class="sk-line" style="width: 84%"></div>
          <div class="sk-line" style="width: 86%"></div>
        </div>

        <div class="anime-skeleton-card">
          <div class="sk-line" style="width: 40%"></div>
          <div class="sk-line" style="width: 92%"></div>
          <div class="sk-line" style="width: 88%"></div>
        </div>
      </div>

      <div class="anime-sidebar">
        <div class="anime-skeleton-card">
          <div class="sk-line" style="width: 50%"></div>
          <div class="sk-line" style="width: 90%"></div>
          <div class="sk-line" style="width: 84%"></div>
          <div class="sk-line" style="width: 88%"></div>
          <div class="sk-line" style="width: 80%"></div>
        </div>

        <div class="anime-skeleton-card">
          <div class="sk-line" style="width: 40%"></div>
          <div class="sk-img" style="height: 140px"></div>
        </div>
      </div>
    </div>
  </div>
  <div class="page-wrapper" v-else-if="error">
    <div class="error">数据加载失败: {{ error }}</div>
  </div>
  <div class="page" v-else>
    <div class="anime-detail-layout">
      <!-- 左侧：头部信息和分集 -->
      <div class="anime-left-column">
        <!-- 头部信息 -->
        <AnimeHeroSection
          :anime-data="animeData"
          :title-info="titleInfo"
          :is-on-air="isOnAir"
          :air-day-text="airDayText"
          :rating-main="ratingMain"
          :rating-bangumi="ratingBangumi"
          :rating-anidb="ratingAnidb"
          :main-episodes="mainEpisodes"
          :total-episodes="totalEpisodes"
          :formatted-summary="formattedSummary"
          :is-summary-expanded="isSummaryExpanded"
          :is-following="isFollowing"
          :follow-status="followStatus"
          :follow-loading="followLoading"
          @update:is-summary-expanded="isSummaryExpanded = $event"
          @toggleFollow="() => toggleFollow(resolvedAnimeId, animeData)"
          @set-follow-status="(s) => setFollowStatus(resolvedAnimeId, s, animeData)"
        />

        <AnimeLastWatchSection
          v-if="showLastWatchSection"
          :loading="resumeLoading"
          :progress="animeResume"
          :episode-line="resumeEpisodeLine"
          :progress-text="resumeProgressText"
          :last-play-text="resumeLastPlayText"
          :continue-disabled="!canResumeContinue"
          :next-disabled="!canWatchNextEpisode"
          @continue="continueFromHistory"
          @next="watchNextEpisode"
        />

        <!-- 分集/评论切换：仅当评论区可用时展示 -->
        <div v-if="showCommentsTab" class="detail-section-tabs">
          <button
            class="detail-section-tab"
            :class="{ active: activeSection === 'episodes' }"
            @click="activeSection = 'episodes'"
          >分集</button>
          <button
            class="detail-section-tab"
            :class="{ active: activeSection === 'comments' }"
            @click="activeSection = 'comments'"
          >评论区</button>
        </div>

        <!-- 分集区 -->
        <EpisodeListSection
          v-if="activeSection === 'episodes'"
          :episodes="animeData.episodes"
          :main-count="mainEpisodes.length"
          :total-count="animeData.episodes.length"
          :playable-episode-keys="playableEpisodeKeys"
          @playEpisode="playEpisode"
        />

        <!-- 评论区内容 -->
        <div v-if="activeSection === 'comments' && bangumiSubjectId" class="detail-comments-section">
          <BangumiCollectionCard
            v-if="showBangumiCollectionCard"
            :subject-id="bangumiSubjectId"
            :subject-url="bangumiSubjectUrl"
            :collection-form="bgmCollectionForm"
            :edit-mode="bgmCollectionEditMode"
            :exists="bgmCollectionExists"
            :loading="bgmCollectionLoading"
            :saving="bgmCollectionSaving"
            @update:edit-mode="bgmCollectionEditMode = $event"
            @update:collection-form="bgmCollectionForm = $event"
            @refresh="fetchBangumiCollection"
            @save="submitBangumiCollection"
            @cancel="cancelBangumiEdit"
          />

          <div v-else-if="showBangumiBindHint" class="bangumi-bind-hint-card">
            已登录。绑定 Bangumi 账号后，可在这里同步评分和短评。
            <router-link to="/profile/binding">前往个人中心绑定</router-link>
          </div>

          <p class="comments-source-hint">
            评论来自
            <a :href="BANGUMI_BASE_URL" target="_blank" rel="noopener noreferrer">{{ BANGUMI_BASE_URL }}</a>
            ，查看原页面：
            <a :href="bangumiSubjectUrl" target="_blank" rel="noopener noreferrer">{{ bangumiSubjectUrl }}</a>
          </p>
          <BangumiComments
            :subject-id="bangumiSubjectId"
            @unavailable="handleCommentsUnavailable"
          />
        </div>

        <!-- 预告片 -->
        <TrailerCarousel :trailers="animeData.trailers" />

        <!-- 外部链接和版权 -->
        <FooterLinks :databases="animeData.onlineDatabases" />
      </div>

      <!-- 右侧边栏 -->
      <div class="anime-sidebar">
        <!-- 制作信息 -->
        <MetadataCard
          :staff-list="staffList"
          :tags="animeData.tags"
        />

        <!-- 相关作品 -->
        <RelatedWorksCarousel :relateds="animeData.relateds" />
      </div>
    </div>

    <ResourceSelectDialog
      :open="showResourceDialog"
      :resources="selectedResources"
      :title="selectedEpisodeTitle"
      @select="selectResource"
      @close="closeResourceDialog"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showAppMessage } from '../utils/ui-feedback';
import { getAnimeRawJson, getAnimeRawJsonBySubject } from '../api/anime';
import { getPlayResume } from '../api/playHistory';
import { getCurrentUser } from '../api/auth';
import { getSubjectCollection, saveSubjectCollection } from '../api/bangumi';
import { useAuth } from '../composables/useAuth';
import { useAnimeData } from '../composables/useAnimeData';
import { useAnimeDerived } from '../composables/useAnimeDerived';
import { useFollow } from '../composables/useFollow';
import { useResourceSelection } from '../composables/useResourceSelection';
import { BANGUMI_BASE_URL } from '../utils/constants';
import {
  isFuture,
  filterMainEpisodes,
  filterSpecialEpisodes,
  buildPlayableEpisodeKeys,
  getEpisodeResources,
} from '../utils/episodes';
import { formatDateTime } from '../utils/format';
import AnimeHeroSection from '../components/anime/AnimeHeroSection.vue';
import EpisodeListSection from '../components/anime/EpisodeListSection.vue';
import TrailerCarousel from '../components/anime/TrailerCarousel.vue';
import RelatedWorksCarousel from '../components/anime/RelatedWorksCarousel.vue';
import MetadataCard from '../components/anime/MetadataCard.vue';
import FooterLinks from '../components/anime/FooterLinks.vue';
import BangumiComments from '../components/anime/BangumiComments.vue';
import BangumiCollectionCard from '../components/anime/BangumiCollectionCard.vue';
import AnimeLastWatchSection from '../components/anime/AnimeLastWatchSection.vue';
import ResourceSelectDialog from '../components/anime/ResourceSelectDialog.vue';

// Props
const props = defineProps({
  bgmMode: {
    type: Boolean,
    default: false
  }
});

// State
const isSummaryExpanded = ref(false);
const route = useRoute();
const router = useRouter();
const { token, isLoggedIn, userInfo, setUserInfo } = useAuth();
const { isFollowing, followStatus, followLoading, checkFollowStatus, setFollowStatus, toggleFollow } = useFollow();
const { showResourceDialog, selectedResources, selectedEpisodeTitle, closeResourceDialog, selectResource, playEpisode } =
  useResourceSelection({
    router,
    getAnimeId: () => resolvedAnimeId.value,
    getExistingEpisodes: () => existingEpisodes.value,
  });
const activeSection = ref('episodes'); // 'episodes' | 'comments'
const commentsAvailable = ref(true);
const currentUserInfo = ref(null);
const bgmCollectionLoading = ref(false);
const bgmCollectionSaving = ref(false);
const bgmCollectionEditMode = ref(false);
const bgmCollectionExists = ref(false);
const bgmCollectionForm = ref({
  type: 3,
  rate: 0,
  comment: ''
});

const animeResume = ref(null);
const resumeLoading = ref(false);

// bgmMode 下 animeId 来自 API 响应，否则来自路由参数
const resolvedAnimeId = ref(null);

// Fetch Data
const { animeData, existingEpisodes, loading, error, fetchAnimeData, fetchSeq } = useAnimeData({
  getAnimeId: () => resolvedAnimeId.value || route.params.animeId,
  fetchAnime: () => (props.bgmMode && route.params.subjectId
    ? getAnimeRawJsonBySubject(route.params.subjectId)
    : getAnimeRawJson(resolvedAnimeId.value || route.params.animeId)),
  initialLoading: true,
  onDataLoaded: (animeId) => {
    // bgmMode 下从响应中提取 animeId 用于后续接口调用
    let targetAnimeId = animeId;
    if (props.bgmMode && animeData.value?.animeId) {
      targetAnimeId = animeData.value.animeId;
    }
    resolvedAnimeId.value = targetAnimeId;
    // 检查是否已追番
    checkFollowStatus(targetAnimeId);
    return targetAnimeId;
  },
  onAfterFetch: () => {
    fetchAnimeResume();
  },
});

const fetchAnimeResume = async () => {
  const seq = fetchSeq.value
  if (!token.value) {
    animeResume.value = null;
    resumeLoading.value = false;
    return;
  }
  resumeLoading.value = true;
  try {
    const body = await getPlayResume(resolvedAnimeId.value);
    if (seq !== fetchSeq.value) return
    if (body?.code === 200) {
      animeResume.value = body.data ?? null;
    } else {
      animeResume.value = null;
    }
  } catch (e) {
    if (seq !== fetchSeq.value) return
    animeResume.value = null;
  } finally {
    if (seq === fetchSeq.value) resumeLoading.value = false;
  }
};

onMounted(async () => {
  await refreshCurrentUserInfo();
  fetchAnimeData();
});

// Watch route params
watch(
  () => [props.bgmMode, route.params.animeId, route.params.subjectId],
  () => {
    resolvedAnimeId.value = null;
    closeResourceDialog();
    isSummaryExpanded.value = false;
    activeSection.value = 'episodes';
    commentsAvailable.value = true;
    bgmCollectionEditMode.value = false;
    bgmCollectionExists.value = false;
    bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
    animeResume.value = null;
    fetchAnimeData();
  }
);

// Computed Properties
const mainEpisodes = computed(() => filterMainEpisodes(animeData.value?.episodes));

const {
  isOnAir,
  ratingMain,
  ratingBangumi,
  ratingAnidb,
  totalEpisodes,
  formattedSummary,
  titleInfo,
  airDayText,
  staffList,
} = useAnimeDerived(animeData);

const playableEpisodeKeys = computed(() => buildPlayableEpisodeKeys(existingEpisodes.value));

const bangumiSubjectId = computed(() => {
  const url = animeData.value?.bangumiUrl;
  if (!url) return null;
  const match = String(url).match(/\/subject\/(\d+)/);
  return match ? match[1] : null;
});

const bangumiSubjectUrl = computed(() => {
  const url = animeData.value?.bangumiUrl;
  if (url && /\/subject\/\d+/.test(String(url))) {
    return String(url);
  }
  return bangumiSubjectId.value ? `${BANGUMI_BASE_URL}subject/${bangumiSubjectId.value}` : BANGUMI_BASE_URL;
});

const showCommentsTab = computed(() => bangumiSubjectId.value !== null && commentsAvailable.value);

const showLastWatchSection = computed(() => {
  if (!isLoggedIn.value || loading.value || error.value) return false;
  return resumeLoading.value || !!animeResume.value;
});

const historyResolvedEpisodeId = computed(() => {
  const h = animeResume.value;
  if (!h) return '';
  if (h.episodeId !== undefined && h.episodeId !== null && String(h.episodeId).trim() !== '') {
    return String(h.episodeId);
  }
  const vid = h.videoId;
  if (!vid) return '';
  const row = existingEpisodes.value.find((r) => String(r.id) === String(vid));
  return row?.episodeId != null ? String(row.episodeId) : '';
});

const nextPlayableEpisodeAfterHistory = computed(() => {
  const eps = animeData.value?.episodes;
  if (!eps?.length || !animeResume.value) return null;
  const curId = historyResolvedEpisodeId.value;
  if (!curId) return null;
  const idx = eps.findIndex((ep) => String(ep.episodeId) === curId);
  if (idx < 0) return null;
  for (let i = idx + 1; i < eps.length; i++) {
    const ep = eps[i];
    if (isFuture(ep)) continue;
    const resources = getEpisodeResources(existingEpisodes.value, ep.episodeId);
    if (resources.length) return ep;
  }
  return null;
});

const canResumeContinue = computed(() => Boolean(animeResume.value?.videoId));
const canWatchNextEpisode = computed(() => nextPlayableEpisodeAfterHistory.value != null);

const resumeProgressText = computed(() => {
  const item = animeResume.value;
  if (!item) return '';
  const progress = Number(item.progressSeconds || 0);
  const duration = Number(item.durationSeconds || 0);
  const percent = Number(item.progressPercentage || 0);
  if (!duration) return `${progress} 秒`;
  return `${progress} 秒 / ${duration} 秒（${Math.min(100, Math.max(0, percent))}%）`;
});

const resumeLastPlayText = computed(() => formatDateTime(animeResume.value?.lastPlayTime, ''));

const resumeEpisodeLine = computed(() => {
  const h = animeResume.value;
  if (!h) return '';
  const epId = historyResolvedEpisodeId.value;
  if (epId) {
    const ep = animeData.value?.episodes?.find((e) => String(e.episodeId) === epId);
    const num = ep?.episodeNumber;
    const title = ep?.episodeTitle;
    if (num && title) return `第${num}话 ${title}`;
    if (title) return title;
    if (num) return `第${num}话`;
  }
  if (h.videoName && String(h.videoName).trim()) return h.videoName;
  if (h.episodeId !== undefined && h.episodeId !== null && String(h.episodeId).trim() !== '') {
    return `第 ${h.episodeId} 话`;
  }
  return h.videoId ? `视频 #${h.videoId}` : '最近播放';
});
const isBangumiBound = computed(() => Boolean(currentUserInfo.value?.bangumiBound));
const showBangumiCollectionCard = computed(() => isLoggedIn.value && isBangumiBound.value && bangumiSubjectId.value !== null);
const showBangumiBindHint = computed(() => isLoggedIn.value && !isBangumiBound.value && bangumiSubjectId.value !== null);

watch(
  () => [bangumiSubjectId.value, showBangumiCollectionCard.value],
  ([subjectId, visible]) => {
    if (!visible || !subjectId) {
      return;
    }
    fetchBangumiCollection();
  }
);

// Event Handlers
const handleCommentsUnavailable = () => {
  commentsAvailable.value = false;
  activeSection.value = 'episodes';
};

const loadCurrentUserInfo = () => {
  currentUserInfo.value = userInfo.value;
};

const refreshCurrentUserInfo = async () => {
  if (!token.value) {
    currentUserInfo.value = null;
    return;
  }

  try {
    const body = await getCurrentUser();
    if (body?.code === 200 && body?.data) {
      currentUserInfo.value = body.data;
      setUserInfo(body.data);
      return;
    }
  } catch {
    // ignore and fallback to local cache
  }

  loadCurrentUserInfo();
};

let bangumiCollectionSeq = 0
const fetchBangumiCollection = async () => {
  if (!showBangumiCollectionCard.value || !bangumiSubjectId.value) {
    return;
  }

  const seq = ++bangumiCollectionSeq
  const subjectId = bangumiSubjectId.value
  bgmCollectionLoading.value = true;
  try {
    const response = await getSubjectCollection(subjectId);
    if (seq !== bangumiCollectionSeq) return
    if (response?.code === 200 && response?.data) {
      bgmCollectionForm.value = {
        type: Number(response.data.type || 3),
        rate: Number(response.data.rate || 0),
        comment: response.data.comment || ''
      };
      bgmCollectionExists.value = true;
      return;
    }

    if (response?.code === 404) {
      if (seq !== bangumiCollectionSeq) return
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }

    if (seq !== bangumiCollectionSeq) return
    showAppMessage(response?.msg || '读取 Bangumi 收藏状态失败', 'error');
  } catch (error) {
    if (seq !== bangumiCollectionSeq) return
    if (error.response?.data?.code === 404) {
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }
    showAppMessage(error.response?.data?.msg || '读取 Bangumi 收藏状态失败', 'error');
  } finally {
    if (seq === bangumiCollectionSeq) bgmCollectionLoading.value = false;
  }
};

const submitBangumiCollection = async () => {
  if (!bangumiSubjectId.value) {
    return;
  }

  bgmCollectionSaving.value = true;
  try {
    const payload = {
      type: Number(bgmCollectionForm.value.type || 3),
      rate: Number(bgmCollectionForm.value.rate || 0),
      comment: bgmCollectionForm.value.comment || ''
    };
    const response = await saveSubjectCollection(bangumiSubjectId.value, payload);
    if (response?.code === 200) {
      showAppMessage('已同步到 Bangumi', 'success');
      bgmCollectionEditMode.value = false;
      bgmCollectionExists.value = true;
      await fetchBangumiCollection();
      return;
    }
    showAppMessage(response?.msg || '提交 Bangumi 评分失败', 'error');
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '提交 Bangumi 评分失败', 'error');
  } finally {
    bgmCollectionSaving.value = false;
  }
};

const cancelBangumiEdit = async () => {
  bgmCollectionEditMode.value = false;
  await fetchBangumiCollection();
};

const continueFromHistory = () => {
  const h = animeResume.value;
  if (!h?.videoId) return;
  router.push({
    name: 'Player',
    params: { videoId: String(h.videoId) },
    query: {
      animeId: String(resolvedAnimeId.value),
      episodeId: String(h.episodeId ?? ''),
    },
  });
};

const watchNextEpisode = () => {
  const ep = nextPlayableEpisodeAfterHistory.value;
  if (ep) playEpisode(ep);
};
</script>

<style scoped>
/* Page Wrapper */
.page-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  background: var(--al-bg);
  border-radius: 32px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
  padding: 32px;
  min-height: 400px;
}

.loading,
.error {
  text-align: center;
  font-size: 1.2rem;
  color: var(--al-text-secondary);
  padding: 60px;
}

.page {
  background: var(--al-bg);
  border-radius: 32px;
  overflow: hidden;
  padding: 32px;
}

.skeleton-page {
  min-height: 520px;
}

.anime-skeleton-card {
  background: var(--al-bg);
  border: 1px solid var(--al-bg-beige-6);
  border-radius: 16px;
  padding: 14px;
  margin-bottom: 16px;
}

.sk-img,
.sk-line {
  background: linear-gradient(90deg, var(--al-bg-beige) 25%, var(--al-bg-beige-7) 50%, var(--al-bg-beige) 75%);
  background-size: 200% 100%;
  animation: anime-sk-shim 1.4s ease-in-out infinite;
  border-radius: 8px;
}
.sk-img {
  height: 200px;
  margin-bottom: 12px;
}
.sk-line {
  height: 15px;
  margin: 10px 0;
}
@keyframes anime-sk-shim {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Detail Layout */
.anime-detail-layout {
  display: flex;
  gap: 28px;
}

.anime-left-column {
  flex: 1;
  min-width: 0;
}

.anime-sidebar {
  width: 280px;
  flex-shrink: 0;
}

/* Section Tabs */
.detail-section-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 2px solid var(--al-border);
  padding-bottom: 0;
}

.detail-section-tab {
  background: none;
  border: none;
  padding: 8px 20px;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--al-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  border-radius: 4px 4px 0 0;
  transition: color 0.2s, border-color 0.2s;
}

.detail-section-tab:hover {
  color: var(--al-accent);
}

.detail-section-tab.active {
  color: var(--al-accent);
  border-bottom-color: var(--al-accent);
  background: none;
}

.detail-comments-section {
  background: var(--al-bg);
  border-radius: 16px;
  border: 1px solid var(--al-border);
  padding: 16px 20px;
}

.bangumi-bind-hint-card {
  background: var(--al-bg-watch);
  border: 1px solid var(--al-border-warm-2);
  border-radius: 18px;
  padding: 18px 20px;
  margin: 0;
  color: var(--al-text-secondary);
  font-size: 0.92rem;
  line-height: 1.6;
}

.bangumi-bind-hint-card a {
  color: var(--al-accent);
  text-decoration: none;
}

.bangumi-bind-hint-card a:hover {
  text-decoration: underline;
}

.comments-source-hint {
  margin: 0 0 12px;
  font-size: 0.88rem;
  color: var(--al-text-muted);
}

.comments-source-hint a {
  color: var(--al-accent);
  text-decoration: none;
}

.comments-source-hint a:hover {
  text-decoration: underline;
}

/* Responsive */
@media (max-width: 799px) {
  .page {
    padding: 20px;
  }

  .anime-detail-layout {
    display: flex;
    flex-direction: column;
    gap: 20px;
    align-items: center;
  }

  .anime-left-column {
    width: 100%;
  }

  .anime-sidebar {
    width: 100%;
  }
}
</style>