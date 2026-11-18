import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { formatVnd } from '../../shared/lib/format'
import { WEB_CHANNEL } from '../../shared/types'
import { CATEGORIES, SKIN_TYPES } from '../../shared/vocabulary'
import { fetchProducts, STORE_PAGE_SIZE, type StoreQuery } from './api'
import { Attribution } from './Attribution'
import { ProductImage } from './ProductImage'
import type { Product } from './types'

// Khoảng giá web (VND) cho bộ lọc. Giá trị = "min-max", bỏ trống một đầu = không giới hạn.
const PRICE_RANGES = [
  { value: '', label: 'Mọi mức giá' },
  { value: '-200000', label: 'Dưới 200.000đ' },
  { value: '200000-400000', label: '200.000đ – 400.000đ' },
  { value: '400000-700000', label: '400.000đ – 700.000đ' },
  { value: '700000-', label: 'Trên 700.000đ' },
]

const SKELETON_CARDS = 8

function toQuery(params: URLSearchParams): StoreQuery {
  const [min, max] = (params.get('price') ?? '').split('-')
  return {
    skinType: params.get('skin') ?? '',
    category: params.get('cat') ?? '',
    minPrice: min ? Number(min) : null,
    maxPrice: max ? Number(max) : null,
  }
}

interface Loaded {
  key: string
  items: Product[]
  total: number
  error: string | null
}

// Trang store demo (US-09, T-09.1; giao diện US-56). Bộ lọc và trang hiện tại nằm trên URL (?skin=&cat=&price=&page=)
// để mở chi tiết rồi bấm "Quay lại" vẫn giữ nguyên chỗ đang xem. Owner: LucVT.
export function StorePage() {
  const [params, setParams] = useSearchParams()
  const skin = params.get('skin') ?? ''
  const cat = params.get('cat') ?? ''
  const price = params.get('price') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  // Tăng attempt để tải lại cùng bộ lọc sau khi lỗi.
  const [attempt, setAttempt] = useState(0)

  // Kết quả gắn với khoá của lần tải; khoá hiện tại khác khoá kết quả nghĩa là đang tải.
  const key = `${skin}|${cat}|${price}|${page}|${attempt}`
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let stale = false
    fetchProducts(toQuery(new URLSearchParams({ skin, cat, price })), page)
      .then((result) => !stale && setLoaded({ key, items: result.items, total: result.total, error: null }))
      .catch((e: Error) => !stale && setLoaded({ key, items: [], total: 0, error: e.message }))
    return () => {
      stale = true
    }
  }, [skin, cat, price, page, key])

  const loading = loaded?.key !== key
  const products = loaded?.items ?? []
  const total = loaded?.total ?? 0
  const error = loading ? null : loaded?.error ?? null
  const firstLoad = loading && products.length === 0
  const totalPages = Math.max(1, Math.ceil(total / STORE_PAGE_SIZE))

  /** Đổi 1 bộ lọc thì về trang 1; giá trị rỗng thì bỏ khỏi URL cho gọn. */
  function setFilter(name: 'skin' | 'cat' | 'price', value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    next.delete('page')
    setParams(next)
  }

  function goTo(nextPage: number) {
    const next = new URLSearchParams(params)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setParams(next)
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
  }

  const filtered = skin !== '' || cat !== '' || price !== ''
  const clearFilters = () => setParams(new URLSearchParams())

  return (
    <main id="main" tabIndex={-1} className="store">
      <header className="page-head">
        <h1>Chăm sóc da</h1>
        <p className="page-lead">
          Sản phẩm thật của các hãng có bán ở Việt Nam. Lọc theo loại da, hoặc mở trợ lý tư vấn ở góc phải để được gợi ý.
        </p>
      </header>

      <div className="store-filters">
        <span className="filter-label" id="skin-filter-label">Loại da</span>
        <div className="chip-row" role="group" aria-labelledby="skin-filter-label">
          {[{ value: '', label: 'Mọi loại da' }, ...SKIN_TYPES].map((s) => (
            <button key={s.value} type="button" className={s.value === skin ? 'chip chip-active' : 'chip'}
              aria-pressed={s.value === skin} onClick={() => setFilter('skin', s.value)}>
              {s.label}
            </button>
          ))}
        </div>
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
        {filtered && <button type="button" className="btn btn-ghost btn-sm" onClick={clearFilters}>Bỏ lọc</button>}
        <span className="toolbar-count" aria-live="polite">{firstLoad ? 'Đang tải…' : `${total} sản phẩm`}</span>
      </div>

      {error && (
        <div className="state state-error" role="alert">
          <p>Không tải được danh sách sản phẩm: {error}</p>
          <button type="button" className="btn btn-sm" onClick={() => setAttempt((n) => n + 1)}>Thử lại</button>
        </div>
      )}
      {!loading && !error && total === 0 && (
        <div className="state">
          <p>Không có sản phẩm nào khớp bộ lọc.</p>
          {filtered && <button type="button" className="btn btn-sm" onClick={clearFilters}>Bỏ lọc</button>}
        </div>
      )}
      {!loading && !error && total > 0 && page >= totalPages && (
        <div className="state">
          <p>Trang {page + 1} không có sản phẩm.</p>
          <button type="button" className="btn btn-sm" onClick={() => goTo(0)}>Về trang 1</button>
        </div>
      )}

      {firstLoad ? (
        <ul className="product-grid" aria-hidden="true">
          {Array.from({ length: SKELETON_CARDS }, (_, i) => (
            <li key={i} className="product-card skeleton-card">
              <div className="product-media"><span className="skeleton" /></div>
              <div className="product-body">
                <span className="skeleton skeleton-line short" />
                <span className="skeleton skeleton-line" />
              </div>
              <div className="product-meta"><span className="skeleton skeleton-line short" /></div>
            </li>
          ))}
        </ul>
      ) : products.length > 0 && (
        <ul className={loading ? 'product-grid is-loading' : 'product-grid'} aria-busy={loading}>
          {products.map((p) => (
            <li key={p.id} className="product-card">
              <Link to={`/products/${p.id}`} className="product-link">
                <div className="product-media">
                  <ProductImage src={p.imageUrl} alt="" />
                </div>
                <div className="product-body">
                  <span className="product-brand">{p.brand}</span>
                  <h3 className="product-name">{p.name}</h3>
                </div>
              </Link>
              <div className="product-meta">
                <p className="product-price">{p.prices[WEB_CHANNEL] != null ? formatVnd(p.prices[WEB_CHANNEL]) : 'Liên hệ'}</p>
                <p className={p.stock > 0 ? 'product-stock badge badge-on' : 'product-stock badge badge-off'}>
                  {p.stock > 0 ? `Còn ${p.stock}` : 'Hết hàng'}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && page < totalPages && (
        <nav className="pager" aria-label="Phân trang">
          <button type="button" className="btn btn-sm" disabled={page === 0 || loading} onClick={() => goTo(page - 1)}>‹ Trước</button>
          <span>Trang {page + 1}/{totalPages}</span>
          <button type="button" className="btn btn-sm" disabled={page + 1 >= totalPages || loading} onClick={() => goTo(page + 1)}>Sau ›</button>
        </nav>
      )}
      <Attribution />
    </main>
  )
}
