<template>
  <div class="page skeleton-page" v-if="loading">
    <div class="anime-detail-layout">
      <div class="anime-left-column">
        <div class="anime-skeleton-card">
          <v-skeleton-loader type="image, heading, paragraph@2" />
        </div>

        <div class="anime-skeleton-card">
          <v-skeleton-loader type="heading, list-item-two-line@6" />
        </div>

        <div class="anime-skeleton-card">
          <v-skeleton-loader type="heading, list-item-two-line@3" />
        </div>
      </div>

      <div class="anime-sidebar">
        <div class="anime-skeleton-card">
          <v-skeleton-loader type="heading, list-item@5" />
        </div>

        <div class="anime-skeleton-card">
          <v-skeleton-loader type="heading, image" />
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
          :is-favorited="isFavorited"
          :is-following="isFollowing"
          :follow-loading="followLoading"
          @update:is-summary-expanded="isSummaryExpanded = $event"
          @toggleFavorite="toggleFavorite"
          @toggleFollow="toggleFollow"
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
                      <option :value="1">想看</option>
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
            <router-link to="/profile?tab=binding">前往个人中心绑定</router-link>
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
        <FooterLinks
          :databases="animeData.onlineDatabases"
          :copyright-text="copyrightText"
        />
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

    <div v-if="showResourceDialog" class="resource-dialog-mask" @click.self="closeResourceDialog">
      <div class="resource-dialog">
        <h3 class="resource-dialog-title">选择播放资源</h3>
        <p class="resource-dialog-subtitle">{{ selectedEpisodeTitle }}</p>
        <div class="resource-list">
          <button
            v-for="(resource, index) in selectedResources"
            :key="resource.id"
            class="resource-item"
            @click="selectResource(resource)"
          >
            <span class="resource-name">{{ resource.fileName || `资源 ${index + 1}` }}</span>
            <span class="resource-meta">ID: {{ resource.id }}</span>
          </button>
        </div>
        <button class="resource-cancel-btn" @click="closeResourceDialog">取消</button>
      </div>
    </div>

    <v-snackbar
      v-model="snackbarShow"
      :color="snackbarColor"
      location="bottom right"
      :timeout="3500"
      rounded="pill"
    >
      {{ snackbarMsg }}
      <template #actions>
        <v-btn icon="mdi-close" size="small" variant="text" @click="snackbarShow = false" />
      </template>
    </v-snackbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import { showAppMessage } from '../utils/ui-feedback';
import AnimeHeroSection from '../components/anime/AnimeHeroSection.vue';
import EpisodeListSection from '../components/anime/EpisodeListSection.vue';
import TrailerCarousel from '../components/anime/TrailerCarousel.vue';
import RelatedWorksCarousel from '../components/anime/RelatedWorksCarousel.vue';
import MetadataCard from '../components/anime/MetadataCard.vue';
import FooterLinks from '../components/anime/FooterLinks.vue';
import BangumiComments from '../components/anime/BangumiComments.vue';
import AnimeLastWatchSection from '../components/anime/AnimeLastWatchSection.vue';

// Props
const props = defineProps({
  animeId: {
    type: [String, Number],
    required: true
  }
});

// State
const animeData = ref(null);
const existingEpisodes = ref([]);
const loading = ref(true);
const error = ref(null);
const isSummaryExpanded = ref(false);
const isFavorited = ref(false);
const isFollowing = ref(false);
const followLoading = ref(false);
const route = useRoute();
const router = useRouter();
const showResourceDialog = ref(false);
const selectedResources = ref([]);
const selectedEpisodeTitle = ref('');
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

const snackbarShow = ref(false);
const snackbarMsg = ref('');
const snackbarColor = ref('success');
const showSnack = (msg, color = 'success') => {
  snackbarMsg.value = msg;
  snackbarColor.value = color;
  snackbarShow.value = true;
};

// Fetch Data
const fetchAnimeData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const response = await fetch(`/api/animes/${route.params.animeId}/raw-json`);
    const result = await response.json();
    if (result.code === 200 && result.data && result.data.bangumi) {
      animeData.value = result.data.bangumi;
      // 检查是否已追番
      checkFollowStatus();
    } else {
      throw new Error('Unexpected response structure');
    }

    try {
      const episodesResponse = await fetch(`/api/animes/${route.params.animeId}/episodes?page=1&pageSize=9999`);
      const episodesResult = await episodesResponse.json();
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
  const token = localStorage.getItem('token');
  if (!token) {
    animeResume.value = null;
    resumeLoading.value = false;
    return;
  }
  resumeLoading.value = true;
  try {
    const response = await axios.get(`/api/play-history/anime/${route.params.animeId}/resume`);
    if (response.data?.code === 200) {
      animeResume.value = response.data.data ?? null;
    } else {
      animeResume.value = null;
    }
  } catch (e) {
    animeResume.value = null;
  } finally {
    resumeLoading.value = false;
  }
};

// 检查是否已追番
const checkFollowStatus = async () => {
  const token = localStorage.getItem('token');
  if (!token || !animeData.value) {
    isFollowing.value = false;
    return;
  }
  
  try {
    const response = await axios.get(`/api/follows/check/${route.params.animeId}`);
    if (response.data.code === 200) {
      isFollowing.value = response.data.data || false;
    }
  } catch (error) {
    console.error('Failed to check follow status:', error);
    isFollowing.value = false;
  }
};

// 切换追番状态
const toggleFollow = async () => {
  const token = localStorage.getItem('token');
  if (!token) {
    showAppMessage('请先登录', 'warning');
    return;
  }
  
  if (!animeData.value) {
    showAppMessage('请等待数据加载完成', 'warning');
    return;
  }
  
  followLoading.value = true;
  try {
    if (isFollowing.value) {
      // 取消追番
      await axios.delete(`/api/follows/${route.params.animeId}`);
      isFollowing.value = false;
      showAppMessage('已取消追番', 'success');
    } else {
      // 添加追番
      await axios.post('/api/follows', {
        animeId: route.params.animeId,
        animeTitle: animeData.value.titles?.[0]?.title || '未知',
        imageUrl: animeData.value.imageUrl || ''
      });
      isFollowing.value = true;
      showAppMessage('追番成功', 'success');
    }
  } catch (error) {
    console.error('Failed to toggle follow:', error);
    showAppMessage('操作失败，请重试', 'error');
  } finally {
    followLoading.value = false;
  }
};

onMounted(async () => {
  await refreshCurrentUserInfo();
  fetchAnimeData();
});

// Watch route params
watch(() => route.params.animeId, () => {
  closeResourceDialog();
  isSummaryExpanded.value = false;
  activeSection.value = 'episodes';
  commentsAvailable.value = true;
  bgmCollectionEditMode.value = false;
  bgmCollectionExists.value = false;
  bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
  animeResume.value = null;
  fetchAnimeData();
});

// Utility Functions
const formatDate = (iso) => iso ? iso.slice(0, 10) : '';
const todayStr = new Date().toISOString().slice(0, 10);
const isToday = (ep) => formatDate(ep.airDate) === todayStr;
const isFuture = (ep) => new Date(ep.airDate) > new Date();

// Episode type classification
const getEpisodeType = (ep) => {
  const num = ep.episodeNumber;
  if (/^\d+$/.test(num)) return 'main';
  if (num.startsWith('S')) return 'special';
  if (num.startsWith('C')) return 'credit';
  return 'other';
};

// Computed Properties
const mainEpisodes = computed(() => 
  animeData.value?.episodes.filter(ep => getEpisodeType(ep) === 'main') || []
);

const formatRating = (value, digits = 1) => {
  const num = Number(value);
  return Number.isFinite(num) ? num.toFixed(digits) : '--';
};

const isOnAir = computed(() => Boolean(animeData.value?.isOnAir));
const ratingMain = computed(() => formatRating(animeData.value?.rating, 1));
const ratingBangumi = computed(() => animeData.value?.ratingDetails?.['Bangumi评分'] ?? '7.8');
const ratingAnidb = computed(() => animeData.value?.ratingDetails?.['Anidb连载中评分'] ?? '8.51');

const totalEpisodes = computed(() => {
  const meta = animeData.value?.metadata || [];
  const epItem = meta.find(m => m.startsWith('话数'));
  return epItem ? epItem.split(':')[1]?.trim() : '10';
});

const formattedSummary = computed(() => {
  return animeData.value?.summary?.replace(/\n/g, '<br>') || '';
});

const playableEpisodeKeys = computed(() => {
  const set = new Set();
  existingEpisodes.value.forEach((ep) => {
    if (ep.episodeId !== undefined && ep.episodeId !== null) {
      set.add(String(ep.episodeId));
    }
  });
  return set;
});

const titleInfo = computed(() => {
  const titles = animeData.value?.titles || [];
  if (titles.length === 0) return { main: '', sub: '' };
  return {
    main: titles[0]?.title || '',
    sub: titles[1]?.title || ''
  };
});

const airDayText = computed(() => {
  const day = animeData.value?.airDay;
  const dayMap = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' };
  return dayMap[day] || '';
});

const staffList = computed(() => {
  const meta = animeData.value?.metadata || [];
  const keys = ['原作', '导演', '音乐', '动画制作'];
  return meta
    .filter(item => keys.some(key => item.startsWith(key)))
    .map(item => {
      const parts = item.split(':');
      if (parts.length >= 2) {
        return `<strong>${parts[0]}:</strong>${parts.slice(1).join(':')}`;
      }
      return item;
    });
});

const copyrightText = computed(() => {
  const meta = animeData.value?.metadata || [];
  return meta.find(m => m.startsWith('Copyright')) || '©山田鐘人・アベツカサ／小学館／「葬送のフリーレン」製作委員会';
});

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
const isLoggedIn = computed(() => Boolean(localStorage.getItem('token')));

const formatDateTime = (value) => {
  if (!value) return '';
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

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
    const resources = existingEpisodes.value.filter(
      (item) => String(item.episodeId) === String(ep.episodeId) && item.id !== undefined && item.id !== null
    );
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

const resumeLastPlayText = computed(() => formatDateTime(animeResume.value?.lastPlayTime));

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
const toggleFavorite = () => {
  isFavorited.value = !isFavorited.value;
};

const loadCurrentUserInfo = () => {
  try {
    const raw = localStorage.getItem('userInfo');
    currentUserInfo.value = raw ? JSON.parse(raw) : null;
  } catch {
    currentUserInfo.value = null;
  }
};

const refreshCurrentUserInfo = async () => {
  if (!localStorage.getItem('token')) {
    currentUserInfo.value = null;
    return;
  }

  try {
    const response = await axios.post('/api/auth/currentUser');
    if (response.data?.code === 200 && response.data?.data) {
      currentUserInfo.value = response.data.data;
      localStorage.setItem('userInfo', JSON.stringify(response.data.data));
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
    const response = await axios.get(`/api/bangumi/subjects/${bangumiSubjectId.value}/collection`);
    if (response.data?.code === 200 && response.data?.data) {
      bgmCollectionForm.value = {
        type: Number(response.data.data.type || 3),
        rate: Number(response.data.data.rate || 0),
        comment: response.data.data.comment || ''
      };
      bgmCollectionExists.value = true;
      return;
    }

    if (response.data?.code === 404) {
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }

    showSnack(response.data?.msg || '读取 Bangumi 收藏状态失败', 'error');
  } catch (error) {
    if (error.response?.data?.code === 404) {
      bgmCollectionForm.value = { type: 3, rate: 0, comment: '' };
      bgmCollectionExists.value = false;
      return;
    }
    showSnack(error.response?.data?.msg || '读取 Bangumi 收藏状态失败', 'error');
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
    const response = await axios.post(`/api/bangumi/subjects/${bangumiSubjectId.value}/collection`, payload);
    if (response.data?.code === 200) {
      showSnack('已同步到 Bangumi', 'success');
      bgmCollectionEditMode.value = false;
      bgmCollectionExists.value = true;
      await fetchBangumiCollection();
      return;
    }
    showSnack(response.data?.msg || '提交 Bangumi 评分失败', 'error');
  } catch (error) {
    showSnack(error.response?.data?.msg || '提交 Bangumi 评分失败', 'error');
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

const getEpisodeResources = (episodeId) => {
  if (episodeId === undefined || episodeId === null) {
    return [];
  }
  const key = String(episodeId);
  return existingEpisodes.value.filter((item) => String(item.episodeId) === key && item.id !== undefined && item.id !== null);
};

const goToPlayer = (resource) => {
  showResourceDialog.value = false;
  selectedResources.value = [];
  router.push({
    name: 'Player',
    params: { videoId: String(resource.id) },
    query: {
      animeId: String(route.params.animeId),
      episodeId: String(resource.episodeId ?? '')
    }
  });
};

const closeResourceDialog = () => {
  showResourceDialog.value = false;
  selectedResources.value = [];
  selectedEpisodeTitle.value = '';
};

const selectResource = (resource) => {
  goToPlayer(resource);
};

const continueFromHistory = () => {
  const h = animeResume.value;
  if (!h?.videoId) return;
  router.push({
    name: 'Player',
    params: { videoId: String(h.videoId) },
    query: {
      animeId: String(route.params.animeId),
      episodeId: String(h.episodeId ?? ''),
    },
  });
};

const watchNextEpisode = () => {
  const ep = nextPlayableEpisodeAfterHistory.value;
  if (ep) playEpisode(ep);
};

const playEpisode = (ep) => {
  if (isFuture(ep)) return;
  const resources = getEpisodeResources(ep.episodeId);
  if (resources.length === 0) return;

  if (resources.length === 1) {
    goToPlayer(resources[0]);
    return;
  }

  selectedResources.value = resources;
  selectedEpisodeTitle.value = ep.episodeTitle || `第${ep.episodeNumber}话`;
  showResourceDialog.value = true;
};
</script>

<style scoped>
/* Page Wrapper */
.page-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  background: white;
  border-radius: 32px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
  padding: 32px;
  min-height: 400px;
}

.loading,
.error {
  text-align: center;
  font-size: 1.2rem;
  color: #6b5f55;
  padding: 60px;
}

.page {
  background: white;
  border-radius: 32px;
  overflow: hidden;
  padding: 32px;
}

.skeleton-page {
  min-height: 520px;
}

.anime-skeleton-card {
  background: #fff;
  border: 1px solid #efe7de;
  border-radius: 16px;
  padding: 14px;
  margin-bottom: 16px;
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

.resource-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  padding: 20px;
}

.resource-dialog {
  width: min(560px, 100%);
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.25);
  padding: 20px;
}

.resource-dialog-title {
  margin: 0;
  font-size: 1.2rem;
  color: #2e241e;
}

.resource-dialog-subtitle {
  margin: 8px 0 14px;
  color: #6b5f55;
}

.resource-list {
  display: grid;
  gap: 10px;
  max-height: 50vh;
  overflow: auto;
}

.resource-item {
  border: 1px solid #e9e2dc;
  background: #fffaf6;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-item:hover {
  border-color: #c45d2b;
  background: #fff1e8;
}

.resource-name {
  color: #2e241e;
  font-weight: 600;
}

.resource-meta {
  color: #8b7e74;
  font-size: 0.85rem;
}

.resource-cancel-btn {
  margin-top: 14px;
  width: 100%;
  border: 1px solid #dfd2c8;
  background: #ffffff;
  color: #5f5148;
  border-radius: 10px;
  height: 40px;
  cursor: pointer;
}

.resource-cancel-btn:hover {
  background: #f9f4ef;
}

/* Section Tabs */
.detail-section-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 2px solid #e5d8cc;
  padding-bottom: 0;
}

.detail-section-tab {
  background: none;
  border: none;
  padding: 8px 20px;
  font-size: 0.95rem;
  font-weight: 600;
  color: #8b7e74;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  border-radius: 4px 4px 0 0;
  transition: color 0.2s, border-color 0.2s;
}

.detail-section-tab:hover {
  color: #c45d2b;
}

.detail-section-tab.active {
  color: #c45d2b;
  border-bottom-color: #c45d2b;
  background: none;
}

.detail-comments-section {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e5d8cc;
  padding: 16px 20px;
}

.bangumi-collection-card,
.bangumi-bind-hint-card {
  background: #fffaf6;
  border: 1px solid #ead8cb;
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
  color: #2e241e;
  font-size: 1.02rem;
}

.bangumi-collection-header p,
.bangumi-bind-hint-card {
  margin: 0;
  color: #6b5f55;
  font-size: 0.92rem;
  line-height: 1.6;
}

.bangumi-collection-header a,
.bangumi-bind-hint-card a {
  color: #c45d2b;
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
  background: #fff;
  color: #6b5f55;
  border: 1px solid #dfd2c8;
}

.bangumi-refresh-btn {
  background: #f4ebe4;
  color: #5f5148;
}

.bangumi-save-btn {
  background: #c45d2b;
  color: #fff;
}

.bangumi-refresh-btn:disabled,
.bangumi-save-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.bangumi-collection-error {
  margin: 0 0 12px;
  color: #c23b22;
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
  color: #5f5148;
  font-size: 0.9rem;
  font-weight: 600;
}

.bangumi-collection-form select,
.bangumi-comment-field textarea {
  width: 100%;
  border: 1px solid #d9c8bb;
  border-radius: 12px;
  background: #fff;
  padding: 10px 12px;
  font-size: 0.92rem;
  color: #2e241e;
}

.bangumi-static-field {
  border: 1px solid #e2d5ca;
  border-radius: 12px;
  background: #f9f4ef;
  padding: 10px 12px;
  color: #5f5148;
  min-height: 42px;
  display: flex;
  align-items: center;
}

.bangumi-comment-display {
  width: 100%;
  border: 1px solid #e2d5ca;
  border-radius: 12px;
  background: #f9f4ef;
  padding: 10px 12px;
  font-size: 0.92rem;
  color: #5f5148;
  line-height: 1.65;
  min-height: 100px;
  white-space: pre-wrap;
  word-break: break-word;
}

.bangumi-collection-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8b7e74;
  font-size: 0.88rem;
  margin: 0 0 12px;
}

.bangumi-loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #e2d5ca;
  border-top-color: #c45d2b;
  border-radius: 50%;
  animation: bangumi-spin 0.9s linear infinite;
}

@keyframes bangumi-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.bangumi-stars-wrap {
  border: 1px solid #e2d5ca;
  border-radius: 12px;
  background: #f9f4ef;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.bangumi-stars-wrap.editable {
  background: #fff;
}

.bangumi-star-btn {
  border: none;
  background: transparent;
  color: #d7c8b8;
  font-size: 1rem;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

.bangumi-star-btn.active {
  color: #e8954a;
}

.bangumi-star-btn:disabled {
  cursor: default;
}

.bangumi-rate-value {
  margin-left: 8px;
  color: #6b5f55;
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
  border: 1px solid #dfd2c8;
  border-radius: 12px;
  background: #fff;
  color: #5f5148;
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
  color: #8b7e74;
  font-size: 1rem;
}

.bangumi-start-btn {
  border: 1.5px solid #c45d2b;
  background: none;
  color: #c45d2b;
  border-radius: 20px;
  padding: 7px 28px;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}

.bangumi-start-btn:hover {
  background: #c45d2b;
  color: #fff;
}

.bangumi-not-collected {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 28px 0 20px;
}

.bangumi-not-collected-label {
  color: #8b7e74;
  font-size: 1rem;
}

.bangumi-start-btn {
  border: 1.5px solid #c45d2b;
  background: none;
  color: #c45d2b;
  border-radius: 20px;
  padding: 7px 28px;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}

.bangumi-start-btn:hover {
  background: #c45d2b;
  color: #fff;
}

.bangumi-inline-hint {
  color: #8b7e74;
  font-size: 0.86rem;
}

.comments-source-hint {
  margin: 0 0 12px;
  font-size: 0.88rem;
  color: #8b7e74;
}

.comments-source-hint a {
  color: #c45d2b;
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

  .resource-dialog {
    padding: 16px;
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