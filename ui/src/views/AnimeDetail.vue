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
          @toggleFollow="() => toggleFollow(resolvedAnimeId.value, animeData.value)"
          @set-follow-status="(s) => setFollowStatus(resolvedAnimeId.value, s, animeData.value)"
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
          <div v-if="showBangumiCollectionCard" class="bangumi-collection-card">
            <div class="bangumi-collection-header">
              <div>
                <h3>Bangumi 评分与评论</h3>
                <p>
                  将评分与短评同步到
                  <a :href="bangumiSubjectUrl" target="_blank" rel="noopener noreferrer">{{ bangumiSubjectUrl }}</a>
                </p>
              </div>
              <div class="bangumi-header-actions">
                <button
                  v-if="!bgmCollectionEditMode && bgmCollectionExists"
                  class="bangumi-edit-btn"
                  :disabled="bgmCollectionLoading || bgmCollectionSaving"
                  @click="bgmCollectionEditMode = true"
                >编辑</button>
                <button
                  v-if="bgmCollectionExists"
                  class="bangumi-refresh-btn"
                  :disabled="bgmCollectionLoading || bgmCollectionSaving"
                  @click="fetchBangumiCollection()"
                >刷新状态</button>
              </div>
            </div>

            <div v-if="bgmCollectionLoading" class="bangumi-collection-loading">
              <span class="bangumi-loading-spinner"></span>
              正在读取你的 Bangumi 评分与短评...
            </div>

            <!-- 未收藏且非编辑态：极简空状态 -->
            <div v-else-if="!bgmCollectionExists && !bgmCollectionEditMode" class="bangumi-not-collected">
              <span class="bangumi-not-collected-label">暂未评分</span>
              <button class="bangumi-start-btn" @click="bgmCollectionEditMode = true">开始评分</button>
          </div>

            <!-- 有收藏数据或处于编辑态：完整表单 -->
            <template v-else>
              <div class="bangumi-collection-form">
                <label>
                  <span>收藏状态</span>
                  <template v-if="bgmCollectionEditMode">
                    <select v-model.number="bgmCollectionForm.type" :disabled="bgmCollectionLoading || bgmCollectionSaving">
                      <option :value="2">看过</option>
                      <option :value="3">在看</option>
                      <option :value="4">搁置</option>
                      <option :value="5">抛弃</option>
                    </select>
                  </template>
                  <template v-else>
                    <div class="bangumi-static-field">{{ collectionTypeText(bgmCollectionForm.type) }}</div>
                  </template>
                </label>

                <label>
                  <span>评分</span>
                  <div class="bangumi-stars-wrap" :class="{ editable: bgmCollectionEditMode }">
                    <button
                      v-for="n in 10"
                      :key="n"
                      class="bangumi-star-btn"
                      :class="{ active: n <= (bgmCollectionForm.rate || 0) }"
                      :disabled="!bgmCollectionEditMode || bgmCollectionLoading || bgmCollectionSaving"
                      @click="setBangumiRate(n)"
                    >★</button>
                    <span class="bangumi-rate-value">{{ collectionRateText(bgmCollectionForm.rate) }}</span>
                  </div>
                </label>
              </div>

              <label class="bangumi-comment-field">
                <span>短评</span>
                <template v-if="bgmCollectionEditMode">
                  <textarea
                    v-model="bgmCollectionForm.comment"
                    rows="4"
                    maxlength="380"
                    placeholder="写下你对这部作品的评价，会同步到 Bangumi。"
                  ></textarea>
                </template>
                <template v-else>
                  <div class="bangumi-comment-display">{{ bgmCollectionForm.comment?.trim() || '暂未填写短评' }}</div>
                </template>
              </label>

              <div class="bangumi-collection-actions">
                <template v-if="bgmCollectionEditMode">
                  <button
                    class="bangumi-save-btn"
                    :disabled="bgmCollectionSaving || bgmCollectionLoading"
                    @click="submitBangumiCollection"
                  >
                    {{ bgmCollectionSaving ? '提交中...' : '保存到 Bangumi' }}
                  </button>
                  <button
                    class="bangumi-cancel-btn"
                    :disabled="bgmCollectionSaving || bgmCollectionLoading"
                    @click="cancelBangumiEdit"
                  >取消编辑</button>
                </template>
              </div>
            </template>
          </div>

          <div v-else-if="showBangumiBindHint" class="bangumi-bind-hint-card">
            已登录。绑定 Bangumi 账号后，可在这里同步评分和短评。
            <router-link to="/profile/binding">前往个人中心绑定</router-link>
          </div>

          <p class="comments-source-hint">
            评论来自
            <a href="https://bgm.tv/" target="_blank" rel="noopener noreferrer">https://bgm.tv/</a>
            ，查看原页面：
            <a :href="bangumiSubjectUrl" target="_blank" rel="noopener noreferrer">{{ bangumiSubjectUrl }}</a>
          </p>
          <BangumiComments
            :subject-id="bangumiSubjectId"
            @unavailable="commentsAvailable = false; activeSection = 'episodes'"
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
import { getAnimeRawJson, getAnimeRawJsonBySubject, getAnimeEpisodes } from '../api/anime';
import { getPlayResume } from '../api/playHistory';
import { getCurrentUser } from '../api/auth';
import { getSubjectCollection, saveSubjectCollection } from '../api/bangumi';
import { useAuth } from '../composables/useAuth';
import { useAnimeDerived } from '../composables/useAnimeDerived';
import { useFollow } from '../composables/useFollow';
import { useResourceSelection } from '../composables/useResourceSelection';
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
import AnimeLastWatchSection from '../components/anime/AnimeLastWatchSection.vue';
import ResourceSelectDialog from '../components/anime/ResourceSelectDialog.vue';

// Props
const props = defineProps({
  animeId: {
    type: [String, Number],
    required: false,
    default: null
  },
  bgmMode: {
    type: Boolean,
    default: false
  }
});

// State
const animeData = ref(null);
const existingEpisodes = ref([]);
const loading = ref(true);
const error = ref(null);
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
const fetchAnimeData = async () => {
  try {
    loading.value = true;
    error.value = null;

    // 根据模式决定调用哪个 API
    let targetAnimeId = resolvedAnimeId.value || route.params.animeId;
    const result = props.bgmMode && route.params.subjectId
      ? await getAnimeRawJsonBySubject(route.params.subjectId)
      : await getAnimeRawJson(targetAnimeId);

    if (result.code === 200 && result.data && result.data.bangumi) {
      animeData.value = result.data.bangumi;
      // bgmMode 下从响应中提取 animeId 用于后续接口调用
      if (props.bgmMode && result.data.bangumi.animeId) {
        targetAnimeId = result.data.bangumi.animeId;
      }
      resolvedAnimeId.value = targetAnimeId;
      // 检查是否已追番
      checkFollowStatus(targetAnimeId);
    } else {
      throw new Error('Unexpected response structure');
    }

    try {
      const episodesResult = await getAnimeEpisodes(targetAnimeId, { page: 1, pageSize: 9999 });
      if (episodesResult.code === 200 && episodesResult.data && Array.isArray(episodesResult.data.content)) {
        existingEpisodes.value = episodesResult.data.content;
      } else {
        existingEpisodes.value = [];
      }
    } catch {
      existingEpisodes.value = [];
    }
  } catch (err) {
    animeData.value = null;
    existingEpisodes.value = [];
    error.value = err.message;
  } finally {
    loading.value = false;
    fetchAnimeResume();
  }
};

const fetchAnimeResume = async () => {
  if (!token.value) {
    animeResume.value = null;
    resumeLoading.value = false;
    return;
  }
  resumeLoading.value = true;
  try {
    const body = await getPlayResume(resolvedAnimeId.value);
    if (body?.code === 200) {
      animeResume.value = body.data ?? null;
    } else {
      animeResume.value = null;
    }
  } catch (e) {
    animeResume.value = null;
  } finally {
    resumeLoading.value = false;
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
  return bangumiSubjectId.value ? `https://bgm.tv/subject/${bangumiSubjectId.value}` : 'https://bgm.tv/';
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

const fetchBangumiCollection = async () => {
  if (!showBangumiCollectionCard.value || !bangumiSubjectId.value) {
    return;
  }

  bgmCollectionLoading.value = true;
  try {
    const response = await getSubjectCollection(bangumiSubjectId.value);
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
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }

    showAppMessage(response?.msg || '读取 Bangumi 收藏状态失败', 'error');
  } catch (error) {
    if (error.response?.data?.code === 404) {
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }
    showAppMessage(error.response?.data?.msg || '读取 Bangumi 收藏状态失败', 'error');
  } finally {
    bgmCollectionLoading.value = false;
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

const setBangumiRate = (score) => {
  if (!bgmCollectionEditMode.value) {
    return;
  }
  bgmCollectionForm.value.rate = Number(score);
};

const cancelBangumiEdit = async () => {
  bgmCollectionEditMode.value = false;
  await fetchBangumiCollection();
};

const collectionTypeText = (type) => {
  const map = { 1: '想看', 2: '看过', 3: '在看', 4: '搁置', 5: '抛弃' };
  return map[type] || '在看';
};

const collectionRateText = (rate) => {
  const score = Number(rate || 0);
  if (score <= 0) {
    return '暂未评分';
  }
  return `${score}/10`;
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

.bangumi-collection-card,
.bangumi-bind-hint-card {
  background: var(--al-bg-watch);
  border: 1px solid var(--al-border-warm-2);
  border-radius: 18px;
  padding: 18px 20px;
  margin: 0 0 20px;
}

.bangumi-collection-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.bangumi-header-actions {
  display: flex;
  gap: 8px;
}

.bangumi-collection-header h3 {
  margin: 0 0 6px;
  color: var(--al-text-strong);
  font-size: 1.02rem;
}

.bangumi-collection-header p,
.bangumi-bind-hint-card {
  margin: 0;
  color: var(--al-text-secondary);
  font-size: 0.92rem;
  line-height: 1.6;
}

.bangumi-collection-header a,
.bangumi-bind-hint-card a {
  color: var(--al-accent);
  text-decoration: none;
}

.bangumi-collection-header a:hover,
.bangumi-bind-hint-card a:hover {
  text-decoration: underline;
}

.bangumi-refresh-btn,
.bangumi-edit-btn,
.bangumi-save-btn {
  border: none;
  border-radius: 12px;
  cursor: pointer;
  height: 40px;
  padding: 0 16px;
  font-weight: 600;
}

.bangumi-edit-btn {
  background: var(--al-bg);
  color: var(--al-text-secondary);
  border: 1px solid var(--al-border-soft-4);
}

.bangumi-refresh-btn {
  background: var(--al-bg-beige-2);
  color: var(--al-text-brown-21);
}

.bangumi-save-btn {
  background: var(--al-accent);
  color: var(--al-text-on-accent);
}

.bangumi-refresh-btn:disabled,
.bangumi-save-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.bangumi-collection-error {
  margin: 0 0 12px;
  color: var(--al-danger-coral-2);
  font-size: 0.9rem;
}

.bangumi-collection-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.bangumi-collection-form label,
.bangumi-comment-field {
  display: grid;
  gap: 8px;
}

.bangumi-collection-form span,
.bangumi-comment-field span {
  color: var(--al-text-brown-21);
  font-size: 0.9rem;
  font-weight: 600;
}

.bangumi-collection-form select,
.bangumi-comment-field textarea {
  width: 100%;
  border: 1px solid var(--al-border-soft-5);
  border-radius: 12px;
  background: var(--al-bg);
  padding: 10px 12px;
  font-size: 0.92rem;
  color: var(--al-text-strong);
}

.bangumi-static-field {
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 10px 12px;
  color: var(--al-text-brown-21);
  min-height: 42px;
  display: flex;
  align-items: center;
}

.bangumi-comment-display {
  width: 100%;
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 10px 12px;
  font-size: 0.92rem;
  color: var(--al-text-brown-21);
  line-height: 1.65;
  min-height: 100px;
  white-space: pre-wrap;
  word-break: break-word;
}

.bangumi-collection-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--al-text-muted);
  font-size: 0.88rem;
  margin: 0 0 12px;
}

.bangumi-loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--al-border-soft-3);
  border-top-color: var(--al-accent);
  border-radius: 50%;
  animation: bangumi-spin 0.9s linear infinite;
}

@keyframes bangumi-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.bangumi-stars-wrap {
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.bangumi-stars-wrap.editable {
  background: var(--al-bg);
}

.bangumi-star-btn {
  border: none;
  background: transparent;
  color: var(--al-gray-text);
  font-size: 1rem;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

.bangumi-star-btn.active {
  color: var(--al-star-3);
}

.bangumi-star-btn:disabled {
  cursor: default;
}

.bangumi-rate-value {
  margin-left: 8px;
  color: var(--al-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
}

.bangumi-comment-field textarea {
  resize: vertical;
  min-height: 100px;
}

.bangumi-collection-actions {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.bangumi-cancel-btn {
  border: 1px solid var(--al-border-soft-4);
  border-radius: 12px;
  background: var(--al-bg);
  color: var(--al-text-brown-21);
  cursor: pointer;
  height: 40px;
  padding: 0 16px;
  font-weight: 600;
}

.bangumi-not-collected {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 28px 0 20px;
}

.bangumi-not-collected-label {
  color: var(--al-text-muted);
  font-size: 1rem;
}

.bangumi-start-btn {
  border: 1.5px solid var(--al-accent);
  background: none;
  color: var(--al-accent);
  border-radius: 20px;
  padding: 7px 28px;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}

.bangumi-start-btn:hover {
  background: var(--al-accent);
  color: var(--al-text-on-accent);
}

.bangumi-inline-hint {
  color: var(--al-text-muted);
  font-size: 0.86rem;
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

  .bangumi-collection-form {
    grid-template-columns: 1fr;
  }

  .bangumi-collection-header,
  .bangumi-collection-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .bangumi-header-actions {
    width: 100%;
  }
}
</style>