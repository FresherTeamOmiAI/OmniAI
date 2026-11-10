import { useEffect, useRef, useState } from 'react'
import { useChatSocket } from './useChatSocket'

const STATUS_LABEL = {
  connecting: 'Đang kết nối…',
  open: 'Trực tuyến',
  closed: 'Mất kết nối',
}

// Chat widget kênh WEB. Owner: TuTM.
export function ChatWidget() {
  const [open, setOpen] = useState(true)
  const [draft, setDraft] = useState('')
  const { lines, status, isPending, send, reconnect } = useChatSocket()
  const linesEndRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (open) {
      linesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [lines, isPending, open])

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
        <span className="chat-title">Tư vấn mỹ phẩm</span>
        <span className={`chat-status chat-status-${status}`} title={`Trạng thái: ${STATUS_LABEL[status]}`}>
          <span className="chat-status-dot" aria-hidden="true" />
          {STATUS_LABEL[status]}
        </span>
        <button type="button" className="chat-close" onClick={() => setOpen(false)} aria-label="Thu nhỏ chat">
          –
        </button>
      </header>

      {status === 'closed' && (
        <div className="chat-alert chat-alert-error" role="alert">
          <span>Mất kết nối máy chủ tư vấn.</span>
          <button type="button" className="chat-alert-btn" onClick={reconnect}>
            Thử lại
          </button>
        </div>
      )}

      <ol className="chat-lines">
        {lines.length === 0 && (
          <li className="chat-hint">
            Thử hỏi: &ldquo;Kem chống nắng cho da dầu&rdquo; hoặc &ldquo;Mình đang mang thai, dùng retinol được không?&rdquo;
          </li>
        )}
        {lines.map((line) => (
          <li key={line.id} className={`chat-line chat-line-${line.from}`}>
            {line.text}
            {line.handoff && <em className="chat-handoff">Đã chuyển cho nhân viên hỗ trợ</em>}
          </li>
        ))}
        {isPending && (
          <li className="chat-line chat-line-bot chat-line-typing" aria-live="polite">
            <span className="typing-dot" />
            <span className="typing-dot" />
            <span className="typing-dot" />
            <span className="sr-only">Shop đang trả lời...</span>
          </li>
        )}
        <div ref={linesEndRef} />
      </ol>

      <form className="chat-form" onSubmit={submit}>
        <label htmlFor="chat-input" className="sr-only">Nội dung tin nhắn</label>
        <input
          id="chat-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={status === 'open' ? 'Nhập câu hỏi…' : 'Đang chờ kết nối lại…'}
          autoComplete="off"
          disabled={status !== 'open'}
        />
        <button type="submit" disabled={status !== 'open' || !draft.trim() || isPending}>
          Gửi
        </button>
      </form>
    </section>
  )
}
