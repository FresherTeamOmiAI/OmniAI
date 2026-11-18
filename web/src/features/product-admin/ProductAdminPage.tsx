import { Fragment, useCallback, useEffect, useState } from 'react'
import { formatVnd } from '../../shared/lib/format'
import { ApiError } from '../../shared/lib/http'
import type { Page, Product, SalesChannel } from '../../shared/types'
import {
  listProducts, listSalesChannels, listSyncChannels, listSyncStatus, retrySync, setProductActive,
  type ListQuery, type SyncAspect, type SyncView,
} from './api'
import { loadToken, saveToken } from './auth'
import { LoginForm } from './LoginForm'
import { ProductForm } from './ProductForm'
import { SyncDetail, SyncStatusCell } from './SyncStatusCell'
import { CATEGORIES, labelOf } from '../../shared/vocabulary'

const PAGE_SIZE = 20
/** Còn phần đang chờ gửi thì hỏi lại trạng thái sau chừng này (worker quét 1 s, AC US-49 ≤ 10 s). */
const SYNC_POLL_MS = 3000

type Editing = { mode: 'create' } | { mode: 'edit'; product: Product } | null

// Admin > Sản phẩm (US-08, UC-20): danh sách gồm cả SKU ẩn, thêm, sửa, ẩn/hiện. Không có xoá (G-07). Owner: LucVT.
// Cột "Đồng bộ sàn" (US-49, T-49.3): trạng thái đẩy sản phẩm ra từng sàn có connector, thử lại phần lỗi.
export function ProductAdminPage() {
  const [token, setToken] = useState<string | null>(loadToken)
  const [loginNotice, setLoginNotice] = useState<string | null>(null)

  const login = (t: string) => {
    saveToken(t)
    setLoginNotice(null)
    setToken(t)
  }
  // useCallback: ProductTable dùng onUnauthorized trong deps của effect tải danh sách.
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
        <ProductTable token={token} onLogout={onLogout} onUnauthorized={onUnauthorized} />
      ) : (
        <LoginForm notice={loginNotice} onLogin={login} />
      )}
    </main>
  )
}

interface TableProps {
  token: string
  onLogout: () => void
  onUnauthorized: () => void
}

function ProductTable({ token, onLogout, onUnauthorized }: TableProps) {
  const [query, setQuery] = useState<ListQuery>({ q: '', active: '', page: 0, size: PAGE_SIZE })
  const [search, setSearch] = useState('')
  // Tăng reload để tải lại cùng query (sau khi thêm/sửa).
  const [reload, setReload] = useState(0)
  // Kết quả gắn với khoá của lần tải; khoá hiện tại khác khoá kết quả nghĩa là đang tải.
  const [result, setResult] = useState<{ key: string; page: Page<Product> | null } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing>(null)
  const [toggling, setToggling] = useState<number | null>(null)
  // Cột giá và ô giá trong form sinh theo kênh bán (V13), không viết cứng 3 kênh.
  const [channels, setChannels] = useState<SalesChannel[]>([])
  // Đồng bộ ra sàn (T-49.3): kênh có connector + trạng thái của các sản phẩm trên trang đang xem.
  const [syncChannels, setSyncChannels] = useState<string[]>([])
  const [sync, setSync] = useState<SyncView[]>([])
  const [syncError, setSyncError] = useState<string | null>(null)
  const [syncReload, setSyncReload] = useState(0)
  const [openSync, setOpenSync] = useState<{ productId: number; channel: string } | null>(null)

  const key = `${JSON.stringify(query)}#${reload}`
  const loading = result?.key !== key
  const data = result?.page ?? null

  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) return onUnauthorized()
    setError((e as Error).message)
  }, [onUnauthorized])

  useEffect(() => {
    let current = true
    listProducts(token, query)
      .then((page) => {
        if (!current) return
        setResult({ key, page })
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
  }, [token, query, key, fail])

  useEffect(() => {
    let current = true
    listSalesChannels(token)
      .then((list) => current && setChannels(list))
      .catch((e) => current && fail(e))
    return () => {
      current = false
    }
  }, [token, fail])

  useEffect(() => {
    let current = true
    listSyncChannels(token)
      .then((list) => current && setSyncChannels(list))
      .catch((e) => current && fail(e))
    return () => {
      current = false
    }
  }, [token, fail])

  // Trạng thái đồng bộ của trang đang xem. Còn phần PENDING thì tự hỏi lại, hết thì dừng.
  const pageIds = data?.items.map((p) => p.id).join(',') ?? ''
  useEffect(() => {
    if (!pageIds || syncChannels.length === 0) return
    let current = true
    let timer: ReturnType<typeof setTimeout> | undefined
    const load = () =>
      listSyncStatus(token, pageIds.split(',').map(Number))
        .then((rows) => {
          if (!current) return
          setSync(rows)
          setSyncError(null)
          if (rows.some((r) => r.status === 'PENDING')) timer = setTimeout(load, SYNC_POLL_MS)
        })
        .catch((e) => {
          if (!current) return
          if (e instanceof ApiError && e.status === 401) return onUnauthorized()
          setSyncError(`Không tải được trạng thái đồng bộ: ${(e as Error).message}`)
        })
    void load()
    return () => {
      current = false
      clearTimeout(timer)
    }
  }, [token, pageIds, syncChannels.length, syncReload, onUnauthorized])

  const retry = async (productId: number, channel: string, aspect: SyncAspect) => {
    try {
      await retrySync(token, productId, channel, aspect)
      setStatus(`Đã xếp gửi lại sản phẩm #${productId} lên ${channelName(channel)}.`)
      setError(null)
      setSyncReload((n) => n + 1)
    } catch (e) {
      fail(e)
    }
  }

  const channelName = (code: string) => channels.find((c) => c.code === code)?.name ?? code
  const syncColumns = syncChannels.map((code) => ({ code, name: channelName(code) }))
  const columnCount = 6 + channels.length + (syncColumns.length > 0 ? 1 : 0)

  const setData = (update: (page: Page<Product>) => Page<Product>) =>
    setResult((r) => (r?.page ? { ...r, page: update(r.page) } : r))

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault()
    setQuery((q) => ({ ...q, q: search, page: 0 }))
  }

  const toggleActive = async (p: Product) => {
    setToggling(p.id)
    try {
      const saved = await setProductActive(token, p.id, !p.active)
      setData((d) => ({ ...d, items: d.items.map((x) => (x.id === saved.id ? saved : x)) }))
      setSyncReload((n) => n + 1)
      setStatus(saved.active ? `Đã hiện ${saved.sku}: cửa hàng và AI thấy lại sản phẩm này.` : `Đã ẩn ${saved.sku}: cửa hàng không hiện, AI không gợi ý.`)
      setError(null)
    } catch (e) {
      fail(e)
    } finally {
      setToggling(null)
    }
  }

  const onSaved = (saved: Product, created: boolean) => {
    setEditing(null)
    setStatus(created ? `Đã thêm ${saved.sku}.` : `Đã lưu ${saved.sku}.`)
    setReload((n) => n + 1)
    setSyncReload((n) => n + 1)
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div className="admin-header">
        <h1>Sản phẩm</h1>
        <button type="button" className="btn btn-primary" onClick={() => setEditing({ mode: 'create' })}>+ Thêm sản phẩm</button>
        <button type="button" className="btn" onClick={onLogout}>Đăng xuất</button>
      </div>
      <p className="admin-intro">Gồm cả sản phẩm đã ẩn. Ẩn thì cửa hàng không hiện và AI không gợi ý; không có xoá hẳn.</p>

      <form className="admin-toolbar" onSubmit={submitSearch} role="search">
        <label className="sr-only" htmlFor="pa-search">Tìm theo SKU hoặc tên</label>
        <input id="pa-search" type="search" placeholder="Tìm theo SKU hoặc tên…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <button type="submit" className="btn">Tìm</button>
        <label className="sr-only" htmlFor="pa-active">Trạng thái</label>
        <select id="pa-active" value={query.active} onChange={(e) => setQuery((q) => ({ ...q, active: e.target.value as ListQuery['active'], page: 0 }))}>
          <option value="">Mọi trạng thái</option>
          <option value="true">Đang bán</option>
          <option value="false">Đã ẩn</option>
        </select>
        <span className="muted toolbar-count" aria-live="polite">{loading ? 'Đang tải…' : data ? `${data.total} sản phẩm` : ''}</span>
      </form>

      {status && <p className="notice" role="status">{status}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {syncError && <p className="muted sync-load-error" role="status">{syncError}</p>}

      <div className="table-wrap">
        <table className={loading && data ? 'product-table is-loading' : 'product-table'} aria-busy={loading}>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Sản phẩm</th>
              <th>Loại</th>
              {channels.map((c) => <th key={c.code} className="num">{c.owned ? 'Giá web' : c.name}</th>)}
              <th className="num">Tồn</th>
              <th>Trạng thái</th>
              {syncColumns.length > 0 && <th>Đồng bộ sàn</th>}
              <th><span className="sr-only">Thao tác</span></th>
            </tr>
          </thead>
          <tbody>
            {data?.items.length === 0 && (
              <tr><td colSpan={columnCount} className="muted">Không có sản phẩm nào khớp.</td></tr>
            )}
            {data?.items.map((p) => {
              const rows = sync.filter((r) => r.productId === p.id)
              const openChannel = openSync?.productId === p.id ? syncColumns.find((c) => c.code === openSync.channel) : undefined
              return (
                <Fragment key={p.id}>
                  <tr className={p.active ? undefined : 'row-hidden'}>
                    <td><code>{p.sku}</code></td>
                    <td>
                      <span className="cell-name">{p.name}</span>
                      <span className="cell-sub">{p.brand}</span>
                    </td>
                    <td>{labelOf(CATEGORIES, p.category)}</td>
                    {channels.map((c) => (
                      <td key={c.code} className="num">{p.prices[c.code] != null ? formatVnd(p.prices[c.code]) : <span className="muted">—</span>}</td>
                    ))}
                    <td className="num">{p.stock === 0 ? <span className="badge badge-warn">Hết</span> : p.stock}</td>
                    <td>{p.active ? <span className="badge badge-on">Đang bán</span> : <span className="badge badge-off">Đã ẩn</span>}</td>
                    {syncColumns.length > 0 && (
                      <td>
                        <SyncStatusCell productId={p.id} channels={syncColumns} rows={rows} open={openChannel?.code ?? null}
                          onToggle={(channel) => setOpenSync((o) => (o?.productId === p.id && o.channel === channel ? null : { productId: p.id, channel }))} />
                      </td>
                    )}
                    <td className="row-actions">
                      <button type="button" className="btn btn-sm" onClick={() => setEditing({ mode: 'edit', product: p })}>Sửa</button>
                      <button type="button" className="btn btn-sm" disabled={toggling === p.id} onClick={() => toggleActive(p)}>
                        {p.active ? 'Ẩn' : 'Hiện'}
                      </button>
                    </td>
                  </tr>
                  {openChannel && (
                    <tr className="sync-detail-row">
                      <td colSpan={columnCount}>
                        <SyncDetail productId={p.id} channel={openChannel} rows={rows} onRetry={retry} onClose={() => setOpenSync(null)} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>

      {data && totalPages > 1 && (
        <nav className="pager" aria-label="Phân trang">
          <button type="button" className="btn btn-sm" disabled={query.page === 0} onClick={() => setQuery((q) => ({ ...q, page: q.page - 1 }))}>‹ Trước</button>
          <span>Trang {query.page + 1}/{totalPages}</span>
          <button type="button" className="btn btn-sm" disabled={query.page + 1 >= totalPages} onClick={() => setQuery((q) => ({ ...q, page: q.page + 1 }))}>Sau ›</button>
        </nav>
      )}

      {editing && (
        <ProductForm
          key={editing.mode === 'edit' ? editing.product.id : 'new'}
          token={token}
          product={editing.mode === 'edit' ? editing.product : null}
          channels={channels}
          onSaved={onSaved}
          onClose={() => setEditing(null)}
          onUnauthorized={onUnauthorized}
        />
      )}
    </>
  )
}
