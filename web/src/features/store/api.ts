import { getJson } from '../../shared/lib/http'
import type { Page } from '../../shared/types'
import type { Product } from './types'

export const STORE_PAGE_SIZE = 20

// sort=id để các trang không chồng/sót SKU (Postgres không bảo đảm thứ tự khi không ORDER BY).
export function fetchProducts(skinType: string, page: number): Promise<Page<Product>> {
  const params = new URLSearchParams({ page: String(page), size: String(STORE_PAGE_SIZE), sort: 'id' })
  if (skinType) params.set('skinType', skinType)
  return getJson(`/api/products?${params}`)
}
