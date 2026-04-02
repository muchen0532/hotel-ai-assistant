<template>
  <div class="chat-window">
    <!-- Header -->
    <ChatHeader
      v-if="authStore.hotel"
      :hotel="authStore.hotel"
      :room-number="authStore.roomNumber"
      :guest-name="authStore.guestName"
      @clear="handleClear"
      @logout="handleLogout"
    />

    <!-- Error banner -->
    <ErrorBanner
      :message="store.error"
      @dismiss="store.clearError()"
    />

    <!-- Message list -->
    <div
      class="messages"
      ref="containerRef"
      @scroll="onScroll"
    >
      <WelcomeScreen
        v-if="!store.hasMessages && authStore.hotel"
        :hotel="authStore.hotel"
        :guest-name="authStore.guestName"
        @select="handleSend"
      />

      <template v-else>
        <MessageBubble
          v-for="msg in store.messages"
          :key="msg.id"
          :message="msg"
          @chip-select="handleSend"
          @card-select="handleCardSelect"
        />
      </template>

      <Transition name="fade">
        <button
          v-if="!isAutoScrollEnabled && store.hasMessages"
          class="scroll-down-btn"
          @click="scrollToBottom()"
        >↓</button>
      </Transition>
    </div>

    <!-- Input -->
    <ChatInput
      ref="chatInputRef"
      :disabled="store.isStreaming"
      :quick-actions="authStore.hotel?.quick_actions ?? []"
      @send="handleSend"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useStream } from '@/composables/useStream'
import { useGuestAuth } from '@/composables/useGuestAuth'
import { useAutoScroll } from '@/composables/useAutoScroll'
import type { RestaurantCard } from '@/types'

import ChatHeader    from '@/components/ChatHeader.vue'
import ErrorBanner   from '@/components/ErrorBanner.vue'
import WelcomeScreen from '@/components/WelcomeScreen.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ChatInput     from '@/components/ChatInput.vue'

const store = useChatStore()
const authStore = useAuthStore()
const { stream } = useStream()
const { logout } = useGuestAuth()

const emit = defineEmits<{ (e: 'logout'): void }>()

const { containerRef, onScroll, scrollToBottom, isAutoScrollEnabled } =
  useAutoScroll(ref(store.messages))

const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)

async function handleSend(text: string) {
  await stream(text)
}

function handleClear() {
  store.clearHistory()
}

function handleLogout() {
  logout()
  emit('logout')
}

function handleCardSelect(card: RestaurantCard) {
  handleSend(`Tell me more about ${card.name}`)
}

onMounted(() => {
  chatInputRef.value?.focus()
})
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 860px;
  margin: 0 auto;
  background: var(--bg, #0F0E0C);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: var(--border2, #4A4338) transparent;
}

.messages::-webkit-scrollbar { width: 4px; }
.messages::-webkit-scrollbar-track { background: transparent; }
.messages::-webkit-scrollbar-thumb { background: var(--border2, #4A4338); border-radius: 2px; }

.scroll-down-btn {
  position: sticky;
  bottom: 12px;
  align-self: center;
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  color: var(--text2, #A89E8A);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: background 0.15s;
  flex-shrink: 0;
}

.scroll-down-btn:hover {
  background: var(--bg3, #242018);
  color: var(--gold-light, #D4B483);
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>