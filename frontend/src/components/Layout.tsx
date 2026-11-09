import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { roleLabel, todayISO } from '../utils'

const navItems = [
  { to: '/', label: 'Venta', icon: '🍣', adminOnly: false },
  { to: '/caja', label: 'Caja', icon: '🧾', adminOnly: false },
  { to: '/productos', label: 'Productos', icon: '📦', adminOnly: false },
  { to: '/reportes', label: 'Reportes', icon: '📊', adminOnly: false },
  { to: '/usuarios', label: 'Usuarios', icon: '👥', adminOnly: true },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">🍣</span>
          <div>
            <strong>Sushi Local</strong>
            <span>Sistema de caja</span>
          </div>
        </div>

        <nav className="nav">
          {navItems
            .filter((item) => !item.adminOnly || user?.role === 'ADMIN')
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
              >
                <span className="nav-icon">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
        </nav>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <div>
            <span className="topbar-label">Fecha de operación</span>
            <strong>{todayISO()}</strong>
          </div>
          <div className="topbar-user">
            <span className="avatar">{user?.fullName?.charAt(0).toUpperCase() ?? '?'}</span>
            <div className="topbar-user-info">
              <strong>{user?.fullName}</strong>
              <span>{user ? roleLabel(user.role) : ''}</span>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout}>
              Cerrar sesión
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
