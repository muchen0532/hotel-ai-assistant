import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { GuestSession, AuthStatus, HotelTheme } from '@/types'

const STORAGE_KEY = 'hotel_ai_session'

export const useAuthStore = defineStore('auth', () => {
  // ─── State ───────────────────────────────────────────────────────────────

  const status = ref<AuthStatus>('idle')
  const guestSession = ref<GuestSession | null>(null)
  const authError = ref<string | null>(null)

  // ─── Getters ─────────────────────────────────────────────────────────────

  const isAuthenticated = computed(() => status.value === 'authenticated' && guestSession.value !== null)
  const hotel = computed(() => guestSession.value?.hotel ?? null)
  const hotelId = computed(() => guestSession.value?.hotel.hotel_id ?? null)
  const sessionId = computed(() => guestSession.value?.session_id ?? null)
  const guestName = computed(() => guestSession.value?.guest_name ?? 'Guest')
  const roomNumber = computed(() => guestSession.value?.room_number ?? '')

  // ─── Actions ─────────────────────────────────────────────────────────────

  /** Restore session from sessionStorage on app boot */
  function restoreSession(): boolean {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY)
      if (!raw) return false
      const parsed: GuestSession = JSON.parse(raw)
      // Basic sanity check
      if (!parsed.session_id || !parsed.hotel?.hotel_id) return false
      guestSession.value = parsed
      status.value = 'authenticated'
      applyTheme(parsed.hotel.theme)
      return true
    } catch {
      sessionStorage.removeItem(STORAGE_KEY)
      return false
    }
  }

  /** Called by useGuestAuth after a successful /api/session/init */
  function setSession(session: GuestSession) {
    guestSession.value = session
    status.value = 'authenticated'
    authError.value = null
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    applyTheme(session.hotel.theme)
  }

  function setLoading() {
    status.value = 'loading'
    authError.value = null
  }

  function setError(msg: string) {
    status.value = 'error'
    authError.value = msg
  }

  function logout() {
    guestSession.value = null
    status.value = 'idle'
    authError.value = null
    sessionStorage.removeItem(STORAGE_KEY)
    resetTheme()
  }

  // ─── Theme injection ──────────────────────────────────────────────────────

  function applyTheme(theme: HotelTheme) {
    const root = document.documentElement
    root.style.setProperty('--gold',       theme.accent)
    root.style.setProperty('--gold-light', theme.accent_light)
    root.style.setProperty('--gold-dim',   theme.accent_dim)
  }

  function resetTheme() {
    const root = document.documentElement
    root.style.removeProperty('--gold')
    root.style.removeProperty('--gold-light')
    root.style.removeProperty('--gold-dim')
  }

  return {
    // state
    status,
    guestSession,
    authError,
    // getters
    isAuthenticated,
    hotel,
    hotelId,
    sessionId,
    guestName,
    roomNumber,
    // actions
    restoreSession,
    setSession,
    setLoading,
    setError,
    logout,
  }
})
