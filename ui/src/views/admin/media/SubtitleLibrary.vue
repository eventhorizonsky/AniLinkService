<script setup>
import { ref, onMounted } from 'vue'
import { askAppConfirm, showAppMessage } from '../../../utils/ui-feedback'
import { formatFileSize } from '../../../utils/format'
import { getSubtitleList, setSubtitleOffset, deleteSubtitle, getSubtitleDownloadUrl } from '../../../api/subtitle'

const subtitles = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const sourceType = ref(null)
const pagination = ref({
  page: 1,
  itemsPerPage: 10,
  totalItems: 0
})

const offsetDialog = ref(false)
const selectedSubtitle = ref(null)
const offsetValue = ref(0)

const headers = [
  { title: '字幕文件', key: 'fileName', width: '22%' },
  { title: '视频文件', key: 'videoFileName', width: '22%' },
  { title: '动漫', key: 'animeTitle', width: '18%' },
  { title: '剧集', key: 'episodeTitle', width: '14%' },
  { title: '来源', key: 'sourceType', width: '10%' },
  { title: '偏移', key: 'timeOffset', width: '8%' },
  { title: '操作', key: 'actions', width: '16%', sortable: false }
]

const fetchSubtitles = async (pageNum = pagination.value.page) => {
  loading.value = true
  try {
    const params = {
      page: pageNum - 1,
      pageSize: pagination.value.itemsPerPage
    }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    if (sourceType.value) {
      params.sourceType = sourceType.value
    }

    const res = await getSubtitleList(params)
    if (res?.code === 200 && res?.data) {
      subtitles.value = res.data.content || []
      pagination.value.page = (res.data.currentPage || 0) + 1
      pagination.value.totalItems = res.data.totalElements || 0
    }
  } catch (error) {
    showAppMessage('获取字幕列表失败: ' + (error.response?.data?.msg || error.message), 'error')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  fetchSubtitles(1)
}

const resetFilters = () => {
  searchKeyword.value = ''
  sourceType.value = null
  pagination.value.page = 1
  fetchSubtitles(1)
}

const onTableOptionsChange = (options) => {
  const page = options.page || 1
  const pageSize = options.itemsPerPage || 10
  if (pageSize !== pagination.value.itemsPerPage) {
    pagination.value.itemsPerPage = pageSize
    pagination.value.page = 1
    fetchSubtitles(1)
    return
  }
  pagination.value.page = page
  fetchSubtitles(page)
}

const handleDownload = (subtitle) => {
  window.open(getSubtitleDownloadUrl(subtitle.id), '_blank')
}

const openOffsetDialog = (subtitle) => {
  selectedSubtitle.value = subtitle
  offsetValue.value = subtitle.timeOffset || 0
  offsetDialog.value = true
}

const submitOffset = async () => {
  if (!selectedSubtitle.value) {
    return
  }
  try {
    const res = await setSubtitleOffset(selectedSubtitle.value.id, offsetValue.value)
    if (res?.code === 200) {
      showAppMessage('偏移量更新成功', 'success')
      offsetDialog.value = false
      await fetchSubtitles(pagination.value.page)
    } else {
      showAppMessage(res?.msg || '偏移量更新失败', 'error')
    }
  } catch (error) {
    showAppMessage('偏移量更新失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

const handleDelete = async (subtitle) => {
  const confirmed = await askAppConfirm({
    title: '删除字幕',
    message: `确定要删除字幕 ${subtitle.fileName} 吗？`,
    color: 'error'
  })
  if (!confirmed) {
    return
  }

  try {
    const res = await deleteSubtitle(subtitle.id)
    if (res?.code === 200) {
      showAppMessage('删除成功', 'success')
      await fetchSubtitles(pagination.value.page)
    } else {
      showAppMessage(res?.msg || '删除失败', 'error')
    }
  } catch (error) {
    showAppMessage('删除失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

const sourceTypeLabel = (value) => {
  const labels = {
    EMBEDDED: '内嵌',
    EXTERNAL: '外部',
    UPLOADED: '上传'
  }
  return labels[value] || value || '-'
}

const sourceTypeColor = (value) => {
  const colors = {
    EMBEDDED: 'success',
    EXTERNAL: 'info',
    UPLOADED: 'warning'
  }
  return colors[value] || 'grey'
}

onMounted(() => {
  fetchSubtitles(1)
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-title class="d-flex align-center ga-2">
        <i class="mdi mdi-subtitles-outline" style="color: #c45d2b;"></i>
        字幕搜索
      </v-card-title>
      <v-card-text class="pa-4">
        <v-row dense class="align-center">
          <v-col cols="12" md="5">
            <v-text-field
              v-model="searchKeyword"
              label="搜索字幕、视频或动漫"
              variant="outlined"
              density="compact"
              prepend-inner-icon="mdi-magnify"
              hide-details
              clearable
              @keyup.enter="handleSearch"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="sourceType"
              :items="[
                { title: '全部来源', value: null },
                { title: '内嵌', value: 'EMBEDDED' },
                { title: '外部', value: 'EXTERNAL' },
                { title: '上传', value: 'UPLOADED' }
              ]"
              item-title="title"
              item-value="value"
              label="字幕来源"
              variant="outlined"
              density="compact"
              hide-details
              @update:model-value="handleSearch"
            />
          </v-col>
          <v-col cols="12" md="4" class="d-flex ga-2 justify-md-end">
            <v-btn color="primary" variant="elevated" size="small" @click="handleSearch">查询</v-btn>
            <v-btn color="grey" variant="text" size="small" @click="resetFilters">重置</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card>
      <!-- 桌面表格 -->
      <div class="d-none d-lg-block">
        <v-data-table-server
          :headers="headers"
          :items="subtitles"
          :loading="loading"
          :items-per-page="pagination.itemsPerPage"
          :items-length="pagination.totalItems"
          density="compact"
          class="elevation-0"
          @update:options="onTableOptionsChange"
        >
          <template #item.fileName="{ item }">
            <div>
              <div class="text-body-2 text-truncate" :title="item.fileName">{{ item.fileName }}</div>
              <div class="text-caption text-grey">{{ formatFileSize(item.fileSize) }}</div>
            </div>
          </template>

          <template #item.videoFileName="{ item }">
            <div>
              <div class="text-body-2 text-truncate" :title="item.videoFileName">{{ item.videoFileName || '-' }}</div>
              <div class="text-caption text-grey text-truncate" :title="item.videoFilePath">{{ item.videoFilePath || '-' }}</div>
            </div>
          </template>

          <template #item.animeTitle="{ item }">
            <div class="text-truncate" :title="item.animeTitle">{{ item.animeTitle || '-' }}</div>
          </template>

          <template #item.episodeTitle="{ item }">
            <div class="text-truncate" :title="item.episodeTitle">{{ item.episodeTitle || '-' }}</div>
          </template>

          <template #item.sourceType="{ item }">
            <v-chip size="small" label :color="sourceTypeColor(item.sourceType)">
              {{ sourceTypeLabel(item.sourceType) }}
            </v-chip>
          </template>

          <template #item.timeOffset="{ item }">
            <span class="text-caption">{{ item.timeOffset || 0 }} ms</span>
          </template>

          <template #item.actions="{ item }">
            <div class="d-flex align-center ga-1">
              <v-btn icon="mdi-download" variant="text" size="x-small" color="primary" @click="handleDownload(item)" />
              <v-btn icon="mdi-timer-cog" variant="text" size="x-small" color="info" @click="openOffsetDialog(item)" />
              <v-btn icon="mdi-delete" variant="text" size="x-small" color="error" @click="handleDelete(item)" />
            </div>
          </template>

          <template #no-data>
            <div class="text-center py-8">
              <v-icon size="64" color="grey-lighten-1">mdi-subtitles-outline</v-icon>
              <p class="text-body-1 mt-4 text-grey">暂无字幕数据</p>
            </div>
          </template>
        </v-data-table-server>
      </div>

      <!-- 移动端卡片 -->
      <v-card-text class="d-lg-none pa-4">
        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" />
        </div>

        <template v-else-if="subtitles.length > 0">
          <v-card v-for="item in subtitles" :key="item.id" class="mb-3" variant="outlined">
            <v-card-item>
              <template #prepend>
                <v-avatar color="purple" variant="tonal">
                  <v-icon>mdi-subtitles</v-icon>
                </v-avatar>
              </template>
              <v-card-title class="text-body-1 text-wrap">{{ item.fileName }}</v-card-title>
              <v-card-subtitle class="text-caption text-truncate" :title="item.videoFileName">
                {{ item.videoFileName || '视频：-' }}
              </v-card-subtitle>
            </v-card-item>

            <v-card-text class="pt-2">
              <div class="text-caption text-grey text-truncate mb-2" :title="item.videoFilePath">
                {{ item.videoFilePath || '-' }}
              </div>
              <div class="d-flex flex-wrap ga-2 align-center">
                <span class="text-body-2 text-truncate">{{ item.animeTitle || '-' }}</span>
                <span class="text-caption text-grey">{{ item.episodeTitle || '-' }}</span>
                <v-chip size="x-small" label :color="sourceTypeColor(item.sourceType)">
                  {{ sourceTypeLabel(item.sourceType) }}
                </v-chip>
                <span class="text-caption text-grey ml-auto">{{ item.timeOffset || 0 }} ms</span>
              </div>
            </v-card-text>

            <v-card-actions>
              <v-btn icon="mdi-download" variant="text" size="small" color="primary" @click="handleDownload(item)" />
              <v-btn icon="mdi-timer-cog" variant="text" size="small" color="info" @click="openOffsetDialog(item)" />
              <v-btn icon="mdi-delete" variant="text" size="small" color="error" @click="handleDelete(item)" />
            </v-card-actions>
          </v-card>

          <div
            v-if="pagination.totalItems > pagination.itemsPerPage"
            class="d-flex justify-center mt-2"
          >
            <v-pagination
              v-model="pagination.page"
              :length="Math.max(1, Math.ceil(pagination.totalItems / pagination.itemsPerPage))"
              @update:model-value="(p) => onTableOptionsChange({ page: p, itemsPerPage: pagination.itemsPerPage })"
            />
          </div>
        </template>

        <div v-else class="text-center py-8">
          <v-icon size="64" color="grey-lighten-1">mdi-subtitles-outline</v-icon>
          <p class="text-body-1 mt-4 text-grey">暂无字幕数据</p>
        </div>
      </v-card-text>
    </v-card>

    <v-dialog v-model="offsetDialog" max-width="420">
      <v-card>
        <v-card-title>设置字幕偏移量</v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <v-text-field
            v-model.number="offsetValue"
            label="偏移量（毫秒）"
            type="number"
            variant="outlined"
            density="compact"
            hint="正数表示延后，负数表示提前"
            persistent-hint
          />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn color="grey" variant="text" @click="offsetDialog = false">取消</v-btn>
          <v-btn color="primary" variant="elevated" @click="submitOffset">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
