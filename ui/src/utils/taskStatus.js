// 下载任务状态常量。
// 此前 STATUS_META（DownloadTaskTable.vue）与 taskStatusMap（TaskBindingDialog.vue）
// 逐字重复，统一收敛到这里。

export const TASK_STATUS_META = {
  PENDING: { label: '等待中', color: 'grey' },
  RUNNING: { label: '下载中', color: 'primary' },
  SEEDING: { label: '做种中', color: 'purple' },
  MOVING: { label: '迁移中', color: 'info' },
  SCANNING: { label: '扫描中', color: 'teal' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'warning' },
  FAILED: { label: '失败', color: 'error' },
  STALLED: { label: '停滞', color: 'orange' }
}

export const ACTIVE_TASK_STATUSES = ['PENDING', 'RUNNING', 'SEEDING', 'MOVING', 'SCANNING']
export const RETRYABLE_TASK_STATUSES = ['FAILED', 'CANCELLED', 'STALLED']
export const DELETABLE_TASK_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED', 'STALLED']
export const BINDING_VIEWABLE_STATUSES = ['COMPLETED', 'SEEDING']

export const formatTaskStatus = (status) => TASK_STATUS_META[status]?.label || status || '-'
export const taskStatusColor = (status) => TASK_STATUS_META[status]?.color || 'grey'
export const canCancelTask = (status) => ACTIVE_TASK_STATUSES.includes(status)
export const canRetryTask = (status) => RETRYABLE_TASK_STATUSES.includes(status)
export const canDeleteTask = (status) => DELETABLE_TASK_STATUSES.includes(status)
export const canViewTaskBinding = (status) => BINDING_VIEWABLE_STATUSES.includes(status)
