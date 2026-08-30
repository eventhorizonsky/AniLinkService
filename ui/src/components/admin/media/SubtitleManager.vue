<script setup>
import { ref, watch } from 'vue'
import { showAppMessage, askAppConfirm } from '../../../utils/ui-feedback'
import { formatFileSize } from '../../../utils/format'
import { getSubtitles, uploadSubtitle, deleteSubtitle, setSubtitleOffset, rescanSubtitles, getSubtitleDownloadUrl } from '../../../api/subtitle'

const props = defineProps({
  mediaFileId: {
    type: Number,
    required: true
  }
})

const subtitles = ref([])
const loading = ref(false)
const uploadDialog = ref(false)
const offsetDialog = ref(false)
const selectedSubtitle = ref(null)
const uploadForm = ref({
  file: null,
  trackName: '',
  language: ''
})
const offsetValue = ref(0)

const subtitleHeaders = [
  { title: '轨道名称', key: 'trackName' },
  { title: '语言', key: 'language' },
  { title: '格式', key: 'subtitleFormat' },
  { title: '来源', key: 'sourceType' },
  { title: '偏移量(ms)', key: 'timeOffset' },
  { title: '大小', key: 'fileSize' },
  { title: '操作', key: 'actions', sortable: false }
]

// 获取字幕列表
const fetchSubtitles = async () => {
  if (!props.mediaFileId) return
  
  loading.value = true
  try {
    const res = await getSubtitles(props.mediaFileId)
    if (res?.code === 200) {
      subtitles.value = res.data || []
    }
  } catch (error) {
    console.error('获取字幕列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 上传字幕
const handleUpload = async () => {
  if (!uploadForm.value.file) {
    showAppMessage('请选择文件', 'warning')
    return
  }

  const formData = new FormData()
  formData.append('mediaFileId', props.mediaFileId)
  formData.append('file', uploadForm.value.file)
  if (uploadForm.value.trackName) {
    formData.append('trackName', uploadForm.value.trackName)
  }
  if (uploadForm.value.language) {
    formData.append('language', uploadForm.value.language)
  }

  try {
    const res = await uploadSubtitle(formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res?.code === 200) {
      showAppMessage('上传成功', 'success')
      uploadDialog.value = false
      resetUploadForm()
      fetchSubtitles()
    } else {
      showAppMessage(res?.msg || '上传失败', 'error')
    }
  } catch (error) {
    showAppMessage('上传失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

// 删除字幕
const handleDelete = async (subtitle) => {
  const confirmed = await askAppConfirm({
    title: '删除字幕',
    message: `确定要删除字幕 "${subtitle.trackName}" 吗？`,
    color: 'error'
  })
  if (!confirmed) return

  try {
    const res = await deleteSubtitle(subtitle.id)
    if (res?.code === 200) {
      showAppMessage('删除成功', 'success')
      fetchSubtitles()
    } else {
      showAppMessage(res?.msg || '删除失败', 'error')
    }
  } catch (error) {
    showAppMessage('删除失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

// 设置偏移量
const openOffsetDialog = (subtitle) => {
  selectedSubtitle.value = subtitle
  offsetValue.value = subtitle.timeOffset || 0
  offsetDialog.value = true
}

const handleSetOffset = async () => {
  if (!selectedSubtitle.value) return

  try {
    const res = await setSubtitleOffset(selectedSubtitle.value.id, offsetValue.value)
    if (res?.code === 200) {
      showAppMessage('设置成功', 'success')
      offsetDialog.value = false
      fetchSubtitles()
    } else {
      showAppMessage(res?.msg || '设置失败', 'error')
    }
  } catch (error) {
    showAppMessage('设置失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

// 重新扫描
const handleRescan = async () => {
  try {
    const res = await rescanSubtitles(props.mediaFileId)
    if (res?.code === 200) {
      showAppMessage('重新扫描完成', 'success')
      fetchSubtitles()
    } else {
      showAppMessage(res?.msg || '扫描失败', 'error')
    }
  } catch (error) {
    showAppMessage('扫描失败: ' + (error.response?.data?.msg || error.message), 'error')
  }
}

// 下载字幕
const handleDownload = (subtitle) => {
  window.open(getSubtitleDownloadUrl(subtitle.id), '_blank')
}

const resetUploadForm = () => {
  uploadForm.value = {
    file: null,
    trackName: '',
    language: ''
  }
}

const getSourceTypeLabel = (sourceType) => {
  const map = {
    'EMBEDDED': '内嵌',
    'EXTERNAL': '外部',
    'UPLOADED': '上传'
  }
  return map[sourceType] || sourceType
}

watch(() => props.mediaFileId, () => {
  if (props.mediaFileId) {
    fetchSubtitles()
  }
}, { immediate: true })
</script>

<template>
  <v-card>
    <v-card-title class="d-flex align-center">
      <v-icon start>mdi-subtitles-outline</v-icon>
      字幕管理
      <v-spacer />
      <v-btn
        color="primary"
        variant="text"
        size="small"
        prepend-icon="mdi-refresh"
        @click="handleRescan"
      >
        重新扫描
      </v-btn>
      <v-btn
        color="primary"
        variant="elevated"
        size="small"
        prepend-icon="mdi-upload"
        @click="uploadDialog = true"
      >
        上传字幕
      </v-btn>
    </v-card-title>

    <v-divider />

    <v-card-text>
      <v-data-table
        :headers="subtitleHeaders"
        :items="subtitles"
        :loading="loading"
        density="compact"
        class="elevation-0"
        :items-per-page="-1"
      >
        <template v-slot:item.sourceType="{ item }">
          <v-chip size="small" :color="item.isExternal ? 'info' : 'success'">
            {{ getSourceTypeLabel(item.sourceType) }}
          </v-chip>
        </template>

        <template v-slot:item.language="{ item }">
          <span class="text-caption">{{ item.language || '-' }}</span>
        </template>

        <template v-slot:item.subtitleFormat="{ item }">
          <v-chip size="x-small" variant="tonal">
            {{ item.subtitleFormat?.toUpperCase() }}
          </v-chip>
        </template>

        <template v-slot:item.timeOffset="{ item }">
          <span class="text-caption">{{ item.timeOffset || 0 }} ms</span>
        </template>

        <template v-slot:item.fileSize="{ item }">
          <span class="text-caption">{{ formatFileSize(item.fileSize) }}</span>
        </template>

        <template v-slot:item.actions="{ item }">
          <div class="d-flex ga-1">
            <v-tooltip location="top" text="下载">
              <template #activator="{ props }">
                <v-btn
                  icon="mdi-download"
                  variant="text"
                  size="x-small"
                  color="primary"
                  v-bind="props"
                  @click="handleDownload(item)"
                />
              </template>
            </v-tooltip>
            <v-tooltip location="top" text="设置偏移量">
              <template #activator="{ props }">
                <v-btn
                  icon="mdi-timer-cog"
                  variant="text"
                  size="x-small"
                  color="info"
                  v-bind="props"
                  @click="openOffsetDialog(item)"
                />
              </template>
            </v-tooltip>
            <v-tooltip location="top" text="删除">
              <template #activator="{ props }">
                <v-btn
                  icon="mdi-delete"
                  variant="text"
                  size="x-small"
                  color="error"
                  v-bind="props"
                  @click="handleDelete(item)"
                />
              </template>
            </v-tooltip>
          </div>
        </template>

        <template v-slot:no-data>
          <div class="text-center py-6 text-grey">
            <v-icon size="48" class="mb-2">mdi-subtitles-outline</v-icon>
            <div>暂无字幕</div>
          </div>
        </template>
      </v-data-table>
    </v-card-text>

    <!-- 上传字幕对话框 -->
    <v-dialog v-model="uploadDialog" max-width="500">
      <v-card>
        <v-card-title>上传字幕文件</v-card-title>
        <v-divider />
        <v-card-text class="pt-4">
          <v-file-input
            v-model="uploadForm.file"
            label="选择字幕文件"
            accept=".srt,.ass,.ssa,.vtt,.sub,.sbv"
            variant="outlined"
            density="compact"
            prepend-icon="mdi-file-document"
            show-size
            required
          />
          <v-text-field
            v-model="uploadForm.trackName"
            label="轨道名称（可选）"
            variant="outlined"
            density="compact"
            class="mt-3"
          />
          <v-text-field
            v-model="uploadForm.language"
            label="语言（可选）"
            variant="outlined"
            density="compact"
            class="mt-3"
            placeholder="例如：zh_CN, en, ja"
          />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn color="grey" variant="text" @click="uploadDialog = false; resetUploadForm()">
            取消
          </v-btn>
          <v-btn color="primary" variant="elevated" @click="handleUpload">
            上传
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 设置偏移量对话框 -->
    <v-dialog v-model="offsetDialog" max-width="400">
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
            hint="正数表示延迟，负数表示提前"
            persistent-hint
          />
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn color="grey" variant="text" @click="offsetDialog = false">
            取消
          </v-btn>
          <v-btn color="primary" variant="elevated" @click="handleSetOffset">
            确定
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<style scoped>
:deep(.v-data-table) {
  background: transparent;
}
</style>
