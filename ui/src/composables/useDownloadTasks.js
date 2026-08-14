import { ref } from 'vue'
import { showAppMessage } from '../utils/ui-feedback'
import { API_BASE } from '../utils/constants'
import { useAuth } from './useAuth'
import {
  cancelDownloadTask,
  retryDownloadTask,
  deleteDownloadTask,
  getDownloadTaskBinding,
} from '../api/download'

export function useDownloadTasks() {
  const { token } = useAuth()
  const tasks = ref([])
  const summary = ref(null)
  const sseConnected = ref(false)
  const sseReconnectAttempts = ref(0)

  const bindingDialog = ref(false)
  const currentBinding = ref(null)
  const bindingLoading = ref(false)

  let eventSource = null
  let reconnectAttempts = 0

  const disconnectProgressStream = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    reconnectAttempts = 0
    sseReconnectAttempts.value = 0
    sseConnected.value = false
  }

  const connectProgressStream = () => {
    disconnectProgressStream()
    const source = new EventSource(
      `${API_BASE}/resource-search/download-tasks/stream?satoken=${encodeURIComponent(token.value)}`
    )
    eventSource = source

    source.addEventListener('download-progress', (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload && Array.isArray(payload.tasks)) {
          tasks.value = payload.tasks
        }
        if (payload && payload.stats) {
          summary.value = payload.stats
        }
        reconnectAttempts = 0
        sseReconnectAttempts.value = 0
        sseConnected.value = true
      } catch (error) {
        console.error('解析 SSE 数据失败:', error)
      }
    })

    source.onerror = () => {
      sseConnected.value = false
      reconnectAttempts += 1
      sseReconnectAttempts.value = reconnectAttempts
    }
  }

  const runAction = async (label, action) => {
    try {
      const body = await action()
      if (body?.code === 200) {
        showAppMessage(body?.msg || `${label}成功`, 'success')
        return true
      }
      showAppMessage(body?.msg || `${label}失败`, 'error')
      return false
    } catch (error) {
      console.error(`${label}失败:`, error)
      showAppMessage(error.response?.data?.msg || `${label}失败`, 'error')
      return false
    }
  }

  const cancelTask = (task) => runAction('取消', () => cancelDownloadTask(task.id))

  const retryTask = (task) => runAction('重试', () => retryDownloadTask(task.id))

  const deleteTask = (task) => runAction('删除', () => deleteDownloadTask(task.id))

  const openBinding = async (taskId) => {
    bindingLoading.value = true
    bindingDialog.value = true
    currentBinding.value = null
    try {
      const body = await getDownloadTaskBinding(taskId)
      currentBinding.value = body?.data || null
    } catch (error) {
      console.error('查询绑定状态失败:', error)
      showAppMessage(error.response?.data?.msg || '查询绑定状态失败', 'error')
    } finally {
      bindingLoading.value = false
    }
  }

  const closeBinding = () => {
    bindingDialog.value = false
    currentBinding.value = null
  }

  return {
    tasks,
    summary,
    sseConnected,
    sseReconnectAttempts,
    bindingDialog,
    currentBinding,
    bindingLoading,
    connectProgressStream,
    reconnect: connectProgressStream,
    disconnectProgressStream,
    cancelTask,
    retryTask,
    deleteTask,
    openBinding,
    closeBinding
  }
}
