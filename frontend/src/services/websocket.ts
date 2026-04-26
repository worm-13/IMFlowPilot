import type { ChatMessage } from '../stores/chatStore'

const WS_URL = 'ws://localhost:8080/ws'

let socket: WebSocket | null = null
let isConnecting = false

const tryParseServerMessage = (raw: string): string => {
    try {
        const data = JSON.parse(raw) as { content?: string; message?: string; text?: string }
        return data.content ?? data.message ?? data.text ?? raw
    } catch {
        return raw
    }
}

export const connectWebSocket = (onReceive: (message: ChatMessage) => void) => {
    if (socket || isConnecting) return

    isConnecting = true
    socket = new WebSocket(WS_URL)

    socket.onopen = () => {
        isConnecting = false
    }

    socket.onmessage = (event) => {
        const content = tryParseServerMessage(String(event.data)).trim()
        if (!content) return

        onReceive({
            id: crypto.randomUUID(),
            sender: 'other',
            content,
            timestamp: Date.now(),
        })
    }

    socket.onerror = () => {
        // Keep UI resilient if server is unavailable.
    }

    socket.onclose = () => {
        socket = null
        isConnecting = false
    }
}

export const sendWebSocketMessage = (payload: string) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return false

    socket.send(payload)
    return true
}

export const disconnectWebSocket = () => {
    if (!socket) return
    socket.close()
    socket = null
    isConnecting = false
}
