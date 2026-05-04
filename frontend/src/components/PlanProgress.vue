<template>
  <div class="rounded-xl border border-indigo-200 bg-white shadow-sm">
    <button
      class="flex w-full items-center justify-between p-3 text-left transition hover:bg-indigo-50/50"
      @click="expanded = !expanded"
    >
      <div class="flex items-center gap-1.5">
        <span class="text-sm font-semibold text-indigo-700">🤖 Agent · 任务执行中</span>
        <span class="text-xs text-slate-400">({{ summary }})</span>
      </div>
      <span class="text-xs text-slate-400 transition-transform" :class="expanded ? 'rotate-180' : ''">▼</span>
    </button>

    <div v-show="expanded" class="border-t border-indigo-100 px-4 pb-4 pt-2">
      <div class="space-y-1.5">
        <div
          v-for="step in steps"
          :key="step.step"
          class="flex items-center gap-2 text-sm"
        >
          <span v-if="step.status === 'completed'" class="text-emerald-500 text-xs">✅</span>
          <span v-else-if="step.status === 'running'" class="animate-pulse text-blue-500 text-xs">🔄</span>
          <span v-else-if="step.status === 'failed'" class="text-red-500 text-xs">❌</span>
          <span v-else class="text-slate-300 text-xs">⏳</span>
          <span
            :class="
              step.status === 'running'
                ? 'font-semibold text-indigo-700'
                : step.status === 'completed'
                  ? 'text-slate-500 line-through'
                  : step.status === 'failed'
                    ? 'text-red-600'
                    : 'text-slate-500'
            "
          >
            {{ step.name }}
          </span>
        </div>
      </div>
      <div class="mt-3 h-2 w-full rounded-full bg-slate-100">
        <div
          class="h-2 rounded-full bg-gradient-to-r from-indigo-500 to-purple-500 transition-all duration-500"
          :style="{ width: percent + '%' }"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { StepItem } from '../stores/chatStore'

const props = defineProps<{
  steps: StepItem[]
}>()

const expanded = ref(true)

const percent = computed(() => {
  if (props.steps.length === 0) return 0
  const done = props.steps.filter(
    (s) => s.status === 'completed' || s.status === 'failed'
  ).length
  return Math.round((done / props.steps.length) * 100)
})

const summary = computed(() => {
  const done = props.steps.filter((s) => s.status === 'completed' || s.status === 'failed').length
  return `${done}/${props.steps.length}`
})
</script>
