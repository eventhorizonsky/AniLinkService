<script setup>
import { ref, computed, onMounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getSystemVersion } from '../../api/system'

const loading = ref(false)
const versionInfo = ref(null)
// accordion 模式下 v-expansion-panels 的 v-model 是单个值，不能用数组初始化
const expanded = ref(null)

const latestRelease = computed(() => versionInfo.value?.releases?.[0] || null)
const updateAvailable = computed(() => !!latestRelease.value && !latestRelease.value.current)
const releaseCount = computed(() => versionInfo.value?.releases?.length || 0)

const fetchVersionInfo = async () => {
  loading.value = true
  try {
    const res = await getSystemVersion()
    if (res?.data) {
      versionInfo.value = res.data
    }
  } catch (error) {
    console.error('获取版本信息失败:', error)
  } finally {
    loading.value = false
  }
}

const renderMarkdown = (body) => {
  if (!body) return ''
  const raw = marked.parse(body, { gfm: true, breaks: true })
  return DOMPurify.sanitize(raw)
}

const formatDate = (iso) => {
  if (!iso) return '-'
  const date = new Date(iso)
  if (isNaN(date.getTime())) return iso
  return date.toLocaleString('zh-CN', { hour12: false })
}

const releaseTitle = (release) =>
  release.name && release.name.trim() && release.name !== release.tagName ? release.name : release.tagName

const releasesUrl = () => {
  const repo = versionInfo.value?.repo || 'eventhorizonsky/AniLinkService'
  return `https://github.com/${repo}/releases`
}

onMounted(() => {
  fetchVersionInfo()
})
</script>

<template>
  <div>
    <v-card v-if="loading" class="text-center pa-8">
      <v-progress-circular indeterminate color="primary" size="48" />
      <p class="mt-4 text-body-1 text-medium-emphasis">加载中...</p>
    </v-card>

    <template v-else-if="versionInfo">
      <v-card class="mb-4 version-hero" rounded="xl">
        <div class="d-flex flex-wrap align-center justify-space-between ga-4 pa-6">
          <div class="d-flex align-center ga-4">
            <div class="version-hero-icon">
              <v-icon size="28" color="primary">mdi-rocket-launch-outline</v-icon>
            </div>
            <div>
              <div class="text-overline text-medium-emphasis">当前版本</div>
              <div class="d-flex align-center ga-3 flex-wrap">
                <span class="text-h4 font-weight-bold">{{ versionInfo.currentVersion }}</span>
                <v-chip
                  :color="updateAvailable ? 'warning' : 'success'"
                  variant="flat"
                  size="small"
                >
                  <v-icon start size="14">{{ updateAvailable ? 'mdi-update' : 'mdi-check-circle' }}</v-icon>
                  {{ updateAvailable ? '有可用更新' : '已是最新版本' }}
                </v-chip>
              </div>
              <div v-if="updateAvailable" class="mt-1 text-body-2 text-medium-emphasis">
                最新发布
                <span class="font-weight-medium">{{ latestRelease.tagName }}</span>
                · {{ formatDate(latestRelease.publishedAt) }}
              </div>
            </div>
          </div>
          <div class="d-flex flex-wrap ga-2">
            <v-btn
              v-if="updateAvailable && latestRelease?.htmlUrl"
              :href="latestRelease.htmlUrl"
              target="_blank"
              rel="noopener"
              color="primary"
              size="small"
            >
              <v-icon start size="16">mdi-open-in-new</v-icon>
              查看新版本
            </v-btn>
            <v-btn
              v-if="releasesUrl()"
              :href="releasesUrl()"
              target="_blank"
              rel="noopener"
              variant="outlined"
              color="primary"
              size="small"
            >
              <v-icon start size="16">mdi-github</v-icon>
              全部发布
            </v-btn>
          </div>
        </div>
      </v-card>

      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon start color="primary">mdi-history</v-icon>
          版本历史
          <v-chip v-if="releaseCount" color="primary" variant="tonal" size="small" class="ml-2">
            {{ releaseCount }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-expansion-panels
            v-if="releaseCount > 0"
            v-model="expanded"
            accordion
            flat
            class="version-panels"
          >
            <v-expansion-panel
              v-for="(release, index) in versionInfo.releases"
              :key="release.tagName"
              :value="index"
              :class="{ 'version-current': release.current }"
              rounded="lg"
            >
              <v-expansion-panel-title density="comfortable">
                <div class="d-flex flex-column w-100 py-1">
                  <div class="d-flex flex-wrap align-center ga-2">
                    <v-icon size="18" :color="release.current ? 'primary' : ''">
                      {{ release.current ? 'mdi-check-decagram' : 'mdi-tag-outline' }}
                    </v-icon>
                    <span class="font-weight-medium text-body-1">{{ releaseTitle(release) }}</span>
                    <v-chip v-if="release.tagName !== releaseTitle(release)" size="x-small" variant="tonal">
                      {{ release.tagName }}
                    </v-chip>
                    <v-chip v-if="release.current" color="primary" size="x-small">
                      当前版本
                    </v-chip>
                    <v-chip v-if="index === 0 && !release.current" color="secondary" size="x-small" variant="tonal">
                      最新
                    </v-chip>
                    <span class="ms-auto text-body-2 text-medium-emphasis">
                      {{ formatDate(release.publishedAt) }}
                    </span>
                  </div>
                  <div
                    v-if="release.body"
                    v-show="expanded !== index"
                    class="release-preview text-body-2 mt-2"
                    v-html="renderMarkdown(release.body)"
                  ></div>
                </div>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <div class="markdown-body text-body-2" v-html="renderMarkdown(release.body)"></div>
                <div v-if="release.htmlUrl" class="mt-4">
                  <a
                    :href="release.htmlUrl"
                    target="_blank"
                    rel="noopener"
                    class="release-ext-link"
                  >
                    <v-icon size="14" start>mdi-open-in-new</v-icon>
                    在 GitHub 查看完整发布说明
                  </a>
                </div>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>
          <v-alert
            v-else
            type="info"
            variant="tonal"
            text="暂无版本发布信息（GitHub Releases 为空或拉取失败）"
          />
        </v-card-text>
      </v-card>
    </template>

    <v-card v-else class="pa-8">
      <v-alert type="error" variant="tonal" text="获取版本信息失败" />
    </v-card>
  </div>
</template>

<style scoped>
.version-hero {
  background: linear-gradient(135deg, rgba(var(--v-theme-primary), 0.14), rgba(var(--v-theme-primary), 0.03));
  border: 1px solid rgba(var(--v-theme-primary), 0.25);
}

.version-hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--v-theme-primary), 0.14);
  flex-shrink: 0;
}

.version-panels {
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  border-radius: 12px;
  overflow: hidden;
}

.version-current {
  background-color: rgba(var(--v-theme-primary), 0.06);
  border-left: 3px solid rgb(var(--v-theme-primary));
}

.release-preview {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: rgba(var(--v-theme-on-surface), var(--v-medium-emphasis-opacity));
  -webkit-mask-image: linear-gradient(to bottom, black 0%, black 70%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 0%, black 70%, transparent 100%);
}

.markdown-body,
.release-preview {
  word-break: break-word;
  line-height: 1.7;
}

.markdown-body {
  color: rgb(var(--v-theme-on-surface));
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 0.75em 0 0.5em;
  line-height: 1.4;
}
.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child) {
  margin-top: 0;
}
.markdown-body :deep(p),
.release-preview :deep(p) {
  margin: 0.35em 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.35em 0;
  padding-left: 1.4em;
}
.markdown-body :deep(li) {
  margin: 0.2em 0;
}
.markdown-body :deep(code) {
  background-color: rgba(var(--v-theme-on-surface), 0.08);
  border-radius: 4px;
  padding: 0.1em 0.35em;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 0.9em;
}
.markdown-body :deep(pre) {
  background-color: rgba(var(--v-theme-on-surface), 0.06);
  border-radius: 6px;
  padding: 0.75em;
  overflow-x: auto;
  margin: 0.5em 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid rgb(var(--v-theme-primary));
  margin: 0.5em 0;
  padding: 0.1em 0.75em;
  color: rgba(var(--v-theme-on-surface), var(--v-medium-emphasis-opacity));
}
.markdown-body :deep(hr) {
  border-color: rgba(var(--v-theme-on-surface), 0.12);
  margin: 0.75em 0;
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

/* 折叠预览与展开正文中的链接统一为主题色，避免默认蓝色下划线 */
.release-preview :deep(a),
.markdown-body :deep(a) {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  border-bottom: 1px solid rgba(var(--v-theme-primary), 0.4);
}
.release-preview :deep(a:hover),
.markdown-body :deep(a:hover) {
  text-decoration: none;
  border-bottom-color: rgb(var(--v-theme-primary));
}

.release-ext-link {
  display: inline-flex;
  align-items: center;
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  font-size: 0.8125rem;
  border-bottom: 1px solid rgba(var(--v-theme-primary), 0.4);
}
.release-ext-link:hover {
  border-bottom-color: rgb(var(--v-theme-primary));
}
</style>
