<template>
  <Transition name="slide">
    <div v-if="message" class="error-banner" role="alert">
      <span class="err-icon">⚠</span>
      <span class="err-text">{{ message }}</span>
      <button class="err-close" @click="emit('dismiss')">✕</button>
    </div>
  </Transition>
</template>

<script setup lang="ts">
defineProps<{ message: string | null }>()
const emit = defineEmits<{ (e: 'dismiss'): void }>()
</script>

<style scoped>
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: #2E1A1A;
  border-top: 0.5px solid #5A2020;
  font-size: 13px;
  color: #C08080;
}

.err-icon { flex-shrink: 0; }
.err-text { flex: 1; }

.err-close {
  background: transparent;
  border: none;
  color: #C08080;
  cursor: pointer;
  font-size: 11px;
  padding: 2px 4px;
}

.slide-enter-active, .slide-leave-active {
  transition: max-height 0.2s ease, opacity 0.2s ease;
  overflow: hidden;
}
.slide-enter-from, .slide-leave-to { max-height: 0; opacity: 0; }
.slide-enter-to, .slide-leave-from { max-height: 60px; opacity: 1; }
</style>
