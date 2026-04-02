<template>
  <div class="chat-input-area">
    <div class="quick-actions" v-if="quickActions.length">
      <button
        v-for="action in quickActions"
        :key="action.query"
        class="qa-btn"
        :disabled="disabled"
        @click="emit('send', action.query)"
      >
        {{ action.label }}
      </button>
    </div>

    <div class="input-row">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        :placeholder="placeholder"
        :disabled="disabled"
        rows="1"
        @keydown="handleKeydown"
        @input="autoResize"
      />
      <button
        class="send-btn"
        :disabled="disabled || !inputText.trim()"
        @click="handleSend"
      >
        <svg v-if="!disabled" width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M14 8L2 2l3 6-3 6 12-6z" fill="currentColor" />
        </svg>
        <svg v-else class="spinner" width="16" height="16" viewBox="0 0 16 16" fill="none">
          <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="2"
            stroke-dasharray="28" stroke-dashoffset="10" stroke-linecap="round" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import type { QuickAction } from '@/types'

withDefaults(defineProps<{
  disabled?: boolean
  placeholder?: string
  quickActions?: QuickAction[]
}>(), {
  disabled: false,
  placeholder: 'Ask your concierge anything…',
  quickActions: () => [],
})

const emit = defineEmits<{ (e: 'send', text: string): void }>()

const inputText = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  emit('send', text)
  inputText.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
      textareaRef.value.focus()
    }
  })
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

defineExpose({ focus: () => textareaRef.value?.focus() })
</script>

<style scoped>
.chat-input-area {
  padding: 10px 16px 14px;
  border-top: 0.5px solid var(--border, #3A342A);
  background: var(--bg2, #1A1814);
}

.quick-actions {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}
.quick-actions::-webkit-scrollbar { display: none; }

.qa-btn {
  background: transparent;
  border: 0.5px solid var(--border2, #4A4338);
  color: var(--text2, #A89E8A);
  border-radius: 8px;
  padding: 5px 11px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
  font-family: 'DM Sans', sans-serif;
  flex-shrink: 0;
}

.qa-btn:hover:not(:disabled) {
  border-color: var(--gold-dim, #7A6038);
  color: var(--gold-light, #D4B483);
  background: var(--bg4, #2E2A22);
}

.qa-btn:disabled { opacity: 0.4; cursor: default; }

.input-row { display: flex; gap: 8px; align-items: flex-end; }

textarea {
  flex: 1;
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  color: var(--text, #F0EBE0);
  border-radius: 10px;
  padding: 9px 13px;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  resize: none;
  outline: none;
  line-height: 1.5;
  max-height: 120px;
  transition: border-color 0.2s;
}

textarea::placeholder { color: var(--text3, #6B6356); }
textarea:focus { border-color: var(--gold-dim, #7A6038); }
textarea:disabled { opacity: 0.5; }

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--gold, #B8965A);
  border: none;
  color: #0F0E0C;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s, transform 0.1s;
}

.send-btn:hover:not(:disabled) { background: var(--gold-light, #D4B483); }
.send-btn:active:not(:disabled) { transform: scale(0.95); }
.send-btn:disabled { background: var(--bg4, #2E2A22); color: var(--text3, #6B6356); cursor: default; }

.spinner { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>