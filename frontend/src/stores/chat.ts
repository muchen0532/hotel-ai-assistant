import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { v4 as uuidv4 } from './uuid'
import type { ChatMessage, MessageMeta, InterruptData } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const hasMessages = computed(() => messages.value.length > 0)

  const lastMessage = computed(() =>
    messages.value[messages.value.length - 1] ?? null
  )

  const isStreaming = computed(() =>
    messages.value.some((m) => m.status === 'streaming')
  )

  function addUserMessage(content: string): ChatMessage {
    const msg: ChatMessage = {
      id: uuidv4(),
      role: 'user',
      content,
      status: 'done',
      timestamp: new Date(),
    }
    messages.value.push(msg)
    return msg
  }

  function addAssistantPlaceholder(): ChatMessage {
    const msg: ChatMessage = {
      id: uuidv4(),
      role: 'assistant',
      content: '',
      status: 'streaming',
      timestamp: new Date(),
    }
    messages.value.push(msg)
    return msg
  }

  function appendStreamDelta(id: string, delta: string) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) msg.content += delta
  }

  function setMessageMeta(id: string, meta: MessageMeta) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) msg.meta = meta
  }

  function finalizeMessage(id: string) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) msg.status = 'done'
  }

  function setMessageError(id: string, errMsg: string) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) {
      msg.status = 'error'
      msg.content = errMsg
    }
    error.value = errMsg
  }

  function setMessagePending(id: string, interruptData?: InterruptData) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) {
      msg.status = 'pending'
      msg.content = '您的请求已提交，正在等待前台确认...'
      msg.interruptData = interruptData
    }
  }

  function clearError() {
    error.value = null
  }

  function clearHistory() {
    messages.value = []
  }

  return {
    messages,
    isLoading,
    error,
    hasMessages,
    lastMessage,
    isStreaming,
    addUserMessage,
    addAssistantPlaceholder,
    appendStreamDelta,
    setMessageMeta,
    finalizeMessage,
    setMessageError,
    setMessagePending,
    clearError,
    clearHistory,
  }
})
