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

const RECONNECT_INTERVAL_MS = 3000

// Kết nối kênh WEB qua WebSocket /ws/chat. Có tự kết nối lại và báo trạng thái rõ ràng.
export function useChatSocket() {
  const [lines, setLines] = useState<ChatLine[]>([])
  const [status, setStatus] = useState<ChatStatus>('connecting')
  const [isPending, setIsPending] = useState(false)
  const socketRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<number | null>(null)
  const explicitlyClosedRef = useRef(false)
  const connectRef = useRef<() => void>(() => {})

  const connect = useCallback(() => {
    if (reconnectTimerRef.current !== null) {
      window.clearTimeout(reconnectTimerRef.current)
      reconnectTimerRef.current = null
    }

    if (
      socketRef.current &&
      (socketRef.current.readyState === WebSocket.OPEN ||
        socketRef.current.readyState === WebSocket.CONNECTING)
    ) {
      return
    }

    setStatus('connecting')
    const socket = new WebSocket(wsUrl('/ws/chat'))
    socketRef.current = socket

    socket.onopen = () => {
      setStatus('open')
    }

    socket.onclose = () => {
      setStatus('closed')
      setIsPending(false)
      if (!explicitlyClosedRef.current) {
        // Tự động kết nối lại sau 3s nếu mất kết nối bất ngờ
        reconnectTimerRef.current = window.setTimeout(() => {
          connectRef.current()
        }, RECONNECT_INTERVAL_MS)
      }
    }

    socket.onerror = () => {
      setStatus('closed')
      setIsPending(false)
    }

    socket.onmessage = (event) => {
      setIsPending(false)
      try {
        const reply: BotReply = JSON.parse(event.data)
        setLines((prev) => [
          ...prev,
          {
            id: reply.messageId + '-bot',
            from: 'bot',
            text: reply.finalText,
            handoff: reply.handoff,
          },
        ])
      } catch (err) {
        console.error('Lỗi giải mã phản hồi WebSocket:', err)
      }
    }
  }, [])

  useEffect(() => {
    connectRef.current = connect
  }, [connect])

  useEffect(() => {
    explicitlyClosedRef.current = false
    connect()

    return () => {
      explicitlyClosedRef.current = true
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
      if (socketRef.current) {
        socketRef.current.close()
        socketRef.current = null
      }
    }
  }, [connect])

  const reconnect = useCallback(() => {
    explicitlyClosedRef.current = false
    if (socketRef.current) {
      socketRef.current.close()
      socketRef.current = null
    }
    connect()
  }, [connect])

  const send = useCallback((text: string) => {
    if (socketRef.current?.readyState !== WebSocket.OPEN) return false
    const messageId = crypto.randomUUID()
    socketRef.current.send(JSON.stringify({ messageId, text }))
    setLines((prev) => [...prev, { id: messageId, from: 'me', text }])
    setIsPending(true)
    return true
  }, [])

  return { lines, status, isPending, send, reconnect }
}
