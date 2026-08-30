import { onBeforeUnmount } from 'vue'

/**
 * 通用轮询。
 * 此前 MediaLibrary.vue / QueueProgress.vue / ScheduledTasks.vue / MainLayout.vue
 * 各自手写 setInterval + onBeforeUnmount 清理，统一收敛到这里。
 *
 * @param {() => Promise<any> | any} fn 轮询任务（异步函数返回的 Promise 若被拒绝会被 catch 忽略）
 * @param {Object} [options]
 * @param {number} [options.interval=5000] 轮询间隔（毫秒）
 * @param {() => boolean} [options.when] 每次轮询前判断是否继续的条件函数
 * @param {boolean} [options.immediate=true] 挂载后是否立即执行一次
 * @returns {{ start: () => void, stop: () => void, isRunning: () => boolean }}
 */
export function usePolling(fn, { interval = 5000, when = () => true, immediate = true } = {}) {
  let timer = null

  const stop = () => {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  const start = () => {
    stop()
    const run = () => {
      if (!when()) {
        stop()
        return
      }
      try {
        const result = fn()
        if (result && typeof result.catch === 'function') {
          result.catch((e) => console.error('轮询任务失败:', e))
        }
      } catch (e) {
        console.error('轮询任务失败:', e)
      }
    }
    if (immediate) run()
    timer = setInterval(run, interval)
  }

  onBeforeUnmount(stop)

  return { start, stop, isRunning: () => timer !== null }
}
