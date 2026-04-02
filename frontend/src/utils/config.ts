// Central config — override via .env.local

/** Go API Gateway base URL. Empty string = use Vite proxy (recommended for dev). */
export const API_BASE = import.meta.env.VITE_API_BASE ?? ''

/**
 * Hotel config, quick_actions, and welcome_chips are no longer static here.
 * They are fetched dynamically from POST /api/session/init and stored in
 * the auth store (src/stores/auth.ts) as part of HotelConfig.
 *
 * This file only holds app-level constants that are truly universal.
 */
