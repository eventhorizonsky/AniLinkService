<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const loading = ref(false)
const versionInfo = ref(null)

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

const formatDate = (iso) => {
  if (!iso) return '-'
  const date = new Date(iso)
  if (isNaN(date.getTime())) return iso
  return date.toLocaleString('zh-CN', { hour12: false })
}

const plainText = (body) => {
  if (!body) return ''
  return body
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#*>`_~]/g, ' ')
    .replace(/^\s*[-+]\s+/gm, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const previewText = (body) => {
  const text = plainText(body)
  return text.length > 260 ? text.slice(0, 260) + '...' : text
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
          <v-list v-if="versionInfo.releases && versionInfo.releases.length > 0" lines="three">
            <v-list-item
              v-for="release in versionInfo.releases"
              :key="release.tagName"
              :class="{ 'version-current': release.current }"
            >
              <template v-slot:prepend>
                <v-icon :color="release.current ? 'primary' : ''">
                  {{ release.current ? 'mdi-check-decagram' : 'mdi-tag-outline' }}
                </v-icon>
              </template>
              <v-list-item-title>
                <span class="font-weight-medium">{{ release.tagName }}</span>
                <v-chip
                  v-if="release.current"
                  color="primary"
                  size="x-small"
                  class="ml-2"
                >
                  当前版本
                </v-chip>
              </v-list-item-title>
              <v-list-item-subtitle>
                <span class="text-body-2">{{ formatDate(release.publishedAt) }}</span>
                <span class="mx-1">·</span>
                <a
                  v-if="release.htmlUrl"
                  :href="release.htmlUrl"
                  target="_blank"
                  rel="noopener"
                  class="text-primary text-decoration-none text-body-2"
                >
                  发布说明
                </a>
              </v-list-item-subtitle>
              <v-list-item-subtitle class="version-body mt-1">
                {{ previewText(release.body) }}
              </v-list-item-subtitle>
            </v-list-item>
          </v-list>
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
.version-body {
  white-space: normal;
  color: rgba(0, 0, 0, 0.7);
}
</style>
