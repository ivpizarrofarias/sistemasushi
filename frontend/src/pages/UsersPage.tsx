import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import Modal from '../components/Modal'
import type { Role, User } from '../types'
import { roleLabel } from '../utils'
import { alertError, alertSuccess } from '../utils/alert'

interface UserFormProps {
  user: User | null
  onClose: () => void
  onSaved: () => void
}

function UserForm({ user, onClose, onSaved }: UserFormProps) {
  const [username, setUsername] = useState(user?.username ?? '')
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>(user?.role ?? 'CASHIER')
  const [active, setActive] = useState(user?.active ?? true)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!user && password.trim().length < 4) {
      alertError('La contraseña debe tener al menos 4 caracteres')
      return
    }
    setSaving(true)
    try {
      const payload = {
        username: username.trim(),
        fullName: fullName.trim(),
        password: password.trim() || undefined,
        role,
        active,
      }
      if (user) {
        await api.updateUser(user.id, payload)
      } else {
        await api.createUser(payload)
      }
      onSaved()
      onClose()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo guardar el usuario')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={user ? 'Editar usuario' : 'Nuevo usuario'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>

        <div className="form-row">
          <label className="field">
            <span>Usuario</span>
            <input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
              maxLength={50}
              autoComplete="off"
            />
          </label>
          <label className="field">
            <span>Rol</span>
            <select value={role} onChange={(event) => setRole(event.target.value as Role)}>
              <option value="CASHIER">Cajero</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </label>
        </div>

        <label className="field">
          <span>Nombre completo</span>
          <input value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={120} />
        </label>

        <label className="field">
          <span>{user ? 'Nueva contraseña (opcional)' : 'Contraseña'}</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder={user ? 'Dejar en blanco para mantener la actual' : '••••••••'}
            autoComplete="new-password"
          />
        </label>

        <label className="switch">
          <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
          <span>Usuario activo</span>
        </label>

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<User | 'new' | null>(null)

  const load = useCallback(async () => {
    try {
      setUsers(await api.users())
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudieron cargar los usuarios')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return (
      <div className="full-screen">
        <div className="spinner" />
      </div>
    )
  }

  return (
    <div className="stack-lg">
      <div className="page-header">
        <div>
          <h1>Usuarios y roles</h1>
          <p>Administradores gestionan todo; los cajeros solo venden y operan la caja.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
          + Nuevo usuario
        </button>
      </div>

      <div className="card table-card">
        <table className="table">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Usuario</th>
              <th>Rol</th>
              <th>Estado</th>
              <th className="text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id} className={user.active ? '' : 'row-muted'}>
                <td>
                  <strong>{user.fullName}</strong>
                </td>
                <td>{user.username}</td>
                <td>
                  <span className={`badge ${user.role === 'ADMIN' ? 'badge-accent' : 'badge-neutral'}`}>
                    {roleLabel(user.role)}
                  </span>
                </td>
                <td>
                  <span className={`badge ${user.active ? 'badge-success' : 'badge-danger'}`}>
                    {user.active ? 'Activo' : 'Inactivo'}
                  </span>
                </td>
                <td className="text-right">
                  <button type="button" className="btn btn-sm btn-ghost" onClick={() => setEditing(user)}>
                    Editar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {users.length === 0 && <div className="empty-state">No hay usuarios registrados.</div>}
      </div>

      {editing !== null && (
        <UserForm
          user={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            alertSuccess(editing === 'new' ? 'Usuario creado' : 'Usuario actualizado')
            void load()
          }}
        />
      )}
    </div>
  )
}
