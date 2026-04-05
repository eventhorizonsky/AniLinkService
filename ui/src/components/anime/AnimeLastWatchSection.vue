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
  background: linear-gradient(135deg, #fffaf6 0%, #fff5ec 100%);
  border: 1px solid #ead9c8;
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
  color: #3d2f26;
  display: flex;
  align-items: center;
  gap: 8px;
}

.last-watch-title .mdi {
  color: #c45d2b;
  font-size: 1.25rem;
}

.last-watch-episode {
  margin: 0 0 6px;
  font-size: 0.98rem;
  font-weight: 600;
  color: #2f2b28;
  line-height: 1.45;
}

.last-watch-meta {
  margin: 0 0 4px;
  font-size: 0.88rem;
  color: #6b5f55;
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
  background: #c45d2b;
  color: #fff;
}

.last-watch-btn--primary:not(:disabled):hover {
  background: #a84e24;
}

.last-watch-btn--secondary {
  background: #fff;
  color: #5c4032;
  border: 1px solid #d4c4b8;
}

.last-watch-btn--secondary:not(:disabled):hover {
  background: #fff8f2;
}

.last-watch-skeleton .sk-line {
  height: 12px;
  background: #ecdfd4;
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
  background: #ecdfd4;
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
