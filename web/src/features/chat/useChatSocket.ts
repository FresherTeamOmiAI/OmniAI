import { useCallback, useEffect, useRef, useState } from 'react'
import { wsUrl } from '../../shared/lib/http'
import type { BotReply, WsErrorReply } from '../../shared/types'

export interface ChatLine {
  id: string
  from: 'me' | 'bot'
  text: string
  handoff?: boolean
  error?: boolean
}

export type ChatStatus = 'connecting' | 'open' | 'closed'

const RECONNECT_INTERVAL_MS = 3000
// Lớn hơn timeout gọi AI Brain của backend (omniai.brain.timeout = 10s) để server kịp trả fallback.
const REPLY_TIMEOUT_MS = 12_000
const TIMEOUT_TEXT = 'Shop đang bận, bạn thử lại sau nhé.'
const INVALID_MESSAGE_TEXT = 'Tin nhắn không hợp lệ, bạn gửi lại giúp shop nhé.'

function isErrorReply(data: unknown): data is WsErrorReply {
  return typeof data === 'object' && data !== null && 'error' in data
}

// Kết nối kênh WEB qua WebSocket /ws/chat. Có tự kết nối lại và báo trạng thái rõ ràng.
export function useChatSocket() {
  const [lines, setLines] = useState<ChatLine[]>([])
  const [status, setStatus] = useState<ChatStatus>('connecting')
  const [isPending, setIsPending] = useState(false)
  const socketRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<number | null>(null)
  const explicitlyClosedRef = useRef(false)
  const connectRef = useRef<() => void>(() => {})
  const replyTimerRef = useRef<number | null>(null)

  const clearReplyTimer = useCallback(() => {
    if (replyTimerRef.current !== null) {
      window.clearTimeout(replyTimerRef.current)
      replyTimerRef.current = null
    }
  }, [])

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
      clearReplyTimer()
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
      clearReplyTimer()
      setIsPending(false)
    }

    socket.onmessage = (event) => {
      clearReplyTimer()
      setIsPending(false)
      try {
        const data: unknown = JSON.parse(event.data)
        if (isErrorReply(data)) {
          // Server báo lỗi (vd. invalid_message): hiện thông báo, không coi là câu trả lời của bot.
          setLines((prev) => [
            ...prev,
            { id: `${data.messageId ?? crypto.randomUUID()}-err`, from: 'bot', text: INVALID_MESSAGE_TEXT, error: true },
          ])
          return
        }
        const reply = data as BotReply
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
  }, [clearReplyTimer])

  useEffect(() => {
    connectRef.current = connect
  }, [connect])

  useEffect(() => {
    explicitlyClosedRef.current = false
    connect()

    return () => {
      explicitlyClosedRef.current = true
      clearReplyTimer()
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
      if (socketRef.current) {
        socketRef.current.close()
        socketRef.current = null
      }
    }
  }, [connect, clearReplyTimer])

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
    clearReplyTimer()
    replyTimerRef.current = window.setTimeout(() => {
      replyTimerRef.current = null
      setIsPending(false)
      setLines((prev) => [...prev, { id: messageId + '-timeout', from: 'bot', text: TIMEOUT_TEXT, error: true }])
    }, REPLY_TIMEOUT_MS)
    return true
  }, [clearReplyTimer])

  return { lines, status, isPending, send, reconnect }
}
