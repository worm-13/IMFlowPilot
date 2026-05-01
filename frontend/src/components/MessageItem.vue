<template>
  <div class="flex w-full" :class="alignmentClass">
    <div
      class="max-w-[80%] rounded-2xl px-4 py-3 shadow-sm sm:max-w-[70%]"
      :class="bubbleClass"
    >
      <div v-if="isAgent" class="mb-1 flex items-center gap-1">
        <span class="text-xs font-semibold" :class="agentLabelClass">🤖 Agent · {{ agentTypeLabel }}</span>
      </div>
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
const isAgent = computed(() => props.message.sender === 'agent')

const alignmentClass = computed(() => {
  return isUser.value ? 'justify-end' : 'justify-start'
})

const bubbleClass = computed(() => {
  if (isAgent.value) {
    return 'rounded-bl-md border border-indigo-200 bg-gradient-to-r from-indigo-50 to-purple-50 text-slate-800'
  }
  return isUser.value
    ? 'rounded-br-md bg-slate-900 text-white'
    : 'rounded-bl-md border border-slate-200 bg-white text-slate-800'
})

const timeClass = computed(() => {
  if (isAgent.value) return 'text-indigo-400'
  return isUser.value ? 'text-slate-300' : 'text-slate-400'
})

const agentLabelClass = computed(() => {
  if (props.message.agentType === 'task') return 'text-blue-600'
  if (props.message.agentType === 'suggestion') return 'text-purple-600'
  return 'text-slate-500'
})

const agentTypeLabel = computed(() => {
  if (props.message.agentType === 'task') return '任务执行'
  if (props.message.agentType === 'suggestion') return '智能建议'
  return '回复'
})

const formattedTime = computed(() => {
  return new Date(props.message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
})
</script>
