<template>
  <div class="msg" :class="message.role">
    <!-- Avatar -->
    <div class="avatar" :class="message.role === 'assistant' ? 'ai-av' : 'user-av'">
      <span v-if="message.role === 'assistant'">✦</span>
      <span v-else>{{ initials }}</span>
    </div>

    <!-- Bubble -->
    <div class="bubble" :class="[message.role, message.status]">
      <!-- Typing dots while streaming with no content yet -->
      <TypingIndicator v-if="isTyping" />

      <!-- Markdown content (streaming or done) -->
      <div
        v-else-if="message.content"
        class="md-content"
        v-html="renderedContent"
      />

      <!-- Error state -->
      <div v-if="message.status === 'error'" class="error-msg">
        {{ message.content }}
      </div>

      <!-- Rich meta: info grid -->
      <InfoGrid
        v-if="message.meta?.info_grid?.length"
        :items="message.meta.info_grid"
      />

      <!-- Rich meta: restaurant cards -->
      <div v-if="message.meta?.cards?.length" class="cards-row">
        <RestaurantCardView
          v-for="card in message.meta.cards"
          :key="card.name"
          :card="card"
          @select="onCardSelect"
        />
      </div>

      <!-- Agent trace -->
      <AgentTrace
        v-if="message.meta?.agent_trace?.length"
        :steps="message.meta.agent_trace"
      />

      <!-- Follow-up chips -->
      <FaqChips
        v-if="message.meta?.chips?.length && message.status === 'done'"
        :chips="message.meta.chips"
        @select="emit('chip-select', $event)"
      />

      <!-- Timestamp -->
      <div class="timestamp">{{ formattedTime }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage, RestaurantCard } from '@/types'
import { useMarkdown } from '@/composables/useMarkdown'
import TypingIndicator from './TypingIndicator.vue'
import InfoGrid from './InfoGrid.vue'
import AgentTrace from './AgentTrace.vue'
import FaqChips from './FaqChips.vue'
import { useAuthStore } from '@/stores/auth'
import RestaurantCardView from './RestaurantCard.vue'


const props = defineProps<{ message: ChatMessage }>()
const emit = defineEmits<{
  (e: 'chip-select', query: string): void
  (e: 'card-select', card: RestaurantCard): void
}>()

const authStore = useAuthStore()

const isTyping = computed(
  () => props.message.status === 'streaming' && !props.message.content
)

const renderedContent = computed(() =>
  useMarkdown(props.message.content)
)

const initials = computed(() => {
  const name = authStore.guestName
  return name.slice(0, 2).toUpperCase()
})

const formattedTime = computed(() => {
  const d = props.message.timestamp
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
})

function onCardSelect(card: RestaurantCard) {
  emit('card-select', card)
}
</script>

<style scoped>
.msg {
  display: flex;
  gap: 10px;
  max-width: 100%;
  animation: fadeUp 0.22s ease;
}

.msg.user { flex-direction: row-reverse; }

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 500;
  margin-top: 2px;
}

.avatar.ai-av {
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  color: var(--gold, #B8965A);
  font-family: 'Cormorant Garamond', serif;
  font-size: 14px;
}

.avatar.user-av {
  background: var(--user-bg, #1E3A2E);
  border: 0.5px solid #2A5040;
  color: var(--user-text, #A8D4B8);
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  position: relative;
}

.bubble.assistant {
  background: var(--ai-bg, #1A1814);
  border: 0.5px solid var(--border, #3A342A);
  border-radius: 4px 14px 14px 14px;
  color: var(--text, #F0EBE0);
}

.bubble.user {
  background: var(--user-bg, #1E3A2E);
  border: 0.5px solid #2A5040;
  border-radius: 14px 4px 14px 14px;
  color: var(--user-text, #A8D4B8);
}

.bubble.error { border-color: #5A2020; }
.error-msg { color: #e08080; font-size: 13px; }

/* Markdown styles inside bubble */
.md-content :deep(p)      { margin: 0 0 6px; }
.md-content :deep(p:last-child) { margin-bottom: 0; }
.md-content :deep(strong) { font-weight: 500; color: var(--gold-light, #D4B483); }
.md-content :deep(code)   {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  background: var(--bg4, #2E2A22);
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--gold-light, #D4B483);
}
.md-content :deep(pre) {
  background: var(--bg4, #2E2A22);
  border-radius: 6px;
  padding: 10px;
  overflow-x: auto;
  margin: 6px 0;
}
.md-content :deep(ul), .md-content :deep(ol) {
  padding-left: 18px;
  margin: 4px 0;
}
.md-content :deep(li) { margin-bottom: 2px; }
.md-content :deep(a)  { color: var(--gold-light, #D4B483); }

/* Streaming cursor animation */
.bubble.streaming .md-content::after {
  content: '▋';
  display: inline;
  color: var(--gold, #B8965A);
  animation: blink 0.8s step-end infinite;
  margin-left: 1px;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}

.cards-row {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: var(--border2, #4A4338) transparent;
}

.timestamp {
  font-size: 10px;
  color: var(--text3, #6B6356);
  margin-top: 6px;
  text-align: right;
}
</style>
