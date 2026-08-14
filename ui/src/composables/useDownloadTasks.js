import { ref } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../utils/ui-feedback'
import { API_BASE } from '../utils/constants'
import { useAuth } from './useAuth'

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
      const res = await action()
      if (res.data?.code === 200) {
        showAppMessage(res.data?.msg || `${label}成功`, 'success')
        return true
      }
      showAppMessage(res.data?.msg || `${label}失败`, 'error')
      return false
    } catch (error) {
      console.error(`${label}失败:`, error)
      showAppMessage(error.response?.data?.msg || `${label}失败`, 'error')
      return false
    }
  }

  const cancelTask = (task) =>
    runAction('取消', () => axios.post(`${API_BASE}/resource-search/download-tasks/${task.id}/cancel`))

  const retryTask = (task) =>
    runAction('重试', () => axios.post(`${API_BASE}/resource-search/download-tasks/${task.id}/retry`))

  const deleteTask = (task) =>
    runAction('删除', () => axios.delete(`${API_BASE}/resource-search/download-tasks/${task.id}`))

  const openBinding = async (taskId) => {
    bindingLoading.value = true
    bindingDialog.value = true
    currentBinding.value = null
    try {
      const res = await axios.get(`${API_BASE}/resource-search/download-tasks/${taskId}/binding`)
      currentBinding.value = res.data?.data || null
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
