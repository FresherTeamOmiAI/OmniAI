import { useEffect, useRef, useState } from 'react'
import { formatVnd, toDateTimeLocal } from '../../shared/lib/format'
import { ApiError } from '../../shared/lib/http'
import type { Promotion, PromotionRequest, PromotionType, SalesChannel } from '../../shared/types'
import { createPromotion, lookupSku, updatePromotion, type SkuPrice } from './api'

interface Props {
  token: string
  /** null = thêm mới. */
  promotion: Promotion | null
  channels: SalesChannel[]
  onSaved: (promotion: Promotion, created: boolean) => void
  onClose: () => void
  onUnauthorized: () => void
}

interface ItemRow {
  /** Khoá React ổn định khi thêm/xoá dòng. */
  key: number
  sku: string
  salePrice: string
}

// Giá trị trong form để dạng chuỗi giống input; đổi sang số khi gửi.
interface FormState {
  channel: string
  type: PromotionType
  name: string
  voucherCode: string
  discountAmount: string
  minOrderAmount: string
  /** Giá trị ô datetime-local (giờ Việt Nam, không offset). */
  startsAt: string
  endsAt: string
  enabled: boolean
  items: ItemRow[]
}

// Khoá = tên field backend trả về (name, startsAt, items[0].salePrice...).
type FieldErrors = Record<string, string>

let nextKey = 1
const row = (sku = '', salePrice = ''): ItemRow => ({ key: nextKey++, sku, salePrice })

function empty(channels: SalesChannel[]): FormState {
  const start = toDateTimeLocal(new Date().toISOString())
  const end = toDateTimeLocal(new Date(Date.now() + 24 * 3600_000).toISOString())
  return {
    channel: channels[0]?.code ?? '', type: 'VOUCHER', name: '', voucherCode: '', discountAmount: '',
    minOrderAmount: '', startsAt: start, endsAt: end, enabled: true, items: [row()],
  }
}

function fromPromotion(p: Promotion): FormState {
  return {
    channel: p.channel, type: p.type, name: p.name, voucherCode: p.voucherCode ?? '',
    discountAmount: p.discountAmount != null ? String(p.discountAmount) : '',
    minOrderAmount: p.minOrderAmount != null ? String(p.minOrderAmount) : '',
    startsAt: toDateTimeLocal(p.startsAt), endsAt: toDateTimeLocal(p.endsAt), enabled: p.enabled,
    items: p.items.length ? p.items.map((i) => row(i.sku, String(i.salePrice))) : [row()],
  }
}

const num = (value: string) => (value.trim() === '' ? null : Number(value))
const normSku = (value: string) => value.trim().toUpperCase()

/** Dòng gửi lên + vị trí của nó trong form (bỏ dòng trống hẳn), để gắn lỗi items[i] về đúng dòng. */
function toRequest(f: FormState): { input: PromotionRequest; rowKeys: number[] } {
  const base = { channel: f.channel, type: f.type, name: f.name, startsAt: f.startsAt, endsAt: f.endsAt, enabled: f.enabled }
  if (f.type === 'VOUCHER') {
    return {
      input: { ...base, voucherCode: f.voucherCode, discountAmount: num(f.discountAmount), minOrderAmount: num(f.minOrderAmount) },
      rowKeys: [],
    }
  }
  const filled = f.items.filter((i) => i.sku.trim() !== '' || i.salePrice.trim() !== '')
  return {
    input: { ...base, items: filled.map((i) => ({ sku: normSku(i.sku), salePrice: num(i.salePrice) })) },
    rowKeys: filled.map((i) => i.key),
  }
}

/** Đổi lỗi API thành lỗi từng ô. items[i] → khoá của dòng thứ i đã gửi. 409 trùng mã gắn vào ô mã voucher. */
function toFieldErrors(e: ApiError, rowKeys: number[]): { fields: FieldErrors; message: string } {
  const fields: FieldErrors = {}
  for (const v of e.problem?.errors ?? []) {
    const m = /^items\[(\d+)]\.(sku|salePrice)$/.exec(v.field)
    const key = m && rowKeys[Number(m[1])] !== undefined ? `row-${rowKeys[Number(m[1])]}.${m[2]}` : v.field
    fields[key] = fields[key] ? `${fields[key]}; ${v.message}` : v.message
  }
  if (e.status === 409) fields.voucherCode = e.message
  const count = Object.keys(fields).length
  return { fields, message: count > 0 ? `Chưa lưu: ${count} ô cần sửa (tô đỏ bên dưới).` : e.message }
}

type Lookup = SkuPrice | 'missing' | 'loading'

// Thêm/sửa 1 khuyến mãi (T-33.2, UC-21). UI không tự kiểm luật: backend kiểm và trả lỗi theo ô (web.md, admin sản phẩm).
export function PromotionForm({ token, promotion, channels, onSaved, onClose, onUnauthorized }: Props) {
  const creating = promotion === null
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [form, setForm] = useState<FormState>(() => (promotion ? fromPromotion(promotion) : empty(channels)))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  // Tra giá niêm yết theo SKU để admin thấy % giảm trước khi lưu. Chỉ là gợi ý.
  const [lookups, setLookups] = useState<Record<string, Lookup>>({})
  const [opener] = useState(() => (document.activeElement instanceof HTMLElement ? document.activeElement : null))

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => {
      if (opener?.isConnected) opener.focus()
    }
  }, [opener])

  useEffect(() => {
    dialogRef.current?.querySelector<HTMLElement>('.field-invalid input, .field-invalid select')?.focus()
  }, [errors])

  const look = (raw: string) => {
    const sku = normSku(raw)
    if (!sku || lookups[sku]) return
    setLookups((l) => ({ ...l, [sku]: 'loading' }))
    lookupSku(sku)
      .then((p) => setLookups((l) => ({ ...l, [sku]: p })))
      .catch(() => setLookups((l) => ({ ...l, [sku]: 'missing' })))
  }

  // Sửa flash sale: tra sẵn các SKU đang có để hiện giá niêm yết ngay.
  useEffect(() => {
    if (promotion?.type !== 'FLASH_SALE') return
    let current = true
    for (const { sku } of promotion.items) {
      lookupSku(sku)
        .then((p) => current && setLookups((l) => ({ ...l, [sku]: p })))
        .catch(() => current && setLookups((l) => ({ ...l, [sku]: 'missing' })))
    }
    return () => {
      current = false
    }
  }, [promotion])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }))
  const setRow = (key: number, patch: Partial<ItemRow>) =>
    setForm((f) => ({ ...f, items: f.items.map((i) => (i.key === key ? { ...i, ...patch } : i)) }))

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    const { input, rowKeys } = toRequest(form)
    try {
      const saved = creating ? await createPromotion(token, input) : await updatePromotion(token, promotion.id, input)
      onSaved(saved, creating)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return onUnauthorized()
      if (e instanceof ApiError) {
        const r = toFieldErrors(e, rowKeys)
        setErrors(r.fields)
        setMessage(r.message)
      } else {
        setErrors({})
        setMessage(`Không gọi được backend: ${(e as Error).message}`)
      }
    } finally {
      setBusy(false)
    }
  }

  const errorId = (key: string) => `prf-${key.replace(/[^a-zA-Z0-9-]/g, '-')}-error`
  const field = (key: string, label: string, input: React.ReactNode, hint?: string, wide = false) => (
    <div className={`field${wide ? ' field-wide' : ''}${errors[key] ? ' field-invalid' : ''}`}>
      <label htmlFor={`prf-${key}`}>{label}</label>
      {input}
      {hint && !errors[key] && <small className="muted">{hint}</small>}
      {errors[key] && <small className="field-error" id={errorId(key)}>{errors[key]}</small>}
    </div>
  )
  const aria = (key: string) => ({ 'aria-invalid': !!errors[key], 'aria-describedby': errors[key] ? errorId(key) : undefined })

  const channelName = (code: string) => channels.find((c) => c.code === code)?.name ?? code

  /** Gợi ý dưới dòng flash sale: tên + giá niêm yết ở kênh đang chọn + % giảm. */
  const itemHint = (item: ItemRow): React.ReactNode => {
    const sku = normSku(item.sku)
    const found = sku ? lookups[sku] : undefined
    if (!found) return null
    if (found === 'loading') return <span className="muted">Đang tra…</span>
    if (found === 'missing') return <span className="field-error">Không có sản phẩm đang bán với SKU này.</span>
    const list = found.prices[form.channel]
    if (list == null) return <span className="muted">{found.name}: chưa có giá ở {channelName(form.channel)}.</span>
    const sale = num(item.salePrice)
    const percent = sale != null && sale > 0 && sale < list ? Math.floor(((list - sale) * 100) / list) : null
    return (
      <span className="muted">
        {found.name} · giá niêm yết {channelName(form.channel)} <strong>{formatVnd(list)}</strong>
        {percent != null && <> · giảm <strong>{percent}%</strong></>}
      </span>
    )
  }

  return (
    <dialog ref={dialogRef} className="product-dialog" onClose={onClose} aria-labelledby="prf-title">
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="prf-title">{creating ? 'Thêm khuyến mãi' : `Sửa khuyến mãi #${promotion.id}`}</h2>
          <button type="button" className="btn-icon" onClick={onClose} aria-label="Đóng">×</button>
        </header>

        <div className="dialog-body">
          {message && <p className="form-message" role="alert">{message}</p>}

          <fieldset className={`field field-wide${errors.type ? ' field-invalid' : ''}`}>
            <legend>Loại</legend>
            <div className="check-list">
              {([['VOUCHER', 'Voucher: giảm số tiền cố định cho cả đơn'], ['FLASH_SALE', 'Flash sale: giá sale riêng từng sản phẩm']] as const)
                .map(([value, label]) => (
                  <label key={value} className="check">
                    <input type="radio" name="prf-type" value={value} checked={form.type === value} disabled={!creating}
                      onChange={() => set('type', value)} />
                    {label}
                  </label>
                ))}
            </div>
            {!creating && <small className="muted">Không đổi loại khi sửa. Cần loại khác thì tạo khuyến mãi mới.</small>}
            {errors.type && <small className="field-error">{errors.type}</small>}
          </fieldset>

          <div className="form-grid">
            {field('channel', 'Kênh', (
              <select id="prf-channel" value={form.channel} onChange={(e) => set('channel', e.target.value)} {...aria('channel')}>
                {channels.map((c) => <option key={c.code} value={c.code}>{c.name}{c.active ? '' : ' (tạm ngừng)'}</option>)}
              </select>
            ), 'Bot trên sàn chỉ nói khuyến mãi của chính sàn đó.')}
            {field('name', 'Tên khuyến mãi', (
              <input id="prf-name" value={form.name} onChange={(e) => set('name', e.target.value)} autoFocus
                placeholder={form.type === 'VOUCHER' ? 'Voucher 12.12' : 'Flash sale 20h'} {...aria('name')} />
            ))}
            {field('startsAt', 'Bắt đầu', (
              <input id="prf-startsAt" type="datetime-local" value={form.startsAt} onChange={(e) => set('startsAt', e.target.value)} {...aria('startsAt')} />
            ), 'Giờ Việt Nam.')}
            {field('endsAt', 'Kết thúc', (
              <input id="prf-endsAt" type="datetime-local" value={form.endsAt} onChange={(e) => set('endsAt', e.target.value)} {...aria('endsAt')} />
            ), 'Hết giờ thì khuyến mãi tự tắt, bot và cửa hàng không thấy nữa.')}
          </div>

          {form.type === 'VOUCHER' ? (
            <fieldset className="price-group">
              <legend>Voucher</legend>
              <div className="form-grid form-grid-4">
                {field('voucherCode', 'Mã voucher', (
                  <input id="prf-voucherCode" value={form.voucherCode} placeholder="SALE1212" autoCapitalize="characters"
                    onChange={(e) => set('voucherCode', e.target.value.toUpperCase())} {...aria('voucherCode')} />
                ), '3–20 chữ không dấu hoặc số.')}
                {field('discountAmount', 'Giảm (VNĐ)', (
                  <input id="prf-discountAmount" type="number" inputMode="numeric" min={1} step={1000} value={form.discountAmount}
                    onChange={(e) => set('discountAmount', e.target.value)} {...aria('discountAmount')} />
                ))}
                {field('minOrderAmount', 'Đơn tối thiểu (VNĐ)', (
                  <input id="prf-minOrderAmount" type="number" inputMode="numeric" min={0} step={1000} value={form.minOrderAmount}
                    placeholder="0" onChange={(e) => set('minOrderAmount', e.target.value)} {...aria('minOrderAmount')} />
                ), 'Để trống = mọi đơn.')}
              </div>
            </fieldset>
          ) : (
            <fieldset className={`price-group${errors.items ? ' field-invalid' : ''}`}>
              <legend>Sản phẩm và giá sale</legend>
              <p className="muted">Giá sale phải thấp hơn giá niêm yết ở kênh {channelName(form.channel)}. Mỗi sản phẩm chỉ có 1 flash sale tại 1 thời điểm trên 1 kênh.</p>
              {errors.items && <p className="field-error">{errors.items}</p>}
              <ul className="promo-items">
                {form.items.map((item, index) => {
                  const skuKey = `row-${item.key}.sku`
                  const priceKey = `row-${item.key}.salePrice`
                  return (
                    <li key={item.key} className="promo-item">
                      <div className={`field${errors[skuKey] ? ' field-invalid' : ''}`}>
                        <label htmlFor={`prf-${skuKey}`}>SKU {index + 1}</label>
                        <input id={`prf-${skuKey}`} value={item.sku} placeholder="CERA-CLE-01" autoCapitalize="characters"
                          onChange={(e) => setRow(item.key, { sku: e.target.value.toUpperCase() })} onBlur={(e) => look(e.target.value)}
                          {...aria(skuKey)} />
                        {errors[skuKey] && <small className="field-error" id={errorId(skuKey)}>{errors[skuKey]}</small>}
                      </div>
                      <div className={`field${errors[priceKey] ? ' field-invalid' : ''}`}>
                        <label htmlFor={`prf-${priceKey}`}>Giá sale (VNĐ)</label>
                        <input id={`prf-${priceKey}`} type="number" inputMode="numeric" min={1} step={1000} value={item.salePrice}
                          onChange={(e) => setRow(item.key, { salePrice: e.target.value })} {...aria(priceKey)} />
                        {errors[priceKey] && <small className="field-error" id={errorId(priceKey)}>{errors[priceKey]}</small>}
                      </div>
                      <button type="button" className="btn btn-sm promo-item-remove" disabled={form.items.length === 1}
                        onClick={() => set('items', form.items.filter((i) => i.key !== item.key))} aria-label={`Bỏ dòng ${index + 1}`}>
                        Bỏ
                      </button>
                      <small className="promo-item-hint">{itemHint(item)}</small>
                    </li>
                  )
                })}
              </ul>
              <button type="button" className="btn btn-sm" onClick={() => set('items', [...form.items, row()])}>+ Thêm sản phẩm</button>
            </fieldset>
          )}

          <label className="check">
            <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />
            Đang bật (bỏ chọn để lưu nháp, bot và cửa hàng không thấy)
          </label>
        </div>

        <footer className="dialog-footer">
          <button type="button" className="btn" onClick={onClose}>Huỷ</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Đang lưu…' : creating ? 'Thêm khuyến mãi' : 'Lưu thay đổi'}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
