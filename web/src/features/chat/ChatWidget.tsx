import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { channelOffer, dong } from '../marketplace'
import { formatVnd } from '../../shared/lib/format'
import { getJson } from '../../shared/lib/http'
import type { Page, Product } from '../../shared/types'
import { ChatIcon, ChevronDownIcon, SparkleIcon } from '../../shared/ui/icons'
import { ProductThumb, splitSuggestion } from './suggestionCards'
import { useChatSocket } from './useChatSocket'

const STATUS_LABEL = {
  connecting: 'Đang kết nối…',
  open: 'Trực tuyến',
  closed: 'Mất kết nối',
}

const OPEN_KEY = 'omniai.chat.open'

/** Trạng thái mở/thu nhỏ giữ trong phiên (sessionStorage). Không đọc được (chế độ riêng tư) thì coi là thu nhỏ. */
function readOpen(): boolean {
  try {
    return sessionStorage.getItem(OPEN_KEY) === '1'
  } catch {
    return false
  }
}

function saveOpen(open: boolean) {
  try {
    sessionStorage.setItem(OPEN_KEY, open ? '1' : '0')
  } catch {
    // Không lưu được thì lần sau mở lại ở trạng thái thu nhỏ.
  }
}

const reduceMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const THINKING_STEPS = [
  'Đang suy nghĩ…',
  'Đang xem sản phẩm trong shop…',
  'Đang lấy thông tin từ Shopee và TikTok…',
  'Đang soạn câu trả lời…',
]
const THINKING_STEP_MS = [0, 1600, 3600, 6200]

/** Bước đang hiện khi chờ AI. Về 0 khi hết chờ. Bước cuối giữ đến khi có câu trả lời. */
function useThinkingStep(active: boolean) {
  const [step, setStep] = useState(0)
  useEffect(() => {
    if (!active) return
    const timers = THINKING_STEP_MS.map((delay, index) =>
      window.setTimeout(() => setStep(index), delay),
    )
    return () => {
      timers.forEach((id) => window.clearTimeout(id))
      setStep(0)
    }
  }, [active])
  return step
}

// Chat widget kênh WEB. Owner: TuTM. Từ US-56: mặc định thu nhỏ thành nút ở góc phải dưới để không che giá và nút
// Đặt thử; Esc để thu nhỏ; danh mục sản phẩm (để ghép ảnh vào thẻ gợi ý) chỉ tải khi mở chat lần đầu. T-43.3 sẽ bỏ hẳn việc tải này.
export function ChatWidget() {
  const [open, setOpen] = useState(readOpen)
  const [catalogWanted, setCatalogWanted] = useState(open)
  const [draft, setDraft] = useState('')
  const [products, setProducts] = useState<Product[]>([])
  const { lines, status, isPending, send, reconnect } = useChatSocket()
  const thinkingStep = useThinkingStep(isPending)
  const logRef = useRef<HTMLDivElement | null>(null)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const closeRef = useRef<HTMLButtonElement | null>(null)
  const launcherRef = useRef<HTMLButtonElement | null>(null)
  // Chỉ dời focus khi khách tự bấm mở/thu nhỏ, không dời lúc tải trang.
  const focusAfterToggle = useRef(false)

  useEffect(() => {
    if (!catalogWanted) return
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
  }, [catalogWanted])

  useEffect(() => {
    const log = logRef.current
    if (open && log) log.scrollTo({ top: log.scrollHeight, behavior: reduceMotion() ? 'auto' : 'smooth' })
  }, [lines, isPending, thinkingStep, open])

  useEffect(() => {
    if (!focusAfterToggle.current) return
    focusAfterToggle.current = false
    if (!open) launcherRef.current?.focus()
    else if (inputRef.current && !inputRef.current.disabled) inputRef.current.focus()
    else closeRef.current?.focus()
  }, [open])

  const toggle = (next: boolean) => {
    focusAfterToggle.current = true
    if (next) setCatalogWanted(true)
    setOpen(next)
    saveOpen(next)
  }

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const text = draft.trim()
    if (text && send(text)) setDraft('')
  }

  if (!open) {
    return (
      <button ref={launcherRef} type="button" className="chat-launcher" aria-expanded="false" onClick={() => toggle(true)}>
        <ChatIcon />
        <span>Tư vấn mỹ phẩm</span>
        <span className={`chat-status-dot chat-status-${status}`} aria-hidden="true" />
        <span className="sr-only">, {STATUS_LABEL[status]}</span>
      </button>
    )
  }

  return (
    <section
      className="chat"
      aria-label="Chat với shop"
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.stopPropagation()
          toggle(false)
        }
      }}
    >
      <header className="chat-header">
        <span className="chat-avatar" aria-hidden="true"><SparkleIcon /></span>
        <div className="chat-heading">
          <span className="chat-title">Tư vấn mỹ phẩm</span>
          <span className={`chat-status chat-status-${status}`} title={`Trạng thái: ${STATUS_LABEL[status]}`}>
            <span className="chat-status-dot" aria-hidden="true" />
            {STATUS_LABEL[status]}
          </span>
        </div>
        <button ref={closeRef} type="button" className="btn-icon chat-close" onClick={() => toggle(false)} aria-label="Thu nhỏ chat">
          <ChevronDownIcon />
        </button>
      </header>

      {status === 'closed' && (
        <div className="chat-alert chat-alert-error" role="alert">
          <span>Mất kết nối máy chủ tư vấn.</span>
          <button type="button" className="btn btn-sm chat-alert-btn" onClick={reconnect}>
            Thử lại
          </button>
        </div>
      )}

      <div className="chat-lines" ref={logRef} role="log" aria-live="polite" aria-label="Tin nhắn">
        <ol className="chat-list">
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
                          {!item.product && <p className="chat-sku-price">{formatVnd(item.price)}</p>}
                        </div>
                        {item.product && (
                          <ul className="mkt-rows">
                            {(['SHOPEE', 'TIKTOK'] as const).map((market) => {
                              const offer = channelOffer(item.product!, market)
                              const path = market === 'SHOPEE' ? 'shopee' : 'tiktok'
                              const name = market === 'SHOPEE' ? 'Shopee' : 'TikTok Shop'
                              return (
                                <li key={market} className={`mkt-row mkt-row-${path}`}>
                                  <span className="mkt-brand">{name}</span>
                                  <span className="mkt-prices">
                                    {offer.promo && <s>{dong(offer.listPrice)}</s>}
                                    <strong>{dong(offer.salePrice)}</strong>
                                    {offer.promo
                                      ? <em>{offer.promo.label}</em>
                                      : <em className="mkt-plain">Không giảm</em>}
                                  </span>
                                  <Link className="mkt-buy" to={`/mo-phong/${path}/${item.product!.sku}`}>Mua ngay</Link>
                                </li>
                              )
                            })}
                          </ul>
                        )}
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
            <li className="chat-line chat-line-bot chat-progress">
              <span className="chat-progress-mark" aria-hidden="true" />
              <span>{THINKING_STEPS[thinkingStep]}</span>
            </li>
          )}
        </ol>
      </div>

      <form className="chat-form" onSubmit={submit}>
        <label htmlFor="chat-input" className="sr-only">Nội dung tin nhắn</label>
        <input
          ref={inputRef}
          id="chat-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={status === 'open' ? 'Nhập câu hỏi…' : 'Đang chờ kết nối lại…'}
          autoComplete="off"
          disabled={status !== 'open'}
        />
        <button type="submit" className="btn btn-primary" disabled={status !== 'open' || !draft.trim() || isPending}>
          Gửi
        </button>
      </form>
    </section>
  )
}
