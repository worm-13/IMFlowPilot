import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ChatSender = 'user' | 'other'

export interface ChatMessage {
    id: string
    sender: ChatSender
    content: string
    timestamp: number
}

export const useChatStore = defineStore('chat', () => {
    const messages = ref<ChatMessage[]>([])

    const addMessage = (message: ChatMessage) => {
        messages.value.push(message)
    }

    const clearMessages = () => {
        messages.value = []
    }

    return {
        messages,
        addMessage,
        clearMessages,
    }
})
