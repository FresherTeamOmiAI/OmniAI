import { useState } from 'react'
import type { Product } from '../../shared/types'

export interface SuggestedSku {
  key: string
  sku: string | null
  brand: string
  name: string
  price: number
  category: string | null
  imageUrl: string | null
}

const PRODUCT_LINE = /^\d+\.\s+(.+?)\s+—\s+(.+?)\s+—\s+(\d{1,3}(?:\.\d{3})*)đ$/

/** Tách các dòng "1. Thương hiệu — Tên — 289.000đ" thành thẻ SKU, phần chữ còn lại giữ làm lời nhắn. */
export function splitSuggestion(text: string, products: Product[]): { items: SuggestedSku[]; note: string } {
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
    })
  }
  return { items, note: notes.filter(Boolean).join('\n') }
}

const TONE = ['#a3245a', '#0f6e56', '#9a6700', '#185fa5', '#533ab7', '#993556']

function toneFor(label: string): string {
  let hash = 0
  for (const char of label) hash = (hash * 33 + char.charCodeAt(0)) % TONE.length
  return TONE[hash]
}

/** Ảnh thật theo SKU. Thiếu file thì vẽ khung theo nhóm hàng. */
export function ProductThumb({ category, label, imageUrl }: { category: string | null; label: string; imageUrl: string | null }) {
  const [failed, setFailed] = useState(false)
  if (imageUrl && !failed) {
    return <img className="chat-sku-photo" src={imageUrl} alt="" onError={() => setFailed(true)} />
  }
  const ink = toneFor(label)
  return (
    <svg className="chat-sku-photo" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="10" fill="#f4f1f6" />
      <Pack category={category} ink={ink} />
    </svg>
  )
}

function Pack({ category, ink }: { category: string | null; ink: string }) {
  if (category === 'sunscreen' || category === 'lip-care') {
    return (
      <>
        <rect x="26" y="8" width="12" height="8" rx="2" fill={ink} />
        <rect x="20" y="16" width="24" height="38" rx="8" fill={ink} />
        <rect x="24" y="28" width="16" height="10" rx="2" fill="#fff" opacity="0.85" />
      </>
    )
  }
  if (category === 'moisturizer' || category === 'mask') {
    return (
      <>
        <rect x="16" y="22" width="32" height="26" rx="8" fill={ink} />
        <rect x="18" y="16" width="28" height="8" rx="3" fill={ink} opacity="0.75" />
        <circle cx="32" cy="35" r="6" fill="#fff" opacity="0.85" />
      </>
    )
  }
  if (category === 'serum') {
    return (
      <>
        <rect x="29" y="8" width="6" height="10" rx="2" fill={ink} />
        <rect x="24" y="18" width="16" height="34" rx="6" fill={ink} />
        <circle cx="32" cy="34" r="4" fill="#fff" opacity="0.9" />
      </>
    )
  }
  return (
    <>
      <rect x="28" y="8" width="8" height="6" rx="2" fill={ink} />
      <path d="M24 16h16l4 8v26a8 8 0 0 1-8 8h-8a8 8 0 0 1-8-8V24z" fill={ink} />
      <rect x="24" y="30" width="16" height="8" rx="2" fill="#fff" opacity="0.85" />
    </>
  )
}
