import { ref } from 'vue'
import type { StreamChunk, MessageMeta } from '@/types'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { API_BASE } from '@/utils/config'

export function useStream() {
  const store = useChatStore()
  const authStore = useAuthStore()
  const isStreaming = ref(false)

  async function stream(userMessage: string): Promise<void> {
    isStreaming.value = true
    store.isLoading = true
    store.clearError()

    store.addUserMessage(userMessage)

    const placeholder = store.addAssistantPlaceholder()

    try {
      const response = await fetch(`${API_BASE}/api/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          session_id: authStore.guestSession?.session_id,
          message: userMessage,
        }),
        signal: AbortSignal.timeout(60_000),
      })

      if (!response.ok) {
        const text = await response.text()
        throw new Error(`HTTP ${response.status}: ${text}`)
      }

      if (!response.body) throw new Error('No response body')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { value, done } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue
          const raw = line.slice(6).trim()
          if (!raw || raw === '[DONE]') continue

          let chunk: StreamChunk
          try {
            chunk = JSON.parse(raw)
          } catch {
            continue
          }

          handleChunk(chunk, placeholder.id)
        }
      }

      if (buffer.startsWith('data: ')) {
        const raw = buffer.slice(6).trim()
        if (raw && raw !== '[DONE]') {
          try {
            const chunk: StreamChunk = JSON.parse(raw)
            handleChunk(chunk, placeholder.id)
          } catch { /* ignore */ }
        }
      }

      store.finalizeMessage(placeholder.id)
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      store.setMessageError(placeholder.id, `⚠️ ${msg}`)
    } finally {
      isStreaming.value = false
      store.isLoading = false
    }
  }

  function handleChunk(chunk: StreamChunk, msgId: string) {
    switch (chunk.type) {
      case 'text_delta':
        if (chunk.delta) store.appendStreamDelta(msgId, chunk.delta)
        break
      case 'meta':
        if (chunk.meta) store.setMessageMeta(msgId, chunk.meta as MessageMeta)
        break
      case 'done':
        store.finalizeMessage(msgId)
        break
      case 'error':
        store.setMessageError(msgId, chunk.error ?? 'Stream error')
        break
    }
  }

  return { stream, isStreaming }
}
