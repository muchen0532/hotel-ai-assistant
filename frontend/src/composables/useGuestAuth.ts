/**
 * useGuestAuth
 *
 * Calls POST /api/session/init with hotel_code + room_number + guest_name.
 * On success, populates the auth store and redirects to the chat view.
 *
 * Error messages are written into authStore.authError so the form can
 * display them without any extra local state.
 */
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { API_BASE } from '@/utils/config'
import type { GuestCredentials, SessionInitRequest, SessionInitResponse, GuestSession } from '@/types'

export function useGuestAuth() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()

  async function login(credentials: GuestCredentials): Promise<boolean> {
    authStore.setLoading()

    const body: SessionInitRequest = {
      hotel_code:   credentials.hotel_code.trim().toUpperCase(),
      room_number:  credentials.room_number.trim(),
      guest_name:   credentials.guest_name.trim(),
    }

    try {
      const res = await fetch(`${API_BASE}/api/session/init`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(10_000),
      })

      if (res.status === 404) {
        authStore.setError('Hotel code not found. Please check and try again.')
        return false
      }
      if (res.status === 401) {
        authStore.setError('Room number not found. Please check with the front desk.')
        return false
      }
      if (!res.ok) {
        const text = await res.text().catch(() => '')
        authStore.setError(`Server error (${res.status})${text ? ': ' + text : ''}`)
        return false
      }

      const data: SessionInitResponse = await res.json()

      const session: GuestSession = {
        session_id:     data.session_id,
        hotel:          data.hotel,
        room_number:    body.room_number,
        guest_name:     body.guest_name,
        checked_in_at:  new Date().toISOString(),
      }

      // Clear any previous hotel's chat history before switching
      chatStore.clearHistory()
      authStore.setSession(session)
      return true

    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Network error'
      authStore.setError(`Connection failed: ${msg}`)
      return false
    }
  }

  function logout() {
    chatStore.clearHistory()
    authStore.logout()
  }

  return { login, logout }
}