// ─── Message Types ───────────────────────────────────────────────────────────

export type MessageRole = 'user' | 'assistant'
export type MessageStatus = 'sending' | 'streaming' | 'done' | 'error' | 'pending'



export interface RestaurantCard {
  name: string
  type: string
  emoji: string
  rating: string
  hours: string
  distance?: string
  tag: 'date' | 'open' | 'busy' | 'closed'
  tagText: string
}

export interface InfoGridItem {
  label: string
  value: string
}

export interface AgentTraceStep {
  label: string
  status: 'pending' | 'running' | 'done' | 'error'
}

export interface MessageMeta {
  cards?: RestaurantCard[]
  info_grid?: InfoGridItem[]
  agent_trace?: AgentTraceStep[]
  chips?: string[]
}

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  status: MessageStatus
  meta?: MessageMeta
  timestamp: Date
  interruptData?: InterruptData
}

// ─── Hotel ───────────────────────────────────────────────────────────────────

/** Returned by POST /api/session/init — describes the property the guest is at */
export interface HotelConfig {
  hotel_id: string
  name: string
  tagline: string
  location: string       // e.g. "Taipei, Taiwan"
  locale: string         // e.g. "zh-TW" — controls UI language hints
  theme: HotelTheme
  quick_actions: QuickAction[]
  welcome_chips: string[]
}

export interface HotelTheme {
  /** CSS hex, used as the gold accent. e.g. "#B8965A" */
  accent: string
  accent_light: string
  accent_dim: string
}

export interface QuickAction {
  label: string
  query: string
}

// ─── Auth / Guest Session ─────────────────────────────────────────────────────

/** What the guest types on the login screen */
export interface GuestCredentials {
  hotel_code: string   // short human-readable code, e.g. "GRANDPLC"
  room_number: string
  guest_name: string
}

/** Persisted after successful /api/session/init */
export interface GuestSession {
  session_id: string
  hotel: HotelConfig
  room_number: string
  guest_name: string
  checked_in_at: string   // ISO string — serialisable for sessionStorage
}

// ─── API Types ────────────────────────────────────────────────────────────────

export interface SessionInitRequest {
  hotel_code: string
  room_number: string
  guest_name: string
}

export interface SessionInitResponse {
  session_id: string
  hotel: HotelConfig
}

export interface ChatRequest {
  session_id: string
  hotel_id: string
  message: string
}


/** Server-Sent Events delta chunk */
export type StreamChunk =
  | { type: 'text_delta'; delta: string }
  | { type: 'meta'; meta: MessageMeta }
  | { type: 'done' }
  | { type: 'error'; error: string }
  | { type: 'interrupt'; data: InterruptData }
  

// ─── Auth state ───────────────────────────────────────────────────────────────

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'error'


// ─── Interrupt ────────────────────────────────────────────────────────────────

export interface InterruptData {
  intent:      string
  room_number: string
  action:      string | null
  detail:      string | null
  message:     string
}
