<template>
  <div class="flex w-full" :class="alignmentClass">
    <div
      class="max-w-[80%] rounded-2xl px-4 py-3 shadow-sm sm:max-w-[70%]"
      :class="bubbleClass"
    >
      <div v-if="isAgent" class="mb-1 flex items-center gap-1">
        <span class="text-xs font-semibold" :class="agentLabelClass">🤖 Agent · {{ agentTypeLabel }}</span>
      </div>
      <div v-if="message.mentions && message.mentions.length > 0 && !isAgent" class="mb-1 flex flex-wrap gap-1">
        <span
          v-for="m in message.mentions"
          :key="m"
          class="inline-flex items-center gap-0.5 rounded-md bg-indigo-100 px-1.5 py-0.5 text-xs font-medium text-indigo-600"
        >
          @{{ m }}
        </span>
      </div>
      <p class="whitespace-pre-wrap break-words text-[15px] leading-relaxed" v-html="renderedContent"></p>
      <PlanProgress v-if="showProgress" :steps="message.steps!" />
      <div v-if="showConfirm" class="mt-3 flex gap-2">
        <button
          class="flex-1 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-indigo-700"
          @click="$emit('confirm', message.confirmTask)"
        >
          开始执行
        </button>
        <button
          class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-500 transition hover:bg-slate-50"
          @click="$emit('cancel')"
        >
          取消
        </button>
      </div>
      <p class="mt-1 text-right text-xs" :class="timeClass">{{ formattedTime }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '../stores/chatStore'
import PlanProgress from './PlanProgress.vue'

const props = defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  confirm: [task: string]
  cancel: []
}>()

const MENTION_RENDER_REGEX = /(@\S+)/g

const isUser = computed(() => props.message.sender === 'user')
const isAgent = computed(() => props.message.sender === 'agent')
const showProgress = computed(() =>
  isAgent.value &&
  props.message.steps &&
  props.message.steps.length > 0 &&
  (props.message.agentType === 'plan' || props.message.agentType === 'progress')
)

const showConfirm = computed(() =>
  isAgent.value &&
  props.message.confirmTask &&
  props.message.agentType === 'suggestion'
)

const renderedContent = computed(() => {
  return props.message.content.replace(
    MENTION_RENDER_REGEX,
    '<span class="font-medium text-indigo-600">$1</span>'
  )
})

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
