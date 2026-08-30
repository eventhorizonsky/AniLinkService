<script setup>
import { computed, ref, watch } from 'vue'
import { showAppMessage } from '../../../utils/ui-feedback'
import { updateMediaFile, getRematchCandidates } from '../../../api/media'
import { searchEpisodes } from '../../../api/anime'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  mediaFile: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'applied'])

const innerVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const autoLoading = ref(false)
const autoError = ref('')
const autoCandidates = ref([])

const manualForm = ref({
  anime: '',
  episode: ''
})
const candidateLoading = ref(false)
const searchedCandidates = ref([])
const applyingKey = ref('')

const displayCandidates = computed(() => {
  const merged = []
  const seen = new Set()

  autoCandidates.value.forEach((item) => {
    if (!seen.has(item.key)) {
      seen.add(item.key)
      merged.push({ ...item, source: 'auto' })
    }
  })
  searchedCandidates.value.forEach((item) => {
    if (!seen.has(item.key)) {
      seen.add(item.key)
      merged.push({ ...item, source: 'search' })
    }
  })

  return merged
})

const normalizeCandidate = (candidate, fallbackAnime = {}) => {
  if (!candidate || !candidate.episodeId) {
    return null
  }
  const animeId = candidate.animeId ?? fallbackAnime.animeId ?? null
  const animeTitle = candidate.animeTitle || candidate.title || fallbackAnime.animeTitle || '未知番剧'
  const episodeTitle = candidate.episodeTitle || candidate.title || '未知剧集'
  const key = `${animeId || ''}-${candidate.episodeId}`

  return {
    key,
    animeId,
    animeTitle,
    episodeId: candidate.episodeId,
    episodeTitle
  }
}

const collectEpisodeCandidates = (rawData) => {
  const list = []
  const seen = new Set()

  const pushIfValid = (item, fallbackAnime) => {
    const normalized = normalizeCandidate(item, fallbackAnime)
    if (!normalized || seen.has(normalized.key)) {
      return
    }
    seen.add(normalized.key)
    list.push(normalized)
  }

  const walk = (node, fallbackAnime = {}) => {
    if (!node) return
    if (Array.isArray(node)) {
      node.forEach((item) => walk(item, fallbackAnime))
      return
    }
    if (typeof node !== 'object') {
      return
    }

    const localAnime = {
      animeId: node.animeId ?? fallbackAnime.animeId ?? null,
      animeTitle: node.animeTitle || node.title || fallbackAnime.animeTitle || ''
    }

    pushIfValid(node, localAnime)

    if (Array.isArray(node.episodes)) {
      node.episodes.forEach((episode) => pushIfValid(episode, localAnime))
    }

    Object.values(node).forEach((value) => walk(value, localAnime))
  }

  walk(rawData)
  return list
}

const applyCandidate = async (candidate) => {
  if (!props.mediaFile?.id || !candidate) return

  applyingKey.value = candidate.key
  try {
    const payload = {
      animeId: candidate.animeId,
      animeTitle: candidate.animeTitle,
      episodeId: candidate.episodeId,
      episodeTitle: candidate.episodeTitle
    }
    const res = await updateMediaFile(props.mediaFile.id, payload)
    if (res?.code === 200) {
      showAppMessage('已应用匹配结果', 'success')
      emit('applied', res?.data || payload)
      innerVisible.value = false
    } else {
      showAppMessage(res?.msg || '应用匹配失败', 'error')
    }
  } catch (error) {
    showAppMessage('应用匹配失败: ' + (error.response?.data?.msg || error.message), 'error')
  } finally {
    applyingKey.value = ''
  }
}

const runAutoMatch = async () => {
  if (!props.mediaFile?.id) return

  autoLoading.value = true
  autoError.value = ''
  autoCandidates.value = []
  try {
    const res = await getRematchCandidates(props.mediaFile.id)
    if (res?.code === 200) {
      autoCandidates.value = collectEpisodeCandidates(res.data)
      if (autoCandidates.value.length === 0) {
        autoError.value = '自动匹配未找到候选结果，可继续手动搜索。'
      }
    } else {
      autoError.value = res?.msg || '自动匹配失败'
    }
  } catch (error) {
    autoError.value = error.response?.data?.msg || error.message || '自动匹配失败'
  } finally {
    autoLoading.value = false
  }
}

const searchCandidates = async () => {
  if (!manualForm.value.anime?.trim()) {
    showAppMessage('请先输入动漫标题', 'warning')
    return
  }

  candidateLoading.value = true
  searchedCandidates.value = []
  try {
    const res = await searchEpisodes({
      anime: manualForm.value.anime.trim(),
      episode: manualForm.value.episode?.trim() || undefined
    })
    if (res?.code === 200) {
      searchedCandidates.value = collectEpisodeCandidates(res.data)
      if (searchedCandidates.value.length === 0) {
        showAppMessage('没有找到候选结果，请尝试更具体的标题', 'warning')
      }
    } else {
      showAppMessage(res?.msg || '搜索失败', 'error')
    }
  } catch (error) {
    showAppMessage('搜索失败: ' + (error.response?.data?.msg || error.message), 'error')
  } finally {
    candidateLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (opened) => {
    if (!opened) {
      return
    }
    manualForm.value = {
      anime: props.mediaFile?.animeTitle || '',
      episode: props.mediaFile?.episodeTitle || ''
    }
    autoCandidates.value = []
    searchedCandidates.value = []
    runAutoMatch()
  }
)
</script>

<template>
  <v-dialog v-model="innerVisible" max-width="980">
    <v-card>
      <v-toolbar color="primary" flat>
        <v-toolbar-title class="text-white">重新匹配视频</v-toolbar-title>
        <v-spacer />
        <v-btn icon="mdi-close" color="white" @click="innerVisible = false" />
      </v-toolbar>

      <v-card-text class="pa-6">
        <div class="text-body-2 mb-4">
          当前文件: <span class="font-weight-medium">{{ mediaFile?.fileName || '-' }}</span>
        </div>

        <v-card variant="tonal" color="primary" class="mb-4">
          <v-card-title class="text-subtitle-1">自动匹配候选</v-card-title>
          <v-card-text>
            <div v-if="autoLoading" class="d-flex align-center ga-2">
              <v-progress-circular indeterminate size="18" width="2" color="primary" />
              <span class="text-body-2">正在调用 /api/v2/match 加载候选...</span>
            </div>

            <v-alert
              v-else-if="autoError"
              type="warning"
              variant="tonal"
              density="compact"
            >
              {{ autoError }}
            </v-alert>
            <v-alert
              v-else
              type="info"
              variant="tonal"
              density="compact"
            >
              自动候选已加载，请在下方候选列表中直接选择应用。
            </v-alert>
          </v-card-text>
        </v-card>

        <v-card variant="outlined">
          <v-card-title class="text-subtitle-1">候选列表（自动匹配 + 手动搜索）</v-card-title>
          <v-card-text>
            <v-row dense>
              <v-col cols="12" md="6">
                <v-text-field
                  v-model="manualForm.anime"
                  label="动漫标题"
                  variant="outlined"
                  density="compact"
                  clearable
                />
              </v-col>
              <v-col cols="12" md="4">
                <v-text-field
                  v-model="manualForm.episode"
                  label="剧集关键词（可选）"
                  variant="outlined"
                  density="compact"
                  clearable
                  @keyup.enter="searchCandidates"
                />
              </v-col>
              <v-col cols="12" md="2" class="d-flex align-center">
                <v-btn
                  color="primary"
                  variant="elevated"
                  :loading="candidateLoading"
                  @click="searchCandidates"
                >
                  <v-icon start>mdi-magnify</v-icon>
                  搜索
                </v-btn>
              </v-col>
            </v-row>

            <v-list v-if="displayCandidates.length > 0" border rounded density="compact" class="mt-2">
              <v-list-item v-for="candidate in displayCandidates" :key="`${candidate.source}-${candidate.key}`">
                <v-list-item-title>{{ candidate.animeTitle }}</v-list-item-title>
                <v-list-item-subtitle>
                  {{ candidate.episodeTitle }} | animeId={{ candidate.animeId || '-' }} | episodeId={{ candidate.episodeId }}
                </v-list-item-subtitle>
                <template #append>
                  <v-chip size="x-small" variant="outlined" class="mr-2">
                    {{ candidate.source === 'auto' ? '自动候选' : '搜索候选' }}
                  </v-chip>
                  <v-btn
                    color="success"
                    variant="text"
                    size="small"
                    :loading="applyingKey === candidate.key"
                    @click="applyCandidate(candidate)"
                  >
                    <v-icon start>mdi-check-circle-outline</v-icon>
                    应用
                  </v-btn>
                </template>
              </v-list-item>
            </v-list>

            <v-alert
              v-else-if="!autoLoading && !candidateLoading"
              type="info"
              variant="tonal"
              density="compact"
              class="mt-2"
            >
              暂无候选，请修改关键词后搜索。
            </v-alert>
          </v-card-text>
        </v-card>
      </v-card-text>

      <v-divider />
      <v-card-actions class="pa-4">
        <v-spacer />
        <v-btn variant="text" @click="innerVisible = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
