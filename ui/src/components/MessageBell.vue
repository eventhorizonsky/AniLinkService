<template>
  <div class="message-wrapper">
    <button
      @click="handleMessageBtnClick"
      class="message-btn"
      :title="`消息${unreadCount > 0 ? ' (' + unreadCount + ')' : ''}`"
    >
      <i class="mdi mdi-bell-outline"></i>
      <span v-if="unreadCount > 0" class="unread-dot"></span>
    </button>

    <div v-if="modelValue" class="message-dropdown" @click.stop @wheel.stop>
      <div class="message-dropdown-header">
        <span>消息通知</span>
        <div class="message-header-actions">
          <span v-if="unreadCount > 0" class="unread-count-badge">{{ unreadCount }}</span>
          <button
            v-if="unreadCount > 0"
            class="mark-all-read-btn"
            :disabled="markingAllRead"
            @click="handleMarkAllAsRead"
          >
            {{ markingAllRead ? '处理中...' : '一键已读' }}
          </button>
        </div>
      </div>

      <div class="message-list">
        <div v-if="loadingMessages" class="message-loading">
          <i class="mdi mdi-loading mdi-spin"></i>
          <span>加载中...</span>
        </div>

        <template v-else-if="recentMessages.length > 0">
          <div
            v-for="msg in recentMessages"
            :key="msg.id"
            class="message-item"
            :class="{ 'unread': !msg.isRead }"
            @click="handleMessageClick(msg)"
          >
            <div class="message-item-indicator">
              <span v-if="!msg.isRead" class="unread-indicator"></span>
            </div>
            <div class="message-item-content">
              <div class="message-item-title">{{ msg.title }}</div>
              <div class="message-item-text">{{ msg.content }}</div>
              <div class="message-item-time">{{ formatRelativeTime(msg.createdAt) }}</div>
            </div>
          </div>
        </template>

        <div v-else class="message-empty">
          <i class="mdi mdi-bell-off-outline"></i>
          <span>暂无消息</span>
        </div>
      </div>

      <div class="message-dropdown-footer">
        <button @click="goToMessages" class="view-all-messages-btn">
          查看全部消息
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatRelativeTime } from '../utils/format'
import { useAuth } from '../composables/useAuth'
import { useIsMobile } from '../composables/useIsMobile'
import { useMessageActions } from '../composables/useMessageActions'
import { usePolling } from '../composables/usePolling'
import { getUnreadCount, getMessages } from '../api/messages'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  unreadCount: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const { isLoggedIn } = useAuth()
const { isMobile } = useIsMobile(1280, { useInnerWidth: true })
const { openMessage, markAllRead } = useMessageActions()

const unreadCount = ref(props.unreadCount || 0)
watch(
  () => props.unreadCount,
  (val) => {
    unreadCount.value = val || 0
  }
)

const recentMessages = ref([])
const loadingMessages = ref(false)
const markingAllRead = ref(false)

const fetchUnreadCount = async () => {
  if (!isLoggedIn.value) {
    unreadCount.value = 0
    return
  }

  try {
    const res = await getUnreadCount()
    // 后端 success 统一返回 code=200，兼容历史 code=0 的情况
    if (res?.code === 200 || res?.code === 0) {
      unreadCount.value = Number(res.data?.unreadCount || 0)
      return
    }
    unreadCount.value = 0
  } catch (error) {
    console.error('获取未读消息数失败:', error)
    unreadCount.value = 0
  }
}

const fetchRecentMessages = async () => {
  if (!isLoggedIn.value) {
    recentMessages.value = []
    return
  }

  loadingMessages.value = true
  try {
    const res = await getMessages({
      page: 1,
      pageSize: 5
    })
    if (res?.code === 200) {
      recentMessages.value = res.data.content || []
    }
  } catch (error) {
    console.error('获取最近消息失败:', error)
  } finally {
    loadingMessages.value = false
  }
}

const closeMenu = () => {
  emit('update:modelValue', false)
}

const goToMessages = () => {
  closeMenu()
  router.push('/profile/messages')
}

const toggleMessageMenu = () => {
  emit('update:modelValue', !props.modelValue)
}

const handleMessageBtnClick = () => {
  if (isMobile.value) {
    // 移动端直接跳转到消息列表
    goToMessages()
  } else {
    // PC端打开下拉菜单
    toggleMessageMenu()
  }
}

const handleMessageClick = async (message) => {
  closeMenu()

  const wasUnread = !message.isRead
  await openMessage(message)

  if (wasUnread) {
    fetchUnreadCount()
    fetchRecentMessages()
  }
}

const handleMarkAllAsRead = async () => {
  if (markingAllRead.value || unreadCount.value <= 0) {
    return
  }

  markingAllRead.value = true
  try {
    const ok = await markAllRead()
    if (ok) {
      fetchUnreadCount()
      fetchRecentMessages()
    }
  } finally {
    markingAllRead.value = false
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      fetchRecentMessages()
    }
  }
)

// 每 3 分钟轮询未读消息数（登录后由 MainLayout 挂载本组件）
const { start } = usePolling(() => fetchUnreadCount(), {
  interval: 180000,
  when: () => isLoggedIn.value
})

start()
</script>

<style scoped>
.message-wrapper {
  position: relative;
}

.message-btn {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 999px;
  background: var(--al-bg-cream);
  color: var(--al-text-secondary);
  font-size: 17px;
  cursor: pointer;
  transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 1px solid transparent;
}

.message-btn:hover {
  background: rgba(196, 93, 43, 0.08);
  color: var(--al-accent);
  border-color: rgba(196, 93, 43, 0.2);
}

.unread-dot {
  position: absolute;
  top: 9px;
  right: 9px;
  width: 8px;
  height: 8px;
  background: var(--al-danger-bright);
  border-radius: 50%;
  border: 2px solid #fff;
}

.message-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: var(--al-bg);
  border-radius: 12px;
  border: 1px solid var(--anime-border-light);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  width: 360px;
  max-height: 500px;
  overflow: hidden;
  overscroll-behavior: contain;
  animation: slideDown 0.2s ease;
  z-index: 1001;
}

.message-dropdown-header {
  padding: 16px;
  border-bottom: 1px solid var(--anime-border-light);
  background: var(--anime-bg-cream);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--anime-primary-dark);
}

.unread-count-badge {
  background: var(--al-danger-deep);
  color: var(--al-text-on-accent);
  font-size: 0.75rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
}

.message-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mark-all-read-btn {
  border: 1px solid var(--anime-border-light);
  background: var(--al-bg);
  color: var(--anime-accent-red);
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mark-all-read-btn:hover:not(:disabled) {
  background: var(--anime-bg-beige);
  border-color: var(--anime-accent-brown);
}

.mark-all-read-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.message-list {
  max-height: 380px;
  overflow-y: auto;
}

.message-loading {
  padding: 40px 20px;
  text-align: center;
  color: var(--anime-text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-loading i {
  font-size: 1.5rem;
}

.message-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--al-border-neutral);
  cursor: pointer;
  transition: background 0.2s ease;
  display: flex;
  gap: 10px;
}

.message-item:hover {
  background: var(--anime-bg-cream);
}

.message-item.unread {
  background: rgba(196, 93, 43, 0.05);
}

.message-item.unread:hover {
  background: rgba(196, 93, 43, 0.1);
}

.message-item:last-child {
  border-bottom: none;
}

.message-item-indicator {
  flex-shrink: 0;
  padding-top: 4px;
}

.unread-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: var(--al-danger-deep);
  border-radius: 50%;
}

.message-item-content {
  flex: 1;
  min-width: 0;
}

.message-item-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--anime-text-main);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item-text {
  font-size: 0.85rem;
  color: var(--anime-text-secondary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-clamp: 2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
}

.message-item-time {
  font-size: 0.75rem;
  color: var(--anime-text-secondary);
  opacity: 0.7;
}

.message-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--anime-text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-empty i {
  font-size: 2.5rem;
  opacity: 0.5;
}

.message-dropdown-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--anime-border-light);
  background: var(--al-bg);
}

.view-all-messages-btn {
  width: 100%;
  padding: 8px;
  background: transparent;
  border: 1px solid var(--anime-border-light);
  border-radius: 8px;
  color: var(--anime-accent-red);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.view-all-messages-btn:hover {
  background: var(--anime-bg-cream);
  border-color: var(--anime-accent-brown);
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1280px) {
  .message-dropdown {
    width: 340px;
  }
}

@media (max-width: 600px) {
  .message-dropdown {
    width: calc(100vw - 20px);
    max-width: 340px;
    right: -10px;
  }
}

@media (max-width: 480px) {
  .message-btn {
    width: 36px;
    height: 36px;
    font-size: 15px;
  }
  .message-dropdown {
    width: calc(100vw - 16px);
    max-width: 340px;
    right: -8px;
  }
}
</style>
