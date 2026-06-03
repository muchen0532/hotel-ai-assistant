<template>
  <div class="agent-trace" v-if="steps.length">
    <div class="trace-header">
      <span class="trace-icon" aria-hidden="true">•</span>
      <span>Agent trace</span>
    </div>
    <div
      v-for="(step, i) in steps"
      :key="i"
      class="trace-step"
      :class="step.status"
    >
      <span class="step-dot" :class="step.status" />
      <span class="step-label">{{ step.label }}</span>
      <span v-if="step.status === 'running'" class="step-spinner" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AgentTraceStep } from '@/types'

defineProps<{
  steps: AgentTraceStep[]
}>()
</script>

<style scoped>
.agent-trace {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--trace-bg, rgba(184, 150, 90, 0.06));
  border-left: 2px solid var(--gold-dim, #7A6038);
  border-radius: 0 6px 6px 0;
}

.trace-header {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--gold-dim, #7A6038);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.trace-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 12px;
  color: var(--text3, #6B6356);
  transition: color 0.3s;
}

.trace-step.done  { color: var(--text2, #A89E8A); }
.trace-step.running { color: var(--text, #F0EBE0); }
.trace-step.error { color: #e06060; }

.step-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text3, #6B6356);
  flex-shrink: 0;
  transition: background 0.3s;
}

.step-dot.done    { background: #4CAF50; }
.step-dot.running { background: var(--gold, #B8965A); }
.step-dot.error   { background: #e06060; }

.step-spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid var(--gold-dim, #7A6038);
  border-top-color: var(--gold, #B8965A);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-left: auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
