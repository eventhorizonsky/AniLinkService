<template>
  <footer class="anime-footer">
    <div class="anime-external-links">
      <a 
        v-for="db in databases" 
        :key="db.name" 
        class="anime-external-link" 
        :href="db.url" 
        target="_blank" 
        rel="noopener"
      >
        <i class="mdi mdi-link-variant"></i> {{ db.name }}
      </a>
    </div>
  </footer>
</template>

<script setup>
defineProps({
  databases: {
    type: Array,
    required: true
  },
  copyrightText: {
    type: String,
    required: true
  }
});
</script>

<style scoped>
</style>
