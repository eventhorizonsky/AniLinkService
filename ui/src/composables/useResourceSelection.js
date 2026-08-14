import { ref } from 'vue'
import { truncateText, isFuture, getEpisodeResources } from '../utils/episodes'

/**
 * 资源选择对话框状态管理（多资源番剧点击分集时的选择流程）。
 * 此前 Player.vue 与 AnimeDetail.vue 中几乎逐字重复，统一收敛到这里。
 *
 * @param {Object} options
 * @param {import('vue-router').Router} options.router
 * @param {() => string} options.getAnimeId 返回当前 animeId 的回调
 * @param {() => Array} options.getExistingEpisodes 返回本地媒体文件列表的回调
 * @param {number} [options.titleMaxLen=0] 弹窗标题最大长度（0 表示不截断）
 */
export function useResourceSelection({ router, getAnimeId, getExistingEpisodes, titleMaxLen = 0 }) {
  const showResourceDialog = ref(false)
  const selectedResources = ref([])
  const selectedEpisodeTitle = ref('')

  const goToPlayer = async (resource) => {
    showResourceDialog.value = false
    selectedResources.value = []

    const targetVideoId = String(resource.id)
    const targetEpisodeId = String(resource.episodeId ?? '')

    try {
      await router.push({
        name: 'Player',
        params: { videoId: targetVideoId },
        query: {
          animeId: String(getAnimeId()),
          episodeId: targetEpisodeId
        }
      })
    } catch (error) {
      console.warn('router.push 异常，继续执行播放刷新:', error)
    }
  }

  const closeResourceDialog = () => {
    showResourceDialog.value = false
    selectedResources.value = []
    selectedEpisodeTitle.value = ''
  }

  const selectResource = (resource) => {
    goToPlayer(resource)
  }

  /**
   * 播放剧集。
   * @param {object} ep 分集
   * @param {boolean} fromPlayerChrome 为 true 时表示来自播放器内选集/上下集：多资源时直接播第一条，不弹窗。
   */
  const playEpisode = (ep, fromPlayerChrome = false) => {
    if (isFuture(ep)) return
    const resources = getEpisodeResources(getExistingEpisodes(), ep.episodeId)
    if (resources.length === 0) return

    if (resources.length === 1 || fromPlayerChrome) {
      goToPlayer(resources[0])
      return
    }

    selectedResources.value = resources
    const rawTitle = ep.episodeTitle || `第${ep.episodeNumber}话`
    selectedEpisodeTitle.value = titleMaxLen > 0 ? truncateText(rawTitle, titleMaxLen) : rawTitle
    showResourceDialog.value = true
  }

  return {
    showResourceDialog,
    selectedResources,
    selectedEpisodeTitle,
    goToPlayer,
    closeResourceDialog,
    selectResource,
    playEpisode
  }
}
