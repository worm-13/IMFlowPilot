import type { ChatMessage, StepItem } from '../stores/chatStore'

const WS_URL = 'ws://localhost:8080/ws'

let socket: WebSocket | null = null
let isConnecting = false

export interface OutboundChatPayload {
    id: string
    sender: string
    content: string
    timestamp: number
    mentions?: string[]
    confirmTask?: string
}

const MENTION_REGEX = /@(\S+)/g

const normalizeSender = (sender: unknown): ChatMessage['sender'] => {
    if (sender === 'user') return 'user'
    if (sender === 'agent') return 'agent'
    return 'other'
}

const parseSteps = (raw: unknown): StepItem[] | undefined => {
    if (!Array.isArray(raw)) return undefined
    const filtered = raw.filter((s): s is StepItem =>
        typeof s === 'object' && s !== null && typeof (s as StepItem).step === 'string'
    )
    if (filtered.length === 0) return undefined
    return filtered.map(s => {
        const item = s as unknown as Record<string, unknown>
        return {
            ...s as StepItem,
            tool: typeof item.tool === 'string' ? item.tool as string : undefined,
            args: typeof item.args === 'object' && item.args !== null
                ? item.args as Record<string, unknown>
                : undefined,
        }
    })
}

const tryParseServerMessage = (raw: string): ChatMessage | null => {
    try {
        const data = JSON.parse(raw) as {
            id?: unknown
            sender?: unknown
            content?: unknown
            message?: unknown
            text?: unknown
            timestamp?: unknown
            mentions?: unknown
            steps?: unknown
            confirmTask?: unknown
            slidesData?: unknown
            documentContent?: unknown
            agentType?: unknown
            downloadUrl?: unknown
            fileName?: unknown
            fileSize?: unknown
        }

        const contentCandidate = data.content ?? data.message ?? data.text
        if (typeof contentCandidate !== 'string' || !contentCandidate.trim()) {
            return null
        }

        return {
            id: typeof data.id === 'string' && data.id.trim() ? data.id : crypto.randomUUID(),
            sender: normalizeSender(data.sender),
            content: contentCandidate.trim(),
            timestamp: typeof data.timestamp === 'number' ? data.timestamp : Date.now(),
            agentType: typeof data.agentType === 'string' ? data.agentType : undefined,
            mentions: Array.isArray(data.mentions) ? data.mentions.filter((m): m is string => typeof m === 'string') : undefined,
            steps: parseSteps(data.steps),
            confirmTask: typeof data.confirmTask === 'string' ? data.confirmTask : undefined,
            slidesData: Array.isArray(data.slidesData) ? data.slidesData.filter((s): s is { title: string; content: string } =>
                typeof s === 'object' && s !== null && typeof (s as Record<string, unknown>).title === 'string'
            ) : undefined,
            documentContent: typeof data.documentContent === 'string' ? data.documentContent : undefined,
            downloadUrl: typeof data.downloadUrl === 'string' ? data.downloadUrl : undefined,
            fileName: typeof data.fileName === 'string' ? data.fileName : undefined,
            fileSize: typeof data.fileSize === 'number' ? data.fileSize : undefined,
        }
    } catch {
        const content = raw.trim()
        if (!content) return null

        return {
            id: crypto.randomUUID(),
            sender: 'other',
            content,
            timestamp: Date.now(),
        }
    }
}

export const extractMentions = (text: string): string[] => {
    const mentions: string[] = []
    let match: RegExpExecArray | null
    while ((match = MENTION_REGEX.exec(text)) !== null) {
        const name = match[1].trim()
        if (name && !mentions.includes(name)) {
            mentions.push(name)
        }
    }
    return mentions
}

export const connectWebSocket = (onReceive: (message: ChatMessage) => void) => {
    if (socket || isConnecting) return

    isConnecting = true
    socket = new WebSocket(WS_URL)

    socket.onopen = () => {
        isConnecting = false
    }

    socket.onmessage = (event) => {
        const message = tryParseServerMessage(String(event.data))
        if (!message) return
        onReceive(message)
    }

    socket.onerror = () => {
        // Keep UI resilient if server is unavailable.
    }

    socket.onclose = () => {
        socket = null
        isConnecting = false
    }
}

export const sendWebSocketMessage = (payload: OutboundChatPayload) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return false

    socket.send(JSON.stringify(payload))
    return true
}

export const disconnectWebSocket = () => {
    if (!socket) return
    socket.close()
    socket = null
    isConnecting = false
}
