import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../shared/lib/http'
import type { Product } from '../../shared/types'
import { createProduct, updateProduct, type ProductInput } from './api'
import { CATEGORIES, SKIN_CONCERNS, SKIN_TYPES, USAGE_TIMES, type Option } from './vocabulary'

interface Props {
  token: string
  /** null = thêm mới. */
  product: Product | null
  onSaved: (product: Product, created: boolean) => void
  onClose: () => void
  onUnauthorized: () => void
}

// Giá trị trong form để dạng chuỗi giống input; đổi sang số khi gửi.
interface FormState {
  sku: string
  name: string
  brand: string
  category: string
  skinTypes: string[]
  skinConcerns: string[]
  usageTime: string
  description: string
  ingredientsInci: string
  sizeMl: string
  paoMonths: string
  priceWeb: string
  priceShopee: string
  priceTiktok: string
  stock: string
  active: boolean
}

type FieldErrors = Partial<Record<keyof FormState, string>>

const EMPTY: FormState = {
  sku: '', name: '', brand: '', category: '', skinTypes: [], skinConcerns: [], usageTime: '', description: '',
  ingredientsInci: '', sizeMl: '', paoMonths: '', priceWeb: '', priceShopee: '', priceTiktok: '', stock: '0', active: true,
}

const csv = (value: string | null) => (value ? value.split(',').map((s) => s.trim()).filter(Boolean) : [])

function fromProduct(p: Product): FormState {
  return {
    sku: p.sku, name: p.name, brand: p.brand, category: p.category, skinTypes: csv(p.skinTypes),
    skinConcerns: csv(p.skinConcerns), usageTime: p.usageTime ?? '', description: p.description ?? '',
    ingredientsInci: p.ingredientsInci, sizeMl: String(p.sizeMl), paoMonths: String(p.paoMonths),
    priceWeb: String(p.priceWeb), priceShopee: String(p.priceShopee), priceTiktok: String(p.priceTiktok),
    stock: String(p.stock), active: p.active,
  }
}

const num = (value: string) => (value.trim() === '' ? null : Number(value))
const text = (value: string) => (value.trim() === '' ? null : value)

function toInput(f: FormState, creating: boolean): ProductInput {
  return {
    sku: f.sku, name: f.name, brand: f.brand, category: f.category, skinTypes: f.skinTypes.join(','),
    skinConcerns: f.skinConcerns.length ? f.skinConcerns.join(',') : null, usageTime: text(f.usageTime),
    description: text(f.description), ingredientsInci: f.ingredientsInci, sizeMl: num(f.sizeMl),
    paoMonths: num(f.paoMonths), priceWeb: num(f.priceWeb), priceShopee: num(f.priceShopee),
    priceTiktok: num(f.priceTiktok), stock: num(f.stock),
    // Sửa thì không gửi active: backend giữ nguyên, ẩn/hiện dùng nút riêng trong bảng.
    active: creating ? f.active : null,
  }
}

/** Đổi lỗi API thành lỗi từng ô. 409 trùng SKU gắn vào ô SKU. */
function toFieldErrors(e: ApiError): { fields: FieldErrors; message: string } {
  const fields: FieldErrors = {}
  for (const v of e.problem?.errors ?? []) {
    const key = v.field as keyof FormState
    fields[key] = fields[key] ? `${fields[key]}; ${v.message}` : v.message
  }
  if (e.status === 409) fields.sku = e.message
  const count = Object.keys(fields).length
  return { fields, message: count > 0 ? `Chưa lưu: ${count} ô cần sửa (tô đỏ bên dưới).` : e.message }
}

export function ProductForm({ token, product, onSaved, onClose, onUnauthorized }: Props) {
  const creating = product === null
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [form, setForm] = useState<FormState>(product ? fromProduct(product) : EMPTY)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  // Có lỗi thì đưa con trỏ tới ô sai đầu tiên (form dài, lỗi có thể nằm dưới phải cuộn).
  useEffect(() => {
    dialogRef.current?.querySelector<HTMLElement>('.field-invalid input, .field-invalid select, .field-invalid textarea')?.focus()
  }, [errors])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }))

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const input = toInput(form, creating)
      const saved = creating ? await createProduct(token, input) : await updateProduct(token, product.id, input)
      onSaved(saved, creating)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return onUnauthorized()
      if (e instanceof ApiError) {
        const r = toFieldErrors(e)
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

  const field = (key: keyof FormState, label: string, input: React.ReactNode, hint?: string, wide = false) => (
    <div className={`field${wide ? ' field-wide' : ''}${errors[key] ? ' field-invalid' : ''}`}>
      <label htmlFor={`pf-${key}`}>{label}</label>
      {input}
      {hint && !errors[key] && <small className="muted">{hint}</small>}
      {errors[key] && <small className="field-error" id={`pf-${key}-error`}>{errors[key]}</small>}
    </div>
  )

  const textInput = (key: 'sku' | 'name' | 'brand', props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <input id={`pf-${key}`} value={form[key]} onChange={(e) => set(key, e.target.value)}
      aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `pf-${key}-error` : undefined} {...props} />
  )

  const numberInput = (key: 'sizeMl' | 'paoMonths' | 'priceWeb' | 'priceShopee' | 'priceTiktok' | 'stock', min: number) => (
    <input id={`pf-${key}`} type="number" inputMode="numeric" min={min} step={1} value={form[key]}
      onChange={(e) => set(key, e.target.value)} aria-invalid={!!errors[key]}
      aria-describedby={errors[key] ? `pf-${key}-error` : undefined} />
  )

  const checkboxGroup = (key: 'skinTypes' | 'skinConcerns', label: string, options: Option[], hint: string) => (
    <fieldset className={`field field-wide checks${errors[key] ? ' field-invalid' : ''}`}>
      <legend>{label}</legend>
      <div className="check-list">
        {options.map((o) => (
          <label key={o.value} className="check">
            <input type="checkbox" checked={form[key].includes(o.value)}
              onChange={(e) => set(key, e.target.checked ? [...form[key], o.value] : form[key].filter((v) => v !== o.value))} />
            {o.label}
          </label>
        ))}
      </div>
      {!errors[key] && <small className="muted">{hint}</small>}
      {errors[key] && <small className="field-error">{errors[key]}</small>}
    </fieldset>
  )

  return (
    <dialog ref={dialogRef} className="product-dialog" onClose={onClose} aria-labelledby="pf-title">
      <form onSubmit={submit} noValidate>
        <header className="dialog-header">
          <h2 id="pf-title">{creating ? 'Thêm sản phẩm' : `Sửa ${product.sku}`}</h2>
          <button type="button" className="btn-icon" onClick={onClose} aria-label="Đóng">×</button>
        </header>

        <div className="dialog-body">
          {message && <p className="form-message" role="alert">{message}</p>}
          <div className="form-grid">
            {field('sku', 'SKU', textInput('sku', { disabled: !creating, placeholder: 'CERA-SER-02', autoFocus: creating }),
              creating ? 'Dạng BRAND-LOẠI-SỐ, ví dụ CERA-SER-02. Không sửa được sau khi tạo.' : 'SKU không sửa được. Nhập sai thì ẩn sản phẩm rồi tạo SKU mới.')}
            {field('brand', 'Thương hiệu', textInput('brand', { placeholder: 'Lumi Lab' }))}
            {field('name', 'Tên sản phẩm', textInput('name', { autoFocus: !creating }), 'Không dùng từ như thuốc: "trị mụn", "đặc trị", "chữa"…', true)}
            {field('category', 'Loại sản phẩm', (
              <select id="pf-category" value={form.category} onChange={(e) => set('category', e.target.value)} aria-invalid={!!errors.category}>
                <option value="">Chọn loại…</option>
                {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            ))}
            {field('usageTime', 'Buổi dùng', (
              <select id="pf-usageTime" value={form.usageTime} onChange={(e) => set('usageTime', e.target.value)} aria-invalid={!!errors.usageTime}>
                {USAGE_TIMES.map((u) => <option key={u.value} value={u.value}>{u.label}</option>)}
              </select>
            ))}
            {checkboxGroup('skinTypes', 'Loại da phù hợp', SKIN_TYPES, 'Chọn ít nhất 1.')}
            {checkboxGroup('skinConcerns', 'Vấn đề da', SKIN_CONCERNS, 'Không bắt buộc.')}
            {field('ingredientsInci', 'Thành phần (INCI)', (
              <textarea id="pf-ingredientsInci" rows={3} value={form.ingredientsInci} onChange={(e) => set('ingredientsInci', e.target.value)}
                aria-invalid={!!errors.ingredientsInci} placeholder="Aqua, Niacinamide, Glycerin" />
            ), 'Cách nhau bằng dấu phẩy, ít nhất 2 thành phần.', true)}
            {field('description', 'Mô tả', (
              <textarea id="pf-description" rows={3} value={form.description} onChange={(e) => set('description', e.target.value)}
                aria-invalid={!!errors.description} />
            ), 'Không bắt buộc, tối đa 2000 ký tự.', true)}
            {field('sizeMl', 'Dung tích (ml)', numberInput('sizeMl', 1))}
            {field('paoMonths', 'Hạn dùng sau mở (tháng)', numberInput('paoMonths', 1))}
          </div>

          <fieldset className="price-group">
            <legend>Giá niêm yết (VNĐ) và tồn kho</legend>
            <p className="muted">Giá web dùng cho kênh của shop. Khuyến mãi quản lý riêng (US-33).</p>
            <div className="form-grid form-grid-4">
              {field('priceWeb', 'Giá web', numberInput('priceWeb', 0))}
              {field('priceShopee', 'Giá Shopee', numberInput('priceShopee', 0))}
              {field('priceTiktok', 'Giá TikTok', numberInput('priceTiktok', 0))}
              {field('stock', 'Tồn kho', numberInput('stock', 0))}
            </div>
          </fieldset>

          {creating && (
            <label className="check">
              <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
              Hiện ngay ở cửa hàng
            </label>
          )}
        </div>

        <footer className="dialog-footer">
          <button type="button" className="btn" onClick={onClose}>Huỷ</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Đang lưu…' : creating ? 'Thêm sản phẩm' : 'Lưu thay đổi'}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
