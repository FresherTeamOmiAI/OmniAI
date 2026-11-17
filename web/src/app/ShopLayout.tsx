import { NavLink, Outlet, useLocation } from 'react-router'
import { AccountMenu, useCustomerSession } from '../features/account'
import { ChatWidget } from '../features/chat'
import { DemoBanner } from '../shared/ui/DemoBanner'

const tabClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'tab tab-active' : 'tab')

export function ShopLayout() {
  const isAdmin = useLocation().pathname.startsWith('/admin')
  const session = useCustomerSession()
  return (
    <>
      <DemoBanner />
      <nav className="topbar">
        <strong className="logo">Lumi Demo Store</strong>
        <NavLink to="/" end className={tabClass}>Cửa hàng</NavLink>
        <NavLink to="/admin/products" className={tabClass}>Admin · Sản phẩm</NavLink>
        <NavLink to="/admin/policy" className={tabClass}>Admin · Thử luật kênh</NavLink>
        {!isAdmin && <AccountMenu />}
      </nav>
      <Outlet />
      {/* Đổi tài khoản thì dựng lại widget: xoá tin của người trước, nối lại WebSocket với token mới. */}
      {!isAdmin && <ChatWidget key={session?.customer.id ?? 'guest'} />}
    </>
  )
}
