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
      <div v-if="showConfirm" class="mt-3 flex gap-2">
        <button
          class="flex-1 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-indigo-700"
          @click="$emit('confirm', message.confirmTask!)"
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
      <div v-if="showPresentation" class="mt-3">
        <button
          class="w-full rounded-lg bg-gradient-to-r from-indigo-600 to-purple-600 px-3 py-2 text-sm font-medium text-white transition hover:from-indigo-700 hover:to-purple-700"
          @click="startDemo"
        >
          开始演示
        </button>
      </div>
      <div v-if="showDocPreview" class="mt-3">
        <button
          class="w-full rounded-lg bg-gradient-to-r from-emerald-600 to-teal-600 px-3 py-2 text-sm font-medium text-white transition hover:from-emerald-700 hover:to-teal-700"
          @click="startDocPreview"
        >
          预览文档
        </button>
      </div>
      <div v-if="showDownload" class="mt-3">
        <a
          :href="message.downloadUrl"
          :download="message.fileName"
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-amber-500 to-orange-500 px-3 py-2 text-sm font-medium text-white transition hover:from-amber-600 hover:to-orange-600"
        >
          <span>⬇</span>
          <span>下载文件</span>
          <span v-if="message.fileName" class="text-xs opacity-80">({{ message.fileName }})</span>
        </a>
      </div>
      <p class="mt-1 text-right text-xs" :class="timeClass">{{ formattedTime }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '../stores/chatStore'
import { useTaskStore } from '../stores/taskStore'

const props = defineProps<{
  message: ChatMessage
}>()

const emit = defineEmits<{
  confirm: [task: string]
  cancel: []
  preview: [content: string]
}>()

const taskStore = useTaskStore()

const MENTION_RENDER_REGEX = /(@\S+)/g

const isUser = computed(() => props.message.sender === 'user')
const isAgent = computed(() => props.message.sender === 'agent')

const showConfirm = computed(() =>
  isAgent.value &&
  props.message.confirmTask &&
  props.message.agentType === 'suggestion'
)

const showPresentation = computed(() =>
  isAgent.value &&
  props.message.slidesData &&
  props.message.slidesData.length > 0
)

const showDocPreview = computed(() =>
  isAgent.value &&
  !!props.message.documentContent
)

const showDownload = computed(() =>
  isAgent.value &&
  !!props.message.downloadUrl
)

const startDemo = () => {
  if (props.message.slidesData && props.message.slidesData.length > 0) {
    taskStore.startPresentation(props.message.slidesData)
  }
}

const startDocPreview = () => {
  if (props.message.documentContent) {
    emit('preview', props.message.documentContent)
  }
}

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
  if (props.message.agentType === 'info_request') return 'text-teal-600'
  return 'text-slate-500'
})

const agentTypeLabel = computed(() => {
  if (props.message.agentType === 'task') return '任务执行'
  if (props.message.agentType === 'suggestion') return '智能建议'
  if (props.message.agentType === 'info_request') return '信息收集'
  return '回复'
})

const formattedTime = computed(() => {
  return new Date(props.message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
})
</script>
