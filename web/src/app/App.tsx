import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { LoginPage } from '../features/account'
import { ConversationInboxPage } from '../features/conversation-inbox'
import { ShopeeProductPage, TiktokProductPage } from '../features/marketplace'
import { PolicyPlaygroundPage } from '../features/policy-playground'
import { ProductAdminPage } from '../features/product-admin'
import { ProductDetailPage, StorePage } from '../features/store'
import { ShopLayout } from './ShopLayout'

function NotFoundPage() {
  return (
    <main id="main" tabIndex={-1} className="page page-empty">
      <h1>Không tìm thấy trang.</h1>
      <p className="muted">Đường dẫn này không có trong cửa hàng thử nghiệm.</p>
      <Link to="/" className="btn btn-primary">Về cửa hàng</Link>
    </main>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="mo-phong/shopee/:sku" element={<ShopeeProductPage />} />
        <Route path="mo-phong/tiktok/:sku" element={<TiktokProductPage />} />
        <Route element={<ShopLayout />}>
          <Route index element={<StorePage />} />
          <Route path="products/:id" element={<ProductDetailPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="admin/products" element={<ProductAdminPage />} />
          <Route path="admin/conversations" element={<ConversationInboxPage />} />
          <Route path="admin/policy" element={<PolicyPlaygroundPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
