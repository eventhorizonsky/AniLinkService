const APP_NOTIFY_EVENT = 'anilink:notify'
const APP_SESSION_EXPIRED_EVENT = 'anilink:session-expired'
const APP_CONFIRM_EVENT = 'anilink:confirm'

export function showAppMessage(message, color = 'info') {
  if (!message) {
    return
  }
  window.dispatchEvent(
    new CustomEvent(APP_NOTIFY_EVENT, {
      detail: { message, color }
    })
  )
}

export function showSessionExpiredDialog(message = '登录状态已过期，请重新登录。') {
  window.dispatchEvent(
    new CustomEvent(APP_SESSION_EXPIRED_EVENT, {
      detail: { message }
    })
  )
}

/**
 * 全局确认框。
 * 点确认 resolve(true)，点取消 resolve(false)；点附加操作按钮 resolve(该按钮的 value)。
 * @param {{title?: string, message?: string, confirmText?: string, cancelText?: string,
 *          color?: string, links?: Array<{text: string, href: string}>,
 *          actions?: Array<{text: string, value: string, color?: string}>}} options
 */
export function askAppConfirm({
  title = '请确认',
  message = '确认执行该操作吗？',
  confirmText = '确定',
  cancelText = '取消',
  color = 'primary',
  links = [],
  actions = []
} = {}) {
  return new Promise((resolve) => {
    window.dispatchEvent(
      new CustomEvent(APP_CONFIRM_EVENT, {
        detail: {
          title,
          message,
          confirmText,
          cancelText,
          color,
          links,
          actions,
          resolve
        }
      })
    )
  })
}

export const UiFeedbackEvents = {
  APP_NOTIFY_EVENT,
  APP_SESSION_EXPIRED_EVENT,
  APP_CONFIRM_EVENT
}
