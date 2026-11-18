import { startTransition, useCallback, useEffect, useOptimistic, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError } from '../../shared/lib/http'
import type { ChannelType, Page, PolicyViolation } from '../../shared/types'
import { LoginForm, loadToken, saveToken } from '../product-admin'
import { LIST_SIZE, MESSAGE_SIZE, listConversations, listMessages } from './api'
import type { ConversationView, InboxFilter, MessageView } from './types'

const CHANNELS: { code: ChannelType; label: string }[] = [
  { code: 'WEB', label: 'Web' },
  { code: 'SHOPEE', label: 'Shopee' },
  { code: 'TIKTOK', label: 'TikTok Shop' },
]
const ACTION_LABEL: Record<PolicyViolation['action'], string> = { BLOCK: 'chặn', REDACT: 'che', REPLACE: 'thay' }

const channelLabel = (code: ChannelType) => CHANNELS.find((c) => c.code === code)?.label ?? code
const formatTime = (iso: string) =>
  new Date(iso).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' })

function readFilter(params: URLSearchParams): InboxFilter {
  const channel = params.get('channel')
  return {
    channel: CHANNELS.some((c) => c.code === channel) ? (channel as ChannelType) : '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    hasViolation: params.get('violation') === '1',
    handoff: params.get('handoff') === '1',
    page: Math.max(0, Number(params.get('page')) || 0),
  }
}

function toParams(filter: InboxFilter, selectedId: number | null): URLSearchParams {
  const p = new URLSearchParams()
  if (filter.channel) p.set('channel', filter.channel)
  if (filter.from) p.set('from', filter.from)
  if (filter.to) p.set('to', filter.to)
  if (filter.hasViolation) p.set('violation', '1')
  if (filter.handoff) p.set('handoff', '1')
  if (filter.page) p.set('page', String(filter.page))
  if (selectedId) p.set('id', String(selectedId))
  return p
}

// Admin > Hội thoại (US-14): danh sách hội thoại mọi kênh + chi tiết từng tin, thấy chỗ policy can thiệp. Owner: TuTM.
export function ConversationInboxPage() {
  const [token, setToken] = useState<string | null>(loadToken)
  const [loginNotice, setLoginNotice] = useState<string | null>(null)

  const login = (t: string) => {
    saveToken(t)
    setLoginNotice(null)
    setToken(t)
  }
  const logout = useCallback((notice: string | null = null) => {
    saveToken(null)
    setLoginNotice(notice)
    setToken(null)
  }, [])
  const onLogout = useCallback(() => logout(), [logout])
  const onUnauthorized = useCallback(() => logout('Phiên đăng nhập không còn hợp lệ, hãy đăng nhập lại.'), [logout])

  return (
    <main id="main" tabIndex={-1} className="admin">
      {token ? (
        <Inbox token={token} onLogout={onLogout} onUnauthorized={onUnauthorized} />
      ) : (
        <LoginForm notice={loginNotice} onLogin={login} />
      )}
    </main>
  )
}

interface InboxProps {
  token: string
  onLogout: () => void
  onUnauthorized: () => void
}

function Inbox({ token, onLogout, onUnauthorized }: InboxProps) {
  const [params, setParams] = useSearchParams()
  const filter = readFilter(params)
  // react-router đổi URL trong transition: control lấy giá trị từ URL sẽ nháy về giá trị cũ 1 nhịp sau khi bấm.
  // useOptimistic cho chip, ô ngày, checkbox hiện ngay giá trị mới; danh sách vẫn tải theo URL.
  const [shown, setShown] = useOptimistic(filter)
  const selectedId = Number(params.get('id')) || null
  const [result, setResult] = useState<{ key: string; page: Page<ConversationView> | null } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  // Khoá chỉ gồm bộ lọc (không gồm id đang xem) để chọn hội thoại không tải lại danh sách.
  const query = toParams(filter, null).toString()
  const key = `${query}#${reload}`
  const loading = result?.key !== key
  const data = result?.page ?? null

  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) return onUnauthorized()
    setError((e as Error).message)
  }, [onUnauthorized])

  useEffect(() => {
    let current = true
    listConversations(token, readFilter(new URLSearchParams(query)))
      .then((page) => {
        if (!current) return
        setResult({ key, page })
        setError(null)
      })
      .catch((e) => {
        if (!current) return
        setResult((r) => ({ key, page: r?.page ?? null }))
        fail(e)
      })
    return () => {
      current = false
    }
  }, [token, query, key, fail])

  const update = (change: Partial<InboxFilter>, keepSelection = false) => {
    const next = { ...filter, page: 0, ...change }
    startTransition(() => {
      setShown(next)
      setParams(toParams(next, keepSelection ? selectedId : null))
    })
  }
  const select = (id: number | null) => setParams(toParams(filter, id))
  const filtered = Boolean(filter.channel || filter.from || filter.to || filter.hasViolation || filter.handoff)
  const totalPages = data ? Math.max(1, Math.ceil(data.total / LIST_SIZE)) : 1
  const selected = data?.items.find((c) => c.id === selectedId) ?? null

  return (
    <>
      <div className="admin-header">
        <h1>Hội thoại</h1>
        <button type="button" className="btn" onClick={() => setReload((n) => n + 1)}>Tải lại</button>
        <button type="button" className="btn" onClick={onLogout}>Đăng xuất</button>
      </div>
      <p className="admin-intro">
        Hội thoại của mọi kênh, mới nhất trước. Mở 1 hội thoại để so câu gốc của AI với câu khách thực nhận: câu bị chặn tô đỏ, câu bị sửa theo luật tô vàng.
      </p>

      <form className="admin-toolbar inbox-filters" onSubmit={(e) => e.preventDefault()} aria-label="Lọc hội thoại">
        <div className="inbox-chips" role="group" aria-label="Kênh">
          <button type="button" className={shown.channel ? 'chip' : 'chip chip-active'} aria-pressed={!shown.channel} onClick={() => update({ channel: '' })}>
            Mọi kênh
          </button>
          {CHANNELS.map((c) => (
            <button key={c.code} type="button" className={shown.channel === c.code ? 'chip chip-active' : 'chip'} aria-pressed={shown.channel === c.code} onClick={() => update({ channel: c.code })}>
              {c.label}
            </button>
          ))}
        </div>
        <label className="inbox-date">
          Từ ngày
          <input type="date" value={shown.from} max={shown.to || undefined} onChange={(e) => update({ from: e.target.value })} />
        </label>
        <label className="inbox-date">
          Đến ngày
          <input type="date" value={shown.to} min={shown.from || undefined} onChange={(e) => update({ to: e.target.value })} />
        </label>
        <label className="check">
          <input type="checkbox" checked={shown.hasViolation} onChange={(e) => update({ hasViolation: e.target.checked })} />
          Có vi phạm
        </label>
        <label className="check">
          <input type="checkbox" checked={shown.handoff} onChange={(e) => update({ handoff: e.target.checked })} />
          Cần nhân viên
        </label>
        {filtered && <button type="button" className="btn btn-ghost btn-sm" onClick={() => setParams(new URLSearchParams())}>Bỏ lọc</button>}
        <span className="muted toolbar-count" aria-live="polite">{loading ? 'Đang tải…' : data ? `${data.total} hội thoại` : ''}</span>
      </form>

      {error && <p className="state state-error" role="alert">{error}</p>}

      <div className={selectedId ? 'inbox inbox-open' : 'inbox'}>
        <section className="inbox-list" aria-label="Danh sách hội thoại" aria-busy={loading}>
          {!data && loading && (
            <div className="inbox-skeleton" aria-hidden="true">
              {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton skeleton-line" />)}
            </div>
          )}
          {data?.items.length === 0 && (
            <div className="state">
              <p>Không có hội thoại nào khớp.</p>
              {filtered && <button type="button" className="btn btn-sm" onClick={() => setParams(new URLSearchParams())}>Bỏ lọc</button>}
            </div>
          )}
          {data && data.items.length > 0 && (
            <ul className={loading ? 'inbox-items is-loading' : 'inbox-items'}>
              {data.items.map((c) => (
                <li key={c.id}>
                  <button type="button" className={c.id === selectedId ? 'inbox-item inbox-item-active' : 'inbox-item'} aria-current={c.id === selectedId ? 'true' : undefined} onClick={() => select(c.id)}>
                    <span className="inbox-item-top">
                      <span className={`channel-tag channel-${c.channel.toLowerCase()}`}>{channelLabel(c.channel)}</span>
                      <time dateTime={c.lastMessageAt}>{formatTime(c.lastMessageAt)}</time>
                    </span>
                    <span className="inbox-item-user">{c.channelUserId}</span>
                    <span className="inbox-item-text">{c.lastText ?? 'Chưa có tin'}</span>
                    <span className="inbox-item-badges">
                      <span className="muted">{c.messageCount} tin</span>
                      {c.flaggedCount > 0 && <span className="badge badge-warn">{c.flaggedCount} câu bị can thiệp</span>}
                      {c.handoffCount > 0 && <span className="badge badge-danger">{c.handoffCount} cần nhân viên</span>}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {data && totalPages > 1 && (
            <nav className="pager" aria-label="Phân trang hội thoại">
              <button type="button" className="btn btn-sm" disabled={filter.page === 0} onClick={() => update({ page: filter.page - 1 }, true)}>‹ Trước</button>
              <span>Trang {filter.page + 1}/{totalPages}</span>
              <button type="button" className="btn btn-sm" disabled={filter.page + 1 >= totalPages} onClick={() => update({ page: filter.page + 1 }, true)}>Sau ›</button>
            </nav>
          )}
        </section>

        <section className="inbox-detail" aria-label="Chi tiết hội thoại">
          {selectedId ? (
            <ConversationDetail key={`${selectedId}#${reload}`} token={token} id={selectedId} conversation={selected} onClose={() => select(null)} onError={fail} />
          ) : (
            <div className="state inbox-placeholder"><p>Chọn 1 hội thoại bên trái để xem từng tin.</p></div>
          )}
        </section>
      </div>
    </>
  )
}

interface DetailProps {
  token: string
  id: number
  /** Có khi hội thoại nằm trong trang danh sách đang xem. */
  conversation: ConversationView | null
  onClose: () => void
  onError: (e: unknown) => void
}

function ConversationDetail({ token, id, conversation, onClose, onError }: DetailProps) {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<{ page: number; data: Page<MessageView> | null; notFound: boolean } | null>(null)
  const loading = result?.page !== page

  useEffect(() => {
    let current = true
    listMessages(token, id, page)
      .then((data) => current && setResult({ page, data, notFound: false }))
      .catch((e) => {
        if (!current) return
        if (e instanceof ApiError && e.status === 404) return setResult({ page, data: null, notFound: true })
        setResult((r) => ({ page, data: r?.data ?? null, notFound: false }))
        onError(e)
      })
    return () => {
      current = false
    }
  }, [token, id, page, onError])

  const data = result?.data ?? null
  const totalPages = data ? Math.max(1, Math.ceil(data.total / MESSAGE_SIZE)) : 1

  return (
    <article className="thread" aria-busy={loading}>
      <header className="thread-head">
        <button type="button" className="btn btn-sm inbox-back" onClick={onClose}>‹ Danh sách</button>
        <div>
          <h2>{conversation ? `${channelLabel(conversation.channel)} · ${conversation.channelUserId}` : `Hội thoại #${id}`}</h2>
          {conversation && (
            <p className="muted thread-meta">
              <code>{conversation.conversationId}</code> · bắt đầu {formatTime(conversation.startedAt)}
              {conversation.customerId != null && <> · khách #{conversation.customerId}</>}
            </p>
          )}
        </div>
      </header>
      {result?.notFound && <p className="state state-error" role="alert">Không có hội thoại #{id}.</p>}
      {!data && loading && (
        <div className="inbox-skeleton" aria-hidden="true">
          {[0, 1, 2].map((i) => <div key={i} className="skeleton skeleton-line" />)}
        </div>
      )}
      {data && data.items.length === 0 && <p className="muted">Hội thoại chưa có tin.</p>}
      {data && (
        <ol className={loading ? 'thread-messages is-loading' : 'thread-messages'}>
          {data.items.map((m) => <Message key={m.id} message={m} />)}
        </ol>
      )}
      {data && totalPages > 1 && (
        <nav className="pager" aria-label="Phân trang tin">
          <button type="button" className="btn btn-sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>‹ Tin cũ hơn</button>
          <span>Trang {page + 1}/{totalPages}</span>
          <button type="button" className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Tin mới hơn ›</button>
        </nav>
      )}
    </article>
  )
}

function Message({ message: m }: { message: MessageView }) {
  if (m.direction === 'IN') {
    return (
      <li className="msg msg-in">
        <span className="msg-who">Khách · <time dateTime={m.createdAt}>{formatTime(m.createdAt)}</time></span>
        <p className="msg-text">{m.text}</p>
      </li>
    )
  }
  const flagged = m.violations.length > 0
  const tone = m.blocked ? 'msg-blocked' : flagged ? 'msg-flagged' : ''
  const changed = m.rawText != null && m.rawText !== m.text
  return (
    <li className={`msg msg-out ${tone}`}>
      <span className="msg-who">Bot · <time dateTime={m.createdAt}>{formatTime(m.createdAt)}</time></span>
      <span className="msg-badges">
        {m.blocked && <span className="badge badge-danger">Bị chặn, gửi câu thay thế</span>}
        {!m.blocked && flagged && <span className="badge badge-warn">Đã sửa theo luật</span>}
        {m.handoff && <span className="badge badge-danger">Cần nhân viên</span>}
        {m.deliveryStatus === 'FAILED' && <span className="badge badge-danger">Gửi lỗi sau {m.deliveryAttempts} lần</span>}
        {m.deliveryStatus === 'RETRYING' && <span className="badge badge-warn">Đang gửi lại (lần {m.deliveryAttempts})</span>}
      </span>
      <dl className="msg-texts">
        <dt>Khách nhận (finalText)</dt>
        <dd className="msg-text">{m.text}</dd>
        {changed && (
          <>
            <dt>Câu gốc của AI (rawText)</dt>
            <dd className="msg-text msg-raw">{m.rawText}</dd>
          </>
        )}
      </dl>
      {flagged && (
        <ul className="msg-violations" aria-label="Luật đã can thiệp">
          {m.violations.map((v, i) => (
            <li key={i}>
              <code>{v.ruleId}</code> · {ACTION_LABEL[v.action]}
              {v.matched && <> · khớp “<mark>{v.matched}</mark>”</>}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
