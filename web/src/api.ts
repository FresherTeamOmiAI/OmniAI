// Kiểu dữ liệu khớp với backend (com.omniai.*). Sửa backend thì sửa ở đây.

export type ChannelType = 'WEB' | 'TELEGRAM' | 'MESSENGER' | 'SHOPEE' | 'TIKTOK'

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

export interface Product {
  id: number
  sku: string
  name: string
  brand: string
  category: string
  skinTypes: string
  ingredientsInci: string
  sizeMl: number
  priceWeb: number
  priceShopee: number
  priceTiktok: number
  stock: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export async function fetchProducts(skinType?: string): Promise<Page<Product>> {
  const query = skinType ? `?skinType=${encodeURIComponent(skinType)}` : ''
  const res = await fetch(`/api/products${query}`)
  if (!res.ok) throw new Error(`Không tải được sản phẩm (HTTP ${res.status})`)
  return res.json()
}

export async function simulate(channel: ChannelType, text: string, messageId?: string): Promise<BotReply> {
  const res = await fetch(`/api/simulator/${channel.toLowerCase()}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: 'admin-tester', text, messageId }),
  })
  if (!res.ok) throw new Error(`Simulator lỗi (HTTP ${res.status})`)
  return res.json()
}

export const formatVnd = (value: number) => value.toLocaleString('vi-VN') + 'đ'
