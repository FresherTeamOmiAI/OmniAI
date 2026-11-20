import { useEffect, useRef, useState } from 'react'
import { formatVnDateTime, formatVnd } from '../../shared/lib/format'
import { ApiError } from '../../shared/lib/http'
import type { Page, PriceHistoryEntry, Product, SalesChannel } from '../../shared/types'
import { listPriceHistory } from './api'

interface Props {
  token: string
  product: Product
  channels: SalesChannel[]
  onClose: () => void
  onUnauthorized: () => void
}

// Lịch sử giá niêm yết của 1 sản phẩm (US-33, T-33.2). Hub tự ghi mỗi lần đổi giá theo kênh; chỉ xem, không sửa.
export function PriceHistoryDialog({ token, product, channels, onClose, onUnauthorized }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [channel, setChannel] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<{ key: string; data: Page<PriceHistoryEntry> | null; error: string | null } | null>(null)
  const [opener] = useState(() => (document.activeElement instanceof HTMLElement ? document.activeElement : null))

  const key = `${channel}#${page}`
  const loading = result?.key !== key
  const data = result?.data ?? null

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => {
      if (opener?.isConnected) opener.focus()
    }
  }, [opener])

  useEffect(() => {
    let current = true
    listPriceHistory(token, product.id, channel, page)
      .then((d) => current && setResult({ key, data: d, error: null }))
      .catch((e) => {
        if (!current) return
        if (e instanceof ApiError && e.status === 401) return onUnauthorized()
        setResult((r) => ({ key, data: r?.data ?? null, error: (e as Error).message }))
      })
    return () => {
      current = false
    }
  }, [token, product.id, channel, page, key, onUnauthorized])

  const channelName = (code: string) => channels.find((c) => c.code === code)?.name ?? code
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <dialog ref={dialogRef} className="product-dialog" onClose={onClose} aria-labelledby="ph-title">
      <div className="price-history">
        <header className="dialog-header">
          <h2 id="ph-title">Lịch sử giá · {product.sku}</h2>
          <button type="button" className="btn-icon" onClick={onClose} aria-label="Đóng">×</button>
        </header>
        <div className="dialog-body">
          <p className="muted">{product.name}. Mỗi lần đổi giá ở một kênh là 1 dòng, mới nhất trước. Giờ Việt Nam.</p>
          <div className="ph-chips" role="group" aria-label="Lọc theo kênh">
            {[{ code: '', name: 'Mọi kênh' }, ...channels].map((c) => (
              <button key={c.code} type="button" className={channel === c.code ? 'chip chip-active' : 'chip'}
                aria-pressed={channel === c.code} onClick={() => { setChannel(c.code); setPage(0) }}>{c.name}</button>
            ))}
          </div>
          {result?.error && <p className="error" role="alert">{result.error}</p>}
          <div className="table-wrap">
            <table className={loading && data ? 'is-loading' : undefined} aria-busy={loading}>
              <thead>
                <tr>
                  <th>Thời điểm</th>
                  <th>Kênh</th>
                  <th className="num">Giá cũ</th>
                  <th className="num">Giá mới</th>
                  <th className="num">Chênh</th>
                </tr>
              </thead>
              <tbody>
                {data?.items.length === 0 && <tr><td colSpan={5} className="muted">Chưa có dòng nào.</td></tr>}
                {data?.items.map((h, i) => {
                  const diff = h.oldPrice == null ? null : h.newPrice - h.oldPrice
                  return (
                    <tr key={`${h.changedAt}-${h.channel}-${i}`}>
                      <td className="ph-time">{formatVnDateTime(h.changedAt)}</td>
                      <td>{channelName(h.channel)}</td>
                      <td className="num">{h.oldPrice == null ? <span className="muted">Giá ban đầu</span> : formatVnd(h.oldPrice)}</td>
                      <td className="num"><strong>{formatVnd(h.newPrice)}</strong></td>
                      <td className="num">
                        {diff == null ? '' : <span className={diff < 0 ? 'price-down' : 'price-up'}>{diff > 0 ? '+' : diff < 0 ? '−' : ''}{formatVnd(Math.abs(diff))}</span>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {data && totalPages > 1 && (
            <nav className="pager" aria-label="Phân trang lịch sử giá">
              <button type="button" className="btn btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>‹ Mới hơn</button>
              <span>Trang {page + 1}/{totalPages}</span>
              <button type="button" className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Cũ hơn ›</button>
            </nav>
          )}
        </div>
        <footer className="dialog-footer">
          <button type="button" className="btn" onClick={onClose}>Đóng</button>
        </footer>
      </div>
    </dialog>
  )
}
