import { useEffect, useRef, useState } from 'react'
import { formatVnd } from '../../shared/lib/format'
import { getJson } from '../../shared/lib/http'
import type { Page, Product } from '../../shared/types'
import { ProductThumb, splitSuggestion } from './suggestionCards'
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
  const [products, setProducts] = useState<Product[]>([])
  const { lines, status, isPending, send, reconnect } = useChatSocket()

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      const all: Product[] = []
      let page = 0
      try {
        for (;;) {
          const result = await getJson<Page<Product>>(`/api/products?page=${page}&size=100`)
          all.push(...result.items)
          if (cancelled || all.length >= result.total || result.items.length === 0) break
          page += 1
        }
        if (!cancelled) setProducts(all)
      } catch {
        if (!cancelled) setProducts([])
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])
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
        {lines.map((line) => {
          const suggestion = line.from === 'bot' && !line.error ? splitSuggestion(line.text, products) : null
          const items = suggestion?.items ?? []
          const note = suggestion && items.length > 0 ? suggestion.note : line.text
          return (
            <li
              key={line.id}
              className={`chat-line chat-line-${line.from}${line.error ? ' chat-line-error' : ''}${items.length > 0 ? ' chat-line-rich' : ''}`}
              role={line.error ? 'alert' : undefined}
            >
              {items.length > 0 && (
                <ul className="chat-skus">
                  {items.map((item) => (
                    <li key={item.key} className="chat-sku">
                      <ProductThumb category={item.category} label={item.sku ?? item.brand} imageUrl={item.imageUrl} />
                      <div className="chat-sku-copy">
                        {item.sku && <span className="chat-sku-code">{item.sku}</span>}
                        <p className="chat-sku-name">{item.name}</p>
                        <p className="chat-sku-brand">{item.brand}</p>
                        <p className="chat-sku-price">{formatVnd(item.price)}</p>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
              {items.length > 0 ? note && <p className="chat-note">{note}</p> : line.text}
              {line.handoff && <em className="chat-handoff">Đã chuyển cho nhân viên hỗ trợ</em>}
            </li>
          )
        })}
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
