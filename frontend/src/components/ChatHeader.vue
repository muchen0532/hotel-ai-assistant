<template>
  <header class="chat-header">
    <div class="header-left">
      <div class="hotel-logo">⟦H⟧</div>
      <div class="header-text">
        <div class="hotel-name">{{ hotel.name }}</div>
        <div class="hotel-sub">Room {{ roomNumber }} · {{ guestName }}</div>
      </div>
    </div>

    <div class="header-right">
      <div class="status-badge" :class="{ online: isOnline }">
        <span class="status-dot" />
        <span class="status-label">{{ isOnline ? 'Online' : 'Connecting…' }}</span>
      </div>

      <button
        class="icon-btn"
        title="Clear conversation"
        @click="emit('clear')"
      >
        <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
          <path d="M2 2l9 9M11 2l-9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>

      <button
        class="icon-btn logout"
        title="Check out / switch hotel"
        @click="emit('logout')"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M9.5 7H2m4-3.5L2 7l4 3.5M8 2h3a1 1 0 011 1v8a1 1 0 01-1 1H8"
            stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { HotelConfig } from '@/types'
import { API_BASE } from '@/utils/config'

defineProps<{
  hotel: HotelConfig
  roomNumber: string
  guestName: string
}>()

const emit = defineEmits<{
  (e: 'clear'): void
  (e: 'logout'): void
}>()

const isOnline = ref(true)

let timer: ReturnType<typeof setInterval>

async function ping() {
  try {
    const res = await fetch(`${API_BASE}/api/health`, {
      signal: AbortSignal.timeout(3000),
    })
    isOnline.value = res.ok
  } catch {
    isOnline.value = false
  }
}

onMounted(() => {
  ping()
  timer = setInterval(ping, 30_000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 18px;
  border-bottom: 0.5px solid var(--border, #3A342A);
  background: var(--bg2, #1A1814);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.hotel-logo {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--gold-dim, #7A6038), var(--gold, #B8965A));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #0F0E0C;
  font-family: 'Cormorant Garamond', serif;
  flex-shrink: 0;
}

.hotel-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 16px;
  font-weight: 500;
  color: var(--text, #F0EBE0);
  letter-spacing: 0.03em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hotel-sub {
  font-size: 11px;
  color: var(--text2, #A89E8A);
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 5px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text3, #6B6356);
  transition: background 0.3s;
}

.status-badge.online .status-dot {
  background: #4CAF50;
  box-shadow: 0 0 0 2px var(--bg2, #1A1814), 0 0 0 3px #4CAF5050;
}

.status-label {
  font-size: 11px;
  color: var(--text2, #A89E8A);
}

.icon-btn {
  background: transparent;
  border: 0.5px solid var(--border2, #4A4338);
  border-radius: 6px;
  color: var(--text3, #6B6356);
  width: 28px;
  height: 28px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.15s, border-color 0.15s;
}

.icon-btn:hover {
  color: var(--text, #F0EBE0);
  border-color: var(--border, #3A342A);
}

.icon-btn.logout:hover {
  color: #C08080;
  border-color: #5A2020;
}
</style>
