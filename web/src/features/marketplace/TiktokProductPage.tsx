import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { channelOffer, dong, marketStats } from './offers'
import { useMarketProduct } from './useMarketProduct'
import './tiktok.css'

export function TiktokProductPage() {
  const { sku = '' } = useParams()
  const { product, missing } = useMarketProduct(sku)
  const [qty, setQty] = useState(1)
  const [cartNote, setCartNote] = useState('')
  const [checkingOut, setCheckingOut] = useState(false)
  const [placed, setPlaced] = useState(false)

  if (missing) {
    return (
      <main className="tt">
        <p className="tt-status">TikTok Shop giả lập không có sản phẩm {sku}.</p>
        <p className="tt-status"><Link to="/">Về cửa hàng Lumi</Link></p>
      </main>
    )
  }
  if (!product) {
    return <main className="tt"><p className="tt-status">Đang mở TikTok Shop…</p></main>
  }

  const offer = channelOffer(product, 'TIKTOK')
  const stats = marketStats(product.sku)
  const pay = offer.salePrice * qty

  return (
    <main className="tt">
      <header className="tt-top">
        <div className="tt-top-inner">
          <Link to="/" className="tt-logo" aria-label="TikTok Shop giả lập, về cửa hàng">
            <span className="tt-mark" aria-hidden="true" />
            TikTok Shop
          </Link>
          <div className="tt-search">Tìm trên TikTok Shop</div>
          <span className="tt-bag">Giỏ · giả lập</span>
        </div>
      </header>

      <article className="tt-wrap">
        {product.imageUrl
          ? <img className="tt-photo" src={product.imageUrl} alt="" />
          : <div className="tt-photo tt-photo-empty">Chưa có ảnh</div>}
        <div>
          {offer.promo && (
            <div className="tt-flash">
              <span>Flash Sale</span>
              <span>Kết thúc sau 02:18:44</span>
            </div>
          )}
          <h1 className="tt-title">
            {product.name.toLocaleLowerCase('vi').startsWith(product.brand.toLocaleLowerCase('vi'))
              ? product.name
              : `${product.brand} ${product.name}`}
          </h1>
          <p className="tt-meta">
            <span><b>{stats.rating}</b> ★</span>
            <span>{stats.reviews.toLocaleString('vi-VN')} đánh giá</span>
            <span>{stats.sold.toLocaleString('vi-VN')} đã bán</span>
          </p>
          <p className="tt-price">
            <span className="tt-sale">{dong(offer.salePrice)}</span>
            {offer.promo && <span className="tt-list">{dong(offer.listPrice)}</span>}
            {offer.promo && <span className="tt-off">-{offer.promo.percent}%</span>}
          </p>
          <div className="tt-row">
            <span className="tt-label">Khuyến mãi</span>
            <span>{offer.promo ? offer.promo.label : 'Không có flash sale cho sản phẩm này'}</span>
          </div>
          <div className="tt-row">
            <span className="tt-label">Vận chuyển</span>
            <span>Freeship · Giao 1–3 ngày</span>
          </div>
          <div className="tt-row">
            <span className="tt-label">Số lượng</span>
            <div className="tt-qty">
              <button type="button" aria-label="Giảm số lượng" onClick={() => setQty((value) => Math.max(1, value - 1))}>−</button>
              <span>{qty}</span>
              <button type="button" aria-label="Tăng số lượng" onClick={() => setQty((value) => Math.min(product.stock || 1, value + 1))}>+</button>
            </div>
          </div>
          <p className="tt-label">{product.stock > 0 ? `Còn ${product.stock}` : 'Hết hàng'}</p>
          {cartNote && <p>{cartNote}</p>}
          <div className="tt-actions">
            <button
              type="button"
              className="tt-add"
              disabled={product.stock <= 0}
              onClick={() => setCartNote(`Đã thêm ${qty} vào giỏ TikTok giả lập.`)}
            >
              Thêm vào giỏ
            </button>
            <button type="button" className="tt-buy" disabled={product.stock <= 0} onClick={() => { setPlaced(false); setCheckingOut(true) }}>
              Mua ngay
            </button>
          </div>

          <div className="tt-seller">
            <span className="tt-avatar">L</span>
            <div>
              <h2>Lumi Official</h2>
              <p>Shop giả lập · {stats.sold.toLocaleString('vi-VN')} đã bán</p>
            </div>
            <button type="button" className="tt-follow">Follow</button>
          </div>
          <p className="tt-desc">
            {product.description || `${product.brand} — ${product.name}.`}
            {product.sizeMl ? ` Dung tích ${product.sizeMl} ml.` : ''}
            {' Giá niêm yết lấy từ catalog. Flash sale, đồng hồ đếm ngược, lượt bán và sao là số của simulator.'}
          </p>
        </div>
      </article>

      {checkingOut && (
        <div className="tt-mask" role="presentation" onClick={() => setCheckingOut(false)}>
          <div className="tt-sheet" role="dialog" aria-labelledby="tt-pay-title" onClick={(event) => event.stopPropagation()}>
            <h2 id="tt-pay-title">{placed ? 'Đã đặt hàng' : 'Mua với TikTok Shop'}</h2>
            {placed ? (
              <p>Đơn giả lập. Không trừ tiền, không giao hàng, không trừ tồn kho.</p>
            ) : (
              <>
                <p>{product.name}</p>
                <p>Số lượng {qty}{offer.promo ? ` · ${offer.promo.label}` : ''}</p>
                <p>Tổng: <b>{dong(pay)}</b></p>
              </>
            )}
            <button type="button" className="tt-buy" onClick={() => (placed ? setCheckingOut(false) : setPlaced(true))}>
              {placed ? 'Xong' : 'Đặt hàng'}
            </button>
          </div>
        </div>
      )}
    </main>
  )
}
