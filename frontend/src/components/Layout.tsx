import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { confirmAction } from '../utils/alert'
import { roleLabel, todayDisplay } from '../utils'

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
  const location = useLocation()
  const [menuAbierto, setMenuAbierto] = useState(false)
  const [ahora, setAhora] = useState(() => new Date())
  const [sidebarColapsado, setSidebarColapsado] = useState(() => {
    if (typeof window === 'undefined') return false
    return window.localStorage.getItem('sidebar-colapsado') === '1'
  })

  useEffect(() => {
    const intervalo = window.setInterval(() => setAhora(new Date()), 1000)
    return () => window.clearInterval(intervalo)
  }, [])

  useEffect(() => {
    setMenuAbierto(false)
  }, [location.pathname])

  useEffect(() => {
    window.localStorage.setItem('sidebar-colapsado', sidebarColapsado ? '1' : '0')
  }, [sidebarColapsado])

  useEffect(() => {
    if (!menuAbierto) return

    function manejarEscape(evento: KeyboardEvent) {
      if (evento.key === 'Escape') setMenuAbierto(false)
    }

    document.addEventListener('keydown', manejarEscape)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', manejarEscape)
      document.body.style.overflow = ''
    }
  }, [menuAbierto])

  async function handleLogout() {
    if (!(await confirmAction('¿Realmente deseas cerrar sesión?', 'Cerrar sesión'))) return
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <button
        type="button"
        className={`sidebar-overlay${menuAbierto ? ' visible' : ''}`}
        aria-label="Cerrar menú"
        onClick={() => setMenuAbierto(false)}
      />

      <aside
        id="sidebar"
        className={`sidebar${menuAbierto ? ' open' : ''}${sidebarColapsado ? ' collapsed' : ''}`}
        aria-label="Navegación principal"
      >
        <div className="sidebar-top">
          <div className="brand">
            <span className="brand-mark">🍣</span>
            <div className="brand-text">
              <strong>Sushi Local</strong>
              <span>Sistema de caja</span>
            </div>
          </div>
          <button
            type="button"
            className="sidebar-close"
            aria-label="Cerrar menú"
            onClick={() => setMenuAbierto(false)}
          >
            ✕
          </button>
        </div>

        <nav className="nav">
          {navItems
            .filter((item) => !item.adminOnly || user?.role === 'ADMIN')
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                title={sidebarColapsado ? item.label : undefined}
                className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
              >
                <span className="nav-icon">{item.icon}</span>
                <span className="nav-label">{item.label}</span>
              </NavLink>
            ))}
        </nav>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="menu-toggle"
              aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
              aria-expanded={menuAbierto}
              aria-controls="sidebar"
              onClick={() => setMenuAbierto((abierto) => !abierto)}
            >
              <span />
              <span />
              <span />
            </button>
            <button
              type="button"
              className="sidebar-collapse-toggle"
              aria-label={sidebarColapsado ? 'Expandir menú lateral' : 'Contraer menú lateral'}
              aria-pressed={sidebarColapsado}
              title={sidebarColapsado ? 'Mostrar nombres' : 'Mostrar solo íconos'}
              onClick={() => setSidebarColapsado((valor) => !valor)}
            >
              <span />
              <span />
              <span />
            </button>
            <div>
              <span className="topbar-label">Fecha de operación</span>
              <strong>{todayDisplay(ahora)}</strong>
            </div>
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
