import { ref, onMounted, onBeforeUnmount } from 'vue'

/**
 * 移动端视口判断。
 * 此前 DownloadCenter/ResourceRssSubscription/ResourceSearchTab/ScheduledTasks/
 * DownloadTaskTable/MainLayout/Admin 等各自复制了 checkViewport 实现，统一收敛到这里。
 *
 * @param {number} breakpoint 断点宽度（默认 768）
 * @param {Object} [options]
 * @param {boolean} [options.useInnerWidth=false] 使用 window.innerWidth 比较（如布局侧栏 1280 的判断）
 */
export function useIsMobile(breakpoint = 768, { useInnerWidth = false } = {}) {
  const isMobile = ref(false)

  const checkViewport = () => {
    if (typeof window === 'undefined') {
      isMobile.value = false
      return
    }
    if (useInnerWidth) {
      isMobile.value = window.innerWidth <= breakpoint
    } else if (typeof window.matchMedia === 'function') {
      isMobile.value = window.matchMedia(`(max-width: ${breakpoint}px)`).matches
    } else {
      isMobile.value = false
    }
  }

  checkViewport()

  onMounted(() => {
    checkViewport()
    window.addEventListener('resize', checkViewport)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', checkViewport)
  })

  return { isMobile, checkViewport }
}
