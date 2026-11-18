import type { Product } from '../../shared/types'

export type Market = 'SHOPEE' | 'TIKTOK'

export interface Promo {
  label: string
  percent: number
  kind: 'voucher' | 'flash'
}

/** Giá niêm yết lấy từ catalog. Chương trình giảm chỉ hiện khi SKU rơi vào nhóm có khuyến mãi giả lập. */
export interface ChannelOffer {
  market: Market
  listPrice: number
  salePrice: number
  promo: Promo | null
}

function hash(text: string): number {
  let value = 0
  for (const char of text) value = Math.imul(value, 33) + char.charCodeAt(0)
  return value >>> 0
}

/**
 * Giá sàn là giá trong DB. Khuyến mãi là chương trình giả lập của simulator:
 * khoảng hai phần ba SKU có voucher Shopee hoặc flash sale TikTok, phần còn lại bán đúng giá niêm yết.
 */
export function channelOffer(product: Pick<Product, 'sku' | 'prices'>, market: Market): ChannelOffer {
  const listPrice = product.prices[market] ?? product.prices.WEB ?? 0
  const bucket = hash(`${product.sku}:${market}`)
  const hasPromo = listPrice >= 20_000 && bucket % 3 !== 0
  if (!hasPromo) return { market, listPrice, salePrice: listPrice, promo: null }
  const percent = market === 'SHOPEE' ? 8 + (bucket % 5) * 2 : 10 + (bucket % 3) * 5
  const salePrice = Math.max(1_000, Math.round((listPrice * (100 - percent)) / 100 / 1_000) * 1_000)
  if (salePrice >= listPrice) return { market, listPrice, salePrice: listPrice, promo: null }
  return {
    market,
    listPrice,
    salePrice,
    promo: {
      percent,
      kind: market === 'SHOPEE' ? 'voucher' : 'flash',
      label: market === 'SHOPEE' ? `Voucher -${percent}%` : `Flash Sale -${percent}%`,
    },
  }
}

export function marketStats(sku: string) {
  const bucket = hash(sku)
  return {
    rating: (4.7 + (bucket % 4) / 10).toFixed(1),
    reviews: 120 + (bucket % 1800),
    sold: 40 + (bucket % 3200),
  }
}

export function dong(value: number) {
  return `₫${value.toLocaleString('vi-VN')}`
}
