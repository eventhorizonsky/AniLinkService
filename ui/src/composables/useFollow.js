import { ref } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../utils/ui-feedback'
import { API_BASE } from '../utils/constants'
import { followStatusLabel } from '../utils/followStatus'
import { useAuth } from './useAuth'

/**
 * 追番状态管理。
 * 此前 checkFollowStatus/setFollowStatus/toggleFollow/upgradeFollowWishToWatching
 * 在 Player.vue 与 AnimeDetail.vue 中几乎逐字重复，统一收敛到这里。
 */
export function useFollow() {
  const { token } = useAuth()

  const isFollowing = ref(false)
  const followStatus = ref('watching')
  const followLoading = ref(false)

  const checkFollowStatus = async (animeId) => {
    if (!token.value || !animeId) {
      isFollowing.value = false
      followStatus.value = 'wish'
      return
    }

    try {
      const response = await axios.get(`${API_BASE}/follows/${animeId}`)
      if (response.data?.code === 200 && response.data.data) {
        isFollowing.value = true
        followStatus.value = response.data.data.status || 'watching'
      } else {
        isFollowing.value = false
        followStatus.value = 'wish'
      }
    } catch (e) {
      console.error('检查追番状态失败:', e)
      isFollowing.value = false
      followStatus.value = 'wish'
    }
  }

  const setFollowStatus = async (animeId, status, animeData) => {
    if (!token.value) {
      showAppMessage('请先登录', 'warning')
      return false
    }
    if (!animeId || !animeData) {
      showAppMessage('请等待数据加载完成', 'warning')
      return false
    }

    followLoading.value = true
    try {
      if (isFollowing.value) {
        await axios.put(`${API_BASE}/follows/${animeId}/status`, null, { params: { status } })
      } else {
        await axios.post(`${API_BASE}/follows`, {
          animeId: Number(animeId),
          animeTitle: animeData.titles?.[0]?.title || '未知',
          imageUrl: animeData.imageUrl || '',
          status
        })
      }
      isFollowing.value = true
      followStatus.value = status
      showAppMessage(`已标记为「${followStatusLabel(status)}」`, 'success')
      return true
    } catch (error) {
      console.error('更新追番状态失败:', error)
      showAppMessage('操作失败，请重试', 'error')
      return false
    } finally {
      followLoading.value = false
    }
  }

  const toggleFollow = async (animeId, animeData) => {
    if (!token.value) {
      showAppMessage('请先登录', 'warning')
      return
    }
    if (!animeId || !animeData) {
      showAppMessage('番剧数据未就绪，请稍后重试', 'warning')
      return
    }

    followLoading.value = true
    try {
      if (isFollowing.value) {
        await axios.delete(`${API_BASE}/follows/${animeId}`)
        isFollowing.value = false
        showAppMessage('已取消追番', 'success')
      } else {
        await axios.post(`${API_BASE}/follows`, {
          animeId: Number(animeId),
          animeTitle: animeData?.titles?.[0]?.title || '未知',
          imageUrl: animeData?.imageUrl || ''
        })
        isFollowing.value = true
        showAppMessage('追番成功', 'success')
      }
    } catch (e) {
      console.error('切换追番状态失败:', e)
      showAppMessage('操作失败，请重试', 'error')
    } finally {
      followLoading.value = false
    }
  }

  const upgradeFollowWishToWatching = async (animeId) => {
    if (!token.value || !isFollowing.value || followStatus.value !== 'wish' || !animeId) return
    try {
      await axios.put(`${API_BASE}/follows/${animeId}/status`, null, { params: { status: 'watching' } })
      followStatus.value = 'watching'
    } catch (e) {
      console.debug('追番自动升级失败:', e)
    }
  }

  return {
    isFollowing,
    followStatus,
    followLoading,
    checkFollowStatus,
    setFollowStatus,
    toggleFollow,
    upgradeFollowWishToWatching
  }
}
