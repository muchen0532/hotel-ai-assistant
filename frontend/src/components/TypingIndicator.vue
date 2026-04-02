<template>
  <div class="typing-indicator" aria-label="AI is thinking">
    <span class="dot" />
    <span class="dot" />
    <span class="dot" />
  </div>
</template>

<style scoped>
.typing-indicator {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 4px 2px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-tertiary, #888);
  animation: bounce 1.2s ease-in-out infinite;
  display: inline-block;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.5; }
  40%           { transform: translateY(-6px); opacity: 1; }
}
</style>
