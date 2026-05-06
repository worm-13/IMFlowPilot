<template>
  <main class="flex h-screen w-screen items-center justify-center bg-slate-100 px-4 py-6 sm:px-6">
    <div class="flex h-full max-h-[900px] w-full max-w-6xl gap-4">
      <section class="flex flex-1 flex-col overflow-hidden rounded-3xl border border-slate-200 bg-slate-50 shadow-xl">
        <header class="border-b border-slate-200 bg-white px-5 py-4">
          <h1 class="text-base font-semibold text-slate-800 sm:text-lg">IMFlowPilot Chat</h1>
        </header>

        <div ref="messageListRef" class="flex-1 overflow-y-auto px-4 py-5 sm:px-6">
          <div class="mx-auto flex w-full max-w-3xl flex-col gap-4">
            <MessageItem v-for="message in chatStore.messages" :key="message.id" :message="message" @confirm="handleConfirm" />
          </div>
        </div>

        <MessageInput @send="handleSend" />
      </section>

      <AgentPanel />
    </div>
  </main>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import MessageInput from './MessageInput.vue'
import MessageItem from './MessageItem.vue'
import AgentPanel from './AgentPanel.vue'
import { useChatStore, type ChatMessage } from '../stores/chatStore'
import { useTaskStore } from '../stores/taskStore'
import { connectWebSocket, disconnectWebSocket, sendWebSocketMessage, extractMentions, type OutboundChatPayload } from '../services/websocket'

const chatStore = useChatStore()
const taskStore = useTaskStore()
const messageListRef = ref<HTMLElement | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (!messageListRef.value) return
  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

const handleSend = (content: string, confirmTask?: string) => {
  const id = crypto.randomUUID()
  const timestamp = Date.now()
  const mentions = extractMentions(content)

  const outgoingMessage: ChatMessage = {
    id,
    sender: 'user',
    content,
    timestamp,
    mentions,
  }

  chatStore.addMessage(outgoingMessage)

  const outboundPayload: OutboundChatPayload = {
    id,
    sender: `client-${id}`,
    content,
    timestamp,
    mentions: mentions.length > 0 ? mentions : undefined,
    confirmTask,
  }

  sendWebSocketMessage(outboundPayload)
}

const handleConfirm = (confirmTask: string) => {
  handleSend('开始', confirmTask)
}

onMounted(() => {
  connectWebSocket((message) => {
    if (message.sender === 'agent' && (message.agentType === 'progress' || message.agentType === 'plan')) {
      if (message.steps && message.steps.length > 0) {
        taskStore.updateTask(message.content, message.steps)
      }
      return
    }
    if (chatStore.messages.some((item) => item.id === message.id)) {
      return
    }
    chatStore.addMessage(message)
  })
})

onUnmounted(() => {
  disconnectWebSocket()
})

watch(
  () => chatStore.messages.length,
  () => {
    scrollToBottom()
  },
)
</script>
