import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { formatVnd } from '../../shared/lib/format'
import { WEB_CHANNEL } from '../../shared/types'
import { CATEGORIES, SKIN_TYPES } from '../../shared/vocabulary'
import { fetchProducts, STORE_PAGE_SIZE, type StoreQuery } from './api'
import { Attribution } from './Attribution'
import type { Product } from './types'

// Khoảng giá web (VND) cho bộ lọc. Giá trị = "min-max", bỏ trống một đầu = không giới hạn.
const PRICE_RANGES = [
  { value: '', label: 'Mọi mức giá' },
  { value: '-200000', label: 'Dưới 200.000đ' },
  { value: '200000-400000', label: '200.000đ – 400.000đ' },
  { value: '400000-700000', label: '400.000đ – 700.000đ' },
  { value: '700000-', label: 'Trên 700.000đ' },
]

function toQuery(params: URLSearchParams): StoreQuery {
  const [min, max] = (params.get('price') ?? '').split('-')
  return {
    skinType: params.get('skin') ?? '',
    category: params.get('cat') ?? '',
    minPrice: min ? Number(min) : null,
    maxPrice: max ? Number(max) : null,
  }
}

// Trang store demo (US-09, T-09.1). Bộ lọc và trang hiện tại nằm trên URL (?skin=&cat=&price=&page=)
// để mở chi tiết rồi bấm "Quay lại" vẫn giữ nguyên chỗ đang xem. Owner: LucVT.
export function StorePage() {
  const [params, setParams] = useSearchParams()
  const skin = params.get('skin') ?? ''
  const cat = params.get('cat') ?? ''
  const price = params.get('price') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)

  const [products, setProducts] = useState<Product[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let stale = false
    fetchProducts(toQuery(new URLSearchParams({ skin, cat, price })), page)
      .then((result) => {
        if (stale) return
        setProducts(result.items)
        setTotal(result.total)
        setError(null)
      })
      .catch((e: Error) => !stale && setError(e.message))
    return () => {
      stale = true
    }
  }, [skin, cat, price, page])

  const totalPages = Math.max(1, Math.ceil(total / STORE_PAGE_SIZE))

  /** Đổi 1 bộ lọc thì về trang 1; giá trị rỗng thì bỏ khỏi URL cho gọn. */
  function setFilter(key: 'skin' | 'cat' | 'price', value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setParams(next)
  }

  function goTo(nextPage: number) {
    const next = new URLSearchParams(params)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setParams(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const filtered = skin !== '' || cat !== '' || price !== ''

  return (
    <main className="store">
      <div className="store-filters" role="group" aria-label="Lọc theo loại da">
        {[{ value: '', label: 'Mọi loại da' }, ...SKIN_TYPES].map((s) => (
          <button key={s.value} type="button" className={s.value === skin ? 'chip chip-active' : 'chip'}
            aria-pressed={s.value === skin} onClick={() => setFilter('skin', s.value)}>
            {s.label}
          </button>
        ))}
      </div>
      <div className="store-toolbar">
        <label className="sr-only" htmlFor="store-cat">Loại sản phẩm</label>
        <select id="store-cat" value={cat} onChange={(e) => setFilter('cat', e.target.value)}>
          <option value="">Mọi loại sản phẩm</option>
          {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
        <label className="sr-only" htmlFor="store-price">Khoảng giá</label>
        <select id="store-price" value={price} onChange={(e) => setFilter('price', e.target.value)}>
          {PRICE_RANGES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
        {filtered && <button type="button" className="btn btn-sm" onClick={() => setParams(new URLSearchParams())}>Bỏ lọc</button>}
        <span className="toolbar-count muted" aria-live="polite">{total} sản phẩm</span>
      </div>
      {error && <p className="error">{error}</p>}
      {!error && total === 0 && <p className="muted">Không có sản phẩm nào khớp bộ lọc.</p>}
      <ul className="product-grid">
        {products.map((p) => (
          <li key={p.id} className="product-card">
            <Link to={`/products/${p.id}`} className="product-link">
              <div className="product-image">
                {p.imageUrl ? <img src={p.imageUrl} alt={p.name} loading="lazy" /> : <span aria-hidden="true">Chưa có ảnh</span>}
              </div>
              <span className="product-brand">{p.brand}</span>
              <h3>{p.name}</h3>
            </Link>
            <p className="product-inci">{p.ingredientsInci}</p>
            <p className="product-price">{p.prices[WEB_CHANNEL] != null ? formatVnd(p.prices[WEB_CHANNEL]) : 'Liên hệ'}</p>
            <p className="product-stock">{p.stock > 0 ? `Còn ${p.stock}` : 'Hết hàng'}</p>
          </li>
        ))}
      </ul>
      {totalPages > 1 && (
        <nav className="pager" aria-label="Phân trang">
          <button type="button" className="btn btn-sm" disabled={page === 0} onClick={() => goTo(page - 1)}>‹ Trước</button>
          <span>Trang {page + 1}/{totalPages}</span>
          <button type="button" className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => goTo(page + 1)}>Sau ›</button>
        </nav>
      )}
      <Attribution />
    </main>
  )
}
