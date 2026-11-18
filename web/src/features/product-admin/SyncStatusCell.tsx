import { useState } from 'react'
import { formatVnd } from '../../shared/lib/format'
import type { Product } from '../../shared/types'
import type { SyncAspect, SyncStatus, SyncView } from './api'

// Trạng thái đồng bộ 1 sản phẩm ra từng sàn trên bảng admin (US-49, T-49.3). Owner: LucVT.
// Ô trong bảng: mỗi kênh 1 nhãn gộp 3 phần (nội dung, giá, tồn). Bấm nhãn mở 1 hàng chi tiết rộng hết bảng
// ngay dưới sản phẩm (SyncDetail): trạng thái từng phần, giá trị đang có trên sàn, lỗi gần nhất, nút Thử lại.
// Backend không lưu giá trị đã gửi; phần SYNCED nghĩa là sàn giữ đúng giá trị hiện tại của hub (hub đổi thì dòng
// về PENDING), nên giá trị hiện ở đây lấy từ sản phẩm trên hub.

const ASPECTS: { aspect: SyncAspect; label: string }[] = [
  { aspect: 'PRODUCT', label: 'Nội dung' },
  { aspect: 'PRICE', label: 'Giá' },
  { aspect: 'STOCK', label: 'Tồn kho' },
]

type Summary = SyncStatus | 'NONE'

const SUMMARY: Record<Summary, { label: string; badge: string }> = {
  FAILED: { label: 'Lỗi', badge: 'badge badge-danger' },
  PENDING: { label: 'Đang chờ', badge: 'badge badge-warn' },
  SYNCED: { label: 'Đã đồng bộ', badge: 'badge badge-on' },
  NONE: { label: 'Chưa đồng bộ', badge: 'badge badge-off' },
}

const STATUS_TEXT: Record<SyncStatus, string> = { FAILED: 'Lỗi', PENDING: 'Đang chờ gửi', SYNCED: 'Đã lên sàn' }

/** Có phần lỗi → Lỗi; còn phần chờ → Đang chờ; mọi phần đã lên → Đã đồng bộ; chưa có dòng nào → Chưa đồng bộ. */
function summarize(rows: SyncView[]): Summary {
  if (rows.length === 0) return 'NONE'
  if (rows.some((r) => r.status === 'FAILED')) return 'FAILED'
  if (rows.some((r) => r.status === 'PENDING')) return 'PENDING'
  return 'SYNCED'
}

const time = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit', day: '2-digit', month: '2-digit' }) : ''

export interface SyncChannel {
  code: string
  name: string
}

interface CellProps {
  productId: number
  channels: SyncChannel[]
  rows: SyncView[]
  /** Mã kênh đang mở chi tiết của sản phẩm này, null nếu không mở. */
  open: string | null
  onToggle: (channel: string) => void
}

export function SyncStatusCell({ productId, channels, rows, open, onToggle }: CellProps) {
  return (
    <div className="sync-cell">
      {channels.map((c) => {
        const own = rows.filter((r) => r.channel === c.code)
        const summary = SUMMARY[summarize(own)]
        const content = (
          <>
            <span className="sync-channel">{c.name}</span>
            <span className={summary.badge}>{summary.label}</span>
          </>
        )
        if (own.length === 0) {
          return <span key={c.code} className="sync-item" title={`${c.name}: sản phẩm chưa từng gửi lên kênh này`}>{content}</span>
        }
        return (
          <button key={c.code} type="button" className="sync-item sync-toggle" aria-expanded={open === c.code}
            aria-controls={`sync-detail-${productId}`} title={`${c.name}: ${summary.label}. Bấm để xem chi tiết`}
            onClick={() => onToggle(c.code)}>
            {content}
          </button>
        )
      })}
    </div>
  )
}

/** Giá trị hub của 1 phần trên kênh: nội dung = ẩn/hiện, giá = giá của đúng kênh, tồn = tồn chung. */
function hubValue(product: Product, channel: string, aspect: SyncAspect): string {
  if (aspect === 'PRICE') return product.prices[channel] != null ? formatVnd(product.prices[channel]) : 'chưa có giá'
  if (aspect === 'STOCK') return `${product.stock} sản phẩm`
  return product.active ? 'đang hiện (tên, mô tả, ảnh, INCI)' : 'đã ẩn'
}

const VALUE_PREFIX: Record<SyncStatus, string> = {
  SYNCED: 'Trên sàn',
  PENDING: 'Sắp gửi',
  FAILED: 'Chưa gửi được',
}

interface DetailProps {
  product: Product
  channel: SyncChannel
  rows: SyncView[]
  onRetry: (productId: number, channel: string, aspect: SyncAspect) => Promise<void>
  onClose: () => void
}

export function SyncDetail({ product, channel, rows, onRetry, onClose }: DetailProps) {
  const productId = product.id
  const [retrying, setRetrying] = useState<SyncAspect | null>(null)
  const own = rows.filter((r) => r.channel === channel.code)
  const external = own.find((r) => r.externalId)?.externalId

  const retry = async (aspect: SyncAspect) => {
    setRetrying(aspect)
    try {
      await onRetry(productId, channel.code, aspect)
    } finally {
      setRetrying(null)
    }
  }

  return (
    <section className="sync-detail" id={`sync-detail-${productId}`} aria-label={`Đồng bộ ${channel.name}`}>
      <header className="sync-detail-head">
        <strong>Đồng bộ {channel.name}</strong>
        {external && <span className="muted">Mã trên sàn <code>{external}</code></span>}
        <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>Đóng</button>
      </header>
      <ul className="sync-parts">
        {ASPECTS.map(({ aspect, label }) => {
          const row = own.find((r) => r.aspect === aspect)
          return (
            <li key={aspect} className={row?.status === 'FAILED' ? 'sync-part sync-part-failed' : 'sync-part'}>
              <span className="sync-part-label">{label}</span>
              {!row ? (
                <span className="muted">Chưa gửi</span>
              ) : (
                <>
                  <span className="sync-part-status">
                    {STATUS_TEXT[row.status]}
                    {row.status === 'SYNCED' && row.syncedAt && <span className="muted"> · {time(row.syncedAt)}</span>}
                    {row.status === 'FAILED' && <span className="muted"> · sau {row.attempts} lần thử</span>}
                    {row.status === 'PENDING' && row.attempts > 0 && (
                      <span className="muted"> · đã thử {row.attempts} lần, thử lại lúc {time(row.nextAttemptAt)}</span>
                    )}
                  </span>
                  <span className="sync-part-value">
                    {VALUE_PREFIX[row.status]}: <strong>{hubValue(product, channel.code, aspect)}</strong>
                  </span>
                  {row.lastError && row.status !== 'SYNCED' && <span className="sync-error">{row.lastError}</span>}
                  {row.status === 'FAILED' && (
                    <button type="button" className="btn btn-sm" disabled={retrying === aspect} onClick={() => retry(aspect)}>
                      {retrying === aspect ? 'Đang gửi…' : 'Thử lại'}
                    </button>
                  )}
                </>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
