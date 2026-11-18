import { useState } from 'react'
import { BottleIcon } from '../../shared/ui/icons'

interface Props {
  src: string | null
  /** Để rỗng khi tên sản phẩm đã nằm ngay cạnh ảnh (thẻ trong danh sách), tránh đọc tên 2 lần. */
  alt: string
  lazy?: boolean
}

// Ảnh sản phẩm có hình dự phòng khi không có URL hoặc URL lỗi (US-56). Ảnh nằm trong khung tỉ lệ 1:1 nên không xô lệch trang.
export function ProductImage({ src, alt, lazy = true }: Props) {
  // Ghi lại URL đã lỗi: đổi sang sản phẩm khác (src mới) thì thử tải lại, không cần effect.
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  if (!src || failedSrc === src) {
    return (
      <span className="product-placeholder" aria-hidden="true">
        <BottleIcon />
        <span>Chưa có ảnh</span>
      </span>
    )
  }
  return (
    <img src={src} alt={alt} loading={lazy ? 'lazy' : 'eager'} decoding="async" onError={() => setFailedSrc(src)} />
  )
}
