import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../shared/lib/http'
import { formatVnd } from '../../shared/lib/format'
import { WEB_CHANNEL } from '../../shared/types'
import { CATEGORIES, labelOf, SKIN_CONCERNS, SKIN_TYPES, USAGE_TIMES } from '../../shared/vocabulary'
import { fetchProduct } from './api'
import { Attribution } from './Attribution'
import { DemoOrderBox } from './DemoOrderBox'
import { ProductImage } from './ProductImage'
import type { Product } from './types'

const csv = (value: string | null) => (value ? value.split(',').map((s) => s.trim()).filter(Boolean) : [])

type Result = { status: 'ok'; product: Product } | { status: 'missing' } | { status: 'error'; message: string }

// Chi tiết sản phẩm (US-09, T-09.1; giao diện US-56): ảnh, giá web, thành phần INCI đầy đủ, thông tin dùng, nguồn dữ liệu.
// Sản phẩm ẩn hoặc không tồn tại: API trả 404 (SRS G-06), trang báo "không còn bán". Owner: LucVT.
export function ProductDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [attempt, setAttempt] = useState(0)
  // Kết quả gắn với khoá của lần tải (id + lần thử); khoá khác nghĩa là đang tải.
  const key = `${id}#${attempt}`
  const [loaded, setLoaded] = useState<{ key: string; result: Result } | null>(null)
  const state = loaded?.key === key ? loaded.result : null

  useEffect(() => {
    let stale = false
    fetchProduct(id)
      .then((product) => !stale && setLoaded({ key, result: { status: 'ok', product } }))
      .catch((e: Error) => {
        if (stale) return
        const missing = e instanceof ApiError && (e.status === 404 || e.status === 400)
        setLoaded({ key, result: missing ? { status: 'missing' } : { status: 'error', message: e.message } })
      })
    return () => {
      stale = true
    }
  }, [id, key])

  // Vào thẳng bằng link thì không có trang trước trong app: về store thay vì thoát khỏi site.
  const back = () => (window.history.state?.idx > 0 ? navigate(-1) : navigate('/'))

  return (
    <main id="main" tabIndex={-1} className="store product-detail">
      <button type="button" className="btn btn-ghost btn-sm back-link" onClick={back}>‹ Quay lại cửa hàng</button>
      {state === null && <DetailSkeleton />}
      {state?.status === 'error' && (
        <div className="state state-error" role="alert">
          <p>Không tải được sản phẩm: {state.message}</p>
          <button type="button" className="btn btn-sm" onClick={() => setAttempt((n) => n + 1)}>Thử lại</button>
        </div>
      )}
      {state?.status === 'missing' && (
        <div className="state">
          <h1 className="sr-only">Không tìm thấy sản phẩm</h1>
          <p>Sản phẩm không tồn tại hoặc đã ngừng bán.</p>
          <Link to="/" className="btn btn-sm">Xem sản phẩm khác</Link>
        </div>
      )}
      {state?.status === 'ok' && <Detail product={state.product} />}
      <Attribution />
    </main>
  )
}

function DetailSkeleton() {
  return (
    <div className="detail" aria-busy="true">
      <span className="sr-only" role="status">Đang tải…</span>
      <div className="detail-image" aria-hidden="true"><span className="skeleton" /></div>
      <div className="detail-info" aria-hidden="true">
        <span className="skeleton skeleton-line short" />
        <span className="skeleton skeleton-line" />
        <span className="skeleton skeleton-line short" />
      </div>
    </div>
  )
}

function Detail({ product: p }: { product: Product }) {
  const price = p.prices[WEB_CHANNEL]
  const inci = csv(p.ingredientsInci)
  return (
    <article className="detail">
      <div className="detail-image">
        <ProductImage src={p.imageUrl} alt={p.name} lazy={false} />
      </div>
      <div className="detail-info">
        <span className="product-brand">{p.brand}</span>
        <h1 className="detail-title">{p.name}</h1>
        <div className="detail-price-row">
          <p className="product-price detail-price">{price != null ? formatVnd(price) : 'Liên hệ'}</p>
          <p className={p.stock > 0 ? 'product-stock badge badge-on' : 'product-stock badge badge-off'}>
            {p.stock > 0 ? `Còn ${p.stock} sản phẩm` : 'Hết hàng'}
          </p>
        </div>
        <DemoOrderBox key={p.id} product={p} />
        {p.description && <p className="detail-desc">{p.description}</p>}

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

        <section className="detail-section" aria-labelledby="inci-title">
          <h2 id="inci-title">Thành phần (INCI, {inci.length} chất)</h2>
          <p className="detail-inci">{inci.join(', ')}</p>
        </section>

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
