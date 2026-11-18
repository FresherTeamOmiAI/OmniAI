import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../../shared/lib/http'
import { requestOtp, verifyOtp } from './api'
import { startSession, useCustomerSession } from './session'
import type { AuthProblem } from './types'

type Step = 'email' | 'code'

/** Chỉ quay về đường trong site (chặn open redirect qua ?next=//evil.com). */
function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

function waitText(seconds: number): string {
  if (seconds < 60) return `${seconds} giây`
  return `${Math.ceil(seconds / 60)} phút`
}

function errorText(err: unknown): string {
  if (!(err instanceof ApiError)) return 'Không kết nối được máy chủ, bạn thử lại sau nhé.'
  const problem = err.problem as AuthProblem | null
  const wait = problem?.retryAfterSeconds ?? 60
  switch (problem?.code) {
    case 'otp_invalid':
      return `Mã không đúng. Bạn còn ${problem.attemptsLeft} lần thử.`
    case 'otp_expired':
      return 'Mã đã hết hạn hoặc đã được dùng. Bấm "Gửi lại mã" để nhận mã mới.'
    case 'otp_locked':
      return `Bạn nhập sai quá nhiều lần nên email tạm bị khoá. Thử lại sau ${waitText(wait)}.`
    case 'otp_rate_limited':
      return `Bạn đã yêu cầu mã nhiều lần. Thử lại sau ${waitText(wait)}.`
    case 'otp_send_failed':
      return 'Chưa gửi được email, bạn thử lại sau nhé.'
  }
  if (problem?.errors?.length) return problem.errors.map((e) => e.message).join('. ')
  return err.message
}

/** Đăng ký = đăng nhập bằng email + OTP (US-13, SRS UC-07). Email chưa có tài khoản thì tự tạo. */
export function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const session = useCustomerSession()

  const [step, setStep] = useState<Step>('email')
  const [email, setEmail] = useState('')
  const [consent, setConsent] = useState(false)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [resendAt, setResendAt] = useState(0)
  const [now, setNow] = useState(() => Date.now())

  const resendIn = Math.max(0, Math.ceil((resendAt - now) / 1000))

  useEffect(() => {
    if (resendAt <= Date.now()) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [resendAt])

  async function send() {
    const target = email.trim()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const issued = await requestOtp(target)
      setEmail(target)
      setStep('code')
      setCode('')
      setNow(Date.now())
      setResendAt(Date.now() + issued.resendAfterSeconds * 1000)
      setNotice(`Đã gửi mã 6 số tới ${target}. Mã có hiệu lực ${Math.round(issued.expiresInSeconds / 60)} phút.`)
    } catch (err) {
      const problem = err instanceof ApiError ? (err.problem as AuthProblem | null) : null
      // Vừa gửi mã (vd. tải lại trang): cho nhập mã đã nhận, chờ hết thời gian mới gửi lại.
      if (problem?.code === 'otp_rate_limited' && (problem.retryAfterSeconds ?? 0) <= 60) {
        setEmail(target)
        setStep('code')
        setNow(Date.now())
        setResendAt(Date.now() + (problem.retryAfterSeconds ?? 60) * 1000)
        setNotice(`Mã vừa được gửi tới ${target}, bạn kiểm tra email nhé.`)
      } else {
        setError(errorText(err))
      }
    } finally {
      setBusy(false)
    }
  }

  async function submitEmail(e: FormEvent) {
    e.preventDefault()
    if (!consent) {
      setError('Bạn cần đồng ý để shop xử lý dữ liệu cá nhân.')
      return
    }
    await send()
  }

  async function submitCode(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      startSession(await verifyOtp(email, code))
      navigate(next, { replace: true })
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  if (session && step === 'email') {
    return (
      <main id="main" tabIndex={-1} className="account">
        <div className="login-form">
          <h1>Bạn đã đăng nhập</h1>
          <p>Đang dùng tài khoản <strong>{session.customer.email}</strong>.</p>
          <Link to={next} className="btn btn-primary">Tiếp tục mua sắm</Link>
        </div>
      </main>
    )
  }

  if (step === 'email') {
    return (
      <main id="main" tabIndex={-1} className="account">
        <form className="login-form" onSubmit={submitEmail} noValidate>
          <h1>Đăng nhập / Đăng ký</h1>
          <p className="muted">Nhập email, shop gửi mã 6 số để đăng nhập. Email chưa có tài khoản thì tự tạo mới.</p>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email"
                   placeholder="ban@example.com" required autoFocus />
          </label>
          <label className="login-consent">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>
              Tôi đồng ý cho shop lưu email, lịch sử chat và đơn hàng để tư vấn và ghi nhớ tôi ở các kênh
              (Luật Bảo vệ dữ liệu cá nhân).
            </span>
          </label>
          {error && <p className="error" role="alert">{error}</p>}
          <button type="submit" className="btn btn-primary" disabled={busy || !email.trim()}>
            {busy ? 'Đang gửi…' : 'Gửi mã'}
          </button>
        </form>
      </main>
    )
  }

  return (
    <main id="main" tabIndex={-1} className="account">
      <form className="login-form" onSubmit={submitCode}>
        <h1>Nhập mã xác thực</h1>
        {notice && <p className="notice" role="status">{notice}</p>}
        {import.meta.env.DEV && <p className="muted">Khi chạy dev, mã được in trong log backend (OTP_SENDER=log).</p>}
        <label>
          Mã 6 số
          <input className="otp-input" value={code} inputMode="numeric" autoComplete="one-time-code" maxLength={6}
                 pattern="\d{6}" required autoFocus
                 onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="btn btn-primary" disabled={busy || code.length !== 6}>
          {busy ? 'Đang kiểm tra…' : 'Đăng nhập'}
        </button>
        <div className="login-actions">
          <button type="button" className="btn btn-sm" disabled={busy || resendIn > 0} onClick={send}>
            {resendIn > 0 ? `Gửi lại mã sau ${resendIn}s` : 'Gửi lại mã'}
          </button>
          <button type="button" className="btn btn-sm" disabled={busy}
                  onClick={() => { setStep('email'); setError(null); setNotice(null) }}>
            Đổi email
          </button>
        </div>
      </form>
    </main>
  )
}
