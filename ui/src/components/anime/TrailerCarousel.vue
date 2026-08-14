<template>
  <div class="anime-trailer-section">
    <h3 class="anime-section-title"><i class="mdi mdi-movie-open"></i> 预告片</h3>
    <div class="anime-trailer-row">
      <a 
        v-for="trailer in trailers" 
        :key="trailer.id" 
        :href="trailer.url" 
        target="_blank" 
        rel="noopener" 
        class="anime-trailer-card"
      >
        <img :src="trailer.imageUrl" :alt="trailer.title" loading="lazy" />
        <div class="anime-trailer-info">
          <div class="anime-trailer-title">{{ trailer.title }}</div>
          <div class="anime-trailer-date">{{ formatDate(trailer.date) }}</div>
        </div>
      </a>
    </div>
  </div>
</template>

<script setup>
import { formatDate } from '../../utils/format';

defineProps({
  trailers: {
    type: Array,
    required: true
  }
});
</script>

<style scoped>
</style>
