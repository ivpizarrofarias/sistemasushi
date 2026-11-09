import type {
  CashSession,
  Category,
  Product,
  ProductPayload,
  Sale,
  SalePayload,
  SalesReport,
  StockMovement,
  User,
  UserPayload,
} from './types'

export class ApiError extends Error {
  status: number
  details: string[]

  constructor(message: string, status: number, details: string[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const { headers, ...rest } = options
  const response = await fetch(`/api${path}`, {
    credentials: 'include',
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(headers ?? {}),
    },
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(
      data?.message ?? 'Ocurrió un error al comunicarse con el servidor',
      response.status,
      data?.details ?? [],
    )
  }

  return data as T
}

export const api = {
  login: (username: string, password: string) =>
    request<User>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  me: () => request<User>('/auth/me'),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  categories: () => request<Category[]>('/categories'),
  createCategory: (data: { name: string; color: string }) =>
    request<Category>('/categories', { method: 'POST', body: JSON.stringify(data) }),

  products: (activeOnly = true) =>
    request<Product[]>(`/products?activeOnly=${activeOnly}`),
  createProduct: (data: ProductPayload) =>
    request<Product>('/products', { method: 'POST', body: JSON.stringify(data) }),
  updateProduct: (id: number, data: ProductPayload) =>
    request<Product>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  restockProduct: (id: number, quantity: number, reason: string) =>
    request<Product>(`/products/${id}/restock`, {
      method: 'PATCH',
      body: JSON.stringify({ quantity, reason }),
    }),
  deactivateProduct: (id: number) =>
    request<void>(`/products/${id}`, { method: 'DELETE' }),

  sales: (date?: string) =>
    request<Sale[]>(`/sales${date ? `?date=${date}` : ''}`),
  createSale: (data: SalePayload) =>
    request<Sale>('/sales', { method: 'POST', body: JSON.stringify(data) }),
  voidSale: (id: number) =>
    request<Sale>(`/sales/${id}/void`, { method: 'POST' }),

  currentCash: () => request<CashSession | null>('/cash/current'),
  openCash: (openingAmount: number) =>
    request<CashSession>('/cash/open', {
      method: 'POST',
      body: JSON.stringify({ openingAmount }),
    }),
  closeCash: (closingAmount: number, notes: string) =>
    request<CashSession>('/cash/close', {
      method: 'POST',
      body: JSON.stringify({ closingAmount, notes }),
    }),
  cashSessions: () => request<CashSession[]>('/cash/sessions'),

  report: (from: string, to: string) =>
    request<SalesReport>(`/reports?from=${from}&to=${to}`),
  downloadReportExcel: async (from: string, to: string) => {
    const params = new URLSearchParams({ from, to })
    const response = await fetch(`/api/reports/excel?${params.toString()}`, {
      credentials: 'include',
    })

    if (!response.ok) {
      let message = 'No se pudo generar el Excel'
      try {
        const text = await response.text()
        if (text) {
          const data = JSON.parse(text)
          message = data?.message ?? message
        }
      } catch {
        // Se usa el mensaje por defecto si la respuesta no es JSON.
      }
      throw new ApiError(message, response.status)
    }

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const header = response.headers.get('Content-Disposition') ?? ''
    const match = header.match(/filename="([^"]+)"|filename=([^;]+)/)
    link.download = match
      ? (match[1] ?? match[2] ?? `reporte-ventas-${from}_a_${to}.xlsx`).trim()
      : `reporte-ventas-${from}_a_${to}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  },

  users: () => request<User[]>('/users'),
  createUser: (data: UserPayload) =>
    request<User>('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id: number, data: UserPayload) =>
    request<User>(`/users/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  movements: () => request<StockMovement[]>('/inventory/movements'),
}
