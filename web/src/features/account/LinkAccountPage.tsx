import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../shared/lib/http'
import { confirmLink, fetchLinkRequests, issueLinkCode, rejectLink } from './api'
import { logout, useCustomerSession, type CustomerSession } from './session'
import type { LinkChannel, LinkedView, LinkProblem, LinkRequest } from './types'

// Liên kết tài khoản Shopee/TikTok bằng mã OMNI (US-22, SRS UC-08).
// T-22.2: lấy mã, xem thời gian còn lại. T-22.4: xác nhận yêu cầu liên kết + đồng ý xử lý dữ liệu.
// Backend chỉ lưu hash của mã nên trang giữ mã trong sessionStorage để F5 vẫn còn.

const CODE_KEY = 'omniai.link.code'
const POLL_MS = 5000

const CHANNEL_LABEL: Record<LinkChannel, string> = { SHOPEE: 'Shopee', TIKTOK: 'TikTok Shop' }

interface StoredCode {
  customerId: number
  code: string
  expiresAt: string
}

function loadCode(customerId: number): StoredCode | null {
  try {
    const raw = sessionStorage.getItem(CODE_KEY)
    if (!raw) return null
    const stored = JSON.parse(raw) as StoredCode
    const valid = stored.customerId === customerId && new Date(stored.expiresAt).getTime() > Date.now()
    return valid ? stored : null
  } catch {
    return null
  }
}

function saveCode(code: StoredCode | null) {
  try {
    if (code) sessionStorage.setItem(CODE_KEY, JSON.stringify(code))
    else sessionStorage.removeItem(CODE_KEY)
  } catch {
    // Không lưu được thì mã chỉ còn tới khi tải lại trang.
  }
}

function secondsLeft(until: string, now: number): number {
  return Math.max(0, Math.ceil((new Date(until).getTime() - now) / 1000))
}

function clock(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

function linkErrorText(err: unknown): string {
  if (!(err instanceof ApiError)) return 'Không kết nối được máy chủ, bạn thử lại sau nhé.'
  const problem = err.problem as LinkProblem | null
  switch (problem?.code) {
    case 'link_code_expired':
      return 'Yêu cầu đã hết hạn hoặc đã được xử lý. Bạn lấy mã mới và gõ lại trong chat nhé.'
    case 'identity_already_linked':
      return 'Tài khoản sàn này đã được liên kết với một tài khoản khác.'
  }
  if (err.status === 404) return 'Không tìm thấy yêu cầu liên kết này.'
  return err.message
}

/** Trang /account/link. Chưa đăng nhập thì mời đăng nhập rồi quay lại. */
export function LinkAccountPage() {
  const session = useCustomerSession()
  return (
    <main id="main" tabIndex={-1} className="account">
      {session
        ? <LinkPanel key={session.customer.id} session={session} />
        : (
          <div className="login-form">
            <h1>Liên kết tài khoản sàn</h1>
            <p className="muted">
              Liên kết tài khoản Shopee, TikTok Shop với tài khoản web để shop nhớ hồ sơ da và đơn hàng của bạn ở mọi kênh.
            </p>
            <Link to={`/login?next=${encodeURIComponent('/account/link')}`} className="btn btn-primary">
              Đăng nhập để liên kết
            </Link>
          </div>
        )}
    </main>
  )
}

function LinkPanel({ session }: { session: CustomerSession }) {
  const token = session.token
  const [code, setCode] = useState<StoredCode | null>(() => loadCode(session.customer.id))
  const [requests, setRequests] = useState<LinkRequest[]>([])
  const [linked, setLinked] = useState<LinkedView[]>([])
  const [now, setNow] = useState(() => Date.now())
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const codeLeft = code ? secondsLeft(code.expiresAt, now) : 0
  const codeActive = codeLeft > 0

  /** Lỗi 401 ở bất kỳ request nào: token hết hạn hoặc khách đã bị xoá, đăng xuất. */
  const handle401 = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) {
      logout()
      return true
    }
    return false
  }, [])

  const refresh = useCallback(async () => {
    try {
      setRequests(await fetchLinkRequests(token))
      setLoadError(null)
    } catch (err) {
      if (!handle401(err)) setLoadError('Chưa tải được yêu cầu liên kết, trang sẽ thử lại.')
    }
  }, [token, handle401])

  // Tải lần đầu khi mở trang; bỏ kết quả nếu đã rời trang.
  useEffect(() => {
    let alive = true
    fetchLinkRequests(token)
      .then((list) => alive && setRequests(list))
      .catch((err: unknown) => alive && !handle401(err) && setLoadError('Chưa tải được yêu cầu liên kết, trang sẽ thử lại.'))
    return () => {
      alive = false
    }
  }, [token, handle401])

  // Hỏi lại backend mỗi 5 giây khi đang chờ khách gõ mã trong chat, và khi quay lại tab.
  const waiting = codeActive || requests.length > 0
  useEffect(() => {
    if (!waiting) return
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh()
    }, POLL_MS)
    return () => window.clearInterval(timer)
  }, [waiting, refresh])

  useEffect(() => {
    const onFocus = () => void refresh()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [refresh])

  // Đồng hồ cho đếm ngược của mã và hạn xác nhận.
  useEffect(() => {
    if (!waiting) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [waiting])

  useEffect(() => {
    if (code && !codeActive) saveCode(null)
  }, [code, codeActive])

  async function getCode() {
    setBusy(true)
    setError(null)
    setCopied(false)
    try {
      const issued = await issueLinkCode(token)
      const stored = { customerId: session.customer.id, code: issued.code, expiresAt: issued.expiresAt }
      saveCode(stored)
      setCode(stored)
      setNow(Date.now())
    } catch (err) {
      if (!handle401(err)) setError(linkErrorText(err))
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    if (!code) return
    try {
      await navigator.clipboard.writeText(code.code)
      setCopied(true)
    } catch {
      setCopied(false)
      setError('Trình duyệt không cho sao chép, bạn chép tay mã giúp shop nhé.')
    }
  }

  function onLinked(view: LinkedView) {
    setLinked((prev) => [view, ...prev])
    saveCode(null)
    setCode(null)
    void refresh()
  }

  return (
    <div className="link-page">
      <header className="link-head">
        <h1>Liên kết tài khoản sàn</h1>
        <p className="muted">
          Gõ mã liên kết vào chat với shop trên Shopee hoặc TikTok Shop. Sau khi bạn xác nhận ở đây, bot nhận ra bạn ở
          mọi kênh: nhớ hồ sơ da và đơn đã mua.
        </p>
      </header>

      {linked.map((l) => (
        <p key={`${l.channel}-${l.linkedAt}`} className="notice" role="status">
          Đã liên kết tài khoản {CHANNEL_LABEL[l.channel]} {l.channelUser}.
        </p>
      ))}

      <section className="link-card" aria-labelledby="link-code-title">
        <h2 id="link-code-title">1. Lấy mã liên kết</h2>
        {codeActive && code ? (
          <>
            <div className="link-code-row">
              <output className="link-code" aria-label={`Mã liên kết ${code.code.split('').join(' ')}`}>{code.code}</output>
              <button type="button" className="btn btn-ghost" onClick={copy}>{copied ? 'Đã sao chép' : 'Sao chép'}</button>
            </div>
            <p className="link-timer" role="timer">
              Mã dùng được 1 lần, còn hiệu lực <strong>{clock(codeLeft)}</strong>.
            </p>
            <ol className="link-steps">
              <li>Mở chat với shop trên Shopee hoặc TikTok Shop.</li>
              <li>Gõ đúng mã <strong>{code.code}</strong> và gửi.</li>
              <li>Quay lại trang này để xác nhận liên kết (trang tự cập nhật).</li>
            </ol>
            <button type="button" className="btn btn-ghost link-renew" onClick={getCode} disabled={busy}>
              {busy ? 'Đang lấy mã…' : 'Lấy mã khác'}
            </button>
            <small className="muted">Lấy mã khác thì mã đang hiện không dùng được nữa.</small>
          </>
        ) : (
          <>
            {code && <p className="notice notice-warning" role="status">Mã {code.code} đã hết hạn.</p>}
            <p className="muted">Mã gồm 6 ký tự, có hiệu lực 15 phút và chỉ dùng được 1 lần.</p>
            <button type="button" className="btn btn-primary" onClick={getCode} disabled={busy}>
              {busy ? 'Đang lấy mã…' : code ? 'Lấy mã mới' : 'Lấy mã liên kết'}
            </button>
          </>
        )}
        {error && <p className="error" role="alert">{error}</p>}
      </section>

      <section className="link-card" aria-labelledby="link-requests-title">
        <h2 id="link-requests-title">2. Xác nhận liên kết</h2>
        {loadError && <p className="error" role="alert">{loadError}</p>}
        {requests.length === 0 ? (
          <p className="muted" aria-live="polite">
            {codeActive ? 'Đang chờ bạn gõ mã trong chat…' : 'Chưa có yêu cầu liên kết nào.'}
          </p>
        ) : (
          <ul className="link-requests" aria-live="polite">
            {requests.map((r) => (
              <RequestCard key={r.id} request={r} token={token} now={now} onLinked={onLinked}
                           onGone={refresh} onUnauthorized={handle401} />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

interface RequestCardProps {
  request: LinkRequest
  token: string
  now: number
  onLinked: (view: LinkedView) => void
  onGone: () => Promise<void>
  onUnauthorized: (err: unknown) => boolean
}

function RequestCard({ request, token, now, onLinked, onGone, onUnauthorized }: RequestCardProps) {
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const channel = CHANNEL_LABEL[request.channel]
  const left = secondsLeft(request.confirmBefore, now)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      if (onUnauthorized(err)) return
      setError(linkErrorText(err))
      if (err instanceof ApiError && (err.status === 410 || err.status === 404)) void onGone()
    } finally {
      setBusy(false)
    }
  }

  const confirm = () => run(async () => onLinked(await confirmLink(token, request.id)))
  const reject = () => run(async () => {
    await rejectLink(token, request.id)
    await onGone()
  })

  return (
    <li className="link-request">
      <p className="link-request-title">
        Liên kết tài khoản <strong>{channel} {request.channelUser}</strong> với tài khoản này?
      </p>
      <small className="muted">
        Tài khoản này vừa gõ mã của bạn trong chat. Còn {clock(left)} để xác nhận. Không phải bạn thì bấm "Không phải tôi".
      </small>
      <label className="login-consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          Tôi đồng ý cho shop gộp lịch sử chat và đơn hàng của tài khoản {channel} này vào tài khoản web, để tư vấn và
          ghi nhớ tôi ở các kênh (Luật Bảo vệ dữ liệu cá nhân).
        </span>
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="login-actions">
        <button type="button" className="btn btn-primary" onClick={confirm} disabled={busy || !consent || left === 0}>
          {busy ? 'Đang xử lý…' : 'Xác nhận liên kết'}
        </button>
        <button type="button" className="btn btn-ghost" onClick={reject} disabled={busy}>Không phải tôi</button>
      </div>
    </li>
  )
}
