// Bộ giá trị hợp lệ của catalog, nhãn tiếng Việt cho UI.
// Phải khớp backend catalog/CatalogVocabulary.java và tools/catalog/validate_catalog.py (docs/specs/data-model.md).
// UI chỉ dùng để hiện lựa chọn; kiểm tra dữ liệu là việc của backend.

export interface Option {
  value: string
  label: string
}

export const CATEGORIES: Option[] = [
  { value: 'cleanser', label: 'Sữa rửa mặt' },
  { value: 'toner', label: 'Toner' },
  { value: 'serum', label: 'Serum' },
  { value: 'moisturizer', label: 'Kem dưỡng ẩm' },
  { value: 'sunscreen', label: 'Kem chống nắng' },
  { value: 'exfoliant', label: 'Tẩy tế bào chết' },
  { value: 'spot-treatment', label: 'Chấm mụn' },
  { value: 'mask', label: 'Mặt nạ' },
  { value: 'eye-cream', label: 'Kem mắt' },
  { value: 'lip-care', label: 'Dưỡng môi' },
]

export const SKIN_TYPES: Option[] = [
  { value: 'oily', label: 'Da dầu' },
  { value: 'dry', label: 'Da khô' },
  { value: 'combination', label: 'Da hỗn hợp' },
  { value: 'sensitive', label: 'Da nhạy cảm' },
  { value: 'normal', label: 'Da thường' },
]

export const SKIN_CONCERNS: Option[] = [
  { value: 'acne', label: 'Mụn' },
  { value: 'oil-control', label: 'Kiềm dầu' },
  { value: 'pores', label: 'Lỗ chân lông' },
  { value: 'hydration', label: 'Cấp ẩm' },
  { value: 'dullness', label: 'Da xỉn màu' },
  { value: 'dark-spots', label: 'Vết thâm' },
  { value: 'aging', label: 'Lão hoá' },
  { value: 'sensitivity', label: 'Da kích ứng' },
  { value: 'barrier', label: 'Hàng rào da' },
  { value: 'sun-protection', label: 'Chống nắng' },
]

export const USAGE_TIMES: Option[] = [
  { value: '', label: 'Không ghi' },
  { value: 'am', label: 'Sáng' },
  { value: 'pm', label: 'Tối' },
  { value: 'am,pm', label: 'Sáng và tối' },
]

export function labelOf(options: Option[], value: string): string {
  return options.find((o) => o.value === value)?.label ?? value
}
