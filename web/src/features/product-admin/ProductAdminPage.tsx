import { useCallback, useEffect, useState } from 'react'
import { formatVnd } from '../../shared/lib/format'
import { ApiError } from '../../shared/lib/http'
import type { Page, Product } from '../../shared/types'
import { listProducts, setProductActive, type ListQuery } from './api'
import { loadToken, saveToken } from './auth'
import { LoginForm } from './LoginForm'
import { ProductForm } from './ProductForm'
import { CATEGORIES, labelOf } from './vocabulary'

const PAGE_SIZE = 20

type Editing = { mode: 'create' } | { mode: 'edit'; product: Product } | null

// Admin > Sản phẩm (US-08, UC-20): danh sách gồm cả SKU ẩn, thêm, sửa, ẩn/hiện. Không có xoá (G-07). Owner: LucVT.
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
    <main className="admin">
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
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1

  return (
    <>
      <div className="admin-header">
        <h2>Sản phẩm</h2>
        <button type="button" className="btn btn-primary" onClick={() => setEditing({ mode: 'create' })}>+ Thêm sản phẩm</button>
        <button type="button" className="btn" onClick={onLogout}>Đăng xuất</button>
      </div>
      <p className="muted">Gồm cả sản phẩm đã ẩn. Ẩn thì cửa hàng không hiện và AI không gợi ý; không có xoá hẳn.</p>

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

      <div className="table-wrap">
        <table className="product-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>Sản phẩm</th>
              <th>Loại</th>
              <th className="num">Giá web</th>
              <th className="num">Shopee</th>
              <th className="num">TikTok</th>
              <th className="num">Tồn</th>
              <th>Trạng thái</th>
              <th><span className="sr-only">Thao tác</span></th>
            </tr>
          </thead>
          <tbody>
            {data?.items.length === 0 && (
              <tr><td colSpan={9} className="muted">Không có sản phẩm nào khớp.</td></tr>
            )}
            {data?.items.map((p) => (
              <tr key={p.id} className={p.active ? undefined : 'row-hidden'}>
                <td><code>{p.sku}</code></td>
                <td>
                  {p.name}
                  <span className="cell-sub">{p.brand}</span>
                </td>
                <td>{labelOf(CATEGORIES, p.category)}</td>
                <td className="num">{formatVnd(p.priceWeb)}</td>
                <td className="num">{formatVnd(p.priceShopee)}</td>
                <td className="num">{formatVnd(p.priceTiktok)}</td>
                <td className="num">{p.stock === 0 ? <span className="badge badge-warn">Hết</span> : p.stock}</td>
                <td>{p.active ? <span className="badge badge-on">Đang bán</span> : <span className="badge badge-off">Đã ẩn</span>}</td>
                <td className="row-actions">
                  <button type="button" className="btn btn-sm" onClick={() => setEditing({ mode: 'edit', product: p })}>Sửa</button>
                  <button type="button" className="btn btn-sm" disabled={toggling === p.id} onClick={() => toggleActive(p)}>
                    {p.active ? 'Ẩn' : 'Hiện'}
                  </button>
                </td>
              </tr>
            ))}
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
          onSaved={onSaved}
          onClose={() => setEditing(null)}
          onUnauthorized={onUnauthorized}
        />
      )}
    </>
  )
}
