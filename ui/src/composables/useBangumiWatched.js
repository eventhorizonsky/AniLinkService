// 剧集"看过"标记：详情页 / 播放页共享。
// 绑定 Bangumi 账号后，从后端拉取该番剧已标记"看过"的本地集数集合（Set<string>），
// 用于在选集列表中打上"看过"标记。未绑定 / 未关联条目 / 接口异常时静默降级，不影响页面。
//
// 本地乐观标记（markWatchedLocally）与后端数据合并展示：
// 播放页看到 80% 触发 Bangumi 同步时立即本地标记，避免等待后端异步写入，
// 刷新后端数据也不会把刚标记的剧集抹掉。

import { ref, computed, watch } from 'vue'
import { getAnimeWatchedEpisodes } from '../api/bangumi'

/**
 * @param {import('vue').Ref<string|null>} animeIdRef 本地番剧 ID（弹弹 animeId）
 * @param {import('vue').Ref<boolean>} enabledRef 是否启用（已登录且已绑定 Bangumi 时）
 */
export function useBangumiWatched(animeIdRef, enabledRef) {
  const serverWatched = ref(new Set())
  const optimisticWatched = ref(new Set())
  const loading = ref(false)
  const available = ref(false)
  let seq = 0

  /** 服务端数据 + 本地乐观标记 的并集，供选集组件展示 */
  const watchedEpisodeNumbers = computed(() => {
    const merged = new Set(serverWatched.value)
    for (const num of optimisticWatched.value) {
      merged.add(num)
    }
    return merged
  })

  const fetchWatched = async () => {
    const animeId = animeIdRef?.value
    if (!animeId) {
      seq++
      serverWatched.value = new Set()
      available.value = false
      loading.value = false
      return
    }
    const curSeq = ++seq
    loading.value = true
    try {
      const res = await getAnimeWatchedEpisodes(animeId)
      if (curSeq !== seq) return
      const data = res?.data
      if (res?.code === 200 && data?.available) {
        available.value = true
        serverWatched.value = new Set((data.watched || []).map(String))
      } else {
        available.value = false
        serverWatched.value = new Set()
      }
    } catch (e) {
      if (curSeq !== seq) return
      available.value = false
      serverWatched.value = new Set()
    } finally {
      if (curSeq === seq) loading.value = false
    }
  }

  /** 本地立即标记某集为"看过"（播放页看完自动同步时调用） */
  const markWatchedLocally = (episodeNumber) => {
    if (episodeNumber === undefined || episodeNumber === null) return
    const next = new Set(optimisticWatched.value)
    next.add(String(episodeNumber))
    optimisticWatched.value = next
  }

  watch(
    [animeIdRef, enabledRef],
    ([id, enabled]) => {
      if (enabled && id) {
        fetchWatched()
      } else {
        seq++
        serverWatched.value = new Set()
        optimisticWatched.value = new Set()
        available.value = false
        loading.value = false
      }
    },
    { immediate: true }
  )

  return {
    watchedEpisodeNumbers,
    loading,
    available,
    refresh: fetchWatched,
    markWatchedLocally,
  }
}
