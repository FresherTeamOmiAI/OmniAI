// Kiểu dùng chung nhiều feature, khớp backend (com.omniai.common, policy).

export type ChannelType = 'WEB' | 'SHOPEE' | 'TIKTOK'

export interface PolicyViolation {
  ruleId: string
  action: 'BLOCK' | 'REDACT' | 'REPLACE'
  matched: string
}

export interface BotReply {
  messageId: string
  channel: ChannelType
  rawText: string
  finalText: string
  violations: PolicyViolation[]
  blocked: boolean
  handoff: boolean
}

/** Frame lỗi từ /ws/chat (tin JSON hỏng hoặc text rỗng). messageId null nếu server không đọc được. */
export interface WsErrorReply {
  error: 'invalid_message'
  messageId: string | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

// Khớp entity backend catalog/Product.java (xem docs/specs/data-model.md). Dùng ở store và product-admin.
export interface Product {
  id: number
  sku: string
  name: string
  brand: string
  category: string
  skinTypes: string
  skinConcerns: string | null
  usageTime: 'am' | 'pm' | 'am,pm' | null
  description: string | null
  ingredientsInci: string
  sizeMl: number
  paoMonths: number
  priceWeb: number
  priceShopee: number
  priceTiktok: number
  stock: number
  active: boolean
  // Nguồn và ảnh (V9, T-47.1). null với sản phẩm admin tự tạo.
  barcode: string | null
  sourceUrl: string | null
  imageKey: string | null
  imageSourceUrl: string | null
  imageLicenseNote: string | null
  // URL public trên Supabase, backend ghép từ imageKey; null khi chưa có ảnh hoặc chưa cấu hình SUPABASE_URL.
  imageUrl: string | null
  updatedAt: string
}

// Lỗi API theo ProblemDetail (RFC 9457). `errors` chỉ có ở lỗi 400 sai dữ liệu.
export interface FieldViolation {
  field: string
  message: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  errors?: FieldViolation[]
}
