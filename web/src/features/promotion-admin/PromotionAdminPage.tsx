import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { formatVnDateTime, formatVnd } from '../../shared/lib/format'
import { ApiError } from '../../shared/lib/http'
import type { Page, Promotion, PromotionStatus, SalesChannel } from '../../shared/types'
import { LoginForm, loadToken, saveToken } from '../product-admin'
import {
  deletePromotion, listPromotions, listSalesChannels, setPromotionEnabled, type PromotionQuery,
} from './api'
import { PromotionForm } from './PromotionForm'

const STATUSES: { value: PromotionStatus; label: string; badge: string }[] = [
  { value: 'ACTIVE', label: 'Đang chạy', badge: 'badge-on' },
  { value: 'SCHEDULED', label: 'Sắp diễn ra', badge: 'badge-scheduled' },
  { value: 'EXPIRED', label: 'Hết hạn', badge: 'badge-off' },
  { value: 'DISABLED', label: 'Đã tắt', badge: 'badge-off badge-disabled' },
]
const statusOf = (s: PromotionStatus) => STATUSES.find((x) => x.value === s) ?? STATUSES[0]

type Editing = { mode: 'create' } | { mode: 'edit'; promotion: Promotion } | null

// Admin > Khuyến mãi (US-33, T-33.2, UC-21): voucher và flash sale theo kênh; thêm, sửa, tắt/bật, xoá. Owner: LucVT.
export function PromotionAdminPage() {
  const [token, setToken] = useState<string | null>(loadToken)
  const [loginNotice, setLoginNotice] = useState<string | null>(null)

  const login = (t: string) => {
    saveToken(t)
    setLoginNotice(null)
    setToken(t)
  }
  const logout = useCallback((notice: string | null = null) => {
    saveToken(null)
    setLoginNotice(notice)
    setToken(null)
  }, [])
  const onLogout = useCallback(() => logout(), [logout])
  const onUnauthorized = useCallback(() => logout('Phiên đăng nhập không còn hợp lệ, hãy đăng nhập lại.'), [logout])

  return (
    <main id="main" tabIndex={-1} className="admin">
      {token ? (
        <PromotionTable token={token} onLogout={onLogout} onUnauthorized={onUnauthorized} />
      ) : (
        <LoginForm notice={loginNotice} onLogin={login} />
      )}
    </main>
  )
}

/** Bộ lọc nằm trên URL (?channel=&status=&page=) như inbox hội thoại: F5 hay gửi link vẫn giữ. */
function readQuery(params: URLSearchParams): PromotionQuery {
  const status = params.get('status') ?? ''
  return {
    channel: params.get('channel') ?? '',
    status: STATUSES.some((s) => s.value === status) ? (status as PromotionStatus) : '',
    page: Math.max(0, Number(params.get('page')) || 0),
  }
}

interface TableProps {
  token: string
  onLogout: () => void
  onUnauthorized: () => void
}

function PromotionTable({ token, onLogout, onUnauthorized }: TableProps) {
  const [params, setParams] = useSearchParams()
  const query = readQuery(params)
  const [reload, setReload] = useState(0)
  const [result, setResult] = useState<{ key: string; page: Page<Promotion> | null } | null>(null)
  const [channels, setChannels] = useState<SalesChannel[]>([])
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const key = `${JSON.stringify(query)}#${reload}`
  const loading = result?.key !== key
  const data = result?.page ?? null

  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) return onUnauthorized()
    setError((e as Error).message)
  }, [onUnauthorized, setError])

  const { channel, status: statusFilter, page } = query
  useEffect(() => {
    let current = true
    listPromotions(token, { channel, status: statusFilter, page })
      .then((p) => {
        if (!current) return
        setResult({ key, page: p })
        setError(null)
      })
      .catch((e) => {
        if (!current) return
        setResult((r) => ({ key, page: r?.page ?? null }))
        fail(e)
      })
    return () => {
      current = false
    }
  }, [token, channel, statusFilter, page, key, fail])

  useEffect(() => {
    let current = true
    listSalesChannels(token)
      .then((list) => current && setChannels(list))
      .catch((e) => current && fail(e))
    return () => {
      current = false
    }
  }, [token, fail])

  const update = (patch: Partial<PromotionQuery>) => {
    const next = { ...query, page: 0, ...patch }
    const p = new URLSearchParams()
    if (next.channel) p.set('channel', next.channel)
    if (next.status) p.set('status', next.status)
    if (next.page) p.set('page', String(next.page))
    setParams(p)
  }

  const channelName = (code: string) => channels.find((c) => c.code === code)?.name ?? code

  const toggle = async (p: Promotion) => {
    setBusyId(p.id)
    try {
      const saved = await setPromotionEnabled(token, p.id, !p.enabled)
      setStatus(saved.enabled ? `Đã bật "${saved.name}".` : `Đã tắt "${saved.name}": bot và cửa hàng không thấy nữa.`)
      setError(null)
      setReload((n) => n + 1)
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        // Bật lại flash sale bị chồng giờ: backend nói rõ trùng với khuyến mãi nào.
        setError(e.problem?.errors?.map((v) => v.message).join('; ') || e.message)
      } else {
        fail(e)
      }
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (p: Promotion) => {
    if (!window.confirm(`Xoá hẳn "${p.name}"? Không khôi phục được. Muốn giữ lại thì bấm Tắt.`)) return
    setBusyId(p.id)
    try {
      await deletePromotion(token, p.id)
      setStatus(`Đã xoá "${p.name}".`)
      setError(null)
      setReload((n) => n + 1)
    } catch (e) {
      fail(e)
    } finally {
      setBusyId(null)
    }
  }

  const onSaved = (saved: Promotion, created: boolean) => {
    setEditing(null)
    setStatus(created ? `Đã thêm "${saved.name}".` : `Đã lưu "${saved.name}".`)
    setReload((n) => n + 1)
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div className="admin-header">
        <h1>Khuyến mãi</h1>
        <button type="button" className="btn btn-primary" disabled={channels.length === 0}
          onClick={() => setEditing({ mode: 'create' })}>+ Thêm khuyến mãi</button>
        <button type="button" className="btn" onClick={onLogout}>Đăng xuất</button>
      </div>
      <p className="admin-intro">
        Mỗi khuyến mãi thuộc 1 kênh. Voucher giảm số tiền cố định cho cả đơn; flash sale đặt giá sale riêng từng sản phẩm.
        Hết giờ thì tự tắt. Giờ hiển thị theo giờ Việt Nam.
      </p>

      <div className="admin-toolbar promo-toolbar">
        <div className="promo-chips" role="group" aria-label="Lọc theo kênh">
          <button type="button" className={query.channel === '' ? 'chip chip-active' : 'chip'} aria-pressed={query.channel === ''}
            onClick={() => update({ channel: '' })}>Mọi kênh</button>
          {channels.map((c) => (
            <button key={c.code} type="button" className={query.channel === c.code ? 'chip chip-active' : 'chip'}
              aria-pressed={query.channel === c.code} onClick={() => update({ channel: c.code })}>{c.name}</button>
          ))}
        </div>
        <label className="sr-only" htmlFor="pr-status">Trạng thái</label>
        <select id="pr-status" value={query.status} onChange={(e) => update({ status: e.target.value as PromotionQuery['status'] })}>
          <option value="">Mọi trạng thái</option>
          {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
        <span className="muted toolbar-count" aria-live="polite">{loading ? 'Đang tải…' : data ? `${data.total} khuyến mãi` : ''}</span>
      </div>

      {status && <p className="notice" role="status">{status}</p>}
      {error && <p className="error" role="alert">{error}</p>}

      <div className="table-wrap">
        <table className={loading && data ? 'product-table promo-table is-loading' : 'product-table promo-table'} aria-busy={loading}>
          <thead>
            <tr>
              <th>Kênh</th>
              <th>Khuyến mãi</th>
              <th>Mức giảm</th>
              <th>Thời gian</th>
              <th>Trạng thái</th>
              <th><span className="sr-only">Thao tác</span></th>
            </tr>
          </thead>
          <tbody>
            {data?.items.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  {query.channel || query.status ? 'Không có khuyến mãi nào khớp bộ lọc.' : 'Chưa có khuyến mãi. Bấm "+ Thêm khuyến mãi" để tạo voucher hoặc flash sale.'}
                </td>
              </tr>
            )}
            {data?.items.map((p) => {
              const s = statusOf(p.status)
              return (
                <tr key={p.id} className={p.status === 'EXPIRED' || p.status === 'DISABLED' ? 'row-hidden' : undefined}>
                  <td>{channelName(p.channel)}</td>
                  <td>
                    <span className="cell-name">{p.name}</span>
                    <span className="cell-sub">
                      {p.type === 'VOUCHER' ? <>Voucher <code>{p.voucherCode}</code></> : `Flash sale · ${p.items.length} sản phẩm`}
                    </span>
                  </td>
                  <td>{discountText(p)}</td>
                  <td className="promo-period">
                    <span>{formatVnDateTime(p.startsAt)}</span>
                    <span className="cell-sub">đến {formatVnDateTime(p.endsAt)}</span>
                  </td>
                  <td><span className={`badge ${s.badge}`}>{s.label}</span></td>
                  <td className="row-actions">
                    <button type="button" className="btn btn-sm" onClick={() => setEditing({ mode: 'edit', promotion: p })}>Sửa</button>
                    <button type="button" className="btn btn-sm" disabled={busyId === p.id} onClick={() => toggle(p)}>
                      {p.enabled ? 'Tắt' : 'Bật'}
                    </button>
                    <button type="button" className="btn btn-sm" disabled={busyId === p.id} onClick={() => remove(p)}>Xoá</button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {data && totalPages > 1 && (
        <nav className="pager" aria-label="Phân trang">
          <button type="button" className="btn btn-sm" disabled={query.page === 0} onClick={() => update({ page: query.page - 1 })}>‹ Trước</button>
          <span>Trang {query.page + 1}/{totalPages}</span>
          <button type="button" className="btn btn-sm" disabled={query.page + 1 >= totalPages} onClick={() => update({ page: query.page + 1 })}>Sau ›</button>
        </nav>
      )}

      {editing && (
        <PromotionForm
          key={editing.mode === 'edit' ? editing.promotion.id : 'new'}
          token={token}
          promotion={editing.mode === 'edit' ? editing.promotion : null}
          channels={channels}
          onSaved={onSaved}
          onClose={() => setEditing(null)}
          onUnauthorized={onUnauthorized}
        />
      )}
    </>
  )
}

/** "−30.000đ, đơn từ 300.000đ" hoặc "Giảm đến 15%" (flash sale, theo giá niêm yết hiện tại). */
function discountText(p: Promotion): React.ReactNode {
  if (p.type === 'VOUCHER') {
    return (
      <>
        <strong>−{formatVnd(p.discountAmount ?? 0)}</strong>
        <span className="cell-sub">{p.minOrderAmount ? `đơn từ ${formatVnd(p.minOrderAmount)}` : 'mọi đơn'}</span>
      </>
    )
  }
  const percents = p.items.map((i) => i.discountPercent).filter((x): x is number => x != null)
  if (percents.length === 0) return <span className="muted">—</span>
  const max = Math.max(...percents)
  const min = Math.min(...percents)
  return <strong>{min === max ? `Giảm ${max}%` : `Giảm ${min}–${max}%`}</strong>
}
