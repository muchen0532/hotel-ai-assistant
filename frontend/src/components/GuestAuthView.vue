<template>
  <div class="auth-shell">
    <!-- Background decoration -->
    <div class="bg-grid" aria-hidden="true" />

    <div class="auth-card">
      <!-- Logo / brand -->
      <div class="brand">
        <div class="brand-icon">⟦H⟧</div>
        <div class="brand-name">Hotel AI Assistant</div>
      </div>


      <!-- Form -->
      <form class="auth-form" @submit.prevent="handleSubmit" novalidate>

        <!-- Hotel code -->
        <div class="field" :class="{ error: fieldErrors.hotel_code }">
          <label for="hotel_code">Hotel code</label>
          <input
            id="hotel_code"
            readonly
            v-model="form.hotel_code"
            type="text"
            autocomplete="off"
            autocapitalize="characters"
            maxlength="12"
            spellcheck="false"
            :disabled="isLoading"
            @input="clearFieldError('hotel_code')"
          />
          <span v-if="fieldErrors.hotel_code" class="field-err">
            {{ fieldErrors.hotel_code }}
          </span>
        </div>

        <!-- Room number -->
        <div class="field" :class="{ error: fieldErrors.room_number }">
          <label for="room_number">Room number</label>
          <input
            id="room_number"
            readonly
            v-model="form.room_number"
            type="text"
            autocomplete="off"
            maxlength="8"
            :disabled="isLoading"
            @input="clearFieldError('room_number')"
          />
          <span v-if="fieldErrors.room_number" class="field-err">
            {{ fieldErrors.room_number }}
          </span>
        </div>

        <!-- Guest name -->
        <div class="field" :class="{ error: fieldErrors.guest_name }">
          <label for="guest_name">Your name</label>
          <input
            id="guest_name"
            readonly
            v-model="form.guest_name"
            type="text"
            autocomplete="given-name"
            maxlength="60"
            :disabled="isLoading"
            @input="clearFieldError('guest_name')"
          />
          <span v-if="fieldErrors.guest_name" class="field-err">
            {{ fieldErrors.guest_name }}
          </span>
        </div>

        <!-- API error -->
        <Transition name="slide">
          <div v-if="authStore.authError" class="api-error" role="alert">
            <span class="err-icon">⚠</span>
            {{ authStore.authError }}
          </div>
        </Transition>

        <!-- Submit -->
        <button type="submit" class="submit-btn" :disabled="isLoading">
          <span v-if="!isLoading">Check in →</span>
          <span v-else class="loading-row">
            <svg class="spinner" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="2"
                stroke-dasharray="28" stroke-dashoffset="10" stroke-linecap="round"/>
            </svg>
            Verifying…
          </span>
        </button>
      </form>

      <!-- Demo codes -->
      <div class="demo-section">
        <div class="demo-label">Demo hotels — click to fill</div>
        <div class="demo-codes">
          <button
            v-for="demo in DEMO_HOTELS"
            :key="demo.code"
            class="demo-chip"
            type="button"
            :disabled="isLoading"
            @click="fillDemo(demo)"
          >
            <span class="demo-dot" :style="{ background: demo.color }" />
            {{ demo.name }}
          </button>
        </div>
      </div>
      
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useGuestAuth } from '@/composables/useGuestAuth'
import { onMounted } from 'vue'

onMounted(() => {
  fillDemo(DEMO_HOTELS[0])
})

const authStore = useAuthStore()
const { login } = useGuestAuth()

const emit = defineEmits<{ (e: 'authenticated'): void }>()

// ─── Form state ────────────────────────────────────────────────────────────

const form = reactive({
  hotel_code:  '',
  room_number: '',
  guest_name:  'Demo Guest',
})

const fieldErrors = reactive<Record<string, string>>({})
const isLoading = computed(() => authStore.status === 'loading')

// ─── Demo data ─────────────────────────────────────────────────────────────

const DEMO_HOTELS = [
  { name: '智宿酒店', code: 'NEXSTAY', room: '905',  color: '#6C8EF5' },
  { name: '樱花旅馆', code: 'SAKURA',  room: '308',  color: '#D4849A' },
]

function fillDemo(demo: typeof DEMO_HOTELS[0]) {
  form.hotel_code  = demo.code
  form.room_number = demo.room
  form.guest_name  = form.guest_name || 'Demo Guest'
  Object.keys(fieldErrors).forEach(k => delete fieldErrors[k])
}

// ─── Validation ────────────────────────────────────────────────────────────

function validate(): boolean {
  let ok = true

  if (!form.hotel_code.trim()) {
    fieldErrors.hotel_code = 'Hotel code is required'
    ok = false
  } else if (!/^[A-Z0-9]{3,12}$/i.test(form.hotel_code.trim())) {
    fieldErrors.hotel_code = 'Must be 3–12 letters or numbers'
    ok = false
  }

  if (!form.room_number.trim()) {
    fieldErrors.room_number = 'Room number is required'
    ok = false
  }

  if (!form.guest_name.trim()) {
    fieldErrors.guest_name = 'Your name is required'
    ok = false
  }

  return ok
}

function clearFieldError(field: string) {
  delete fieldErrors[field]
}

// ─── Submit ────────────────────────────────────────────────────────────────

async function handleSubmit() {
  if (!validate()) return

  const ok = await login({
    hotel_code:  form.hotel_code,
    room_number: form.room_number,
    guest_name:  form.guest_name,
  })

  if (ok) emit('authenticated')
}
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg, #0F0E0C);
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* Subtle dot-grid background */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(184,150,90,0.08) 1px, transparent 1px);
  background-size: 28px 28px;
  pointer-events: none;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  background: var(--bg2, #1A1814);
  border: 0.5px solid var(--border, #3A342A);
  border-radius: 16px;
  padding: 32px 28px 24px;
  position: relative;
  animation: rise 0.35s ease;
}

@keyframes rise {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* ─── Brand ─────────────────────────────────────────────────────────────── */

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-icon {
  font-size: 28px;
  color: var(--gold, #B8965A);
  font-family: 'Cormorant Garamond', serif;
  margin-bottom: 10px;
  line-height: 1;
}

.brand-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 500;
  color: var(--text, #F0EBE0);
  letter-spacing: 0.03em;
  margin-bottom: 4px;
}

/* ─── Fields ─────────────────────────────────────────────────────────────── */

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text2, #A89E8A);
}

input {
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  color: var(--text, #F0EBE0);
  border-radius: 8px;
  padding: 10px 13px;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  width: 100%;
}

input::placeholder { color: var(--text3, #6B6356); }

input:focus { border-color: var(--gold-dim, #7A6038); }

.field.error input { border-color: #8B3A3A; }

input:disabled { opacity: 0.45; }

.field-err {
  font-size: 11px;
  color: #C08080;
}

/* ─── API error ──────────────────────────────────────────────────────────── */

.api-error {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 10px 13px;
  background: #2E1A1A;
  border: 0.5px solid #5A2020;
  border-radius: 8px;
  font-size: 13px;
  color: #C08080;
  line-height: 1.5;
}

.err-icon { flex-shrink: 0; margin-top: 1px; }

/* ─── Submit ─────────────────────────────────────────────────────────────── */

.submit-btn {
  background: var(--gold, #B8965A);
  color: #0F0E0C;
  border: none;
  border-radius: 10px;
  padding: 12px;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, transform 0.1s;
  margin-top: 4px;
  width: 100%;
}

.submit-btn:hover:not(:disabled) { background: var(--gold-light, #D4B483); }
.submit-btn:active:not(:disabled) { transform: scale(0.98); }
.submit-btn:disabled { opacity: 0.5; cursor: default; }

.loading-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.spinner { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ─── Demo section ───────────────────────────────────────────────────────── */

.demo-section {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 0.5px solid var(--border, #3A342A);
}

.demo-label {
  font-size: 11px;
  color: var(--text3, #6B6356);
  text-align: center;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.demo-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  justify-content: center;
}

.demo-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg4, #2E2A22);
  border: 0.5px solid var(--border2, #4A4338);
  border-radius: 20px;
  padding: 5px 11px;
  font-size: 12px;
  color: var(--text2, #A89E8A);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  font-family: 'DM Sans', sans-serif;
}

.demo-chip:hover:not(:disabled) {
  border-color: var(--gold-dim, #7A6038);
  color: var(--text, #F0EBE0);
}

.demo-chip:disabled { opacity: 0.4; cursor: default; }

.demo-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ─── Transition ─────────────────────────────────────────────────────────── */

.slide-enter-active, .slide-leave-active {
  transition: max-height 0.2s ease, opacity 0.2s ease;
  overflow: hidden;
}
.slide-enter-from, .slide-leave-to { max-height: 0; opacity: 0; }
.slide-enter-to, .slide-leave-from { max-height: 80px; opacity: 1; }
</style>