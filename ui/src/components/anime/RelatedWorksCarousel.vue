<template>
  <div class="anime-related-section">
    <h3 class="anime-section-title">相关作品</h3>
    <div class="anime-related-grid">
      <router-link 
        v-for="rel in relateds" 
        :key="rel.animeId" 
        :to="`/anime/${rel.animeId}`" 
        class="anime-related-card"
      >
        <img :src="rel.imageUrl" :alt="rel.animeTitle" />
        <div class="anime-related-info">
          <h4>{{ rel.animeTitle }}</h4>
          <span class="anime-related-rating">⭐ {{ rel.rating }}</span>
        </div>
      </router-link>
    </div>
  </div>
</template>

<script setup>
defineProps({
  relateds: {
    type: Array,
    required: true
  }
});
</script>

<style scoped>
</style>
