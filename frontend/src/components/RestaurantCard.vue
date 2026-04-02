<template>
  <div class="restaurant-card" @click="emit('select', card)">
    <div class="card-emoji">{{ card.emoji }}</div>
    <div class="card-name">{{ card.name }}</div>
    <div class="card-type">{{ card.type }}</div>
    <div class="card-meta">
      <span class="card-rating">{{ card.rating }}</span>
      <span class="card-hours">{{ card.hours }}</span>
    </div>
    <span v-if="card.distance" class="card-dist">{{ card.distance }}</span>
    <span class="card-tag" :class="card.tag">{{ card.tagText }}</span>
  </div>
</template>

<script setup lang="ts">
import type { RestaurantCard } from '@/types'

defineProps<{ card: RestaurantCard }>()
const emit = defineEmits<{ (e: 'select', card: RestaurantCard): void }>()
</script>

<style scoped>
.restaurant-card {
  min-width: 165px;
  max-width: 165px;
  background: var(--card-bg, #221F18);
  border: 0.5px solid var(--card-border, #3A342A);
  border-radius: 10px;
  padding: 12px;
  flex-shrink: 0;
  cursor: pointer;
  transition: border-color 0.2s, transform 0.15s;
  user-select: none;
}

.restaurant-card:hover {
  border-color: var(--gold-dim, #7A6038);
  transform: translateY(-2px);
}

.card-emoji { font-size: 24px; margin-bottom: 7px; }

.card-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text, #F0EBE0);
  margin-bottom: 2px;
  font-family: 'Cormorant Garamond', serif;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-type {
  font-size: 11px;
  color: var(--text2, #A89E8A);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.card-rating { font-size: 11px; color: var(--gold, #B8965A); }
.card-hours  { font-size: 10px; color: var(--text3, #6B6356); }
.card-dist   { display: block; font-size: 10px; color: var(--text3, #6B6356); margin-bottom: 4px; }

.card-tag {
  display: inline-block;
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 4px;
  border: 0.5px solid;
}

.card-tag.date   { background: #2A1E30; border-color: #5A3A6A; color: #C8A0D8; }
.card-tag.open   { background: #1A2E1A; border-color: #2A5A2A; color: #80C080; }
.card-tag.busy   { background: #2E2A1A; border-color: #5A5020; color: #C0B060; }
.card-tag.closed { background: #2E1A1A; border-color: #5A2020; color: #C08080; }
</style>
