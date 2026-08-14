<template>
  <component
    :is="to ? 'router-link' : 'div'"
    :to="to"
    class="br-card"
    :class="cardClass"
    @click="onClick"
  >
    <div class="br-card-image">
      <img
        v-if="imageUrl"
        :src="imageUrl"
        :alt="alt"
        loading="lazy"
        decoding="async"
      />
      <div v-else class="anime-card-no-img"><i class="mdi mdi-image-off"></i></div>
      <div v-if="hover" class="anime-card-hover"><i class="mdi mdi-play-circle-outline"></i></div>
      <slot name="badges" />
    </div>
    <div class="br-card-body">
      <h4 :title="titleAttr || undefined">{{ title }}</h4>
      <div class="br-card-meta">
        <slot name="meta" />
      </div>
    </div>
  </component>
</template>

<script setup>
const props = defineProps({
  to: {
    type: [String, Object],
    default: null
  },
  imageUrl: {
    type: String,
    default: null
  },
  alt: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    default: ''
  },
  titleAttr: {
    type: String,
    default: ''
  },
  cardClass: {
    type: String,
    default: ''
  },
  hover: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

const onClick = (e) => {
  if (props.to) return
  emit('click', e)
}
</script>

<style scoped>
/* 缺失封面占位 */
.anime-card-no-img {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--al-gray-muted);
  font-size: 2rem;
  background: linear-gradient(135deg, var(--al-bg-beige), var(--al-bg-beige-13));
}

/* hover 播放遮罩 */
.anime-card-hover {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
}
.anime-card-hover i {
  font-size: 2rem;
  color: #fff;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}
.br-card:hover .anime-card-hover {
  opacity: 1;
}
</style>
