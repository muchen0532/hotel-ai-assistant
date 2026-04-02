<template>
  <div class="info-grid">
    <div v-for="item in items" :key="item.label" class="info-item">
      <div class="info-label">{{ item.label }}</div>
      <div class="info-value">{{ item.value }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { InfoGridItem } from '@/types'

defineProps<{ items: InfoGridItem[] }>()
</script>

<style scoped>
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 8px;
}

.info-item {
  background: var(--bg4, #2E2A22);
  border-radius: 8px;
  padding: 10px 12px;
  border: 0.5px solid var(--border, #3A342A);
}

.info-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text3, #6B6356);
  margin-bottom: 4px;
}

.info-value {
  font-size: 14px;
  color: var(--text, #F0EBE0);
  font-weight: 500;
}
</style>
