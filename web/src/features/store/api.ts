import { getJson } from '../../shared/lib/http'
import type { Page } from '../../shared/types'
import type { Product } from './types'

export const STORE_PAGE_SIZE = 20

/** Bộ lọc của store, khớp GET /api/products (T-09.2). Chuỗi rỗng / null = không lọc. */
export interface StoreQuery {
  skinType: string
  category: string
  minPrice: number | null
  maxPrice: number | null
}

// sort=id để các trang không chồng/sót SKU (Postgres không bảo đảm thứ tự khi không ORDER BY).
export function fetchProducts(query: StoreQuery, page: number): Promise<Page<Product>> {
  const params = new URLSearchParams({ page: String(page), size: String(STORE_PAGE_SIZE), sort: 'id' })
  if (query.skinType) params.set('skinType', query.skinType)
  if (query.category) params.set('category', query.category)
  if (query.minPrice != null) params.set('minPrice', String(query.minPrice))
  if (query.maxPrice != null) params.set('maxPrice', String(query.maxPrice))
  return getJson(`/api/products?${params}`)
}

/** Chi tiết 1 sản phẩm đang bán. Sản phẩm ẩn hoặc không tồn tại: backend trả 404 (SRS G-06). */
export function fetchProduct(id: string): Promise<Product> {
  return getJson(`/api/products/${encodeURIComponent(id)}`)
}
