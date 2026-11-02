import { BrowserRouter, Route, Routes } from 'react-router'
import { PolicyPlaygroundPage } from '../features/policy-playground'
import { StorePage } from '../features/store'
import { ShopLayout } from './ShopLayout'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<ShopLayout />}>
          <Route index element={<StorePage />} />
          <Route path="admin/policy" element={<PolicyPlaygroundPage />} />
          <Route path="*" element={<p className="page-empty">Không tìm thấy trang.</p>} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
