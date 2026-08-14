<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { askAppConfirm, showAppMessage } from '../utils/ui-feedback'
import { API_BASE } from '../utils/constants'

const mediaLibraries = ref([])
const loading = ref(false)
const dialog = ref(false)
const errorMessage = ref('')
const scanning = ref(false)
const pathTree = ref([])
const loadingPaths = ref(false)
const showPathTree = ref(false)
const rootPath = ref('/')

const newLibrary = ref({
  name: '',
  path: ''
})

const fetchLibraries = async () => {
  try {
    const res = await axios.get(`${API_BASE}/init/media-library`)
    if (res.data?.code === 200) {
      mediaLibraries.value = res.data.data || []
    }
  } catch (error) {
    console.error('获取媒体库失败:', error)
  }
}

const loadPaths = async (root, assign) => {
  loadingPaths.value = true
  try {
    const res = await axios.get(`${API_BASE}/init/media-library/paths`, {
      params: {
        rootPath: root,
        onlyDir: true
      }
    })
    if (res.data?.code === 200) {
      const items = res.data.data || []
      const tree = items.map(item => ({
        id: item.path,
        title: item.name,
        children: []
      }))
      assign(tree)
    }
  } catch (error) {
    console.error('获取路径失败:', error)
  } finally {
    loadingPaths.value = false
  }
}

const fetchPaths = async (path = rootPath.value) => {
  loadPaths(path, (tree) => { pathTree.value = tree })
}

const handleNodeSelect = async (item) => {
  loadPaths(item.id, (tree) => { if (tree.length > 0) item.children = tree })
}

const addLibrary = async () => {
  if (!newLibrary.value.name || !newLibrary.value.path) {
    errorMessage.value = '请填写媒体库名称和路径'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const res = await axios.post(`${API_BASE}/init/media-library`, newLibrary.value)
    if (res.data?.code === 200) {
      dialog.value = false
      newLibrary.value = { name: '', path: '' }
      showPathTree.value = false
      await fetchLibraries()
    } else {
      errorMessage.value = res.data?.msg || '添加媒体库失败'
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.msg || '添加媒体库失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const deleteLibrary = async (id) => {
  const confirmed = await askAppConfirm({
    title: '删除媒体库',
    message: '确定要删除这个媒体库吗？',
    color: 'error'
  })
  if (!confirmed) return

  try {
    const res = await axios.delete(`${API_BASE}/init/media-library/${id}`)
    if (res.data?.code === 200) {
      await fetchLibraries()
    }
  } catch (error) {
    showAppMessage('删除失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  }
}

const scanLibrary = async (id) => {
  scanning.value = true
  try {
    const res = await axios.post(`${API_BASE}/media-library/scan/${id}`)
    if (res.data?.code === 200) {
      showAppMessage('扫描已触发', 'success')
      await fetchLibraries()
    }
  } catch (error) {
    showAppMessage('扫描失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  } finally {
    scanning.value = false
  }
}

const scanAll = async () => {
  const confirmed = await askAppConfirm({
    title: '扫描所有媒体库',
    message: '确定要扫描所有媒体库吗？',
    color: 'warning'
  })
  if (!confirmed) return

  scanning.value = true
  try {
    const res = await axios.post(`${API_BASE}/media-library/scan-all`)
    if (res.data?.code === 200) {
      showAppMessage('扫描已触发', 'success')
      await fetchLibraries()
    }
  } catch (error) {
    showAppMessage('扫描失败：' + (error.response?.data?.msg || '请稍后重试'), 'error')
  } finally {
    scanning.value = false
  }
}

const openAddDialog = () => {
  errorMessage.value = ''
  showPathTree.value = false
  newLibrary.value = { name: '', path: '' }
  dialog.value = true
}

const closeDialog = () => {
  dialog.value = false
  errorMessage.value = ''
  showPathTree.value = false
  pathTree.value = []
}

const togglePathTree = () => {
  if (!showPathTree.value) {
    fetchPaths(rootPath.value)
  }
  showPathTree.value = !showPathTree.value
}

const onPathSelect = (selected) => {
  if (selected && selected.length > 0) {
    newLibrary.value.path = selected[0]
    showPathTree.value = false
  }
}

onMounted(() => {
  fetchLibraries()
})
</script>

<template>
  <div>
    <h3 class="text-h6 mb-4 text-primary font-weight-medium">
      <v-icon start color="primary">mdi-folder-multiple</v-icon>
      媒体库配置
    </h3>

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
          <v-icon start>mdi-refresh</v-icon>
          扫描所有
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card v-if="mediaLibraries.length === 0" class="text-center pa-8">
      <v-icon size="64" color="grey-lighten-1">mdi-folder-open-outline</v-icon>
      <p class="text-body-1 mt-4 text-grey">暂无媒体库，请添加</p>
    </v-card>

    <v-list v-else>
      <v-list-item v-for="library in mediaLibraries" :key="library.id">
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
            icon="mdi-refresh"
            variant="text"
            color="info"
            size="small"
            :loading="scanning"
            @click="scanLibrary(library.id)"
          />
          <v-btn
            icon="mdi-delete"
            variant="text"
            color="error"
            @click="deleteLibrary(library.id)"
          />
        </template>
      </v-list-item>
    </v-list>

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
              placeholder="例如：我的动漫"
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
