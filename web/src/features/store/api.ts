import { getJson } from '../../shared/lib/http'
import type { Page } from '../../shared/types'
import type { Product } from './types'

export function fetchProducts(skinType?: string): Promise<Page<Product>> {
  const query = skinType ? `?skinType=${encodeURIComponent(skinType)}` : ''
  return getJson(`/api/products${query}`)
}
