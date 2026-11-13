import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import MoneyInput from '../components/MoneyInput'
import { previewReceiptPdf } from '../pdf/receiptPdf'
import type { CashSession, Category, PaymentMethod, Product, Sale } from '../types'
import { calcIva, money, parseAmount, paymentLabel } from '../utils'
import { alertError } from '../utils/alert'

const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Efectivo' },
  { value: 'DEBIT', label: 'Débito' },
  { value: 'CREDIT', label: 'Crédito' },
  { value: 'TRANSFER', label: 'Transferencia' },
]

export default function PosPage() {
  const { user } = useAuth()
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [cash, setCash] = useState<CashSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState<number | null>(null)
  const [cart, setCart] = useState<Record<number, number>>({})
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH')
  const [discount, setDiscount] = useState('')
  const [received, setReceived] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [lastSale, setLastSale] = useState<Sale | null>(null)
  const [showPreview, setShowPreview] = useState(false)

  const load = useCallback(async () => {
    try {
      const [productList, categoryList, currentCash] = await Promise.all([
        api.products(true),
        api.categories(),
        api.currentCash(),
      ])
      setProducts(productList)
      setCategories(categoryList)
      setCash(currentCash)
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudieron cargar los datos')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const visibleProducts = useMemo(() => {
    const text = search.trim().toLowerCase()
    return products.filter((product) => {
      if (categoryFilter !== null && product.categoryId !== categoryFilter) return false
      if (!text) return true
      return (
        product.name.toLowerCase().includes(text) ||
        (product.description ?? '').toLowerCase().includes(text)
      )
    })
  }, [products, search, categoryFilter])

  const cartLines = useMemo(() => {
    return Object.entries(cart)
      .map(([id, quantity]) => {
        const product = products.find((item) => item.id === Number(id))
        if (!product || quantity <= 0) return null
        return { product, quantity }
      })
      .filter((line): line is { product: Product; quantity: number } => line !== null)
  }, [cart, products])

  const subtotal = useMemo(
    () => cartLines.reduce((sum, line) => sum + line.product.price * line.quantity, 0),
    [cartLines],
  )
  const discountValue = Math.max(0, parseAmount(discount))
  const total = Math.max(0, subtotal - discountValue)
  const receivedValue = parseAmount(received)
  const change = receivedValue - total
  const itemCount = cartLines.reduce((sum, line) => sum + line.quantity, 0)

  function addProduct(product: Product) {
    setCart((previous) => {
      const current = previous[product.id] ?? 0
      if (current >= product.stock) return previous
      return { ...previous, [product.id]: current + 1 }
    })
  }

  function changeQuantity(product: Product, delta: number) {
    setCart((previous) => {
      const current = previous[product.id] ?? 0
      const next = Math.min(product.stock, Math.max(0, current + delta))
      const updated = { ...previous }
      if (next === 0) {
        delete updated[product.id]
      } else {
        updated[product.id] = next
      }
      return updated
    })
  }

  function clearCart() {
    setCart({})
    setDiscount('')
    setReceived('')
  }

  function openPreview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (cartLines.length === 0) {
      alertError('Agrega al menos un producto al carrito')
      return
    }
    if (!cash) {
      alertError('Debes abrir la caja antes de cobrar')
      return
    }
    if (discountValue > subtotal) {
      alertError('El descuento no puede superar el subtotal')
      return
    }
    if (paymentMethod === 'CASH' && receivedValue < total) {
      alertError('El monto recibido es menor al total a pagar')
      return
    }

    setShowPreview(true)
  }

  async function confirmSale(withBoleta: boolean) {
    setSubmitting(true)
    try {
      const sale = await api.createSale({
        items: cartLines.map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
        })),
        paymentMethod,
        discount: discountValue,
        amountReceived: paymentMethod === 'CASH' ? receivedValue : undefined,
      })
      setLastSale(sale)
      setShowPreview(false)
      if (withBoleta) {
        previewReceiptPdf(sale)
      }
      clearCart()
      await load()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo registrar la venta')
    } finally {
      setSubmitting(false)
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
    <div className="pos">
      <div className="pos-header">
        <div>
          <h1>Punto de venta</h1>
          <p>Selecciona productos y registra el cobro en mostrador.</p>
        </div>
        {cash ? (
          <span className="badge badge-success">Caja abierta · {money(cash.openingAmount)} inicial</span>
        ) : (
          <Link to="/caja" className="badge badge-warning badge-link">
            Caja cerrada · abrir caja
          </Link>
        )}
      </div>

      {!cash && (
        <div className="alert alert-warning">
          No hay una caja abierta. Las ventas se bloquean hasta que abras la caja del turno.
          <Link to="/caja"> Ir a Caja</Link>.
        </div>
      )}

      <div className="pos-grid">
        <section className="pos-catalog">
          <div className="catalog-toolbar card">
            <input
              type="search"
              className="search-input"
              placeholder="Buscar producto…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <div className="chips">
              <button
                type="button"
                className={`chip${categoryFilter === null ? ' active' : ''}`}
                onClick={() => setCategoryFilter(null)}
              >
                Todos
              </button>
              {categories
                .filter((category) => category.active)
                .map((category) => (
                  <button
                    key={category.id}
                    type="button"
                    className={`chip${categoryFilter === category.id ? ' active' : ''}`}
                    onClick={() => setCategoryFilter(category.id)}
                  >
                    <span className="chip-dot" style={{ background: category.color }} />
                    {category.name}
                  </button>
                ))}
            </div>
          </div>

          {visibleProducts.length === 0 ? (
            <div className="empty-state card">
              <span>🍱</span>
              <strong>No hay productos disponibles</strong>
              <p>Ajusta la búsqueda o revisa el inventario.</p>
            </div>
          ) : (
            <div className="product-grid">
              {visibleProducts.map((product) => {
                const inCart = cart[product.id] ?? 0
                const outOfStock = product.stock <= 0
                return (
                  <button
                    key={product.id}
                    type="button"
                    className={`product-card${inCart > 0 ? ' selected' : ''}`}
                    onClick={() => addProduct(product)}
                    disabled={outOfStock}
                  >
                    <span className="product-tag" style={{ background: product.categoryColor }}>
                      {product.categoryName}
                    </span>
                    <strong>{product.name}</strong>
                    <span className="product-desc">{product.description ?? 'Sin descripción'}</span>
                    <span className="product-footer">
                      <span className="product-price">{money(product.price)}</span>
                      <span className={`stock-pill${outOfStock ? ' out' : ''}`}>
                        {outOfStock ? 'Sin stock' : `${product.stock} disp.`}
                      </span>
                    </span>
                    {inCart > 0 && <span className="product-count">{inCart}</span>}
                  </button>
                )
              })}
            </div>
          )}
        </section>

        <aside className="pos-cart card">
          <div className="cart-header">
            <h2>Carrito</h2>
            <span className="badge badge-neutral">{itemCount} ítem(s)</span>
          </div>

          <div className="cart-items">
            {cartLines.length === 0 ? (
              <div className="cart-empty">
                <span>🛒</span>
                <p>El carrito está vacío</p>
              </div>
            ) : (
              cartLines.map(({ product, quantity }) => (
                <div key={product.id} className="cart-line">
                  <div className="cart-line-info">
                    <strong>{product.name}</strong>
                    <span>{money(product.price)} c/u</span>
                  </div>
                  <div className="qty-control">
                    <button type="button" onClick={() => changeQuantity(product, -1)} aria-label="Quitar uno">
                      −
                    </button>
                    <span>{quantity}</span>
                    <button type="button" onClick={() => changeQuantity(product, 1)} aria-label="Agregar uno">
                      +
                    </button>
                  </div>
                  <span className="cart-line-total">{money(product.price * quantity)}</span>
                </div>
              ))
            )}
          </div>

          <form className="cart-checkout" onSubmit={openPreview}>

            <div className="payment-methods">
              {PAYMENT_METHODS.map((method) => (
                <button
                  key={method.value}
                  type="button"
                  className={`payment-option${paymentMethod === method.value ? ' active' : ''}`}
                  onClick={() => {
                    setPaymentMethod(method.value)
                  }}
                >
                  {method.label}
                </button>
              ))}
            </div>

            <div className="form-row">
              <label className="field">
                <span>Descuento</span>
                <MoneyInput
                  value={discount}
                  onChange={setDiscount}
                  placeholder="0"
                />
              </label>
              {paymentMethod === 'CASH' && (
                <label className="field">
                  <span>Monto recibido</span>
                  <MoneyInput
                    value={received}
                    onChange={setReceived}
                    placeholder="0"
                  />
                </label>
              )}
            </div>

            <div className="totals">
              <div>
                <span>Subtotal</span>
                <strong>{money(subtotal)}</strong>
              </div>
              {discountValue > 0 && (
                <div>
                  <span>Descuento</span>
                  <strong className="text-danger">−{money(discountValue)}</strong>
                </div>
              )}
              <div className="totals-main">
                <span>Total</span>
                <strong>{money(total)}</strong>
              </div>
              {paymentMethod === 'CASH' && received !== '' && (
                <div>
                  <span>Vuelto</span>
                  <strong className={change < 0 ? 'text-danger' : 'text-success'}>
                    {change >= 0 ? money(change) : 'Falta ' + money(Math.abs(change))}
                  </strong>
                </div>
              )}
            </div>

            <div className="cart-actions">
              <button type="button" className="btn btn-ghost" onClick={clearCart} disabled={cartLines.length === 0}>
                Limpiar
              </button>
              <button type="submit" className="btn btn-primary btn-lg" disabled={submitting || !cash}>
                {submitting ? 'Registrando…' : 'Cobrar'}
              </button>
            </div>
            <span className="muted" style={{ fontSize: 13 }}>
              Al cobrar se mostrará la vista previa para generar la boleta.
            </span>
          </form>
        </aside>
      </div>

      {showPreview && (
        <Modal title="Vista previa" onClose={() => { if (!submitting) setShowPreview(false) }}>
          <div className="receipt">
            <div className="receipt-head">
              <strong>Sushi Local</strong>
              <span>{new Date().toLocaleString('es-CL')}</span>
              <span>Cajero: {user?.fullName ?? '—'}</span>
              <span className="muted">Vista previa: la venta aún no está registrada.</span>
            </div>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="text-right">Cant.</th>
                  <th className="text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {cartLines.map(({ product, quantity }) => (
                  <tr key={product.id}>
                    <td>{product.name}</td>
                    <td className="text-right">{quantity}</td>
                    <td className="text-right">{money(product.price * quantity)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="receipt-totals">
              <div>
                <span>Subtotal</span>
                <strong>{money(subtotal)}</strong>
              </div>
              {discountValue > 0 && (
                <div>
                  <span>Descuento</span>
                  <strong>−{money(discountValue)}</strong>
                </div>
              )}
              <div className="totals-main">
                <span>Total</span>
                <strong>{money(total)}</strong>
              </div>
              {(() => {
                const { netAmount, ivaAmount } = calcIva(total)
                return (
                  <>
                    <div>
                      <span>Neto</span>
                      <strong>{money(netAmount)}</strong>
                    </div>
                    <div>
                      <span>IVA (19%)</span>
                      <strong>{money(ivaAmount)}</strong>
                    </div>
                  </>
                )
              })()}
              <div>
                <span>Pago</span>
                <strong>{paymentLabel(paymentMethod)}</strong>
              </div>
              {paymentMethod === 'CASH' && (
                <>
                  <div>
                    <span>Recibido</span>
                    <strong>{money(receivedValue)}</strong>
                  </div>
                  <div>
                    <span>Vuelto</span>
                    <strong>{money(change)}</strong>
                  </div>
                </>
              )}
            </div>

            <p className="muted" style={{ fontSize: 13 }}>
              Al confirmar se registrará la venta y se abrirá la boleta en una nueva pestaña.
            </p>
            <button
              type="button"
              className="btn btn-primary btn-block btn-lg"
              onClick={() => confirmSale(true)}
              disabled={submitting}
            >
              {submitting ? 'Generando boleta…' : '🧾 Generar boleta'}
            </button>
            <button
              type="button"
              className="btn btn-ghost btn-block"
              onClick={() => confirmSale(false)}
              disabled={submitting}
            >
              {submitting ? 'Registrando…' : 'Cobrar sin boleta'}
            </button>
            <button
              type="button"
              className="btn btn-ghost btn-block"
              onClick={() => setShowPreview(false)}
              disabled={submitting}
            >
              Volver
            </button>
          </div>
        </Modal>
      )}

      {lastSale && (
        <Modal title={`Venta ${lastSale.number}`} onClose={() => setLastSale(null)}>
          <div className="receipt">
            <div className="receipt-head">
              <strong>Sushi Local</strong>
              <span>{new Date(lastSale.createdAt).toLocaleString('es-CL')}</span>
              <span>Cajero: {lastSale.cashierName}</span>
            </div>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="text-right">Cant.</th>
                  <th className="text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {lastSale.items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.productName}</td>
                    <td className="text-right">{item.quantity}</td>
                    <td className="text-right">{money(item.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="receipt-totals">
              <div>
                <span>Subtotal</span>
                <strong>{money(lastSale.subtotal)}</strong>
              </div>
              {lastSale.discount > 0 && (
                <div>
                  <span>Descuento</span>
                  <strong>−{money(lastSale.discount)}</strong>
                </div>
              )}
              <div className="totals-main">
                <span>Total</span>
                <strong>{money(lastSale.total)}</strong>
              </div>
              <div>
                <span>Neto</span>
                <strong>{money(lastSale.netAmount)}</strong>
              </div>
              <div>
                <span>IVA (19%)</span>
                <strong>{money(lastSale.ivaAmount)}</strong>
              </div>
              <div>
                <span>Pago</span>
                <strong>{paymentLabel(lastSale.paymentMethod)}</strong>
              </div>
              <div>
                <span>Recibido</span>
                <strong>{money(lastSale.amountReceived)}</strong>
              </div>
              <div>
                <span>Vuelto</span>
                <strong>{money(lastSale.changeAmount)}</strong>
              </div>
            </div>
            <button type="button" className="btn btn-primary btn-block" onClick={() => previewReceiptPdf(lastSale)}>
              🧾 Ver boleta
            </button>
            <p className="muted" style={{ fontSize: 13, textAlign: 'center' }}>
              Se abrirá la boleta en una nueva pestaña.
            </p>
            <button type="button" className="btn btn-ghost btn-block" onClick={() => setLastSale(null)}>
              Nueva venta
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
