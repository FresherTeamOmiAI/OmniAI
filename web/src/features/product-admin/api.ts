import { requestJson, type RequestOptions } from '../../shared/lib/http'
import type { Page, PriceHistoryEntry, Product, SalesChannel } from '../../shared/types'

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
  /** Giá theo mã kênh. Phải có đủ mọi kênh đang active, thiếu thì backend báo lỗi ở field `prices[<mã>]`. */
  prices: Record<string, number | null>
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

/** Kênh bán, kênh của shop trước. Form và bảng sinh cột giá theo danh sách này (thêm sàn không phải sửa web). */
export function listSalesChannels(token: string): Promise<SalesChannel[]> {
  return call(token, '/api/admin/sales-channels')
}

export function createProduct(token: string, input: ProductInput): Promise<Product> {
  return call(token, BASE, { method: 'POST', body: input })
}

export function updateProduct(token: string, id: number, input: ProductInput): Promise<Product> {
  return call(token, `${BASE}/${id}`, { method: 'PUT', body: input })
}

/** Lịch sử giá niêm yết của 1 sản phẩm, mới nhất trước (US-33). channel '' = mọi kênh. */
export function listPriceHistory(token: string, productId: number, channel: string, page: number): Promise<Page<PriceHistoryEntry>> {
  const params = new URLSearchParams({ page: String(page), size: '50' })
  if (channel) params.set('channel', channel)
  return call(token, `${BASE}/${productId}/price-history?${params}`)
}

export function setProductActive(token: string, id: number, active: boolean): Promise<Product> {
  return call(token, `${BASE}/${id}/active`, { method: 'PATCH', body: { active } })
}

// ---- Đồng bộ sản phẩm ra sàn (US-49, T-49.3). API ở api-contracts.md, mục "Đồng bộ sản phẩm ra sàn". ----

export type SyncAspect = 'PRODUCT' | 'PRICE' | 'STOCK'
export type SyncStatus = 'PENDING' | 'SYNCED' | 'FAILED'

/** Khớp backend sync/SyncView.java: 1 phần (nội dung / giá / tồn) của 1 sản phẩm trên 1 kênh. */
export interface SyncView {
  productId: number
  channel: string
  aspect: SyncAspect
  status: SyncStatus
  attempts: number
  lastError: string | null
  externalId: string | null
  syncedAt: string | null
  nextAttemptAt: string | null
  updatedAt: string
}

/** Mã kênh có connector đồng bộ (vd. ["SHOPEE", "TIKTOK"]). Rỗng thì màn admin không hiện cột đồng bộ. */
export function listSyncChannels(token: string): Promise<string[]> {
  return call(token, '/api/admin/sync/channels')
}

/** Trạng thái của các sản phẩm trên 1 trang bảng (1–100 id). Sản phẩm/kênh chưa từng xếp hàng thì không có dòng. */
export function listSyncStatus(token: string, productIds: number[]): Promise<SyncView[]> {
  return call(token, `/api/admin/sync/products/by-ids?ids=${productIds.join(',')}`)
}

/** Đưa 1 phần về PENDING để gửi lại ở lượt quét kế (thường sau khi sửa lỗi dữ liệu). */
export function retrySync(token: string, productId: number, channel: string, aspect: SyncAspect): Promise<SyncView> {
  const path = `/api/admin/sync/products/${productId}/${encodeURIComponent(channel)}/retry?aspect=${aspect}`
  return call(token, path, { method: 'POST' })
}
