<template>
  <section class="last-watch-card" aria-label="上次看到">
    <div class="last-watch-header">
      <h3 class="last-watch-title">
        <i class="mdi mdi-history"></i>
        上次看到
      </h3>
    </div>

    <div v-if="loading" class="last-watch-skeleton">
      <div class="sk-line sk-line--wide"></div>
      <div class="sk-line"></div>
      <div class="sk-actions">
        <div class="sk-btn"></div>
        <div class="sk-btn"></div>
      </div>
    </div>

    <template v-else-if="progress">
      <p class="last-watch-episode">{{ episodeLine }}</p>
      <p class="last-watch-meta">进度：{{ progressText }}</p>
      <p v-if="lastPlayText" class="last-watch-meta">最近播放：{{ lastPlayText }}</p>
      <div class="last-watch-actions">
        <button
          type="button"
          class="last-watch-btn last-watch-btn--primary"
          :disabled="continueDisabled"
          @click="$emit('continue')"
        >
          继续播放
        </button>
        <button
          type="button"
          class="last-watch-btn last-watch-btn--secondary"
          :disabled="nextDisabled"
          @click="$emit('next')"
        >
          看下一集
        </button>
      </div>
    </template>
  </section>
</template>

<script setup>
defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
  progress: {
    type: Object,
    default: null,
  },
  episodeLine: {
    type: String,
    default: '',
  },
  progressText: {
    type: String,
    default: '',
  },
  lastPlayText: {
    type: String,
    default: '',
  },
  continueDisabled: {
    type: Boolean,
    default: false,
  },
  nextDisabled: {
    type: Boolean,
    default: true,
  },
})

defineEmits(['continue', 'next'])
</script>

<style scoped>
.last-watch-card {
  background: linear-gradient(135deg, var(--al-bg-watch) 0%, var(--al-bg-watch-2) 100%);
  border: 1px solid var(--al-border-warm);
  border-radius: 16px;
  padding: 16px 18px;
  margin-bottom: 18px;
}

.last-watch-header {
  margin-bottom: 10px;
}

.last-watch-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--al-text-brown-19);
  display: flex;
  align-items: center;
  gap: 8px;
}

.last-watch-title .mdi {
  color: var(--al-accent);
  font-size: 1.25rem;
}

.last-watch-episode {
  margin: 0 0 6px;
  font-size: 0.98rem;
  font-weight: 600;
  color: var(--al-text-brown-20);
  line-height: 1.45;
}

.last-watch-meta {
  margin: 0 0 4px;
  font-size: 0.88rem;
  color: var(--al-text-secondary);
}

.last-watch-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.last-watch-btn {
  border-radius: 999px;
  padding: 9px 18px;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.last-watch-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.last-watch-btn:not(:disabled):active {
  transform: scale(0.98);
}

.last-watch-btn--primary {
  background: var(--al-accent);
  color: var(--al-text-on-accent);
}

.last-watch-btn--primary:not(:disabled):hover {
  background: var(--al-accent-strong-3);
}

.last-watch-btn--secondary {
  background: var(--al-bg);
  color: var(--al-text-brown-18);
  border: 1px solid var(--al-border-soft-6);
}

.last-watch-btn--secondary:not(:disabled):hover {
  background: var(--al-bg-hover-soft);
}

.last-watch-skeleton .sk-line {
  height: 12px;
  background: var(--al-bg-beige-9);
  border-radius: 6px;
  margin-bottom: 10px;
  animation: last-watch-pulse 1.2s ease-in-out infinite;
}

.last-watch-skeleton .sk-line--wide {
  width: 72%;
}

.last-watch-skeleton .sk-line:not(.sk-line--wide) {
  width: 48%;
}

.sk-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.sk-btn {
  width: 108px;
  height: 36px;
  border-radius: 999px;
  background: var(--al-bg-beige-9);
  animation: last-watch-pulse 1.2s ease-in-out infinite;
}

@keyframes last-watch-pulse {
  0%,
  100% {
    opacity: 0.65;
  }
  50% {
    opacity: 1;
  }
}
</style>
