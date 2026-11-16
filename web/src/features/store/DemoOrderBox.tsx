import { useState } from 'react'
import { ApiError } from '../../shared/lib/http'
import { formatVnd } from '../../shared/lib/format'
import type { OrderView } from '../../shared/types'
import { createDemoOrder } from './api'
import type { Product } from './types'

/** Khớp OrderService.MAX_QUANTITY của backend. */
const MAX_QUANTITY = 10

// Đặt hàng ảo trên trang chi tiết (T-09.3, SRS UC-06): chọn số lượng, bấm "Đặt thử", backend tạo đơn DEMO.
// Không thanh toán, không giao hàng, không trừ tồn kho. Hết hàng thì nút tắt. Owner: LucVT.
export function DemoOrderBox({ product }: { product: Product }) {
  const max = Math.min(MAX_QUANTITY, product.stock)
  const [quantity, setQuantity] = useState(1)
  const [busy, setBusy] = useState(false)
  const [order, setOrder] = useState<OrderView | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (product.stock <= 0) {
    return <p className="order-box muted">Hết hàng, tạm thời không đặt được.</p>
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setOrder(await createDemoOrder([{ productId: product.id, quantity }]))
    } catch (e) {
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
        <span className="muted">{order.notice}</span>
        <button type="button" className="btn btn-sm" onClick={() => setOrder(null)}>Đặt thêm</button>
      </div>
    )
  }

  return (
    <form className="order-box" onSubmit={submit}>
      <label htmlFor="order-qty">Số lượng</label>
      <input id="order-qty" type="number" inputMode="numeric" min={1} max={max} step={1} value={quantity}
        onChange={(e) => setQuantity(Math.max(1, Math.min(max, Number(e.target.value) || 1)))} />
      <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Đang đặt…' : 'Đặt thử'}</button>
      <small className="muted order-hint">Cửa hàng thử nghiệm: không thanh toán, không giao hàng.</small>
      {error && <small className="field-error order-hint" role="alert">{error}</small>}
    </form>
  )
}
