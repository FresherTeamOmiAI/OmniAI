import { NavLink, Outlet, useLocation } from 'react-router'
import { ChatWidget } from '../features/chat'
import { DemoBanner } from '../shared/ui/DemoBanner'

const tabClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'tab tab-active' : 'tab')

export function ShopLayout() {
  const isAdmin = useLocation().pathname.startsWith('/admin')
  return (
    <>
      <DemoBanner />
      <nav className="topbar">
        <strong className="logo">Lumi Demo Store</strong>
        <NavLink to="/" end className={tabClass}>Cửa hàng</NavLink>
        <NavLink to="/admin/products" className={tabClass}>Admin · Sản phẩm</NavLink>
        <NavLink to="/admin/policy" className={tabClass}>Admin · Thử luật kênh</NavLink>
      </nav>
      <Outlet />
      {!isAdmin && <ChatWidget />}
    </>
  )
}
