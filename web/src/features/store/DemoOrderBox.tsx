import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { logout, useCustomerSession } from '../account'
import { ApiError } from '../../shared/lib/http'
import { formatVnd } from '../../shared/lib/format'
import type { OrderView } from '../../shared/types'
import { createDemoOrder } from './api'
import type { Product } from './types'

/** Khớp OrderService.MAX_QUANTITY của backend. */
const MAX_QUANTITY = 10

// Đặt hàng ảo trên trang chi tiết (T-09.3, SRS UC-06): chọn số lượng, bấm "Đặt thử", backend tạo đơn DEMO.
// Không thanh toán, không giao hàng, không trừ tồn kho. Hết hàng thì nút tắt. Owner: LucVT.
// Từ S3 phải đăng nhập mới đặt được, đơn gắn tài khoản (SRS G-04, decisions #40).
export function DemoOrderBox({ product }: { product: Product }) {
  const session = useCustomerSession()
  const location = useLocation()
  const max = Math.min(MAX_QUANTITY, product.stock)
  const [quantity, setQuantity] = useState(1)
  const [busy, setBusy] = useState(false)
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (product.stock <= 0) {
    return <p className="order-box muted" role="status">Hết hàng, tạm thời không đặt được.</p>
  }

  if (!session && !order) {
    const next = encodeURIComponent(location.pathname + location.search)
    return (
      <div className="order-box">
        <Link to={`/login?next=${next}`} className="btn btn-primary">Đăng nhập để đặt thử</Link>
        {error && <small className="field-error order-hint" role="alert">{error}</small>}
        <small className="muted order-hint">Đơn thử nghiệm được gắn với tài khoản của bạn. Không thanh toán, không giao hàng.</small>
      </div>
    )
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!session) return
    setBusy(true)
    setError(null)
    try {
      setOrder(await createDemoOrder([{ productId: product.id, quantity }], session.token))
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        logout()
        setError('Phiên đăng nhập đã hết hạn, đăng nhập lại để đặt.')
        return
      }
      const problem = e instanceof ApiError ? e.problem : null
      setError(problem?.errors?.map((v) => v.message).join('; ') || (e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (order) {
    return (
      <div className="order-box order-done" role="status">
        <strong>Đã tạo đơn thử nghiệm #{order.id}</strong>
        <span>
          {order.items.map((i) => `${i.quantity} × ${i.name}`).join(', ')}. Tổng {formatVnd(order.total)}. Trạng thái: {order.status}.
        </span>
        {session && <span>Đơn gắn với tài khoản {session.customer.email}.</span>}
        <span className="muted">{order.notice}</span>
        <button type="button" className="btn btn-sm" onClick={() => setOrder(null)}>Đặt thêm</button>
      </div>
    )
  }

  return (
    <form className="order-box" onSubmit={submit} aria-describedby="order-hint">
      <label htmlFor="order-qty">Số lượng</label>
      <input id="order-qty" type="number" inputMode="numeric" min={1} max={max} step={1} value={quantity} disabled={busy}
        onChange={(e) => setQuantity(Math.max(1, Math.min(max, Number(e.target.value) || 1)))} />
      <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Đang đặt…' : 'Đặt thử'}</button>
      <small className="muted order-hint" id="order-hint">Cửa hàng thử nghiệm: không thanh toán, không giao hàng.</small>
      {error && <small className="field-error order-hint" role="alert">{error}</small>}
    </form>
  )
}
