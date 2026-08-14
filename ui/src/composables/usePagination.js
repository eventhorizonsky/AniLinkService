import { ref, computed } from 'vue'

/**
 * 前端分页逻辑（page/totalPages/pages/changePage）。
 * 此前 Danmaku/Follows/History/Messages 四个页面逐字复制了同一套实现。
 *
 * @param {Object} options
 * @param {number} [options.pageSize=20] 每页条数
 * @param {() => number} [options.getTotal] 返回当前总条数的函数（如 () => total.value）
 * @param {(page: number) => void} [options.onPageChange] 页码变化后的回调（如重新拉取数据）
 */
export function usePagination({ pageSize: initialSize = 20, getTotal = () => 0, onPageChange } = {}) {
  const page = ref(1)
  const pageSize = ref(initialSize)

  const totalPages = computed(() => Math.max(1, Math.ceil(getTotal() / pageSize.value)))

  const pages = computed(() => {
    const t = totalPages.value
    const cur = page.value
    const start = Math.max(1, Math.min(cur - 2, t - 4))
    const arr = []
    for (let i = start; i <= Math.min(t, start + 4); i++) arr.push(i)
    return arr
  })

  const changePage = (p) => {
    if (p < 1 || p > totalPages.value || p === page.value) return
    page.value = p
    if (typeof onPageChange === 'function') onPageChange(p)
  }

  return { page, pageSize, totalPages, pages, changePage }
}
