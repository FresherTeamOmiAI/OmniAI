import { useState } from 'react'
import { useChatSocket } from './useChatSocket'

const STATUS_LABEL = {
  connecting: 'Đang kết nối…',
  open: 'Đang kết nối',
  closed: 'Mất kết nối (backend chưa chạy?)',
}

// Chat widget kênh WEB. Owner: TuTM.
export function ChatWidget() {
  const [open, setOpen] = useState(true)
  const [draft, setDraft] = useState('')
  const { lines, status, send } = useChatSocket()

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const text = draft.trim()
    if (text && send(text)) setDraft('')
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
        <span className={`chat-status chat-status-${status}`}>{STATUS_LABEL[status]}</span>
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
      <form className="chat-form" onSubmit={submit}>
        <label htmlFor="chat-input" className="sr-only">Nội dung tin nhắn</label>
        <input id="chat-input" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Nhập câu hỏi…" autoComplete="off" />
        <button type="submit" disabled={status !== 'open'}>Gửi</button>
      </form>
    </section>
  )
}
