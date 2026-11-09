/**
 * Convierte lo que escribe el usuario a un monto CLP.
 * Acepta "20.000", "20,000" o "20000" y siempre devuelve 20000.
 */
export function parseAmount(value: string): number {
  const digits = value.replace(/\D/g, '')
  return digits ? Number(digits) : 0
}

export function money(value: number | null | undefined): string {
  return new Intl.NumberFormat('es-CL', {
    style: 'currency',
    currency: 'CLP',
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0))
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('es-CL', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

export function todayISO(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Santiago' })
}

export function paymentLabel(method: string): string {
  switch (method) {
    case 'CASH':
      return 'Efectivo'
    case 'DEBIT':
      return 'Débito'
    case 'CREDIT':
      return 'Crédito'
    case 'TRANSFER':
      return 'Transferencia'
    default:
      return method
  }
}

export function roleLabel(role: string): string {
  return role === 'ADMIN' ? 'Administrador' : 'Cajero'
}

export function stockLabel(stock: number, minStock: number): string {
  if (stock <= 0) return 'Sin stock'
  if (stock <= minStock) return 'Stock bajo'
  return 'Disponible'
}
