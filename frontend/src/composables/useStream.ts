import { ref } from 'vue'
import type { StreamChunk, MessageMeta } from '@/types'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { API_BASE } from '@/utils/config'

const POLL_INTERVAL_MS = 2000
const POLL_TIMEOUT_MS = 120_000

export function useStream() {
  const store = useChatStore()
  const authStore = useAuthStore()
  const isStreaming = ref(false)

  let _pollTimer: ReturnType<typeof setInterval> | null = null
  let _pollTimeout: ReturnType<typeof setTimeout> | null = null

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
          session_id: authStore.sessionId,
          hotel_id: authStore.hotelId,
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
      let interrupted = false

      while (true) {
        const { value, done } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const raw = line.slice(5).trim()
          if (!raw || raw === '[DONE]') continue

          let chunk: StreamChunk
          try { chunk = JSON.parse(raw) } catch { continue }

          if (chunk.type === 'interrupt') interrupted = true
          handleChunk(chunk, placeholder.id)
        }
      }

      if (buffer.startsWith('data:')) {
        const raw = buffer.slice(5).trim()
        if (raw && raw !== '[DONE]') {
          try {
            const chunk: StreamChunk = JSON.parse(raw)
            if (chunk.type === 'interrupt') interrupted = true
            handleChunk(chunk, placeholder.id)
          } catch {
            // Ignore a trailing partial SSE line.
          }
        }
      }

      if (!interrupted) {
        store.finalizeMessage(placeholder.id)
        isStreaming.value = false
        store.isLoading = false
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      store.setMessageError(placeholder.id, `Error: ${msg}`)
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

      case 'interrupt':
        store.setMessagePending(msgId, chunk.data)
        _startPolling(msgId)
        break
    }
  }

  function _startPolling(msgId: string) {
    _stopPolling()

    _pollTimer = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE}/api/session/pending/${authStore.sessionId}`)
        const data = await res.json()

        if (data.status === 'completed') {
          _stopPolling()

          if (data.response_text) {
            store.appendStreamDelta(msgId, '\n\n' + data.response_text)
          }
          if (data.response_meta) {
            store.setMessageMeta(msgId, data.response_meta as MessageMeta)
          }
          store.finalizeMessage(msgId)
          isStreaming.value = false
          store.isLoading = false
        }
      } catch (e) {
        console.warn('[Poll] fetch failed:', e)
      }
    }, POLL_INTERVAL_MS)

    _pollTimeout = setTimeout(() => {
      _stopPolling()
      store.setMessageError(msgId, '请求超时，请联系前台确认')
      isStreaming.value = false
      store.isLoading = false
    }, POLL_TIMEOUT_MS)
  }

  function _stopPolling() {
    if (_pollTimer) {
      clearInterval(_pollTimer)
      _pollTimer = null
    }
    if (_pollTimeout) {
      clearTimeout(_pollTimeout)
      _pollTimeout = null
    }
  }

  function cleanup() {
    _stopPolling()
  }

  return { stream, isStreaming, cleanup }
}
