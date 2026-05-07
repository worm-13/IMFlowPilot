<template>
  <Teleport to="body">
    <Transition name="presentation">
      <div
        v-if="taskStore.isPresenting"
        class="fixed inset-0 z-50 flex flex-col bg-gray-950"
        @keydown="handleKeydown"
        tabindex="0"
        ref="overlayRef"
      >
        <div class="flex items-center justify-between px-6 py-3">
          <span class="text-sm text-gray-500">{{ taskStore.currentSlideIndex + 1 }} / {{ taskStore.totalSlides }}</span>
          <button
            class="rounded-lg px-4 py-1.5 text-sm text-gray-400 transition hover:bg-gray-800 hover:text-white"
            @click="taskStore.exitPresentation()"
          >
            退出演示
          </button>
        </div>

        <div class="flex flex-1 items-center justify-center px-8 sm:px-16">
          <button
            class="absolute left-4 top-1/2 -translate-y-1/2 rounded-full p-3 text-gray-500 transition hover:bg-gray-800 hover:text-white disabled:opacity-30"
            :disabled="taskStore.currentSlideIndex === 0"
            @click="taskStore.prevSlide()"
          >
            <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>

          <div
            v-if="taskStore.currentSlide"
            class="w-full max-w-4xl animate-fade-in rounded-2xl bg-gray-900 p-10 sm:p-14"
          >
            <h2 class="mb-6 text-2xl font-bold text-white sm:text-3xl">{{ taskStore.currentSlide.title }}</h2>
            <div class="text-base leading-relaxed text-gray-300 sm:text-lg whitespace-pre-wrap">{{ taskStore.currentSlide.content }}</div>
          </div>

          <button
            class="absolute right-4 top-1/2 -translate-y-1/2 rounded-full p-3 text-gray-500 transition hover:bg-gray-800 hover:text-white disabled:opacity-30"
            :disabled="taskStore.currentSlideIndex >= taskStore.totalSlides - 1"
            @click="taskStore.nextSlide()"
          >
            <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>

        <div class="flex justify-center gap-2 px-6 py-4">
          <button
            v-for="(_, idx) in taskStore.slidesData"
            :key="idx"
            class="h-2 rounded-full transition-all"
            :class="idx === taskStore.currentSlideIndex ? 'w-6 bg-indigo-500' : 'w-2 bg-gray-700 hover:bg-gray-600'"
            @click="taskStore.goToSlide(idx)"
          />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useTaskStore } from '../stores/taskStore'

const taskStore = useTaskStore()
const overlayRef = ref<HTMLElement | null>(null)

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
    e.preventDefault()
    taskStore.nextSlide()
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
    e.preventDefault()
    taskStore.prevSlide()
  } else if (e.key === 'Escape') {
    taskStore.exitPresentation()
  }
}

let touchStartX = 0

const handleTouchStart = (e: TouchEvent) => {
  touchStartX = e.touches[0].clientX
}

const handleTouchEnd = (e: TouchEvent) => {
  const diff = touchStartX - e.changedTouches[0].clientX
  if (Math.abs(diff) > 50) {
    if (diff > 0) {
      taskStore.nextSlide()
    } else {
      taskStore.prevSlide()
    }
  }
}

watch(() => taskStore.isPresenting, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
    setTimeout(() => overlayRef.value?.focus(), 100)
  } else {
    document.body.style.overflow = ''
  }
})

onMounted(() => {
  document.addEventListener('touchstart', handleTouchStart, { passive: true })
  document.addEventListener('touchend', handleTouchEnd, { passive: true })
})

onUnmounted(() => {
  document.removeEventListener('touchstart', handleTouchStart)
  document.removeEventListener('touchend', handleTouchEnd)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.presentation-enter-active,
.presentation-leave-active {
  transition: opacity 0.3s ease;
}

.presentation-enter-from,
.presentation-leave-to {
  opacity: 0;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
