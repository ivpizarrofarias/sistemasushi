import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api } from '../api'
import type { Sale, SalesReport } from '../types'
import { money, paymentLabel, todayISO } from '../utils'
import { alertError, alertSuccess, confirmAction } from '../utils/alert'

export default function ReportsPage() {
  const [from, setFrom] = useState(todayISO())
  const [to, setTo] = useState(todayISO())
  const [report, setReport] = useState<SalesReport | null>(null)
  const [sales, setSales] = useState<Sale[]>([])
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)

  const load = useCallback(async (fromDate: string, toDate: string): Promise<boolean> => {
    setLoading(true)
    try {
      const [result, saleList] = await Promise.all([
        api.report(fromDate, toDate),
        api.sales(toDate),
      ])
      setReport(result)
      setSales(saleList)
      return true
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo generar el reporte')
      return false
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(todayISO(), todayISO())
  }, [load])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Al hacer click en "Generar reporte" se carga el resumen en pantalla
    // y se crea/descarga el Excel automáticamente.
    const ok = await load(from, to)
    if (!ok) return
    setExporting(true)
    try {
      await api.downloadReportExcel(from, to)
      alertSuccess(from === to ? `Reporte generado y Excel de ventas del día ${from} descargado` : `Reporte generado y Excel del período ${from} al ${to} descargado`)
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo generar el Excel')
    } finally {
      setExporting(false)
    }
  }

  async function handleVoid(sale: Sale) {
    if (!(await confirmAction(`¿Anular la venta ${sale.number} por ${money(sale.total)}? El stock será restaurado.`, 'Anular'))) return
    try {
      await api.voidSale(sale.id)
      alertSuccess(`Venta ${sale.number} anulada`)
      await load(from, to)
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo anular la venta')
    }
  }

  const maxQuantity = report?.topProducts.reduce((max, product) => Math.max(max, product.quantity), 0) ?? 0

  const dailyCompleted = useMemo(
    () => sales.filter((sale) => sale.status === 'COMPLETED'),
    [sales],
  )

  const dailyProducts = useMemo(() => {
    const byName = new Map<string, { name: string; quantity: number; revenue: number }>()
    for (const sale of dailyCompleted) {
      for (const item of sale.items) {
        const current = byName.get(item.productName) ?? { name: item.productName, quantity: 0, revenue: 0 }
        current.quantity += item.quantity
        current.revenue += item.lineTotal
        byName.set(item.productName, current)
      }
    }
    return [...byName.values()].sort((a, b) => b.quantity - a.quantity || a.name.localeCompare(b.name, 'es-CL'))
  }, [dailyCompleted])

  const dailyUnits = useMemo(
    () => dailyProducts.reduce((sum, product) => sum + product.quantity, 0),
    [dailyProducts],
  )

  const dailyProductTotal = useMemo(
    () => dailyProducts.reduce((sum, product) => sum + product.revenue, 0),
    [dailyProducts],
  )

  const dailySalesTotal = useMemo(
    () => dailyCompleted.reduce((sum, sale) => sum + sale.total, 0),
    [dailyCompleted],
  )

  return (
    <div className="stack-lg">
      <div className="page-header">
        <div>
          <h1>Reportes de venta</h1>
          <p>Resumen del período, medios de pago y productos más vendidos. Al generar el reporte se descarga el Excel.</p>
        </div>
      </div>

      <form className="card filter-bar" onSubmit={handleSubmit}>
        <label className="field">
          <span>Desde</span>
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
        </label>
        <label className="field">
          <span>Hasta</span>
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
        </label>
        <button type="submit" className="btn btn-primary" disabled={loading || exporting}>
          {loading || exporting ? 'Generando…' : 'Generar reporte (Excel)'}
        </button>
      </form>

      {report && (
        <>
          <div className="kpi-grid">
            <div className="kpi card">
              <span>Venta total</span>
              <strong>{money(report.total)}</strong>
              <small>{report.count} venta(s)</small>
            </div>
            <div className="kpi card">
              <span>Unidades vendidas</span>
              <strong>{report.totalUnits}</strong>
              <small>{report.topProducts.length} producto(s) distinto(s) (top)</small>
            </div>
            <div className="kpi card">
              <span>Efectivo</span>
              <strong>{money(report.cashTotal)}</strong>
            </div>
            <div className="kpi card">
              <span>Tarjetas</span>
              <strong>{money(report.cardTotal)}</strong>
              <small>Débito + crédito</small>
            </div>
            <div className="kpi card">
              <span>Transferencias</span>
              <strong>{money(report.transferTotal)}</strong>
            </div>
          </div>

          <div className="report-grid">
            <div className="card table-card">
              <div className="card-header">
                <h2>Medios de pago</h2>
              </div>
              <table className="table">
                <thead>
                  <tr>
                    <th>Método</th>
                    <th className="text-right">Ventas</th>
                    <th className="text-right">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {report.payments.map((payment) => (
                    <tr key={payment.method}>
                      <td>{paymentLabel(payment.method)}</td>
                      <td className="text-right">{payment.count}</td>
                      <td className="text-right">{money(payment.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="card">
              <div className="card-header">
                <h2>Productos más vendidos</h2>
              </div>
              {report.topProducts.length === 0 ? (
                <div className="empty-state">Sin ventas en el período.</div>
              ) : (
                <ul className="rank-list">
                  {report.topProducts.map((product) => (
                    <li key={product.name}>
                      <div className="rank-top">
                        <strong>{product.name}</strong>
                        <span>
                          {product.quantity} u. · {money(product.revenue)}
                        </span>
                      </div>
                      <div className="rank-bar">
                        <span style={{ width: `${maxQuantity ? (product.quantity / maxQuantity) * 100 : 0}%` }} />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}

      <div className="card table-card">
        <div className="card-header">
          <h2>Ventas del día {to}: productos y cantidades</h2>
          <span className="badge badge-neutral">
            {dailyCompleted.length} venta(s) · {dailyUnits} unidad(es) · {money(dailySalesTotal)}
          </span>
        </div>
        {dailyProducts.length === 0 ? (
          <div className="empty-state">No hay ventas completadas en esa fecha.</div>
        ) : (
          <>
            <table className="table">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="text-right">Cantidad</th>
                  <th className="text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {dailyProducts.map((product) => (
                  <tr key={product.name}>
                    <td>{product.name}</td>
                    <td className="text-right">{product.quantity}</td>
                    <td className="text-right">{money(product.revenue)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td>
                    <strong>TOTAL</strong>
                  </td>
                  <td className="text-right">
                    <strong>{dailyUnits}</strong>
                  </td>
                  <td className="text-right">
                    <strong>{money(dailyProductTotal)}</strong>
                  </td>
                </tr>
              </tfoot>
            </table>
            <div className="card-header" style={{ paddingTop: 8 }}>
              <span className="muted">Suma total de las ventas del día (tras descuentos):</span>
              <strong>{money(dailySalesTotal)}</strong>
            </div>
          </>
        )}
      </div>

      <div className="card table-card">
        <div className="card-header">
          <h2>Ventas del {to}</h2>
          <span className="badge badge-neutral">{sales.length} registro(s)</span>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Número</th>
              <th>Hora</th>
              <th>Cajero</th>
              <th>Pago</th>
              <th className="text-right">Total</th>
              <th>Estado</th>
              <th className="text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {sales.map((sale) => (
              <tr key={sale.id} className={sale.status === 'VOIDED' ? 'row-muted' : ''}>
                <td>
                  <strong>{sale.number}</strong>
                </td>
                <td>{new Date(sale.createdAt).toLocaleTimeString('es-CL', { timeStyle: 'short' })}</td>
                <td>{sale.cashierName}</td>
                <td>{paymentLabel(sale.paymentMethod)}</td>
                <td className="text-right">{money(sale.total)}</td>
                <td>
                  <span className={`badge ${sale.status === 'VOIDED' ? 'badge-danger' : 'badge-success'}`}>
                    {sale.status === 'VOIDED' ? 'Anulada' : 'Completada'}
                  </span>
                </td>
                <td className="text-right">
                  {sale.status === 'COMPLETED' && (
                    <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => handleVoid(sale)}>
                      Anular
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {sales.length === 0 && <div className="empty-state">No hay ventas registradas en esa fecha.</div>}
      </div>
    </div>
  )
}
