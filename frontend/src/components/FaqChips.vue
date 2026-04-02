<template>
  <div class="faq-chips">
    <button
      v-for="chip in chips"
      :key="chip"
      class="chip"
      @click="emit('select', chip)"
    >
      {{ chip }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ chips: string[] }>()
const emit = defineEmits<{ (e: 'select', query: string): void }>()
</script>

<style scoped>
.faq-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.chip {
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  border-radius: 20px;
  padding: 5px 12px;
  font-size: 12px;
  color: var(--text2, #A89E8A);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  font-family: 'DM Sans', sans-serif;
  white-space: nowrap;
}

.chip:hover {
  background: var(--bg3, #242018);
  border-color: var(--gold-dim, #7A6038);
  color: var(--gold-light, #D4B483);
}
</style>
