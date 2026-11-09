import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import MoneyInput from '../components/MoneyInput'
import type { Category, Product, StockMovement } from '../types'
import { money, parseAmount, stockLabel } from '../utils'
import { alertError, alertSuccess, confirmAction } from '../utils/alert'

const MOVEMENT_LABELS: Record<StockMovement['type'], string> = {
  SALE: 'Venta',
  SALE_VOID: 'Anulación',
  RESTOCK: 'Reposición',
  ADJUSTMENT: 'Ajuste',
}

interface ProductFormProps {
  product: Product | null
  categories: Category[]
  onClose: () => void
  onSaved: () => void
}

function ProductForm({ product, categories, onClose, onSaved }: ProductFormProps) {
  const selectable = categories.filter((category) => category.active || category.id === product?.categoryId)
  const [name, setName] = useState(product?.name ?? '')
  const [description, setDescription] = useState(product?.description ?? '')
  const [price, setPrice] = useState(product ? String(Math.round(product.price)) : '')
  const [stock, setStock] = useState(product ? String(product.stock) : '')
  const [minStock, setMinStock] = useState(product ? String(product.minStock) : '5')
  const [categoryId, setCategoryId] = useState(String(product?.categoryId ?? selectable[0]?.id ?? ''))
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!categoryId) {
      alertError('Debes seleccionar una categoría')
      return
    }
    setSaving(true)
    try {
      const payload = {
        name: name.trim(),
        description: description.trim(),
        price: parseAmount(price),
        stock: Number(stock),
        minStock: Number(minStock),
        categoryId: Number(categoryId),
      }
      if (product) {
        await api.updateProduct(product.id, payload)
      } else {
        await api.createProduct(payload)
      }
      onSaved()
      onClose()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo guardar el producto')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={product ? 'Editar producto' : 'Nuevo producto'} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>

        <label className="field">
          <span>Nombre</span>
          <input value={name} onChange={(event) => setName(event.target.value)} required maxLength={120} />
        </label>

        <label className="field">
          <span>Descripción</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={2}
            maxLength={300}
          />
        </label>

        <div className="form-row">
          <label className="field">
            <span>Precio</span>
            <MoneyInput
              value={price}
              onChange={setPrice}
              required
            />
          </label>
          <label className="field">
            <span>Categoría</span>
            <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
              {selectable.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="form-row">
          <label className="field">
            <span>Stock actual</span>
            <input
              type="number"
              min="0"
              step="1"
              value={stock}
              onChange={(event) => setStock(event.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>Stock mínimo</span>
            <input
              type="number"
              min="0"
              step="1"
              value={minStock}
              onChange={(event) => setMinStock(event.target.value)}
              required
            />
          </label>
        </div>

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

interface RestockFormProps {
  product: Product
  onClose: () => void
  onSaved: () => void
}

function RestockForm({ product, onClose, onSaved }: RestockFormProps) {
  const [quantity, setQuantity] = useState('10')
  const [reason, setReason] = useState('Reposición de inventario')
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      await api.restockProduct(product.id, Number(quantity), reason.trim())
      onSaved()
      onClose()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo reponer el stock')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Reponer stock · ${product.name}`} onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <p className="muted">
          Stock actual: <strong>{product.stock}</strong> unidades.
        </p>
        <label className="field">
          <span>Cantidad a agregar</span>
          <input
            type="number"
            min="1"
            step="1"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Motivo</span>
          <input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={200} />
        </label>
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Guardando…' : 'Reponer'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface CategoryFormProps {
  onClose: () => void
  onSaved: () => void
}

function CategoryForm({ onClose, onSaved }: CategoryFormProps) {
  const [name, setName] = useState('')
  const [color, setColor] = useState('#ef4444')
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      await api.createCategory({ name: name.trim(), color })
      onSaved()
      onClose()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo crear la categoría')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Nueva categoría" onClose={onClose}>
      <form className="stack" onSubmit={handleSubmit}>
        <label className="field">
          <span>Nombre</span>
          <input value={name} onChange={(event) => setName(event.target.value)} required maxLength={80} />
        </label>
        <label className="field">
          <span>Color</span>
          <input type="color" value={color} onChange={(event) => setColor(event.target.value)} />
        </label>
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Guardando…' : 'Crear categoría'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function ProductsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [movements, setMovements] = useState<StockMovement[]>([])
  const [tab, setTab] = useState<'products' | 'movements'>('products')
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [restocking, setRestocking] = useState<Product | null>(null)
  const [creatingCategory, setCreatingCategory] = useState(false)

  const load = useCallback(async () => {
    try {
      const [productList, categoryList] = await Promise.all([
        api.products(false),
        api.categories(),
      ])
      setProducts(productList)
      setCategories(categoryList)
      if (isAdmin) {
        setMovements(await api.movements())
      }
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudieron cargar los productos')
    } finally {
      setLoading(false)
    }
  }, [isAdmin])

  useEffect(() => {
    void load()
  }, [load])

  async function handleDeactivate(product: Product) {
    if (!(await confirmAction(`¿Desactivar "${product.name}"? Dejará de aparecer en el punto de venta.`, 'Desactivar'))) return
    try {
      await api.deactivateProduct(product.id)
      alertSuccess(`Producto "${product.name}" desactivado`)
      await load()
    } catch (err) {
      alertError(err instanceof Error ? err.message : 'No se pudo desactivar el producto')
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
          <h1>Productos e inventario</h1>
          <p>Administra el catálogo de sushi, precios y stock disponible.</p>
        </div>
        {isAdmin && (
          <div className="button-row">
            <button type="button" className="btn btn-ghost" onClick={() => setCreatingCategory(true)}>
              Nueva categoría
            </button>
            <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
              + Nuevo producto
            </button>
          </div>
        )}
      </div>

      {isAdmin && (
        <div className="tabs">
          <button
            type="button"
            className={`tab${tab === 'products' ? ' active' : ''}`}
            onClick={() => setTab('products')}
          >
            Catálogo
          </button>
          <button
            type="button"
            className={`tab${tab === 'movements' ? ' active' : ''}`}
            onClick={() => setTab('movements')}
          >
            Movimientos de stock
          </button>
        </div>
      )}

      {tab === 'products' ? (
        <div className="card table-card">
          <table className="table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Categoría</th>
                <th className="text-right">Precio</th>
                <th className="text-right">Stock</th>
                <th>Estado</th>
                {isAdmin && <th className="text-right">Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {products.map((product) => {
                const low = product.stock <= product.minStock
                return (
                  <tr key={product.id} className={product.active ? '' : 'row-muted'}>
                    <td>
                      <strong>{product.name}</strong>
                      <span className="table-sub">{product.description ?? 'Sin descripción'}</span>
                    </td>
                    <td>
                      <span className="badge badge-neutral">
                        <span className="chip-dot" style={{ background: product.categoryColor }} />
                        {product.categoryName}
                      </span>
                    </td>
                    <td className="text-right">{money(product.price)}</td>
                    <td className="text-right">
                      <span className={`badge ${product.stock <= 0 ? 'badge-danger' : low ? 'badge-warning' : 'badge-success'}`}>
                        {product.stock} · {stockLabel(product.stock, product.minStock)}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${product.active ? 'badge-success' : 'badge-neutral'}`}>
                        {product.active ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="text-right">
                        <div className="button-row button-row-end">
                          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setEditing(product)}>
                            Editar
                          </button>
                          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setRestocking(product)}>
                            Reponer
                          </button>
                          {product.active && (
                            <button
                              type="button"
                              className="btn btn-sm btn-danger-ghost"
                              onClick={() => handleDeactivate(product)}
                            >
                              Desactivar
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
          {products.length === 0 && <div className="empty-state">No hay productos registrados.</div>}
        </div>
      ) : (
        <div className="card table-card">
          <table className="table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Producto</th>
                <th>Tipo</th>
                <th className="text-right">Cantidad</th>
                <th className="text-right">Stock</th>
                <th>Motivo</th>
                <th>Usuario</th>
              </tr>
            </thead>
            <tbody>
              {movements.map((movement) => (
                <tr key={movement.id}>
                  <td>{new Date(movement.createdAt).toLocaleString('es-CL')}</td>
                  <td>{movement.productName}</td>
                  <td>
                    <span
                      className={`badge ${
                        movement.type === 'SALE'
                          ? 'badge-danger'
                          : movement.type === 'SALE_VOID'
                            ? 'badge-warning'
                            : 'badge-success'
                      }`}
                    >
                      {MOVEMENT_LABELS[movement.type]}
                    </span>
                  </td>
                  <td className="text-right">{movement.quantity}</td>
                  <td className="text-right">
                    {movement.previousStock} → {movement.newStock}
                  </td>
                  <td>{movement.reason ?? '—'}</td>
                  <td>{movement.userName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {movements.length === 0 && <div className="empty-state">Todavía no hay movimientos de stock.</div>}
        </div>
      )}

      {editing !== null && (
        <ProductForm
          product={editing === 'new' ? null : editing}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={() => {
            alertSuccess(editing === 'new' ? 'Producto creado' : 'Producto actualizado')
            void load()
          }}
        />
      )}

      {restocking && (
        <RestockForm
          product={restocking}
          onClose={() => setRestocking(null)}
          onSaved={() => {
            alertSuccess(`Stock de "${restocking.name}" actualizado`)
            void load()
          }}
        />
      )}

      {creatingCategory && (
        <CategoryForm
          onClose={() => setCreatingCategory(false)}
          onSaved={() => {
            alertSuccess('Categoría creada')
            void load()
          }}
        />
      )}
    </div>
  )
}
