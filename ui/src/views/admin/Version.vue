<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { API_BASE } from '../../utils/constants'

const loading = ref(false)
const versionInfo = ref(null)
const expanded = ref([])

const fetchVersionInfo = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/system/version`)
    if (res.data?.data) {
      versionInfo.value = res.data.data
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
      <p class="mt-4 text-body-1">加载中...</p>
    </v-card>

    <template v-else-if="versionInfo">
      <v-card class="mb-4">
        <v-card-title>
          <v-icon start>mdi-update</v-icon>
          当前版本
        </v-card-title>
        <v-card-text>
          <div class="d-flex align-center flex-wrap ga-3">
            <span class="text-h5 font-weight-bold">{{ versionInfo.currentVersion }}</span>
            <v-chip color="primary" variant="flat" size="small">
              <v-icon start size="16">mdi-check-circle</v-icon>
              当前版本
            </v-chip>
            <v-btn
              v-if="releasesUrl()"
              variant="outlined"
              size="small"
              color="primary"
              :href="releasesUrl()"
              target="_blank"
              rel="noopener"
            >
              <v-icon start size="18">mdi-open-in-new</v-icon>
              查看全部发布
            </v-btn>
          </div>
        </v-card-text>
      </v-card>

      <v-card>
        <v-card-title>
          <v-icon start>mdi-history</v-icon>
          版本历史
        </v-card-title>
        <v-card-text>
          <v-expansion-panels
            v-if="versionInfo.releases && versionInfo.releases.length > 0"
            v-model="expanded"
            accordion
            flat
          >
            <v-expansion-panel
              v-for="(release, index) in versionInfo.releases"
              :key="release.tagName"
              :value="index"
              :class="{ 'version-current': release.current }"
            >
              <v-expansion-panel-title>
                <div class="d-flex flex-column w-100 py-1">
                  <div class="d-flex align-center flex-wrap ga-2">
                    <v-icon size="20" :color="release.current ? 'primary' : ''">
                      {{ release.current ? 'mdi-check-decagram' : 'mdi-tag-outline' }}
                    </v-icon>
                    <span class="font-weight-medium">{{ release.tagName }}</span>
                    <v-chip v-if="release.current" color="primary" size="x-small">
                      当前版本
                    </v-chip>
                    <span class="text-body-2 text-medium-emphasis">{{ formatDate(release.publishedAt) }}</span>
                    <a
                      v-if="release.htmlUrl"
                      :href="release.htmlUrl"
                      target="_blank"
                      rel="noopener"
                      class="text-primary text-decoration-none text-body-2"
                    >
                      发布说明
                    </a>
                  </div>
                  <div
                    v-show="expanded !== index"
                    class="release-preview text-body-2 mt-2"
                    v-html="renderMarkdown(release.body)"
                  ></div>
                </div>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <div class="markdown-body text-body-2" v-html="renderMarkdown(release.body)"></div>
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
.version-current {
  background-color: rgba(var(--v-theme-primary), 0.06);
  border-left: 3px solid rgb(var(--v-theme-primary));
}

.release-preview {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: rgba(0, 0, 0, 0.7);
}

.markdown-body,
.release-preview {
  word-break: break-word;
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
  background-color: rgba(127, 127, 127, 0.12);
  border-radius: 4px;
  padding: 0.1em 0.35em;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 0.9em;
}
.markdown-body :deep(pre) {
  background-color: rgba(127, 127, 127, 0.1);
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
  border-left: 3px solid rgba(127, 127, 127, 0.4);
  margin: 0.5em 0;
  padding: 0.1em 0.75em;
  color: rgba(0, 0, 0, 0.6);
}
.markdown-body :deep(a) {
  color: rgb(var(--v-theme-primary));
}
</style>
