import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ChatSender = 'user' | 'other' | 'agent'

export interface StepItem {
    step: string
    name: string
    status: string
    tool?: string
    args?: Record<string, unknown>
}

export interface ChatMessage {
    id: string
    sender: ChatSender
    content: string
    timestamp: number
    agentType?: string
    mentions?: string[]
    steps?: StepItem[]
    confirmTask?: string
    slidesData?: { title: string; content: string }[]
    documentContent?: string
    downloadUrl?: string
    fileName?: string
    fileSize?: number
}

export const useChatStore = defineStore('chat', () => {
    const messages = ref<ChatMessage[]>([])

    const addMessage = (message: ChatMessage) => {
        messages.value.push(message)
    }

    const upsertMessage = (message: ChatMessage) => {
        const idx = messages.value.findIndex((m) => m.id === message.id)
        if (idx >= 0) {
            messages.value[idx] = message
        } else {
            messages.value.push(message)
        }
    }

    const clearMessages = () => {
        messages.value = []
    }

    return {
        messages,
        addMessage,
        upsertMessage,
        clearMessages,
    }
})
