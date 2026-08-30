import { ref, onMounted } from 'vue'
import { askAppConfirm, showAppMessage } from '../utils/ui-feedback'
import { usePolling } from './usePolling'

const errorText = (error, fallback = '请稍后重试') =>
  error.response?.data?.msg || fallback

/**
 * 统一封装媒体库管理逻辑：列表加载、路径树加载与选择、添加/删除媒体库、
 * 单个/全部扫描，以及扫描进度轮询。
 *
 * admin（/media-library）与 install（/init/media-library）仅通过 options.api
 * 注入不同的 API 函数；options.texts 用于覆盖提示文案。
 */
export function useMediaLibrary(options = {}) {
  const api = options.api || {}
  const texts = options.texts || {}

  // ---- 媒体库列表 ----
  const mediaLibraries = ref([])
  const loading = ref(false)

  // ---- 添加媒体库弹窗 ----
  const dialog = ref(false)
  const errorMessage = ref('')
  const newLibrary = ref({ name: '', path: '' })

  // ---- 路径树 ----
  const pathTree = ref([])
  const loadingPaths = ref(false)
  const showPathTree = ref(false)
  const rootPath = ref('/')

  // ---- 扫描 ----
  const scanning = ref(false)

  // ---- 扫描进度追踪（仅当注入 fetchProgress 时启用） ----
  const progressData = ref({})
  const showProgress = ref({})
  const activeProgressLibraries = new Set()

  const fetchLibraries = async () => {
    loading.value = true
    try {
      const res = await api.getLibraries()
      if (res?.code === 200) {
        mediaLibraries.value = res.data || []
      }
    } catch (error) {
      console.error('获取媒体库失败:', error)
    } finally {
      loading.value = false
    }
  }

  // ---- 路径树 ----
  const loadPaths = async (root, assign) => {
    loadingPaths.value = true
    try {
      const res = await api.getPaths(root)
      if (res?.code === 200) {
        const items = res.data || []
        assign(items.map((item) => ({ id: item.path, title: item.name, children: [] })))
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

  // ---- 扫描进度 ----
  const fetchProgressOf = async (libraryId) => {
    if (!api.fetchProgress) return
    const data = await api.fetchProgress(libraryId).catch(() => null)
    if (!data) return
    if (!progressData.value[libraryId]) {
      progressData.value[libraryId] = { metadata: null, match: null }
    }
    Object.assign(progressData.value[libraryId], data)
  }

  const progressPoller = api.fetchProgress
    ? usePolling(
        () => {
          for (const id of activeProgressLibraries) {
            fetchProgressOf(id)
          }
        },
        { interval: 5000, immediate: false }
      )
    : null

  const startProgressPolling = (libraryId) => {
    if (!api.fetchProgress) return
    showProgress.value[libraryId] = true
    fetchProgressOf(libraryId)
    if (!activeProgressLibraries.has(libraryId)) {
      activeProgressLibraries.add(libraryId)
      progressPoller.start()
    }
  }

  const stopProgressPolling = (libraryId) => {
    showProgress.value[libraryId] = false
    activeProgressLibraries.delete(libraryId)
    if (activeProgressLibraries.size === 0) {
      progressPoller.stop()
    }
  }

  const toggleProgressDisplay = (libraryId) => {
    if (showProgress.value[libraryId]) {
      stopProgressPolling(libraryId)
    } else {
      startProgressPolling(libraryId)
    }
  }

  const getProgressData = (libraryId) => progressData.value[libraryId]

  const getLibraryMatchProcessedCount = (libraryId) => {
    const match = progressData.value[libraryId]?.match
    if (!match) return 0
    return Number(match.matched || 0) + Number(match.noMatch || 0)
  }

  // ---- 添加媒体库 ----
  const addLibrary = async () => {
    if (!newLibrary.value.name || !newLibrary.value.path) {
      errorMessage.value = '请填写媒体库名称和路径'
      return
    }

    loading.value = true
    errorMessage.value = ''
    try {
      const res = await api.createLibrary(newLibrary.value)
      if (res?.code === 200) {
        dialog.value = false
        newLibrary.value = { name: '', path: '' }
        showPathTree.value = false
        await fetchLibraries()
      } else {
        errorMessage.value = res?.msg || '添加媒体库失败'
      }
    } catch (error) {
      errorMessage.value = errorText(error, '添加媒体库失败，请稍后重试')
    } finally {
      loading.value = false
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

  // ---- 删除媒体库 ----
  const deleteLibrary = async (id) => {
    const confirmed = await askAppConfirm({
      title: '删除媒体库',
      message: '确定要删除这个媒体库吗？',
      color: 'error'
    })
    if (!confirmed) return

    try {
      const res = await api.removeLibrary(id)
      if (res?.code === 200) {
        await fetchLibraries()
      }
    } catch (error) {
      showAppMessage('删除失败：' + errorText(error), 'error')
    }
  }

  // ---- 扫描 ----
  const scanLibrary = async (id) => {
    scanning.value = true
    try {
      const res = await api.scanLibrary(id)
      if (res?.code === 200) {
        showAppMessage(
          texts.scanSuccess ? texts.scanSuccess(res) : (res.msg || '扫描已触发'),
          'success'
        )
        await fetchLibraries()
        startProgressPolling(id)
      }
    } catch (error) {
      showAppMessage(
        texts.scanError ? texts.scanError(error) : ('扫描失败：' + errorText(error)),
        'error'
      )
    } finally {
      scanning.value = false
    }
  }

  const rematchLibrary = async (id) => {
    if (!api.rematchLibrary) return
    scanning.value = true
    try {
      const res = await api.rematchLibrary(id)
      if (res?.code === 200) {
        showAppMessage(
          texts.rematchSuccess ? texts.rematchSuccess(res) : (res.msg || '重新匹配已触发'),
          'success'
        )
        await fetchLibraries()
        startProgressPolling(id)
      }
    } catch (error) {
      showAppMessage(
        texts.rematchError ? texts.rematchError(error) : ('重新匹配失败：' + errorText(error)),
        'error'
      )
    } finally {
      scanning.value = false
    }
  }

  const scanAll = async () => {
    const confirmed = await askAppConfirm({
      title: texts.scanAllConfirm?.title || '扫描所有媒体库',
      message: texts.scanAllConfirm?.message || '确定要扫描所有媒体库吗？',
      color: 'warning'
    })
    if (!confirmed) return

    scanning.value = true
    try {
      if (api.scanAll) {
        const res = await api.scanAll()
        if (res?.code === 200) {
          showAppMessage(
            texts.scanAllSuccess ? texts.scanAllSuccess(res) : (res.msg || '扫描已触发'),
            'success'
          )
          await fetchLibraries()
        }
      } else {
        for (const lib of mediaLibraries.value) {
          await api.scanLibrary(lib.id)
        }
        showAppMessage(
          texts.scanAllSuccess ? texts.scanAllSuccess() : '扫描已触发',
          'success'
        )
        await fetchLibraries()
      }
      mediaLibraries.value.forEach((lib) => startProgressPolling(lib.id))
    } catch (error) {
      showAppMessage(
        texts.scanAllError ? texts.scanAllError(error) : ('扫描失败：' + errorText(error)),
        'error'
      )
    } finally {
      scanning.value = false
    }
  }

  onMounted(() => {
    fetchLibraries()
  })

  return {
    mediaLibraries,
    loading,
    dialog,
    errorMessage,
    scanning,
    pathTree,
    loadingPaths,
    showPathTree,
    rootPath,
    newLibrary,
    progressData,
    showProgress,
    fetchLibraries,
    fetchPaths,
    handleNodeSelect,
    addLibrary,
    openAddDialog,
    closeDialog,
    togglePathTree,
    onPathSelect,
    deleteLibrary,
    scanLibrary,
    rematchLibrary,
    scanAll,
    startProgressPolling,
    stopProgressPolling,
    toggleProgressDisplay,
    getProgressData,
    getLibraryMatchProcessedCount
  }
}
