<template>
  <div class="accent-panel">
    <div class="accent-panel-title">
      <i class="mdi mdi-palette-swatch-outline"></i> 主题色
    </div>
    <div class="accent-swatches">
      <button
        v-for="p in THEME_COLOR_PRESETS"
        :key="p.key"
        class="accent-swatch"
        :class="{ active: p.key === accentKey }"
        :title="p.name"
        :style="{ background: isDark ? p.dark.accent : p.light.accent }"
        @click="selectAccent(p.key)"
      >
        <i v-if="p.key === accentKey" class="mdi mdi-check"></i>
      </button>
    </div>
    <div class="accent-panel-divider"></div>
    <button class="accent-mode-btn" @click="toggleMode">
      <i class="mdi" :class="isDark ? 'mdi-weather-night' : 'mdi-white-balance-sunny'"></i>
      {{ isDark ? '深色模式' : '浅色模式' }}
    </button>
  </div>
</template>

<script setup>
import { THEME_COLOR_PRESETS } from '../utils/themeColors'
import { useTheme } from '../composables/useTheme'

const emit = defineEmits(['close'])
const { isDark, accentKey, setAccentColor, toggleTheme } = useTheme()

const selectAccent = (key) => {
  setAccentColor(key)
  emit('close')
}

const toggleMode = () => {
  toggleTheme()
  emit('close')
}
</script>

<style scoped>
.accent-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--anime-text-main);
  margin-bottom: 12px;
}
.accent-panel-title i {
  color: var(--anime-accent-red);
  font-size: 15px;
}
.accent-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.accent-swatch {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid var(--al-bg);
  box-shadow: 0 0 0 1px var(--al-border-soft);
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--al-text-on-accent);
  font-size: 15px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.accent-swatch:hover {
  transform: scale(1.12);
}
.accent-swatch.active {
  box-shadow: 0 0 0 2px var(--anime-accent-red);
  transform: scale(1.12);
}
.accent-panel-divider {
  height: 1px;
  background: var(--al-border-neutral);
  margin: 12px 0;
}
.accent-mode-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--anime-text-secondary);
  padding: 6px 8px;
  border-radius: 9px;
  font-family: inherit;
  transition: background 0.15s, color 0.15s;
}
.accent-mode-btn:hover {
  background: rgba(var(--al-accent-rgb), 0.08);
  color: var(--anime-accent-red);
}
.accent-mode-btn i {
  font-size: 15px;
}
</style>
