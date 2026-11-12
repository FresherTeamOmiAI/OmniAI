import { useEffect, useState } from 'react'
import { formatVnd } from '../../shared/lib/format'
import { fetchProducts } from './api'
import type { Product } from './types'

const SKIN_TYPES = [
  { value: '', label: 'Tất cả' },
  { value: 'oily', label: 'Da dầu' },
  { value: 'dry', label: 'Da khô' },
  { value: 'combination', label: 'Da hỗn hợp' },
  { value: 'sensitive', label: 'Da nhạy cảm' },
]

// Trang store demo. Owner: LucVT (sprint 2: trang chi tiết, giỏ hàng, đơn ảo).
export function StorePage() {
  const [skinType, setSkinType] = useState('')
  const [products, setProducts] = useState<Product[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchProducts(skinType)
      .then((page) => {
        setProducts(page.items)
        setError(null)
      })
      .catch((e: Error) => setError(e.message))
  }, [skinType])

  return (
    <main className="store">
      <div className="store-filters" role="group" aria-label="Lọc theo loại da">
        {SKIN_TYPES.map((s) => (
          <button key={s.value} type="button" className={s.value === skinType ? 'chip chip-active' : 'chip'} onClick={() => setSkinType(s.value)}>
            {s.label}
          </button>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      <ul className="product-grid">
        {products.map((p) => (
          <li key={p.id} className="product-card">
            <div className="product-image">
              {p.imageUrl ? <img src={p.imageUrl} alt={p.name} loading="lazy" /> : <span aria-hidden="true">Chưa có ảnh</span>}
            </div>
            <span className="product-brand">{p.brand}</span>
            <h3>{p.name}</h3>
            <p className="product-inci">{p.ingredientsInci}</p>
            <p className="product-price">{formatVnd(p.priceWeb)}</p>
            <p className="product-stock">{p.stock > 0 ? `Còn ${p.stock}` : 'Hết hàng'}</p>
            {p.sourceUrl && (
              <a className="product-source" href={p.sourceUrl} target="_blank" rel="noreferrer">
                Nguồn: Open Beauty Facts
              </a>
            )}
          </li>
        ))}
      </ul>
      {/* Bắt buộc theo giấy phép ODbL / CC BY-SA 3.0 của Open Beauty Facts (docs/catalog/du-lieu-san-pham-that.md) */}
      <p className="store-attribution">
        Dữ liệu và ảnh sản phẩm: <a href="https://world.openbeautyfacts.org" target="_blank" rel="noreferrer">Open Beauty Facts</a>{' '}
        (<a href="https://opendatacommons.org/licenses/odbl/1-0/" target="_blank" rel="noreferrer">ODbL</a>,{' '}
        <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noreferrer">CC BY-SA 3.0</a>). Nhãn hiệu thuộc chủ sở hữu, chỉ dùng cho mục đích học tập.
      </p>
    </main>
  )
}
