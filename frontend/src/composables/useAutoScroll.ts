import { ref, watch, nextTick } from 'vue'
import type { Ref } from 'vue'

/**
 * Auto-scrolls a container to bottom when content changes.
 * Stops auto-scrolling when user manually scrolls up.
 */
export function useAutoScroll(triggerRef: Ref<unknown>) {
  const containerRef = ref<HTMLElement | null>(null)
  const isAutoScrollEnabled = ref(true)

  function scrollToBottom(smooth = true) {
    const el = containerRef.value
    if (!el) return
    el.scrollTo({
      top: el.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    })
  }

  function onScroll() {
    const el = containerRef.value
    if (!el) return
    const threshold = 80
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    isAutoScrollEnabled.value = distFromBottom <= threshold
  }

  watch(
    triggerRef,
    async () => {
      if (!isAutoScrollEnabled.value) return
      await nextTick()
      scrollToBottom(false)
    },
    { deep: true }
  )

  return { containerRef, onScroll, scrollToBottom, isAutoScrollEnabled }
}
