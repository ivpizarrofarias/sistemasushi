import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import MoneyInput from '../components/MoneyInput'
import type { CashSession } from '../types'
import { dateTime, money, parseAmount } from '../utils'
import { alertError, alertSuccess } from '../utils/alert'

export default function CashPage() {
  const [current, setCurrent] = useState<CashSession | null>(null)
  const [history, setHistory] = useState<CashSession[]>([])
  const [loading, setLoading] = useState(true)
  const [openingAmount, setOpeningAmount] = useState('')
  const [closingAmount, setClosingAmount] = useState('')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const [currentSession, sessions] = await Promise.all([api.currentCash(), api.cashSessions()])
      setCurrent(currentSession)
      setHistory(sessions)
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo cargar la información de caja')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleOpen(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    try {
      await api.openCash(parseAmount(openingAmount))
      alertSuccess('Caja abierta correctamente')
      setOpeningAmount('')
      await load()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo abrir la caja')
    } finally {
      setBusy(false)
    }
  }

  async function handleClose(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    try {
      await api.closeCash(parseAmount(closingAmount), notes.trim())
      alertSuccess('Caja cerrada correctamente')
      setClosingAmount('')
      setNotes('')
      await load()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo cerrar la caja')
    } finally {
      setBusy(false)
    }
  }

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
          <h1>Control de caja</h1>
          <p>Apertura, ventas del turno, cierre y arqueo.</p>
        </div>
        <span className={`badge ${current ? 'badge-success' : 'badge-neutral'}`}>
          {current ? 'Caja abierta' : 'Caja cerrada'}
        </span>
      </div>

      {!current ? (
        <div className="card narrow-card">
          <h2>Abrir caja</h2>
          <p className="muted">Ingresa el monto inicial con el que comienza el turno.</p>
          <form className="stack" onSubmit={handleOpen}>
            <label className="field">
              <span>Monto inicial</span>
              <MoneyInput
                value={openingAmount}
                onChange={setOpeningAmount}
                placeholder="0"
                required
                autoFocus
              />
            </label>
            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={busy}>
              {busy ? 'Abriendo…' : 'Abrir caja'}
            </button>
          </form>
        </div>
      ) : (
        <div className="cash-layout">
          <div className="stack">
            <div className="kpi-grid">
              <div className="kpi card">
                <span>Monto inicial</span>
                <strong>{money(current.openingAmount)}</strong>
              </div>
              <div className="kpi card">
                <span>Ventas en efectivo</span>
                <strong>{money(current.cashSales)}</strong>
              </div>
              <div className="kpi card">
                <span>Efectivo esperado</span>
                <strong>{money(current.expectedAmount)}</strong>
              </div>
              <div className="kpi card">
                <span>Ventas del turno</span>
                <strong>{money(current.totalSales)}</strong>
                <small>{current.salesCount} venta(s)</small>
              </div>
            </div>

            <div className="card">
              <h2>Sesión actual</h2>
              <dl className="detail-list">
                <div>
                  <dt>Abierta por</dt>
                  <dd>{current.openedByName}</dd>
                </div>
                <div>
                  <dt>Apertura</dt>
                  <dd>{dateTime(current.openedAt)}</dd>
                </div>
                <div>
                  <dt>Ventas totales</dt>
                  <dd>{money(current.totalSales)}</dd>
                </div>
                <div>
                  <dt>Efectivo en caja</dt>
                  <dd>{money(current.cashSales)}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div className="card">
            <h2>Cerrar caja</h2>
            <p className="muted">
              Cuenta el efectivo físico e ingresa el monto para comparar con el esperado.
            </p>
            <form className="stack" onSubmit={handleClose}>
              <div className="alert alert-info">
                Efectivo esperado: <strong>{money(current.expectedAmount)}</strong>
              </div>
              <label className="field">
                <span>Efectivo contado</span>
                <MoneyInput
                  value={closingAmount}
                  onChange={setClosingAmount}
                  placeholder="0"
                  required
                />
              </label>
              <label className="field">
                <span>Observaciones</span>
                <textarea
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  rows={2}
                  maxLength={300}
                  placeholder="Opcional"
                />
              </label>
              {closingAmount !== '' && (
                <div
                  className={`alert ${
                    parseAmount(closingAmount) - current.expectedAmount >= 0 ? 'alert-success' : 'alert-error'
                  }`}
                >
                  Diferencia:{' '}
                  <strong>{money(parseAmount(closingAmount) - current.expectedAmount)}</strong>
                </div>
              )}
              <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={busy}>
                {busy ? 'Cerrando…' : 'Cerrar caja'}
              </button>
            </form>
          </div>
        </div>
      )}

      <div className="card table-card">
        <div className="card-header">
          <h2>Historial de cajas</h2>
          <span className="badge badge-neutral">{history.length} sesión(es)</span>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Apertura</th>
              <th>Cierre</th>
              <th>Responsable</th>
              <th className="text-right">Inicial</th>
              <th className="text-right">Esperado</th>
              <th className="text-right">Contado</th>
              <th className="text-right">Diferencia</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {history.map((session) => (
              <tr key={session.id}>
                <td>{dateTime(session.openedAt)}</td>
                <td>{session.closedAt ? dateTime(session.closedAt) : '—'}</td>
                <td>
                  <strong>{session.openedByName}</strong>
                  {session.closedByName && <span className="table-sub">Cerró: {session.closedByName}</span>}
                </td>
                <td className="text-right">{money(session.openingAmount)}</td>
                <td className="text-right">{money(session.expectedAmount)}</td>
                <td className="text-right">{session.closingAmount != null ? money(session.closingAmount) : '—'}</td>
                <td className="text-right">
                  {session.difference != null ? (
                    <span className={`badge ${session.difference === 0 ? 'badge-success' : 'badge-danger'}`}>
                      {money(session.difference)}
                    </span>
                  ) : (
                    '—'
                  )}
                </td>
                <td>
                  <span className={`badge ${session.status === 'OPEN' ? 'badge-success' : 'badge-neutral'}`}>
                    {session.status === 'OPEN' ? 'Abierta' : 'Cerrada'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {history.length === 0 && <div className="empty-state">Todavía no hay cajas registradas.</div>}
      </div>
    </div>
  )
}
