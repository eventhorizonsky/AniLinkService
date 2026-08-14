<template>
  <div v-if="open" class="resource-dialog-mask" @click.self="$emit('close')">
    <div class="resource-dialog">
      <h3 class="resource-dialog-title">选择播放资源</h3>
      <p class="resource-dialog-subtitle">{{ title }}</p>
      <div class="resource-list">
        <button
          v-for="(resource, index) in resources"
          :key="resource.id"
          class="resource-item"
          @click="$emit('select', resource)"
        >
          <span class="resource-name">{{ resource.fileName || `资源 ${index + 1}` }}</span>
          <span class="resource-meta">ID: {{ resource.id }}</span>
        </button>
      </div>
      <button class="resource-cancel-btn" @click="$emit('close')">取消</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  resources: { type: Array, default: () => [] },
  title: { type: String, default: '' },
});

defineEmits(['select', 'close']);
</script>

<style scoped>
.resource-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  padding: 20px;
}

.resource-dialog {
  width: min(560px, 100%);
  background: var(--al-bg);
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.25);
  padding: 20px;
}

.resource-dialog-title {
  margin: 0;
  font-size: 1.2rem;
  color: var(--al-text-strong);
}

.resource-dialog-subtitle {
  margin: 8px 0 14px;
  color: var(--al-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.resource-list {
  display: grid;
  gap: 10px;
  max-height: 50vh;
  overflow: auto;
}

.resource-item {
  border: 1px solid var(--al-border-soft);
  background: var(--al-bg-watch);
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-item:hover {
  border-color: var(--al-accent);
  background: var(--al-bg-active-warm);
}

.resource-name {
  color: var(--al-text-strong);
  font-weight: 600;
}

.resource-meta {
  color: var(--al-text-muted);
  font-size: 0.85rem;
}

.resource-cancel-btn {
  margin-top: 14px;
  width: 100%;
  border: 1px solid var(--al-border-soft-4);
  background: var(--al-bg);
  color: var(--al-text-brown-21);
  border-radius: 10px;
  height: 40px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-cancel-btn:hover {
  background: var(--al-bg-panel);
}

@media (max-width: 799px) {
  .resource-dialog {
    padding: 16px;
  }
}
</style>
