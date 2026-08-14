<script setup>
import { ref, onMounted, onBeforeUnmount, provide, defineAsyncComponent } from 'vue'
import { useDownloadTasks } from '../../../composables/useDownloadTasks'
import DownloadTasksTab from './DownloadTasksTab.vue'
import ResourceSearchTab from './ResourceSearchTab.vue'
import TaskBindingDialog from '../../../components/admin/download/TaskBindingDialog.vue'

const ResourceRssSubscription = defineAsyncComponent(() => import('./ResourceRssSubscription.vue'))
const ResourceDownloadSettings = defineAsyncComponent(() => import('./ResourceDownloadSettings.vue'))

const {
  tasks,
  summary,
  sseConnected,
  sseReconnectAttempts,
  bindingDialog,
  currentBinding,
  bindingLoading,
  connectProgressStream,
  reconnect,
  disconnectProgressStream,
  cancelTask,
  retryTask,
  deleteTask,
  openBinding,
  closeBinding
} = useDownloadTasks()

const activeTab = ref('tasks')
const actions = { cancelTask, retryTask, deleteTask, openBinding, reconnect }
const isMobile = ref(false)

const checkViewport = () => {
  isMobile.value =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(max-width: 768px)').matches
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  connectProgressStream()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkViewport)
  disconnectProgressStream()
})

const applyStats = (stats) => {
  if (stats) {
    summary.value = stats
  }
}

provide('navigateTo', () => {
  activeTab.value = 'settings'
})
</script>

<template>
  <div>
    <v-card>
      <v-tabs v-model="activeTab" color="primary" grow>
        <v-tab value="tasks">
          <v-icon start>mdi-download-multiple</v-icon>
          <span class="tab-label">任务</span>
        </v-tab>
        <v-tab value="search">
          <v-icon start>mdi-cloud-search</v-icon>
          <span class="tab-label">资源搜索</span>
        </v-tab>
        <v-tab value="rss">
          <v-icon start>mdi-rss-box</v-icon>
          <span class="tab-label">RSS 订阅</span>
        </v-tab>
        <v-tab value="settings">
          <v-icon start>mdi-tune-variant</v-icon>
          <span class="tab-label">设置</span>
        </v-tab>
      </v-tabs>

      <v-window v-model="activeTab" :touch="false">
        <v-window-item value="tasks">
          <div class="pa-4">
            <DownloadTasksTab
              :stats="summary"
              :live-tasks="tasks"
              :sse-connected="sseConnected"
              :sse-reconnect-attempts="sseReconnectAttempts"
              :actions="actions"
              @stats-loaded="applyStats"
            />
          </div>
        </v-window-item>

        <v-window-item value="search">
          <div class="pa-4">
            <ResourceSearchTab />
          </div>
        </v-window-item>

        <v-window-item value="rss">
          <div class="pa-4">
            <ResourceRssSubscription />
          </div>
        </v-window-item>

        <v-window-item value="settings">
          <div class="pa-4">
            <ResourceDownloadSettings />
          </div>
        </v-window-item>
      </v-window>
    </v-card>

    <TaskBindingDialog
      v-model="bindingDialog"
      :loading="bindingLoading"
      :binding="currentBinding"
      @update:model-value="closeBinding"
    />
  </div>
</template>

<style scoped>
@media (max-width: 768px) {
  :deep(.tab-label) {
    display: none;
  }

  :deep(.v-icon--start) {
    margin: 0;
  }
}
</style>
