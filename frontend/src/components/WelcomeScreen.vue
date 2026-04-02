<template>
  <div class="welcome">
    <div class="welcome-icon">{{ hotel.logo }}</div>
    <h1 class="welcome-title">
      Welcome, {{ guestName }}<br />
      <span class="hotel-name-display">{{ hotel.name }}</span>
    </h1>
    <p class="welcome-sub">{{ hotel.tagline }}</p>

    <div class="welcome-chips">
      <button
        v-for="chip in hotel.welcome_chips"
        :key="chip"
        class="w-chip"
        @click="emit('select', chip)"
      >
        {{ chip }}
      </button>
    </div>

    <p class="welcome-hint">或者在下方输入您的问题 ↓</p>
  </div>
</template>

<script setup lang="ts">
import type { HotelConfig } from '@/types'

defineProps<{
  hotel: HotelConfig
  guestName: string
}>()

const emit = defineEmits<{ (e: 'select', query: string): void }>()
</script>

<style scoped>
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 24px;
  text-align: center;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.welcome-icon {
  font-size: 30px;
  color: var(--gold, #B8965A);
  margin-bottom: 16px;
  font-family: 'Cormorant Garamond', serif;
  opacity: 0.8;
}

.welcome-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 26px;
  font-weight: 400;
  color: var(--text, #F0EBE0);
  line-height: 1.35;
  margin-bottom: 8px;
  letter-spacing: 0.02em;
}

.hotel-name-display {
  font-size: 20px;
  color: var(--gold-light, #D4B483);
  display: block;
}

.welcome-sub {
  font-size: 13px;
  color: var(--text2, #A89E8A);
  margin-bottom: 24px;
  max-width: 300px;
  line-height: 1.6;
}

.welcome-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 420px;
  margin-bottom: 20px;
}

.w-chip {
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  border-radius: 20px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text2, #A89E8A);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  font-family: 'DM Sans', sans-serif;
}

.w-chip:hover {
  background: var(--bg3, #242018);
  border-color: var(--gold-dim, #7A6038);
  color: var(--gold-light, #D4B483);
}

.welcome-hint {
  font-size: 12px;
  color: var(--text3, #6B6356);
}
</style>