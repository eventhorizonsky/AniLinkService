<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../utils/ui-feedback'
import { API_BASE } from '../../utils/constants'
import { formatDateTime, formatDanmakuColor as danmakuColorHex } from '../../utils/format'
import { danmakuModeLabel } from '../../utils/danmakuMode'

const loading = ref(false)
const records = ref([])
const filterUserId = ref('')
const filterEpisodeId = ref('')
const filterAnimeId = ref('')
const filterKeyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

const fetchRecords = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/admin/danmaku-records`, {
      params: {
        page: page.value,
        pageSize: pageSize.value,
        userId: filterUserId.value || undefined,
        episodeId: filterEpisodeId.value || undefined,
        animeId: filterAnimeId.value || undefined,
        keyword: filterKeyword.value?.trim() || undefined,
      }
    })
    if (res.data?.code === 200 && res.data?.data) {
      records.value = res.data.data.content || []
      totalElements.value = Number(res.data.data.totalElements || 0)
      totalPages.value = Number(res.data.data.totalPages || 0)
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取弹幕记录失败', 'error')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchRecords()
}

const handleReset = () => {
  filterUserId.value = ''
  filterEpisodeId.value = ''
  filterAnimeId.value = ''
  filterKeyword.value = ''
  page.value = 1
  fetchRecords()
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchRecords()
}

onMounted(() => {
  fetchRecords()
})
</script>

<template>
  <div>
    <v-card class="mb-6">
      <v-card-title class="d-flex align-center ga-2">
        <i class="mdi mdi-comment-text-multiple" style="color: #c45d2b;"></i>
        弹幕管理
      </v-card-title>
      <v-card-text>
        <v-row dense class="align-center">
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filterUserId"
              label="用户ID"
              variant="outlined"
              density="compact"
              hide-details
              type="number"
              clearable
            />
          </v-col>
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filterEpisodeId"
              label="弹幕库ID"
              variant="outlined"
              density="compact"
              hide-details
              type="number"
              clearable
            />
          </v-col>
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filterAnimeId"
              label="番剧ID"
              variant="outlined"
              density="compact"
              hide-details
              type="number"
              clearable
            />
          </v-col>
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filterKeyword"
              label="关键词搜索"
              variant="outlined"
              density="compact"
              hide-details
              placeholder="弹幕内容/用户名/番剧..."
              clearable
              @keyup.enter="handleSearch"
            />
          </v-col>
          <v-col cols="12" class="d-flex ga-2">
            <v-btn color="primary" variant="elevated" size="small" :loading="loading" @click="handleSearch">
              <v-icon start>mdi-magnify</v-icon>
              搜索
            </v-btn>
            <v-btn color="grey" variant="text" size="small" @click="handleReset">重置</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card>
      <v-card-text>
        <div v-if="loading" class="text-center pa-6">
          <v-progress-circular indeterminate color="primary" />
          <p class="mt-2">加载中...</p>
        </div>

        <div v-else-if="records.length > 0">
          <p class="text-caption text-grey mb-4">共 {{ totalElements }} 条记录</p>

          <div class="d-none d-lg-block">
            <v-table density="compact">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户</th>
                  <th>番剧</th>
                  <th>分集</th>
                  <th>弹幕内容</th>
                  <th>时间</th>
                  <th>模式</th>
                  <th>颜色</th>
                  <th>发送时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in records" :key="item.id">
                  <td>{{ item.id }}</td>
                  <td>{{ item.username }} <span class="text-caption text-grey">(#{{ item.userId }})</span></td>
                  <td>{{ item.animeTitle || `#${item.animeId || '-'}` }}</td>
                  <td>{{ item.episodeTitle || `#${item.episodeId || '-'}` }}</td>
                  <td>
                    <div class="d-flex align-center ga-2">
                      <div
                        class="danmaku-color-dot"
                        :style="{ backgroundColor: danmakuColorHex(item.color) }"
                      />
                      <span>{{ item.comment }}</span>
                    </div>
                  </td>
                  <td>{{ item.time != null ? item.time.toFixed(1) + 's' : '-' }}</td>
                  <td>
                    <v-chip size="x-small" variant="tonal"
                      :color="item.mode === 5 ? 'primary' : item.mode === 4 ? 'success' : 'grey'">
                      {{ danmakuModeLabel(item.mode) }}
                    </v-chip>
                  </td>
                  <td>
                    <code class="text-caption">{{ danmakuColorHex(item.color) }}</code>
                  </td>
                  <td class="text-caption">{{ formatDateTime(item.createdAt) }}</td>
                </tr>
              </tbody>
            </v-table>
          </div>

          <div class="d-lg-none">
            <v-card v-for="item in records" :key="item.id" class="mb-3" variant="outlined">
              <v-card-item>
                <template #prepend>
                  <v-avatar color="primary" variant="tonal" size="small">
                    <v-icon size="small">mdi-comment-text</v-icon>
                  </v-avatar>
                </template>
                <v-card-title class="text-body-1 text-wrap">
                  #{{ item.id }} · {{ item.username || '匿名' }}
                  <span class="text-caption text-grey">(#{{ item.userId }})</span>
                </v-card-title>
                <v-card-subtitle class="text-body-2 text-wrap">
                  {{ item.animeTitle || `#${item.animeId || '-'}` }} ·
                  {{ item.episodeTitle || `#${item.episodeId || '-'}` }}
                </v-card-subtitle>
              </v-card-item>

              <v-card-text class="pt-2">
                <div class="d-flex align-center ga-2 mb-3">
                  <div
                    class="danmaku-color-dot"
                    :style="{ backgroundColor: danmakuColorHex(item.color) }"
                  />
                  <span>{{ item.comment }}</span>
                </div>
                <div class="d-flex flex-wrap ga-2 align-center text-caption text-medium-emphasis">
                  <v-chip size="x-small" variant="tonal"
                    :color="item.mode === 5 ? 'primary' : item.mode === 4 ? 'success' : 'grey'">
                    {{ danmakuModeLabel(item.mode) }}
                  </v-chip>
                  <code class="text-caption">{{ danmakuColorHex(item.color) }}</code>
                  <span>{{ item.time != null ? item.time.toFixed(1) + 's' : '-' }}</span>
                  <span class="ml-auto">{{ formatDateTime(item.createdAt) }}</span>
                </div>
              </v-card-text>
            </v-card>
          </div>

          <div v-if="totalPages > 1" class="d-flex justify-center mt-4">
            <v-pagination v-model="page" :length="totalPages" @update:model-value="handlePageChange" />
          </div>
        </div>

        <v-alert v-else type="info" variant="tonal" class="text-center">暂无弹幕记录</v-alert>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.danmaku-color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.15);
  flex-shrink: 0;
}
</style>
