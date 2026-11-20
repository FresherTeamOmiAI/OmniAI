import { getJson, requestJson, type RequestOptions } from '../../shared/lib/http'
import type { Page, Promotion, PromotionRequest, PromotionStatus, SalesChannel } from '../../shared/types'

// API admin khuyến mãi (docs/specs/api-contracts.md, mục "Khuyến mãi"). Cần Basic admin như admin sản phẩm.
const BASE = '/api/admin/promotions'
export const PAGE_SIZE = 20

export interface PromotionQuery {
  /** '' = mọi kênh. */
  channel: string
  /** '' = mọi trạng thái. */
  status: '' | PromotionStatus
  page: number
}

function call<T>(token: string, path: string, options: RequestOptions = {}): Promise<T> {
  // X-Requested-With: backend trả 401 không kèm WWW-Authenticate nên trình duyệt không bật hộp đăng nhập riêng.
  return requestJson(path, { ...options, headers: { Authorization: token, 'X-Requested-With': 'XMLHttpRequest' } })
}

export function listPromotions(token: string, query: PromotionQuery): Promise<Page<Promotion>> {
  const params = new URLSearchParams({ page: String(query.page), size: String(PAGE_SIZE) })
  if (query.channel) params.set('channel', query.channel)
  if (query.status) params.set('status', query.status)
  return call(token, `${BASE}?${params}`)
}

export function createPromotion(token: string, input: PromotionRequest): Promise<Promotion> {
  return call(token, BASE, { method: 'POST', body: input })
}

export function updatePromotion(token: string, id: number, input: PromotionRequest): Promise<Promotion> {
  return call(token, `${BASE}/${id}`, { method: 'PUT', body: input })
}

export function setPromotionEnabled(token: string, id: number, enabled: boolean): Promise<Promotion> {
  return call(token, `${BASE}/${id}/enabled`, { method: 'PATCH', body: { enabled } })
}

export function deletePromotion(token: string, id: number): Promise<void> {
  return call(token, `${BASE}/${id}`, { method: 'DELETE' })
}

/** Kênh bán, kênh của shop trước (cùng API với admin sản phẩm). Chip lọc và ô chọn kênh sinh theo danh sách này. */
export function listSalesChannels(token: string): Promise<SalesChannel[]> {
  return call(token, '/api/admin/sales-channels')
}

/** Giá niêm yết theo kênh của 1 SKU đang bán (API công khai mà bot dùng). Chỉ để gợi ý trong form. */
export interface SkuPrice {
  sku: string
  name: string
  prices: Record<string, number>
}

export function lookupSku(sku: string): Promise<SkuPrice> {
  return getJson(`/api/products/sku/${encodeURIComponent(sku)}`)
}
