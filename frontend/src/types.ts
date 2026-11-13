export type Role = 'ADMIN' | 'CASHIER'
export type PaymentMethod = 'CASH' | 'DEBIT' | 'CREDIT' | 'TRANSFER'
export type SaleStatus = 'COMPLETED' | 'VOIDED'
export type CashSessionStatus = 'OPEN' | 'CLOSED'

export interface User {
  id: number
  username: string
  fullName: string
  role: Role
  active: boolean
}

export interface Category {
  id: number
  name: string
  color: string
  active: boolean
}

export interface Product {
  id: number
  name: string
  description: string | null
  price: number
  stock: number
  minStock: number
  active: boolean
  categoryId: number
  categoryName: string
  categoryColor: string
}

export interface SaleItem {
  id: number
  productId: number | null
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
  netAmount: number
  ivaAmount: number
}

export interface Sale {
  id: number
  number: string
  createdAt: string
  cashierName: string
  paymentMethod: PaymentMethod
  status: SaleStatus
  subtotal: number
  discount: number
  total: number
  amountReceived: number
  changeAmount: number
  netAmount: number
  ivaAmount: number
  items: SaleItem[]
}

export interface CashSession {
  id: number
  status: CashSessionStatus
  openedAt: string
  closedAt: string | null
  openedByName: string
  closedByName: string | null
  openingAmount: number
  closingAmount: number | null
  expectedAmount: number
  difference: number | null
  cashSales: number
  totalSales: number
  salesCount: number
  notes: string | null
}

export interface TopProduct {
  productId: number | null
  name: string
  quantity: number
  revenue: number
}

export interface PaymentBreakdown {
  method: PaymentMethod
  total: number
  count: number
}

export interface SalesReport {
  from: string
  to: string
  total: number
  count: number
  totalUnits: number
  cashTotal: number
  cardTotal: number
  transferTotal: number
  payments: PaymentBreakdown[]
  topProducts: TopProduct[]
}

export interface StockMovement {
  id: number
  productId: number
  productName: string
  type: 'SALE' | 'SALE_VOID' | 'RESTOCK' | 'ADJUSTMENT'
  quantity: number
  previousStock: number
  newStock: number
  reason: string | null
  userName: string | null
  createdAt: string
}

export interface SalePayload {
  items: { productId: number; quantity: number }[]
  paymentMethod: PaymentMethod
  discount: number
  amountReceived?: number
}

export interface ProductPayload {
  name: string
  description: string
  price: number
  stock: number
  minStock: number
  categoryId: number
}

export interface UserPayload {
  username: string
  password?: string
  fullName: string
  role: Role
  active: boolean
}
