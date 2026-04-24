/**
 * useStream — Server-Sent Events composable for streaming AI responses.
 *
 * Backend streams lines in the format:
 *   data: {"type":"text_delta","delta":"..."}
 *   data: {"type":"meta","meta":{...}}
 *   data: {"type":"interrupt","data":{...}}   ← human-in-the-loop
 *   data: {"type":"done"}
 *   data: {"type":"error","error":"..."}
 */
import { ref } from 'vue'
import type { StreamChunk, MessageMeta } from '@/types'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { API_BASE } from '@/utils/config'

const POLL_INTERVAL_MS = 2000
const POLL_TIMEOUT_MS  = 120_000   // 最多等 2 分钟

export function useStream() {
  const store     = useChatStore()
  const authStore = useAuthStore()
  const isStreaming = ref(false)

  let _pollTimer:   ReturnType<typeof setInterval>  | null = null
  let _pollTimeout: ReturnType<typeof setTimeout>   | null = null

  // ── 主入口 ──────────────────────────────────────────────────────────────────

  async function stream(userMessage: string): Promise<void> {
    isStreaming.value  = true
    store.isLoading    = true
    store.clearError()

    store.addUserMessage(userMessage)
    const placeholder = store.addAssistantPlaceholder()

    try {
      const response = await fetch(`${API_BASE}/api/chat/stream`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          session_id: authStore.sessionId,
          hotel_id:   authStore.hotelId,
          message:    userMessage,
        }),
        signal: AbortSignal.timeout(60_000),
      })

      if (!response.ok) {
        const text = await response.text()
        throw new Error(`HTTP ${response.status}: ${text}`)
      }
      if (!response.body) throw new Error('No response body')

      const reader  = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer    = ''
      let interrupted = false   // 收到 interrupt 后停止 finalize

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

          if (chunk.type === 'interrupt') {
            interrupted = true
          }
          handleChunk(chunk, placeholder.id)
        }
      }

      // flush 剩余 buffer
      if (buffer.startsWith('data:')) {
        const raw = buffer.slice(5).trim()
        if (raw && raw !== '[DONE]') {
          try {
            const chunk: StreamChunk = JSON.parse(raw)
            if (chunk.type === 'interrupt') interrupted = true
            handleChunk(chunk, placeholder.id)
          } catch { /* ignore */ }
        }
      }

      // interrupt 后不 finalize，由轮询完成后 finalize
      if (!interrupted) {
        store.finalizeMessage(placeholder.id)
        isStreaming.value = false
        store.isLoading   = false
      }

    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      store.setMessageError(placeholder.id, `⚠️ ${msg}`)
      isStreaming.value = false
      store.isLoading   = false
    }
  }

  // ── SSE 事件处理 ────────────────────────────────────────────────────────────

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
        // 显示"等待前台确认"状态，启动轮询
        store.setMessagePending(msgId, chunk.data)
        _startPolling(msgId)
        break
    }
  }

  // ── 轮询：等管理员 approve 后拉取结果 ────────────────────────────────────────

  function _startPolling(msgId: string) {
    _stopPolling()

    _pollTimer = setInterval(async () => {
      try {
        const res  = await fetch(`${API_BASE}/api/session/pending/${authStore.sessionId}`)
        const data = await res.json()

        if (data.status === 'completed') {
          _stopPolling()

          if (data.response_text) {
            store.appendStreamDelta(msgId, '\n\n' + data.response_text)  // ← 换行追加
          }
          if (data.response_meta) {
            store.setMessageMeta(msgId, data.response_meta as MessageMeta)
          }
          store.finalizeMessage(msgId)
          isStreaming.value = false
          store.isLoading   = false
        }
      } catch (e) {
        console.warn('[Poll] fetch failed:', e)
      }
    }, POLL_INTERVAL_MS)

    // 超时保底：2 分钟还没结果就放弃
    _pollTimeout = setTimeout(() => {
      _stopPolling()
      store.setMessageError(msgId, '⚠️ 请求超时，请联系前台确认')
      isStreaming.value = false
      store.isLoading   = false
    }, POLL_TIMEOUT_MS)
  }

  function _stopPolling() {
    if (_pollTimer)   { clearInterval(_pollTimer);  _pollTimer   = null }
    if (_pollTimeout) { clearTimeout(_pollTimeout); _pollTimeout = null }
  }

  // 组件卸载时清理
  function cleanup() {
    _stopPolling()
  }

  return { stream, isStreaming, cleanup }
}