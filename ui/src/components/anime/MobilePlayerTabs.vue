<template>
  <div class="mpt">
    <!-- 头部：封面 + 标题 + 评分 -->
    <div class="mpt-head">
      <img
        class="mpt-poster"
        :src="anime.imageUrl || defaultPoster"
        :alt="titleInfo.main"
        loading="lazy"
      />
      <div class="mpt-head-meta">
        <div class="mpt-title">{{ titleInfo.main }}</div>
        <div class="mpt-sub" v-if="titleInfo.sub">{{ titleInfo.sub }}</div>
        <div class="mpt-head-row">
          <span class="mpt-score" v-if="ratingMain">
            <i class="mdi mdi-star"></i> {{ ratingMain }}
          </span>
          <span class="mpt-count" v-if="totalEpisodes">
            <i class="mdi mdi-play-box-multiple-outline"></i> 共{{ totalEpisodes }}话
          </span>
        </div>
      </div>
      <router-link
        class="mpt-detail"
        :to="{ name: 'AnimeDetail', params: { animeId } }"
        aria-label="查看番剧详情"
      >
        <i class="mdi mdi-arrow-right-circle-outline"></i>
      </router-link>
    </div>

    <!-- Tab 栏 -->
    <div class="mpt-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="mpt-tab"
        :class="{ active: activeTab === tab.value }"
        @click="activeTab = tab.value"
      >{{ tab.label }}</button>
    </div>

    <!-- 内容区 -->
    <div class="mpt-body">
      <!-- 简介 -->
      <div v-if="activeTab === 'info'" class="mpt-pane mpt-info">
        <p v-if="summary" class="mpt-summary" v-html="summary"></p>

        <dl class="mpt-meta-list">
          <template v-if="totalEpisodes">
            <dt>话数</dt><dd>{{ totalEpisodes }}</dd>
          </template>
          <template v-if="airDayText">
            <dt>放送</dt><dd>{{ airDayText }}</dd>
          </template>
          <template v-if="ratingMain">
            <dt>评分</dt><dd>{{ ratingMain }}</dd>
          </template>
          <template v-if="typeof isOnAir === 'boolean'">
            <dt>状态</dt><dd>{{ isOnAir ? '连载中' : '已完结' }}</dd>
          </template>
        </dl>

        <div v-if="staffList.length" class="mpt-staff">
          <div
            v-for="(s, i) in staffList"
            :key="i"
            class="mpt-staff-item"
            v-html="s"
          ></div>
        </div>

        <div v-if="copyrightText" class="mpt-copyright" v-html="copyrightText"></div>
      </div>

      <!-- 选集 -->
      <div v-else-if="activeTab === 'episodes'" class="mpt-pane mpt-eps">
        <div v-if="mainEpisodes.length" class="mpt-eps-group">
          <div class="mpt-eps-group-title">正片</div>
          <button
            v-for="ep in mainEpisodes"
            :key="ep.episodeId"
            class="mpt-ep-item"
            :class="{
              'is-current': String(ep.episodeId) === String(currentEpisodeId),
              'is-unavailable': !canPlayEpisode(ep)
            }"
            :disabled="!canPlayEpisode(ep)"
            @click="playEpisode(ep)"
          >
            <span class="mpt-ep-num">{{ episodeNumberDisplay(ep) }}</span>
            <span class="mpt-ep-title">{{ ep.episodeTitle || '' }}</span>
            <span class="mpt-ep-date">{{ formatEpisodeDate(ep.airDate) }}</span>
          </button>
        </div>

        <div v-if="specialEpisodes.length" class="mpt-eps-group">
          <div class="mpt-eps-group-title">特典 / 特别篇</div>
          <button
            v-for="ep in specialEpisodes"
            :key="ep.episodeId"
            class="mpt-ep-item"
            :class="{
              'is-current': String(ep.episodeId) === String(currentEpisodeId),
              'is-unavailable': !canPlayEpisode(ep)
            }"
            :disabled="!canPlayEpisode(ep)"
            @click="playEpisode(ep)"
          >
            <span class="mpt-ep-num">{{ episodeNumberDisplay(ep) }}</span>
            <span class="mpt-ep-title">{{ ep.episodeTitle || '' }}</span>
            <span class="mpt-ep-date">{{ formatEpisodeDate(ep.airDate) }}</span>
          </button>
        </div>

        <div v-if="!mainEpisodes.length && !specialEpisodes.length" class="mpt-empty">
          暂无剧集
        </div>
      </div>

      <!-- 吐槽 -->
      <div v-else class="mpt-pane mpt-comments">
        <EpisodeComments :anime-id="animeId" :episode-number="currentEpisodeNumber" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EpisodeComments from './EpisodeComments.vue'

defineProps({
  anime: { type: Object, required: true },
  animeId: { type: [String, Number], required: true },
  titleInfo: { type: Object, default: () => ({ main: '', sub: '' }) },
  ratingMain: { type: [String, Number], default: '' },
  totalEpisodes: { type: [String, Number], default: '' },
  isOnAir: { type: Boolean, default: null },
  airDayText: { type: String, default: '' },
  summary: { type: String, default: '' },
  staffList: { type: Array, default: () => [] },
  copyrightText: { type: String, default: '' },
  currentEpisodeId: { type: [String, Number], default: '' },
  currentEpisodeNumber: { type: [String, Number], default: '' },
  mainEpisodes: { type: Array, default: () => [] },
  specialEpisodes: { type: Array, default: () => [] },
  playEpisode: { type: Function, required: true },
  canPlayEpisode: { type: Function, required: true },
  episodeNumberDisplay: { type: Function, default: (ep) => String(ep?.episodeNumber ?? '') },
  formatEpisodeDate: { type: Function, default: () => '' },
})

const defaultPoster = 'https://assets.anixplayer.net/image/poster/default.jpg'

const tabs = [
  { label: '简介', value: 'info' },
  { label: '选集', value: 'episodes' },
  { label: '吐槽', value: 'comments' }
]
const activeTab = ref('info')
</script>

<style scoped>
.mpt {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fdfbf9;
  border: 1px solid #e7ddd3;
  border-radius: 16px;
  overflow: hidden;
}

/* ===== 头部 ===== */
.mpt-head {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #efe7de;
  background: #fff;
}

.mpt-poster {
  width: 54px;
  height: 76px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  background: #e8e2da;
}

.mpt-head-meta {
  flex: 1;
  min-width: 0;
}

.mpt-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: #2e241e;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mpt-sub {
  font-size: 0.78rem;
  color: #8b7e74;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mpt-head-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  font-size: 0.75rem;
  color: #a39386;
}

.mpt-score {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #c45d2b;
  font-weight: 600;
}

.mpt-count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.mpt-detail {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e7ddd3;
  border-radius: 50%;
  color: #a39386;
  font-size: 18px;
  text-decoration: none;
  transition: color 0.15s, border-color 0.15s;
}

.mpt-detail:active {
  color: #c45d2b;
  border-color: #c45d2b;
}

/* ===== Tab 栏 ===== */
.mpt-tabs {
  display: flex;
  padding: 0 12px;
  border-bottom: 1px solid #efe7de;
  background: #fff;
}

.mpt-tab {
  flex: 1;
  border: none;
  background: none;
  padding: 12px 0;
  font-size: 0.88rem;
  color: #8b7e74;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.mpt-tab.active {
  color: #c45d2b;
  border-bottom-color: #c45d2b;
  font-weight: 600;
}

/* ===== 内容区 ===== */
.mpt-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.mpt-pane {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

/* 简介 */
.mpt-summary {
  margin: 0 0 12px;
  font-size: 0.85rem;
  color: #4a4039;
  line-height: 1.7;
  word-break: break-word;
}

.mpt-meta-list {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 14px;
  margin: 0 0 12px;
}

.mpt-meta-list dt {
  color: #b0a59a;
  font-size: 0.78rem;
}

.mpt-meta-list dd {
  margin: 0;
  color: #5f5148;
  font-size: 0.8rem;
}

.mpt-staff {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mpt-staff-item {
  font-size: 0.78rem;
  color: #7a6c60;
  line-height: 1.6;
}

.mpt-staff-item :deep(strong) {
  color: #5f5148;
  margin-right: 6px;
}

.mpt-copyright {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed #e7ddd3;
  font-size: 0.72rem;
  color: #b0a59a;
}

/* 选集 */
.mpt-eps {
  padding: 8px 10px;
}

.mpt-eps-group + .mpt-eps-group {
  margin-top: 8px;
}

.mpt-eps-group-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: #b0a59a;
  padding: 6px 10px 4px;
}

.mpt-ep-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: none;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  font-size: 0.82rem;
  color: #5f5148;
  transition: background 0.1s;
}

.mpt-ep-item:active {
  background: rgba(196, 93, 43, 0.08);
}

.mpt-ep-item.is-current {
  background: #fff7f0;
  color: #c45d2b;
  font-weight: 600;
}

.mpt-ep-item.is-unavailable {
  opacity: 0.4;
  cursor: default;
}

.mpt-ep-num {
  flex-shrink: 0;
  min-width: 4.2em;
  font-variant-numeric: tabular-nums;
}

.mpt-ep-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mpt-ep-date {
  flex-shrink: 0;
  font-size: 0.72rem;
  color: #a39386;
  font-variant-numeric: tabular-nums;
}

.mpt-ep-item.is-current .mpt-ep-date {
  color: #c45d2b;
}

/* 吐槽 */
.mpt-comments {
  padding: 4px 8px;
}

.mpt-empty {
  text-align: center;
  color: #b0a59a;
  padding: 32px 0;
  font-size: 0.82rem;
}
</style>
