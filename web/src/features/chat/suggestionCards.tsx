import { useState } from 'react'
import type { ProductCard } from '../../shared/types'

export interface SuggestedSku {
  key: string
  sku: string | null
  brand: string
  name: string
  price: number
  category: string | null
  imageUrl: string | null
  product: ProductCard | null
}

const PRODUCT_LINE = /^\d+\.\s+(.+?)\s+—\s+(.+?)\s+—\s+(\d{1,3}(?:\.\d{3})*)đ$/

/** Tách các dòng "1. Thương hiệu — Tên — 289.000đ" thành thẻ SKU, phần chữ còn lại giữ làm lời nhắn. */
export function splitSuggestion(text: string, products: ProductCard[]): { items: SuggestedSku[]; note: string } {
  const items: SuggestedSku[] = []
  const seen = new Set<string>()
  const notes: string[] = []
  for (const raw of text.split('\n')) {
    const line = raw.trim().replaceAll('**', '')
    if (!line || line === 'Gợi ý trong shop:') continue
    const match = line.match(PRODUCT_LINE)
    if (!match) {
      notes.push(raw.trim())
      continue
    }
    const brand = match[1].trim()
    const name = match[2].trim()
    const identity = `${brand}|${name}`.toLocaleLowerCase('vi')
    if (seen.has(identity)) continue
    seen.add(identity)
    const product = products.find(
      (item) =>
        item.brand.toLocaleLowerCase('vi') === brand.toLocaleLowerCase('vi') &&
        item.name.toLocaleLowerCase('vi') === name.toLocaleLowerCase('vi'),
    )
    items.push({
      key: product?.sku ?? identity,
      sku: product?.sku ?? null,
      brand,
      name,
      price: Number(match[3].replaceAll('.', '')),
      category: product?.category ?? null,
      imageUrl: product?.imageUrl ?? null,
      product: product ?? null,
    })
  }
  items.push(...productsMentioned(text, products, seen))
  return { items, note: notes.filter(Boolean).join('\n') }
}

/** Số màu minh hoạ (--tone-0 … --tone-5 trong tokens.css). */
const TONES = 6

/** Câu chữ tự do vẫn hiện ảnh nếu nhắc đúng tên sản phẩm trong catalog. */
function productsMentioned(text: string, products: ProductCard[], seen: Set<string>): SuggestedSku[] {
  const folded = text.toLocaleLowerCase('vi')
  const ranked = [...products].sort((a, b) => b.name.length - a.name.length)
  const found: SuggestedSku[] = []
  for (const product of ranked) {
    const name = product.name.toLocaleLowerCase('vi')
    if (name.length < 12) continue
    const identity = `${product.brand}|${product.name}`.toLocaleLowerCase('vi')
    if (seen.has(identity)) continue
    const branded = `${product.brand} — ${product.name}`.toLocaleLowerCase('vi')
    if (!folded.includes(branded) && !folded.includes(name)) continue
    if (found.some((item) => item.name.toLocaleLowerCase('vi').includes(name))) continue
    seen.add(identity)
    const price = product.prices.WEB ?? Object.values(product.prices)[0] ?? 0
    found.push({
      key: product.sku,
      sku: product.sku,
      brand: product.brand,
      name: product.name,
      price,
      category: product.category,
      imageUrl: product.imageUrl,
      product,
    })
    if (found.length === 3) break
  }
  return found
}

function toneFor(label: string): number {
  let hash = 0
  for (const char of label) hash = (hash * 33 + char.charCodeAt(0)) % TONES
  return hash
}

/** Ảnh thật theo SKU. Thiếu file thì vẽ khung theo nhóm hàng; màu lấy từ token (chat.css), không viết mã màu ở đây. */
export function ProductThumb({ category, label, imageUrl }: { category: string | null; label: string; imageUrl: string | null }) {
  const [failed, setFailed] = useState(false)
  if (imageUrl && !failed) {
    return <img className="chat-sku-photo" src={imageUrl} alt="" onError={() => setFailed(true)} />
  }
  return (
    <svg className={`chat-sku-photo tone-${toneFor(label)}`} viewBox="0 0 64 64" aria-hidden="true">
      <rect className="pack-bg" width="64" height="64" rx="10" />
      <Pack category={category} />
    </svg>
  )
}

function Pack({ category }: { category: string | null }) {
  if (category === 'sunscreen' || category === 'lip-care') {
    return (
      <>
        <rect className="pack" x="26" y="8" width="12" height="8" rx="2" />
        <rect className="pack" x="20" y="16" width="24" height="38" rx="8" />
        <rect className="pack-label" x="24" y="28" width="16" height="10" rx="2" />
      </>
    )
  }
  if (category === 'moisturizer' || category === 'mask') {
    return (
      <>
        <rect className="pack" x="16" y="22" width="32" height="26" rx="8" />
        <rect className="pack-soft" x="18" y="16" width="28" height="8" rx="3" />
        <circle className="pack-label" cx="32" cy="35" r="6" />
      </>
    )
  }
  if (category === 'serum') {
    return (
      <>
        <rect className="pack" x="29" y="8" width="6" height="10" rx="2" />
        <rect className="pack" x="24" y="18" width="16" height="34" rx="6" />
        <circle className="pack-label" cx="32" cy="34" r="4" />
      </>
    )
  }
  return (
    <>
      <rect className="pack" x="28" y="8" width="8" height="6" rx="2" />
      <path className="pack" d="M24 16h16l4 8v26a8 8 0 0 1-8 8h-8a8 8 0 0 1-8-8V24z" />
      <rect className="pack-label" x="24" y="30" width="16" height="8" rx="2" />
    </>
  )
}
