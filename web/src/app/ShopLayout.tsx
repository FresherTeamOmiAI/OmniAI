import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { AccountMenu, useCustomerSession } from '../features/account'
import { ChatWidget } from '../features/chat'
import { DemoBanner } from '../shared/ui/DemoBanner'

const tabClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'tab tab-active' : 'tab')

// Khung chung: link bỏ qua, banner thử nghiệm, header (tên shop + nav khách + nav admin tách riêng), chat widget.
// Mỗi trang tự render <main id="main" tabIndex={-1}> để link "Bỏ qua" tới được.
export function ShopLayout() {
  const isAdmin = useLocation().pathname.startsWith('/admin')
  const session = useCustomerSession()
  return (
    <>
      <a className="skip-link" href="#main">Bỏ qua, tới nội dung chính</a>
      <DemoBanner />
      <header className="site-header">
        <div className="site-header-inner">
          <Link to="/" className="brand" aria-label="Lumi, về trang cửa hàng">
            <span className="brand-name">Lumi</span>
            <span className="brand-tag">cửa hàng thử nghiệm của OmniAI</span>
          </Link>
          <nav className="site-nav" aria-label="Cửa hàng">
            <NavLink to="/" end className={tabClass}>Cửa hàng</NavLink>
            {!isAdmin && <AccountMenu />}
          </nav>
          <nav className="admin-nav" aria-label="Quản trị">
            <NavLink to="/admin/products" className={tabClass}>Admin · Sản phẩm</NavLink>
            <NavLink to="/admin/policy" className={tabClass}>Admin · Thử luật kênh</NavLink>
          </nav>
        </div>
      </header>
      <Outlet />
      {/* Đổi tài khoản thì dựng lại widget: xoá tin của người trước, nối lại WebSocket với token mới. */}
      {!isAdmin && <ChatWidget key={session?.customer.id ?? 'guest'} />}
    </>
  )
}
