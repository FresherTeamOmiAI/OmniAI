import { ApiError, getJson } from '../../shared/lib/http'
import type { Product } from '../../shared/types'

export async function findProductBySku(sku: string): Promise<Product | null> {
  try {
    return await getJson<Product>(`/api/products/sku/${encodeURIComponent(sku)}/card`)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}
