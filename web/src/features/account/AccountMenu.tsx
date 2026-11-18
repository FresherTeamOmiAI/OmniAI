import { useEffect } from 'react'
import { NavLink, useLocation } from 'react-router'
import { logout, refreshSession, useCustomerSession } from './session'

const tabClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'tab tab-active' : 'tab')

/** Góc phải topbar: link Đăng nhập, hoặc email + Đăng xuất khi đã đăng nhập (US-13). */
export function AccountMenu() {
  const session = useCustomerSession()
  const location = useLocation()

  useEffect(() => {
    void refreshSession()
  }, [])

  if (session) {
    return (
      <span className="account-menu">
        <span className="account-email" title={session.customer.email}>{session.customer.email}</span>
        <button type="button" className="tab" onClick={logout}>Đăng xuất</button>
      </span>
    )
  }
  const next = location.pathname === '/login' ? '' : `?next=${encodeURIComponent(location.pathname + location.search)}`
  return <NavLink to={`/login${next}`} className={tabClass}>Đăng nhập</NavLink>
}
