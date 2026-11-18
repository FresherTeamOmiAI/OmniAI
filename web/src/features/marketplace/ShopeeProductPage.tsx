import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { channelOffer, dong, marketStats } from './offers'
import { useMarketProduct } from './useMarketProduct'
import './shopee.css'

export function ShopeeProductPage() {
  const { sku = '' } = useParams()
  const { product, missing } = useMarketProduct(sku)
  const [qty, setQty] = useState(1)
  const [cartNote, setCartNote] = useState('')
  const [checkingOut, setCheckingOut] = useState(false)
  const [placed, setPlaced] = useState(false)

  if (missing) {
    return (
      <main className="sp">
        <p className="sp-status">Shopee giả lập không có sản phẩm {sku}.</p>
        <p className="sp-status"><Link to="/">Về cửa hàng Lumi</Link></p>
      </main>
    )
  }
  if (!product) {
    return <main className="sp"><p className="sp-status">Đang mở trang Shopee…</p></main>
  }

  const offer = channelOffer(product, 'SHOPEE')
  const stats = marketStats(product.sku)
  const pay = offer.salePrice * qty

  return (
    <main className="sp">
      <header className="sp-top">
        <div className="sp-top-inner">
          <Link to="/" className="sp-logo" aria-label="Shopee giả lập, về cửa hàng">
            Shopee
            <small>Giả lập OmniAI</small>
          </Link>
          <form className="sp-search" onSubmit={(event) => event.preventDefault()}>
            <input aria-label="Tìm kiếm trên Shopee" placeholder="Tìm trong Lumi Official" readOnly />
            <button type="submit">Tìm</button>
          </form>
          <span className="sp-cart">Giỏ hàng</span>
        </div>
      </header>

      <p className="sp-crumb">
        <Link to="/">Shopee</Link> &gt; Sắc đẹp &gt; {product.brand} &gt; {product.name}
      </p>

      <section className="sp-card">
        <div>
          {product.imageUrl
            ? <img className="sp-photo" src={product.imageUrl} alt="" />
            : <div className="sp-photo sp-photo-empty">Chưa có ảnh</div>}
          <div className="sp-thumbs" aria-hidden="true">
            {product.imageUrl && <span><img src={product.imageUrl} alt="" /></span>}
          </div>
        </div>
        <div>
          <h1 className="sp-title">{product.name}</h1>
          <p className="sp-meta">
            <span><b>{stats.rating}</b> <span className="sp-stars">★★★★★</span></span>
            <span><b>{stats.reviews.toLocaleString('vi-VN')}</b> Đánh giá</span>
            <span><b>{stats.sold.toLocaleString('vi-VN')}</b> Đã bán</span>
          </p>
          <div className="sp-pricebox">
            {offer.promo && <span className="sp-list">{dong(offer.listPrice)}</span>}
            <span className="sp-sale">{dong(offer.salePrice)}</span>
            {offer.promo && <span className="sp-badge">-{offer.promo.percent}%</span>}
          </div>
          <div className="sp-row">
            <span className="sp-label">Mã giảm giá của shop</span>
            {offer.promo
              ? <span className="sp-voucher">{offer.promo.label}</span>
              : <span>Shop chưa chạy mã giảm giá cho sản phẩm này</span>}
          </div>
          <div className="sp-row">
            <span className="sp-label">Vận chuyển</span>
            <span className="sp-ship">Miễn phí vận chuyển · Nhận hàng 2–4 ngày</span>
          </div>
          <div className="sp-row">
            <span className="sp-label">Số lượng</span>
            <div className="sp-qty">
              <button type="button" aria-label="Giảm số lượng" onClick={() => setQty((value) => Math.max(1, value - 1))}>−</button>
              <span>{qty}</span>
              <button type="button" aria-label="Tăng số lượng" onClick={() => setQty((value) => Math.min(product.stock || 1, value + 1))}>+</button>
            </div>
          </div>
          <p className="sp-label">{product.stock > 0 ? `${product.stock} sản phẩm có sẵn` : 'Hết hàng'}</p>
          {cartNote && <p>{cartNote}</p>}
          <div className="sp-actions">
            <button
              type="button"
              className="sp-cart-btn"
              disabled={product.stock <= 0}
              onClick={() => setCartNote(`Đã thêm ${qty} vào giỏ giả lập.`)}
            >
              Thêm vào giỏ hàng
            </button>
            <button type="button" className="sp-buy" disabled={product.stock <= 0} onClick={() => { setPlaced(false); setCheckingOut(true) }}>
              Mua ngay
            </button>
          </div>
        </div>
      </section>

      <section className="sp-shop">
        <span className="sp-avatar">L</span>
        <div>
          <h2>Lumi Official</h2>
          <p>Mall giả lập · Online 5 phút trước · {stats.sold.toLocaleString('vi-VN')} sản phẩm đã bán</p>
        </div>
      </section>

      <section className="sp-desc">
        <h2>Mô tả sản phẩm</h2>
        <p>
          {product.description || `${product.brand} — ${product.name}.`}
          {product.sizeMl ? `\nDung tích: ${product.sizeMl} ml.` : ''}
          {product.ingredientsInci ? `\nThành phần: ${product.ingredientsInci}` : ''}
          {'\nGiá niêm yết trên Shopee lấy từ catalog OmniAI. Mã giảm giá, lượt bán và đánh giá là số của simulator, không phải sàn thật.'}
        </p>
      </section>

      {checkingOut && (
        <div className="sp-mask" role="presentation" onClick={() => setCheckingOut(false)}>
          <div className="sp-sheet" role="dialog" aria-labelledby="sp-pay-title" onClick={(event) => event.stopPropagation()}>
            <h2 id="sp-pay-title">{placed ? 'Đặt hàng thành công' : 'Thanh toán'}</h2>
            {placed ? (
              <p>Đơn giả lập trên Shopee. Không trừ tiền, không giao hàng, không trừ tồn kho.</p>
            ) : (
              <>
                <p>Địa chỉ: Quận 1, TP. Hồ Chí Minh (giả lập)</p>
                <p>{product.name}</p>
                <p>Số lượng: {qty}</p>
                {offer.promo && <p>Đã áp {offer.promo.label}</p>}
                <p>Tổng thanh toán: <b>{dong(pay)}</b></p>
              </>
            )}
            <button type="button" className="sp-buy" onClick={() => (placed ? setCheckingOut(false) : setPlaced(true))}>
              {placed ? 'Đóng' : 'Đặt hàng'}
            </button>
          </div>
        </div>
      )}
    </main>
  )
}
