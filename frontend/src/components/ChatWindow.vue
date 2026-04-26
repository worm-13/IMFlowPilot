<template>
  <main class="flex h-screen w-screen items-center justify-center bg-slate-100 px-4 py-6 sm:px-6">
    <section class="flex h-full max-h-[900px] w-full max-w-4xl flex-col overflow-hidden rounded-3xl border border-slate-200 bg-slate-50 shadow-xl">
      <header class="border-b border-slate-200 bg-white px-5 py-4">
        <h1 class="text-base font-semibold text-slate-800 sm:text-lg">IMFlowPilot Chat</h1>
      </header>

      <div ref="messageListRef" class="flex-1 overflow-y-auto px-4 py-5 sm:px-6">
        <div class="mx-auto flex w-full max-w-3xl flex-col gap-4">
          <MessageItem v-for="message in chatStore.messages" :key="message.id" :message="message" />
        </div>
      </div>

      <MessageInput @send="handleSend" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import MessageInput from './MessageInput.vue'
import MessageItem from './MessageItem.vue'
import { useChatStore } from '../stores/chatStore'
import { connectWebSocket, disconnectWebSocket, sendWebSocketMessage } from '../services/websocket'

const chatStore = useChatStore()
const messageListRef = ref<HTMLElement | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (!messageListRef.value) return
  messageListRef.value.scrollTop = messageListRef.value.scrollHeight
}

const handleSend = (content: string) => {
  chatStore.addMessage({
    id: crypto.randomUUID(),
    sender: 'user',
    content,
    timestamp: Date.now(),
  })

  sendWebSocketMessage(content)
}

onMounted(() => {
  connectWebSocket((message) => {
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
