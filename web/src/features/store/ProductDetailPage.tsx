import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/lib/http'
import { formatVnd } from '../../shared/lib/format'
import { WEB_CHANNEL } from '../../shared/types'
import { CATEGORIES, labelOf, SKIN_CONCERNS, SKIN_TYPES, USAGE_TIMES } from '../../shared/vocabulary'
import { fetchProduct } from './api'
import { Attribution } from './Attribution'
import { DemoOrderBox } from './DemoOrderBox'
import type { Product } from './types'

const csv = (value: string | null) => (value ? value.split(',').map((s) => s.trim()).filter(Boolean) : [])

type Result = { status: 'ok'; product: Product } | { status: 'missing' } | { status: 'error'; message: string }

// Chi tiết sản phẩm (US-09, T-09.1): ảnh, giá web, thành phần INCI đầy đủ, thông tin dùng, nguồn dữ liệu.
// Sản phẩm ẩn hoặc không tồn tại: API trả 404 (SRS G-06), trang báo "không còn bán". Owner: LucVT.
export function ProductDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  // Kết quả gắn với id của lần tải; id trên URL khác id của kết quả nghĩa là đang tải.
  const [loaded, setLoaded] = useState<{ id: string; result: Result } | null>(null)
  const state = loaded?.id === id ? loaded.result : null

  useEffect(() => {
    let stale = false
    fetchProduct(id)
      .then((product) => !stale && setLoaded({ id, result: { status: 'ok', product } }))
      .catch((e: Error) => {
        if (stale) return
        const missing = e instanceof ApiError && (e.status === 404 || e.status === 400)
        setLoaded({ id, result: missing ? { status: 'missing' } : { status: 'error', message: e.message } })
      })
    return () => {
      stale = true
    }
  }, [id])

  // Vào thẳng bằng link thì không có trang trước trong app: về store thay vì thoát khỏi site.
  const back = () => (window.history.state?.idx > 0 ? navigate(-1) : navigate('/'))

  return (
    <main className="store product-detail">
      <button type="button" className="btn btn-sm" onClick={back}>‹ Quay lại cửa hàng</button>
      {state === null && <p className="muted">Đang tải…</p>}
      {state?.status === 'error' && <p className="error">{state.message}</p>}
      {state?.status === 'missing' && (
        <p className="muted">Sản phẩm không tồn tại hoặc đã ngừng bán. <Link to="/">Xem sản phẩm khác</Link>.</p>
      )}
      {state?.status === 'ok' && <Detail product={state.product} />}
      <Attribution />
    </main>
  )
}

function Detail({ product: p }: { product: Product }) {
  const price = p.prices[WEB_CHANNEL]
  const inci = csv(p.ingredientsInci)
  return (
    <article className="detail">
      <div className="product-image detail-image">
        {p.imageUrl ? <img src={p.imageUrl} alt={p.name} /> : <span aria-hidden="true">Chưa có ảnh</span>}
      </div>
      <div className="detail-info">
        <span className="product-brand">{p.brand}</span>
        <h2>{p.name}</h2>
        <p className="product-price detail-price">{price != null ? formatVnd(price) : 'Liên hệ'}</p>
        <p className="product-stock">{p.stock > 0 ? `Còn ${p.stock} sản phẩm` : 'Hết hàng'}</p>
        <DemoOrderBox key={p.id} product={p} />
        {p.description && <p>{p.description}</p>}

        <dl className="detail-facts">
          <dt>Loại sản phẩm</dt>
          <dd>{labelOf(CATEGORIES, p.category)}</dd>
          <dt>Loại da phù hợp</dt>
          <dd>{csv(p.skinTypes).map((s) => labelOf(SKIN_TYPES, s)).join(', ')}</dd>
          {p.skinConcerns && (
            <>
              <dt>Vấn đề da</dt>
              <dd>{csv(p.skinConcerns).map((s) => labelOf(SKIN_CONCERNS, s)).join(', ')}</dd>
            </>
          )}
          {p.usageTime && (
            <>
              <dt>Buổi dùng</dt>
              <dd>{labelOf(USAGE_TIMES, p.usageTime)}</dd>
            </>
          )}
          <dt>Dung tích</dt>
          <dd>{p.sizeMl} ml</dd>
          <dt>Dùng trong</dt>
          <dd>{p.paoMonths} tháng sau khi mở</dd>
          <dt>Mã sản phẩm</dt>
          <dd><code>{p.sku}</code></dd>
        </dl>

        <h3>Thành phần (INCI, {inci.length} chất)</h3>
        <p className="detail-inci">{inci.join(', ')}</p>

        {(p.sourceUrl || p.imageLicenseNote) && (
          <p className="product-source">
            {p.sourceUrl && <>Nguồn dữ liệu: <a href={p.sourceUrl} target="_blank" rel="noreferrer">trang sản phẩm trên Open Beauty Facts</a>. </>}
            {p.imageLicenseNote}
          </p>
        )}
      </div>
    </article>
  )
}
