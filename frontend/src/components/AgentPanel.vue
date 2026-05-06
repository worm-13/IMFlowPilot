<template>
  <Transition name="slide">
    <aside
      v-if="taskStore.panelOpen"
      class="flex w-80 flex-shrink-0 flex-col overflow-hidden rounded-2xl border border-indigo-200 bg-white shadow-lg"
    >
      <div class="flex items-center justify-between border-b border-indigo-100 px-4 py-3">
        <span class="text-sm font-semibold text-indigo-700">🤖 Agent 任务面板</span>
        <button class="text-xs text-slate-400 hover:text-slate-600" @click="taskStore.closePanel()">✕</button>
      </div>

      <div v-if="taskStore.steps.length === 0" class="flex flex-1 items-center justify-center p-6">
        <p class="text-sm text-slate-400">暂无任务</p>
      </div>

      <div v-else class="flex-1 overflow-y-auto px-4 py-3">
        <p class="mb-3 text-sm font-medium text-slate-700">
          {{ taskStore.taskName || '任务执行中' }}
        </p>
        <div class="space-y-2">
          <div
            v-for="step in taskStore.steps"
            :key="step.step"
            class="flex items-center gap-2 text-xs"
          >
            <span v-if="step.status === 'completed'" class="text-emerald-500">✅</span>
            <span v-else-if="step.status === 'running'" class="animate-pulse text-blue-500">🔄</span>
            <span v-else-if="step.status === 'failed'" class="text-red-500">❌</span>
            <span v-else class="text-slate-300">⏳</span>
            <span
              :class="
                step.status === 'running' ? 'font-semibold text-indigo-700' :
                step.status === 'completed' ? 'text-slate-500 line-through' :
                step.status === 'failed' ? 'text-red-600' :
                'text-slate-500'
              "
            >
              {{ step.name }}
            </span>
          </div>
        </div>
        <div class="mt-4 h-1.5 w-full rounded-full bg-slate-100">
          <div
            class="h-1.5 rounded-full bg-gradient-to-r from-indigo-500 to-purple-500 transition-all duration-500"
            :style="{ width: percent + '%' }"
          ></div>
        </div>
      </div>
    </aside>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTaskStore } from '../stores/taskStore'

const taskStore = useTaskStore()

const percent = computed(() => {
  if (taskStore.steps.length === 0) return 0
  const done = taskStore.steps.filter(
    (s) => s.status === 'completed' || s.status === 'failed'
  ).length
  return Math.round((done / taskStore.steps.length) * 100)
})
</script>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
