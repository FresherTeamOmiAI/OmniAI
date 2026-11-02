import { useEffect, useState } from 'react'
import { fetchProducts, formatVnd, type Product } from '../api'

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
            <span className="product-brand">{p.brand}</span>
            <h3>{p.name}</h3>
            <p className="product-inci">{p.ingredientsInci}</p>
            <p className="product-price">{formatVnd(p.priceWeb)}</p>
            <p className="product-stock">{p.stock > 0 ? `Còn ${p.stock}` : 'Hết hàng'}</p>
          </li>
        ))}
      </ul>
    </main>
  )
}
