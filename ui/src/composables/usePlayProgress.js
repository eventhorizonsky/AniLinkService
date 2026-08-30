// 播放进度相关逻辑：进度保存（含阈值判断）、进度恢复、定时保存。
// 此前集中在 Player.vue 的"上帝组件"中，统一收敛到这里。
import { savePlayProgress as postPlayProgress, getPlayResume } from '../api/playHistory'

/**
 * 播放进度逻辑。
 * @param {Object} options
 * @param {import('vue').ShallowRef} options.art 播放器实例引用
 * @param {import('vue').Ref} options.token 登录令牌
 * @param {() => string} options.getVideoId
 * @param {() => string} options.getAnimeId
 * @param {() => string} options.getEpisodeId
 * @param {() => string|null} options.getEpisodeTitle 当前资源的真实标题
 * @param {() => string|null} options.getAnimeTitle
 * @param {() => void} options.onProgressComplete 进度达到 80% 时触发（Bangumi 同步）
 */
export function usePlayProgress({ art, token, getVideoId, getAnimeId, getEpisodeId, getEpisodeTitle, getAnimeTitle, onProgressComplete }) {
  let progressSaveTimer = null

  /**
   * 保存播放进度到后端
   */
  const savePlayProgress = async () => {
    if (!token.value || !art.value) {
      return
    }

    try {
      const currentTime = Math.floor(art.value.currentTime || 0)
      const duration = Math.floor(art.value.duration || 0)

      if (!getVideoId() || !getAnimeId() || duration === 0) {
        return
      }

      // 如果播放时间小于5秒或进度小于5%，则不保存
      if (currentTime < 5 || currentTime / duration < 0.05) {
        return
      }

      const isCompleted = currentTime / duration >= 0.8 // 播放超过80%认为已完成
      if (isCompleted) onProgressComplete()    // 达到80%即时同步

      await postPlayProgress({
        videoId: getVideoId(),
        videoName: getEpisodeTitle() || `第 ${getEpisodeId() || ''} 话`,
        animeId: getAnimeId(),
        episodeId: String(getEpisodeId() || ''),
        animeTitle: getAnimeTitle() || '未知',
        progressSeconds: currentTime,
        durationSeconds: duration,
        isCompleted: isCompleted
      })
    } catch (error) {
      console.error('保存播放进度失败:', error)
    }
  }

  /**
   * 加载播放进度
   */
  const loadPlayProgress = async () => {
    if (!token.value || !getAnimeId()) {
      return null
    }

    try {
      const body = await getPlayResume(getAnimeId())
      if (body.code === 200 && body.data) {
        // 仅在当前播放视频与历史视频一致时恢复秒数，避免跨分集误跳进度。
        if (String(body.data.videoId || '') !== String(getVideoId() || '')) {
          return null
        }
        return body.data.progressSeconds || 0
      }
    } catch (error) {
      console.error('加载播放进度失败:', error)
    }
    return null
  }

  /**
   * 开始定时保存播放进度
   */
  const startProgressSaveTimer = () => {
    stopProgressSaveTimer()
    // 每30秒保存一次
    progressSaveTimer = setInterval(() => {
      savePlayProgress()
    }, 30000)
  }

  /**
   * 停止定时保存播放进度
   */
  const stopProgressSaveTimer = () => {
    if (progressSaveTimer) {
      clearInterval(progressSaveTimer)
      progressSaveTimer = null
    }
  }

  return {
    savePlayProgress,
    loadPlayProgress,
    startProgressSaveTimer,
    stopProgressSaveTimer,
  }
}
