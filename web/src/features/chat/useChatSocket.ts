import { useCallback, useEffect, useRef, useState } from 'react'
import { wsUrl } from '../../shared/lib/http'
import type { BotReply } from '../../shared/types'

export interface ChatLine {
  id: string
  from: 'me' | 'bot'
  text: string
  handoff?: boolean
}

export type ChatStatus = 'connecting' | 'open' | 'closed'

// Kết nối kênh WEB qua WebSocket /ws/chat. Tách khỏi UI để test/đổi giao diện dễ.
export function useChatSocket() {
  const [lines, setLines] = useState<ChatLine[]>([])
  const [status, setStatus] = useState<ChatStatus>('connecting')
  const socketRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    const socket = new WebSocket(wsUrl('/ws/chat'))
    socketRef.current = socket
    socket.onopen = () => setStatus('open')
    socket.onclose = () => setStatus('closed')
    socket.onmessage = (event) => {
      const reply: BotReply = JSON.parse(event.data)
      setLines((prev) => [...prev, { id: reply.messageId + '-bot', from: 'bot', text: reply.finalText, handoff: reply.handoff }])
    }
    return () => socket.close()
  }, [])

  const send = useCallback((text: string) => {
    if (socketRef.current?.readyState !== WebSocket.OPEN) return false
    const messageId = crypto.randomUUID()
    socketRef.current.send(JSON.stringify({ messageId, text }))
    setLines((prev) => [...prev, { id: messageId, from: 'me', text }])
    return true
  }, [])

  return { lines, status, send }
}
