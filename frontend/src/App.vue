<template>
  <Transition name="view" mode="out-in">
    <!-- Login screen -->
    <GuestAuthView
      v-if="!authStore.isAuthenticated"
      key="auth"
      @authenticated="onAuthenticated"
    />

    <!-- Chat screen -->
    <ChatWindow
      v-else
      key="chat"
      @logout="onLogout"
    />
  </Transition>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import GuestAuthView from '@/components/GuestAuthView.vue'
import ChatWindow    from '@/components/ChatWindow.vue'

const authStore = useAuthStore()

// Restore session from sessionStorage on page load
onMounted(() => {
  authStore.restoreSession()
})

function onAuthenticated() {
  // Auth store is already updated by useGuestAuth — transition is reactive
}

function onLogout() {
  // Auth store cleared by useGuestAuth.logout() — reactive transition back to login
}
</script>

<style>
/* View transition */
.view-enter-active,
.view-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.view-enter-from { opacity: 0; transform: translateY(10px); }
.view-leave-to   { opacity: 0; transform: translateY(-6px); }
</style>
