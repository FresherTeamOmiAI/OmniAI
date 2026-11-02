import { useEffect, useRef, useState } from 'react'
import type { BotReply } from '../api'

interface ChatLine {
  id: string
  from: 'me' | 'bot'
  text: string
  handoff?: boolean
}

type Status = 'connecting' | 'open' | 'closed'

// Chat widget kênh WEB qua WebSocket /ws/chat. Owner: TuTM.
export function ChatWidget() {
  const [open, setOpen] = useState(true)
  const [lines, setLines] = useState<ChatLine[]>([])
  const [draft, setDraft] = useState('')
  const [status, setStatus] = useState<Status>('connecting')
  const socketRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${protocol}://${location.host}/ws/chat`)
    socketRef.current = socket
    socket.onopen = () => setStatus('open')
    socket.onclose = () => setStatus('closed')
    socket.onmessage = (event) => {
      const reply: BotReply = JSON.parse(event.data)
      setLines((prev) => [...prev, { id: reply.messageId + '-bot', from: 'bot', text: reply.finalText, handoff: reply.handoff }])
    }
    return () => socket.close()
  }, [])

  const send = (event: React.FormEvent) => {
    event.preventDefault()
    const text = draft.trim()
    if (!text || socketRef.current?.readyState !== WebSocket.OPEN) return
    const messageId = crypto.randomUUID()
    socketRef.current.send(JSON.stringify({ messageId, text }))
    setLines((prev) => [...prev, { id: messageId, from: 'me', text }])
    setDraft('')
  }

  if (!open) {
    return (
      <button type="button" className="chat-toggle" onClick={() => setOpen(true)}>
        Chat với shop
      </button>
    )
  }

  return (
    <section className="chat" aria-label="Chat với shop">
      <header className="chat-header">
        <span>Tư vấn mỹ phẩm</span>
        <span className={`chat-status chat-status-${status}`}>
          {status === 'open' ? 'Đang kết nối' : status === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối (backend chưa chạy?)'}
        </span>
        <button type="button" className="chat-close" onClick={() => setOpen(false)} aria-label="Thu nhỏ chat">
          –
        </button>
      </header>
      <ol className="chat-lines">
        {lines.length === 0 && <li className="chat-hint">Thử hỏi: "Kem chống nắng cho da dầu" hoặc "Mình đang mang thai, dùng retinol được không?"</li>}
        {lines.map((line) => (
          <li key={line.id} className={`chat-line chat-line-${line.from}`}>
            {line.text}
            {line.handoff && <em className="chat-handoff">Đã chuyển cho nhân viên</em>}
          </li>
        ))}
      </ol>
      <form className="chat-form" onSubmit={send}>
        <label htmlFor="chat-input" className="sr-only">Nội dung tin nhắn</label>
        <input id="chat-input" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Nhập câu hỏi…" autoComplete="off" />
        <button type="submit" disabled={status !== 'open'}>Gửi</button>
      </form>
    </section>
  )
}
