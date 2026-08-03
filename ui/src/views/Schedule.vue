<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const API_BASE = '/api'

const defaultPoster = 'https://assets.anixplayer.net/image/poster/default.jpg'

const bangumiList = ref([])
const scheduleLoading = ref(false)
const scheduleError = ref('')
const activeDay = ref(new Date().getDay())

const weekTabs = [
  { label: '日', key: 0 }, { label: '一', key: 1 },
  { label: '二', key: 2 }, { label: '三', key: 3 },
  { label: '四', key: 4 }, { label: '五', key: 5 },
  { label: '六', key: 6 }
]

const fetchSchedule = async () => {
  scheduleLoading.value = true; scheduleError.value = ''
  try {
    const res = await fetch(`${API_BASE}/animes/shin/raw-json`)
    const result = await res.json()
    if (result.code !== 200 || !result.data || !Array.isArray(result.data.bangumiList))
      throw new Error('新番接口返回结构不正确')
    const nd = (d) => { if (d === 7) return 0; return Number.isInteger(d) ? d : -1 }
    bangumiList.value = result.data.bangumiList
      .map(i => ({ ...i, airDay: nd(i.airDay) }))
      .filter(i => i.airDay >= 0 && i.airDay <= 6)
  } catch (e) { scheduleError.value = e?.message || '新番数据加载失败'; bangumiList.value = [] }
  finally { scheduleLoading.value = false }
}

const filteredBangumi = computed(() =>
  bangumiList.value.filter(i => i.airDay === activeDay.value).sort((a, b) => (b.rating || 0) - (a.rating || 0))
)
const dayCount = (d) => bangumiList.value.filter(i => i.airDay === d).length

const fmtScore = (v) => {
  if (v == null || v === '') return '-'
  const n = Number(v); return Number.isNaN(n) ? '-' : n.toFixed(1)
}

const goToDetail = (a) => { if (a?.animeId) router.push('/anime/' + a.animeId) }

onMounted(() => { fetchSchedule() })
</script>

<template>
  <div class="schedule-root">
    <!-- 区块标题 -->
    <div class="br-section-header">
      <h3><i class="mdi mdi-calendar-week"></i> 新番时间表</h3>
      <span class="br-day-hint">按星期浏览当季新番</span>
    </div>

    <!-- 星期标签 -->
    <div class="br-day-tabs">
      <button
        v-for="t in weekTabs"
        :key="t.key"
        class="br-day-tab"
        :class="{ active: activeDay === t.key, today: t.key === new Date().getDay() }"
        @click="activeDay = t.key"
      >
        <span class="br-day-label">{{ t.label }}</span>
        <span class="br-day-badge">{{ dayCount(t.key) }}</span>
      </button>
    </div>

    <!-- 内容 -->
    <div v-if="scheduleLoading" class="br-sk-grid">
      <div v-for="i in 12" :key="'s' + i" class="br-sk-card"></div>
    </div>
    <div v-else-if="scheduleError" class="br-empty error"><i class="mdi mdi-alert-circle-outline"></i> {{ scheduleError }}</div>
    <div v-else-if="!filteredBangumi.length" class="br-empty"><i class="mdi mdi-coffee-outline"></i> 该日暂无新番</div>
    <div v-else class="br-grid">
      <div v-for="a in filteredBangumi" :key="a.animeId" class="br-card" @click="goToDetail(a)">
        <div class="br-card-image">
          <img :src="a.imageUrl || defaultPoster" :alt="a.animeTitle" loading="lazy" />
          <span class="br-badge-score"><i class="mdi mdi-star"></i> {{ fmtScore(a.rating) }}</span>
          <span v-if="a.isOnAir" class="br-badge-dot" title="连载中"></span>
        </div>
        <div class="br-card-body">
          <h4>{{ a.animeTitle }}</h4>
          <div class="br-card-meta">
            <span class="genre">{{ a.isOnAir ? '连载中' : '已完结' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.schedule-root {
  animation: schedule-in 0.35s ease-out;
}
@keyframes schedule-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

.br-day-hint {
  font-size: 12px;
  color: var(--anime-text-secondary);
  opacity: 0.75;
}
</style>
