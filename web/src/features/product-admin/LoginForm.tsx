import { useState } from 'react'
import { ApiError } from '../../shared/lib/http'
import { listProducts } from './api'
import { basicToken } from './auth'

interface Props {
  notice: string | null
  onLogin: (token: string) => void
}

// Đăng nhập admin bằng tài khoản HTTP Basic của backend (SRS Q-01). Thử gọi API 1 lần để biết đúng/sai.
export function LoginForm({ notice, onLogin }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const token = basicToken(username, password)
    setBusy(true)
    try {
      await listProducts(token, { q: '', active: '', page: 0, size: 1 })
      onLogin(token)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Sai tài khoản hoặc mật khẩu.' : (e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="login-form" onSubmit={submit}>
      <h2>Đăng nhập admin</h2>
      <p className="muted">
        Tài khoản là <code>ADMIN_USERNAME</code> / <code>ADMIN_PASSWORD</code> trong <code>.env</code> của backend (mặc định khi dev: admin / admin).
      </p>
      {notice && <p className="notice" role="status">{notice}</p>}
      <label>
        Tên đăng nhập
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
      </label>
      <label>
        Mật khẩu
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Đang kiểm tra…' : 'Đăng nhập'}
      </button>
    </form>
  )
}
