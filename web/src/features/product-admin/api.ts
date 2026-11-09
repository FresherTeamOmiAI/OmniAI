import { requestJson, type RequestOptions } from '../../shared/lib/http'
import type { Page, Product } from '../../shared/types'

// API admin sản phẩm (docs/specs/api-contracts.md, mục "Admin sản phẩm").
const BASE = '/api/admin/products'

/** Body của POST/PUT: các field của Product trừ id, updatedAt. Số để null thì backend báo "bắt buộc". */
export interface ProductInput {
  sku: string
  name: string
  brand: string
  category: string
  skinTypes: string
  skinConcerns: string | null
  usageTime: string | null
  description: string | null
  ingredientsInci: string
  sizeMl: number | null
  paoMonths: number | null
  priceWeb: number | null
  priceShopee: number | null
  priceTiktok: number | null
  stock: number | null
  active?: boolean | null
}

export interface ListQuery {
  q: string
  active: '' | 'true' | 'false'
  page: number
  size: number
}

function call<T>(token: string, path: string, options: RequestOptions = {}): Promise<T> {
  // X-Requested-With: backend trả 401 không kèm WWW-Authenticate nên trình duyệt không bật hộp đăng nhập riêng.
  return requestJson(path, { ...options, headers: { Authorization: token, 'X-Requested-With': 'XMLHttpRequest' } })
}

export function listProducts(token: string, query: ListQuery): Promise<Page<Product>> {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size), sort: 'id' })
  if (query.q.trim()) params.set('q', query.q.trim())
  if (query.active) params.set('active', query.active)
  return call(token, `${BASE}?${params}`)
}

export function createProduct(token: string, input: ProductInput): Promise<Product> {
  return call(token, BASE, { method: 'POST', body: input })
}

export function updateProduct(token: string, id: number, input: ProductInput): Promise<Product> {
  return call(token, `${BASE}/${id}`, { method: 'PUT', body: input })
}

export function setProductActive(token: string, id: number, active: boolean): Promise<Product> {
  return call(token, `${BASE}/${id}/active`, { method: 'PATCH', body: { active } })
}
