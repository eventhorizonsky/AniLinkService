<template>
  <div v-if="totalPages > 1" class="pager">
    <button :disabled="page <= 1" @click="changePage(page - 1)"><i class="mdi mdi-chevron-left"></i></button>
    <button v-for="p in pages" :key="p" :class="{ active: p === page }" @click="changePage(p)">{{ p }}</button>
    <button :disabled="page >= totalPages" @click="changePage(page + 1)"><i class="mdi mdi-chevron-right"></i></button>
    <span v-if="totalText" class="info">{{ totalText }}</span>
  </div>
</template>

<script setup>
// 分页条。此前 Danmaku/Follows/History/Messages 四个页面逐字复制了同一套
// .pager 模板（样式来自 styles/browse.css 全局定义），统一收敛到这里。
const props = defineProps({
  page: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  },
  pages: {
    type: Array,
    required: true
  },
  totalText: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['change'])

const changePage = (p) => emit('change', p)
</script>
