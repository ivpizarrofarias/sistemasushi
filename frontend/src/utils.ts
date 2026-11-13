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

export function todayISO(fecha: Date = new Date()): string {
  return fecha.toLocaleDateString('en-CA', { timeZone: 'America/Santiago' })
}

export function todayDisplay(fecha: Date = new Date()): string {
  const ahora = fecha
  const diaSemana = ahora.toLocaleDateString('es-CL', {
    weekday: 'long',
    timeZone: 'America/Santiago',
  })
  const diaSemanaCapitalizado = diaSemana.charAt(0).toUpperCase() + diaSemana.slice(1)
  const [year, month, day] = todayISO(ahora).split('-')

  const horaActual = new Intl.DateTimeFormat('es-CL', {
    timeZone: 'America/Santiago',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(ahora)

  return `${diaSemanaCapitalizado}, ${day}-${month}-${year} ${horaActual}`
}

export function formatDisplayDate(fechaISO: string): string {
  const [year, month, day] = fechaISO.split('-')
  if (!year || !month || !day) return fechaISO
  return `${day}-${month}-${year}`
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

export const IVA_RATE = 0.19

export function calcIva(amount: number): { netAmount: number; ivaAmount: number } {
  const divisor = 1 + IVA_RATE
  const net = amount / divisor
    const iva = amount - net
  return { netAmount: net, ivaAmount: iva }
}

export function stockLabel(stock: number, minStock: number): string {
  if (stock <= 0) return 'Sin stock'
  if (stock <= minStock) return 'Stock bajo'
  return 'Disponible'
}
