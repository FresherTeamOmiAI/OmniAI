import { useState } from 'react'
import { ChatWidget } from './components/ChatWidget'
import { DemoBanner } from './components/DemoBanner'
import { AdminPage } from './pages/AdminPage'
import { StorePage } from './pages/StorePage'

type View = 'store' | 'admin'

export default function App() {
  const [view, setView] = useState<View>(location.hash === '#admin' ? 'admin' : 'store')

  const go = (next: View) => {
    setView(next)
    location.hash = next === 'admin' ? 'admin' : ''
  }

  return (
    <>
      <DemoBanner />
      <nav className="topbar">
        <strong className="logo">Lumi Demo Store</strong>
        <button type="button" className={view === 'store' ? 'tab tab-active' : 'tab'} onClick={() => go('store')}>Cửa hàng</button>
        <button type="button" className={view === 'admin' ? 'tab tab-active' : 'tab'} onClick={() => go('admin')}>Admin</button>
      </nav>
      {view === 'store' ? <StorePage /> : <AdminPage />}
      {view === 'store' && <ChatWidget />}
    </>
  )
}
