import { ref } from 'vue'
import { getAnimeRawJson, getAnimeEpisodes } from '../api/anime'

const EPISODES_PAGE_SIZE = 9999

/**
 * 番剧数据加载。
 * 此前 Player.vue（animeFetchSeq）与 AnimeDetail.vue（fetchSeq）中的 fetchAnimeData
 * 几乎逐字重复，统一收敛到这里，内部保留请求序号竞态守卫。
 *
 * 注意：不在本 composable 内调用 checkFollowStatus（两个页面传入方式不同），
 * 由调用方通过 options.onDataLoaded(animeId) 注入。
 *
 * @param {Object} options
 * @param {(() => string|number) | import('vue').Ref} options.getAnimeId 返回当前番剧 id 的回调（或 ref）
 * @param {() => Promise} [options.fetchAnime] 自定义番剧拉取函数（如 bgmMode 用 subjectId），缺省用 getAnimeRawJson(getAnimeId())
 * @param {(animeId) => string|number|void} [options.onDataLoaded] 番剧数据就绪后的回调，可返回解析后的番剧 id（如 bgmMode 从响应提取）
 * @param {() => void} [options.onAfterFetch] 拉取结束（成功或失败）且未被更新的请求覆盖时触发
 * @param {(err) => void} [options.onError] 拉取失败且未被更新的请求覆盖时触发
 * @param {boolean} [options.initialLoading=false] loading 初始值（AnimeDetail 骨架屏需要 true）
 * @param {string} [options.emptyDataMessage='Unexpected response structure'] 响应结构无效时的错误文案
 */
export function useAnimeData({
  getAnimeId,
  fetchAnime,
  onDataLoaded,
  onAfterFetch,
  onError,
  initialLoading = false,
  emptyDataMessage = 'Unexpected response structure',
}) {
  const animeData = ref(null)
  const existingEpisodes = ref([])
  const loading = ref(initialLoading)
  const error = ref(null)

  const fetchSeq = ref(0)

  const resolveAnimeId = () => {
    if (typeof getAnimeId === 'function') return getAnimeId()
    return getAnimeId?.value
  }

  const fetchAnimeData = async () => {
    const seq = ++fetchSeq.value
    try {
      const animeId = resolveAnimeId()
      if (!animeId && !fetchAnime) return

      loading.value = true
      error.value = null

      const [animeResp, episodesResp] = await Promise.allSettled([
        fetchAnime ? fetchAnime() : getAnimeRawJson(animeId),
        getAnimeEpisodes(animeId, { page: 1, pageSize: EPISODES_PAGE_SIZE }),
      ])

      if (seq !== fetchSeq.value) return

      let resolvedAnimeId = animeId
      if (animeResp.status === 'fulfilled' && animeResp.value.code === 200 && animeResp.value.data?.bangumi) {
        animeData.value = animeResp.value.data.bangumi
        const resolved = onDataLoaded?.(animeId)
        if (typeof resolved === 'string' || typeof resolved === 'number') {
          resolvedAnimeId = resolved
        }
      } else {
        animeData.value = null
      }

      // 番剧 id 需从响应解析时（如 bgmMode），用解析后的 id 重新拉取分集
      if (String(resolvedAnimeId) !== String(animeId)) {
        if (seq !== fetchSeq.value) return
        try {
          const reResp = await getAnimeEpisodes(resolvedAnimeId, { page: 1, pageSize: EPISODES_PAGE_SIZE })
          if (seq !== fetchSeq.value) return
          if (reResp.code === 200 && Array.isArray(reResp.data?.content)) {
            existingEpisodes.value = reResp.data.content
          } else {
            existingEpisodes.value = []
          }
        } catch {
          if (seq !== fetchSeq.value) return
          existingEpisodes.value = []
        }
      } else if (episodesResp.status === 'fulfilled' && episodesResp.value.code === 200 && Array.isArray(episodesResp.value.data?.content)) {
        existingEpisodes.value = episodesResp.value.data.content
      } else {
        existingEpisodes.value = []
      }

      if (seq !== fetchSeq.value) return
      if (animeResp.status === 'rejected') {
        throw animeResp.reason
      }
      if (!animeData.value) {
        throw new Error(emptyDataMessage)
      }
    } catch (err) {
      if (seq !== fetchSeq.value) return
      animeData.value = null
      existingEpisodes.value = []
      error.value = err?.message || '获取番剧数据失败'
      onError?.(err)
    } finally {
      if (seq === fetchSeq.value) {
        loading.value = false
        onAfterFetch?.()
      }
    }
  }

  return { animeData, existingEpisodes, loading, error, fetchAnimeData, fetchSeq }
}
