import { useRouter } from 'vue-router'
import { showAppMessage } from '../utils/ui-feedback'
import { markMessageRead, markAllMessagesRead } from '../api/messages'

/**
 * 消息打开/一键已读逻辑。
 * 此前 MainLayout.vue（下拉消息）与 profile/Messages.vue 各自实现了一份，统一收敛到这里。
 */
export function useMessageActions() {
  const router = useRouter()

  const openMessage = async (msg) => {
    if (!msg.isRead) {
      try {
        await markMessageRead(msg.id)
        msg.isRead = true
      } catch (e) {
        console.error('标记消息已读失败:', e)
      }
    }

    // 剧集更新消息且带视频ID时，在新窗口打开播放页
    if (msg.type === 'episode_update' && msg.videoId) {
      const routeData = router.resolve({
        name: 'Player',
        params: { videoId: String(msg.videoId) },
        query: {
          animeId: msg.animeId ? String(msg.animeId) : undefined,
          episodeId: msg.episodeId ? String(msg.episodeId) : undefined
        }
      })
      window.open(routeData.href, '_blank')
      return
    }

    // 否则如果带有 animeId，跳转到番剧详情页
    if (msg.animeId) {
      router.push(`/anime/${msg.animeId}`)
    }
  }

  const markAllRead = async () => {
    try {
      const res = await markAllMessagesRead()
      if (res?.code === 200 || res?.code === 0) {
        showAppMessage('已全部标记为已读', 'success')
        return true
      }
      showAppMessage(res?.msg || '一键已读失败', 'error')
      return false
    } catch (e) {
      console.error('全部标记已读失败:', e)
      showAppMessage('一键已读失败，请稍后重试', 'error')
      return false
    }
  }

  return { openMessage, markAllRead }
}
