<template>
  <div class="flex w-full" :class="isUser ? 'justify-end' : 'justify-start'">
    <div class="max-w-[80%] rounded-2xl px-4 py-3 shadow-sm sm:max-w-[70%]" :class="bubbleClass">
      <p class="whitespace-pre-wrap break-words text-[15px] leading-relaxed">{{ message.content }}</p>
      <p class="mt-1 text-right text-xs" :class="timeClass">{{ formattedTime }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '../stores/chatStore'

const props = defineProps<{
  message: ChatMessage
}>()

const isUser = computed(() => props.message.sender === 'user')

const bubbleClass = computed(() => {
  return isUser.value
    ? 'rounded-br-md bg-slate-900 text-white'
    : 'rounded-bl-md border border-slate-200 bg-white text-slate-800'
})

const timeClass = computed(() => {
  return isUser.value ? 'text-slate-300' : 'text-slate-400'
})

const formattedTime = computed(() => {
  return new Date(props.message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
})
</script>
