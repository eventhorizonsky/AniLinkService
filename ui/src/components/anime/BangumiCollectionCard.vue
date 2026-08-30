<template>
  <div class="bangumi-collection-card">
    <div class="bangumi-collection-header">
      <div>
        <h3>Bangumi 评分与评论</h3>
        <p>
          将评分与短评同步到
          <a :href="subjectUrl" target="_blank" rel="noopener noreferrer">{{ subjectUrl }}</a>
        </p>
      </div>
      <div class="bangumi-header-actions">
        <button
          v-if="!editMode && exists"
          class="bangumi-edit-btn"
          :disabled="loading || saving"
          @click="emit('update:editMode', true)"
        >编辑</button>
        <button
          v-if="exists"
          class="bangumi-refresh-btn"
          :disabled="loading || saving"
          @click="emit('refresh')"
        >刷新状态</button>
      </div>
    </div>

    <div v-if="loading" class="bangumi-collection-loading">
      <span class="bangumi-loading-spinner"></span>
      正在读取你的 Bangumi 评分与短评...
    </div>

    <!-- 未收藏且非编辑态：极简空状态 -->
    <div v-else-if="!exists && !editMode" class="bangumi-not-collected">
      <span class="bangumi-not-collected-label">暂未评分</span>
      <button class="bangumi-start-btn" @click="emit('update:editMode', true)">开始评分</button>
    </div>

    <!-- 有收藏数据或处于编辑态：完整表单 -->
    <template v-else>
      <div class="bangumi-collection-form">
        <label>
          <span>收藏状态</span>
          <template v-if="editMode">
            <select v-model.number="type" :disabled="loading || saving">
              <option :value="2">看过</option>
              <option :value="3">在看</option>
              <option :value="4">搁置</option>
              <option :value="5">抛弃</option>
            </select>
          </template>
          <template v-else>
            <div class="bangumi-static-field">{{ typeText(type) }}</div>
          </template>
        </label>

        <label>
          <span>评分</span>
          <div class="bangumi-stars-wrap" :class="{ editable: editMode }">
            <button
              v-for="n in 10"
              :key="n"
              class="bangumi-star-btn"
              :class="{ active: n <= (rate || 0) }"
              :disabled="!editMode || loading || saving"
              @click="setRate(n)"
            >★</button>
            <span class="bangumi-rate-value">{{ rateText(rate) }}</span>
          </div>
        </label>
      </div>

      <label class="bangumi-comment-field">
        <span>短评</span>
        <template v-if="editMode">
          <textarea
            v-model="comment"
            rows="4"
            maxlength="380"
            placeholder="写下你对这部作品的评价，会同步到 Bangumi。"
          ></textarea>
        </template>
        <template v-else>
          <div class="bangumi-comment-display">{{ commentText }}</div>
        </template>
      </label>

      <div class="bangumi-collection-actions">
        <template v-if="editMode">
          <button
            class="bangumi-save-btn"
            :disabled="saving || loading"
            @click="emit('save')"
          >
            {{ saving ? '提交中...' : '保存到 Bangumi' }}
          </button>
          <button
            class="bangumi-cancel-btn"
            :disabled="saving || loading"
            @click="emit('cancel')"
          >取消编辑</button>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  subjectId: {
    type: [String, Number],
    default: null,
  },
  subjectUrl: {
    type: String,
    default: '',
  },
  collectionForm: {
    type: Object,
    required: true,
  },
  editMode: {
    type: Boolean,
    default: false,
  },
  exists: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  saving: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:editMode', 'update:collectionForm', 'save', 'cancel', 'refresh'])

const type = computed({
  get: () => props.collectionForm.type,
  set: (value) => emit('update:collectionForm', { ...props.collectionForm, type: value }),
})

const rate = computed({
  get: () => props.collectionForm.rate,
  set: (value) => emit('update:collectionForm', { ...props.collectionForm, rate: value }),
})

const comment = computed({
  get: () => props.collectionForm.comment,
  set: (value) => emit('update:collectionForm', { ...props.collectionForm, comment: value }),
})

const commentText = computed(() => props.collectionForm.comment?.trim() || '暂未填写短评')

const typeText = (type) => {
  const map = { 1: '想看', 2: '看过', 3: '在看', 4: '搁置', 5: '抛弃' };
  return map[type] || '在看';
}

const rateText = (rate) => {
  const score = Number(rate || 0);
  if (score <= 0) {
    return '暂未评分';
  }
  return `${score}/10`;
}

const setRate = (n) => {
  if (!props.editMode) {
    return;
  }
  rate.value = Number(n);
}
</script>

<style scoped>
.bangumi-collection-card {
  background: var(--al-bg-watch);
  border: 1px solid var(--al-border-warm-2);
  border-radius: 18px;
  padding: 18px 20px;
  margin: 0 0 20px;
}

.bangumi-collection-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.bangumi-header-actions {
  display: flex;
  gap: 8px;
}

.bangumi-collection-header h3 {
  margin: 0 0 6px;
  color: var(--al-text-strong);
  font-size: 1.02rem;
}

.bangumi-collection-header p {
  margin: 0;
  color: var(--al-text-secondary);
  font-size: 0.92rem;
  line-height: 1.6;
}

.bangumi-collection-header a {
  color: var(--al-accent);
  text-decoration: none;
}

.bangumi-collection-header a:hover {
  text-decoration: underline;
}

.bangumi-refresh-btn,
.bangumi-edit-btn,
.bangumi-save-btn {
  border: none;
  border-radius: 12px;
  cursor: pointer;
  height: 40px;
  padding: 0 16px;
  font-weight: 600;
}

.bangumi-edit-btn {
  background: var(--al-bg);
  color: var(--al-text-secondary);
  border: 1px solid var(--al-border-soft-4);
}

.bangumi-refresh-btn {
  background: var(--al-bg-beige-2);
  color: var(--al-text-brown-21);
}

.bangumi-save-btn {
  background: var(--al-accent);
  color: var(--al-text-on-accent);
}

.bangumi-refresh-btn:disabled,
.bangumi-save-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.bangumi-collection-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.bangumi-collection-form label,
.bangumi-comment-field {
  display: grid;
  gap: 8px;
}

.bangumi-collection-form span,
.bangumi-comment-field span {
  color: var(--al-text-brown-21);
  font-size: 0.9rem;
  font-weight: 600;
}

.bangumi-collection-form select,
.bangumi-comment-field textarea {
  width: 100%;
  border: 1px solid var(--al-border-soft-5);
  border-radius: 12px;
  background: var(--al-bg);
  padding: 10px 12px;
  font-size: 0.92rem;
  color: var(--al-text-strong);
}

.bangumi-static-field {
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 10px 12px;
  color: var(--al-text-brown-21);
  min-height: 42px;
  display: flex;
  align-items: center;
}

.bangumi-comment-display {
  width: 100%;
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 10px 12px;
  font-size: 0.92rem;
  color: var(--al-text-brown-21);
  line-height: 1.65;
  min-height: 100px;
  white-space: pre-wrap;
  word-break: break-word;
}

.bangumi-collection-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--al-text-muted);
  font-size: 0.88rem;
  margin: 0 0 12px;
}

.bangumi-loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--al-border-soft-3);
  border-top-color: var(--al-accent);
  border-radius: 50%;
  animation: bangumi-spin 0.9s linear infinite;
}

@keyframes bangumi-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.bangumi-stars-wrap {
  border: 1px solid var(--al-border-soft-3);
  border-radius: 12px;
  background: var(--al-bg-panel);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.bangumi-stars-wrap.editable {
  background: var(--al-bg);
}

.bangumi-star-btn {
  border: none;
  background: transparent;
  color: var(--al-gray-text);
  font-size: 1rem;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

.bangumi-star-btn.active {
  color: var(--al-star-3);
}

.bangumi-star-btn:disabled {
  cursor: default;
}

.bangumi-rate-value {
  margin-left: 8px;
  color: var(--al-text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
}

.bangumi-comment-field textarea {
  resize: vertical;
  min-height: 100px;
}

.bangumi-collection-actions {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.bangumi-cancel-btn {
  border: 1px solid var(--al-border-soft-4);
  border-radius: 12px;
  background: var(--al-bg);
  color: var(--al-text-brown-21);
  cursor: pointer;
  height: 40px;
  padding: 0 16px;
  font-weight: 600;
}

.bangumi-not-collected {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 28px 0 20px;
}

.bangumi-not-collected-label {
  color: var(--al-text-muted);
  font-size: 1rem;
}

.bangumi-start-btn {
  border: 1.5px solid var(--al-accent);
  background: none;
  color: var(--al-accent);
  border-radius: 20px;
  padding: 7px 28px;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}

.bangumi-start-btn:hover {
  background: var(--al-accent);
  color: var(--al-text-on-accent);
}

@media (max-width: 799px) {
  .bangumi-collection-form {
    grid-template-columns: 1fr;
  }

  .bangumi-collection-header,
  .bangumi-collection-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .bangumi-header-actions {
    width: 100%;
  }
}
</style>
