import { ref, computed } from 'vue'

/**
 * 服务端分页（页号从 1 开始，请求时转换为后端 0-based page）。
 * 此前 UserManagement/AdminDanmaku/VideoFileManager/AnimeLibrary/SubtitleLibrary
 * 五个页面各自重复实现 page/pageSize/total/pageCount 与 onTableOptionsChange，统一收敛到这里。
 *
 * @param {Object} options
 * @param {number} [options.pageSize=20] 每页条数
 * @param {(query: { page: number, pageSize: number }) => Promise<{ totalElements: number, totalPages?: number }>} options.fetchFn
 *   拉取函数，应返回后端响应（含 data.totalElements 等）。
 * @param {() => boolean} [options.enabled] 是否启用（例如未选中范围时跳过）
 */
export function useServerPagination({ pageSize: initialSize = 20, fetchFn, enabled = () => true } = {}) {
  const page = ref(1)
  const pageSize = ref(initialSize)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)

  const pageCount = computed(() => totalPages.value || Math.max(1, Math.ceil(totalElements.value / pageSize.value)))

  const onOptionsChange = (options) => {
    page.value = options?.page ?? 1
    pageSize.value = options?.itemsPerPage ?? initialSize
  }

  const fetchPage = async () => {
    if (!enabled()) return
    loading.value = true
    try {
      const res = await fetchFn({ page: page.value - 1, pageSize: pageSize.value })
      if (res?.code === 200 || res?.code === 0) {
        totalElements.value = Number(res.data?.totalElements ?? res.data?.length ?? 0)
        totalPages.value = Number(res.data?.totalPages ?? 0)
      }
      return res
    } finally {
      loading.value = false
    }
  }

  return { page, pageSize, totalElements, totalPages, pageCount, loading, onOptionsChange, fetchPage }
}
