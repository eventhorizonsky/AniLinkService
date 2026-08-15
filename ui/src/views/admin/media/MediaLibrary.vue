<script setup>
import { useMediaLibrary } from '../../../composables/useMediaLibrary'
import {
  getLibraries,
  getLibraryPaths,
  createLibrary,
  removeLibrary,
  rematchLibrary as apiRematchLibrary,
  reprocessMediaFileMetadata,
  getMetadataProgress,
  getMatchProgress
} from '../../../api/media'

const {
  mediaLibraries,
  loading,
  dialog,
  errorMessage,
  scanning,
  pathTree,
  loadingPaths,
  showPathTree,
  newLibrary,
  progressData,
  showProgress,
  handleNodeSelect,
  addLibrary,
  deleteLibrary,
  scanLibrary,
  rematchLibrary,
  scanAll,
  openAddDialog,
  closeDialog,
  togglePathTree,
  onPathSelect,
  toggleProgressDisplay,
  getLibraryMatchProcessedCount
} = useMediaLibrary({
  api: {
    getLibraries,
    getPaths: getLibraryPaths,
    createLibrary,
    removeLibrary,
    scanLibrary: reprocessMediaFileMetadata,
    rematchLibrary: apiRematchLibrary,
    fetchProgress: async (libraryId) => {
      const out = {}
      const meta = await getMetadataProgress({ libraryId }).catch(() => null)
      if (meta?.code === 200) out.metadata = meta.data
      const match = await getMatchProgress({ libraryId }).catch(() => null)
      if (match?.code === 200) out.match = match.data
      return out
    }
  },
  texts: {
    scanSuccess: (res) => res.msg || '重新获取元数据已触发',
    scanError: (error) => '重新获取元数据失败：' + (error.response?.data?.msg || '请稍后重试'),
    scanAllSuccess: () => '所有媒体库的元数据重新获取已提交',
    scanAllError: (error) => '提交任务失败：' + (error.response?.data?.msg || '请稍后重试'),
    rematchSuccess: (res) => res.msg || '弹幕重新匹配已触发',
    rematchError: (error) => '重新匹配失败：' + (error.response?.data?.msg || '请稍后重试'),
    scanAllConfirm: {
      title: '批量重新获取元数据',
      message: '确定要重新获取所有媒体库的元数据吗？'
    }
  }
})
</script>

<template>
  <div>
    <v-card class="mb-4">
      <v-card-text class="d-flex gap-2">
        <v-btn
          color="primary"
          variant="elevated"
          @click="openAddDialog"
        >
          <v-icon start>mdi-plus</v-icon>
          添加媒体库
        </v-btn>
        <v-btn
          v-if="mediaLibraries.length > 0"
          color="info"
          variant="elevated"
          :loading="scanning"
          :disabled="scanning"
          @click="scanAll"
        >
          <v-icon start>mdi-database-refresh</v-icon>
          重新获取所有元数据
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card v-if="mediaLibraries.length === 0 && !loading" class="text-center pa-8">
      <v-icon size="64" color="grey-lighten-1">mdi-folder-open-outline</v-icon>
      <p class="text-body-1 mt-4 text-grey">暂无媒体库，请添加</p>
    </v-card>

    <v-card v-else-if="mediaLibraries.length > 0">
      <v-list>
        <template v-for="library in mediaLibraries" :key="library.id">
          <v-list-item>
            <template v-slot:prepend>
              <v-icon color="primary" size="large">mdi-folder</v-icon>
            </template>
            <v-list-item-title class="font-weight-medium">{{ library.name }}</v-list-item-title>
            <v-list-item-subtitle>{{ library.path }}</v-list-item-subtitle>
            <template v-slot:append>
              <v-chip :color="library.status === 'OK' ? 'success' : 'error'" size="small">
                {{ library.status }}
              </v-chip>
              <v-btn
                icon
                variant="text"
                color="info"
                size="small"
                :loading="scanning"
                @click="scanLibrary(library.id)"
              >
                <v-icon>mdi-database-refresh</v-icon>
                <v-tooltip activator="parent" location="top">重新获取元数据</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="success"
                size="small"
                :loading="scanning"
                @click="rematchLibrary(library.id)"
              >
                <v-icon>mdi-sync</v-icon>
                <v-tooltip activator="parent" location="top">重新匹配弹幕</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="warning"
                size="small"
                @click="toggleProgressDisplay(library.id)"
              >
                <v-icon>{{ showProgress[library.id] ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
                <v-tooltip activator="parent" location="top">{{ showProgress[library.id] ? '隐藏' : '显示' }}进度</v-tooltip>
              </v-btn>
              <v-btn
                icon
                variant="text"
                color="error"
                size="small"
                @click="deleteLibrary(library.id)"
              >
                <v-icon>mdi-delete</v-icon>
                <v-tooltip activator="parent" location="top">删除媒体库</v-tooltip>
              </v-btn>
            </template>
          </v-list-item>

          <!-- 进度显示区域 -->
          <v-expand-transition>
            <div v-if="showProgress[library.id]">
              <v-divider />
              <v-card variant="flat" class="ma-2 pa-4" color="surface">
                <!-- 元数据扫描进度 -->
                <div v-if="progressData[library.id] && progressData[library.id].metadata" class="mb-6">
                  <div class="d-flex justify-space-between align-center mb-2">
                    <span class="font-weight-medium">
                      <v-icon small>mdi-database-refresh</v-icon>
                      元数据扫描进度
                    </span>
                    <span class="text-caption text-grey">
                      {{ progressData[library.id].metadata.metadataFetched || 0 }} / {{ progressData[library.id].metadata.totalFiles || 0 }}
                    </span>
                  </div>
                  <v-progress-linear
                    :model-value="progressData[library.id].metadata.totalFiles > 0 ? Math.round((progressData[library.id].metadata.metadataFetched / progressData[library.id].metadata.totalFiles) * 100) : 0"
                    height="6"
                    color="info"
                    class="mb-2"
                  />
                  <div class="text-caption text-grey">
                    <div>队列待处理: {{ progressData[library.id].metadata.pendingMetadata || 0 }} · 活跃线程: {{ progressData[library.id].metadata.activeThreads || 0 }} / {{ progressData[library.id].metadata.maxPoolSize || 4 }}</div>
                    <div v-if="progressData[library.id].metadata.totalSubmitted > 0">提交任务: {{ progressData[library.id].metadata.totalSubmitted }} · 已处理: {{ progressData[library.id].metadata.totalProcessed }} · 失败: {{ progressData[library.id].metadata.failedTasks || 0 }}</div>
                  </div>
                </div>
                <div v-else class="mb-6 text-center text-grey">
                  <v-icon small>mdi-clock-outline</v-icon>
                  等待元数据扫描数据...
                </div>

                <!-- 弹幕匹配进度 -->
                <div v-if="progressData[library.id] && progressData[library.id].match" class="mb-6">
                  <div class="d-flex justify-space-between align-center mb-2">
                    <span class="font-weight-medium">
                      <v-icon small>mdi-sync</v-icon>
                      弹幕匹配进度
                    </span>
                    <span class="text-caption text-grey">
                      {{ getLibraryMatchProcessedCount(library.id) }} / {{ progressData[library.id].match.totalFiles || 0 }}
                    </span>
                  </div>
                  <v-progress-linear
                    :model-value="progressData[library.id].match.totalFiles > 0 ? Math.round((getLibraryMatchProcessedCount(library.id) / progressData[library.id].match.totalFiles) * 100) : 0"
                    height="6"
                    color="success"
                    class="mb-2"
                  />
                  <div class="text-caption text-grey">
                    <div>队列待处理: {{ progressData[library.id].match.queuePending || 0 }} · 总待匹配: {{ progressData[library.id].match.pendingMatch || 0 }} · 活跃批次: {{ progressData[library.id].match.activeBatches || 0 }}</div>
                    <div v-if="progressData[library.id].match.totalEnqueued > 0">当前已处理: {{ getLibraryMatchProcessedCount(library.id) }} · 当前已匹配: {{ progressData[library.id].match.matched || 0 }} · 当前无匹配: {{ progressData[library.id].match.noMatch || 0 }} · 累计入队: {{ progressData[library.id].match.totalEnqueued }} · 累计匹配: {{ progressData[library.id].match.totalMatched }} · 累计无匹配: {{ progressData[library.id].match.totalNoMatch || 0 }} · 失败: {{ progressData[library.id].match.failedTasks || 0 }}</div>
                  </div>
                </div>
                <div v-else class="text-center text-grey">
                  <v-icon small>mdi-clock-outline</v-icon>
                  等待弹幕匹配数据...
                </div>

                <v-divider class="my-4" />
                
                <div class="text-center">
                  <v-btn 
                    size="small" 
                    variant="text" 
                    color="primary"
                    @click="showProgress[library.id] = false"
                  >
                    收起
                  </v-btn>
                </div>
              </v-card>
            </div>
          </v-expand-transition>
        </template>
      </v-list>
    </v-card>

    <v-progress-linear v-else indeterminate color="primary" />

    <!-- 添加媒体库对话框 -->
    <v-dialog v-model="dialog" max-width="700">
      <v-card>
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">添加媒体库</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="closeDialog" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-alert v-if="errorMessage" type="error" class="mb-4" closable>
            {{ errorMessage }}
          </v-alert>

          <v-form>
            <v-text-field
              v-model="newLibrary.name"
              label="媒体库名称"
              prepend-inner-icon="mdi-label"
              variant="outlined"
              color="primary"
              required
              class="mb-4"
            />

            <div class="mb-4">
              <v-text-field
                v-model="newLibrary.path"
                label="媒体库路径"
                prepend-inner-icon="mdi-folder-open"
                variant="outlined"
                color="primary"
                required
                readonly
                @click="togglePathTree"
                :append-inner-icon="showPathTree ? 'mdi-menu-up' : 'mdi-menu-down'"
              />

              <v-card v-if="showPathTree" variant="outlined" class="mt-2 pa-2" max-height="300" style="overflow-y: auto">
                <v-treeview
                  :items="pathTree"
                  item-value="id"
                  :load-children="handleNodeSelect"
                  activatable
                  density="compact"
                  @update:activated="onPathSelect"
                >
                  <template v-slot:prepend="{ item }">
                    <v-icon>mdi-folder</v-icon>
                  </template>
                </v-treeview>
                <v-progress-linear v-if="loadingPaths" indeterminate />
              </v-card>
            </div>
          </v-form>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn
            color="grey"
            variant="text"
            @click="closeDialog"
          >
            取消
          </v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            :loading="loading"
            :disabled="loading"
            @click="addLibrary"
          >
            添加
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
