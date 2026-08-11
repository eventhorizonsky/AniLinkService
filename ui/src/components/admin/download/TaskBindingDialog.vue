<script setup>
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  binding: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const taskStatusMap = {
  PENDING: '等待中',
  RUNNING: '下载中',
  SEEDING: '做种中',
  MOVING: '迁移中',
  SCANNING: '扫描中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FAILED: '失败',
  STALLED: '停滞'
}

const matchStatusMap = {
  MATCHED: '已匹配',
  UNMATCHED: '未匹配',
  PENDING: '待匹配',
  FAILED: '匹配失败'
}

const formatStatus = (status) => taskStatusMap[status] || status || '-'
const formatMatchStatus = (status) => matchStatusMap[status] || status || '-'
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="720"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title>
        <v-icon start color="primary">mdi-link-variant</v-icon>
        下载任务绑定状态
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="props.loading" indeterminate color="primary" class="mb-4" />
        <template v-else-if="props.binding">
          <v-list lines="two">
            <v-list-item title="任务状态" :subtitle="formatStatus(props.binding.taskStatus)" />
            <v-list-item title="最终路径" :subtitle="props.binding.finalPath || '-'" />
            <v-list-item title="媒体文件ID" :subtitle="props.binding.mediaFileId || '-'" />
            <v-list-item
              title="绑定结果"
              :subtitle="props.binding.mediaFileExists ? '已进入媒体库数据库' : '未找到对应媒体文件记录'"
            />
            <v-list-item title="动漫ID" :subtitle="props.binding.animeId || '-'" />
            <v-list-item title="动漫标题" :subtitle="props.binding.animeTitle || '-'" />
            <v-list-item title="剧集ID" :subtitle="props.binding.episodeId || '-'" />
            <v-list-item title="剧集标题" :subtitle="props.binding.episodeTitle || '-'" />
            <v-list-item title="匹配状态" :subtitle="formatMatchStatus(props.binding.matchStatus)" />
          </v-list>
        </template>
        <div v-else class="text-center text-medium-emphasis py-6">暂无绑定信息</div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
