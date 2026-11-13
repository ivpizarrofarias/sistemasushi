import { jsPDF } from 'jspdf'
import type { Sale } from '../types'
import { money, paymentLabel } from '../utils'

const PAGE_WIDTH_MM = 80
const PAGE_HEIGHT_MM = 297
const MARGIN_MM = 5

function formatDate(value: string): string {
  return new Date(value).toLocaleString('es-CL', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

function line(doc: jsPDF, y: number, width: number) {
  doc.setLineWidth(0.2)
  doc.setDrawColor(0, 0, 0)
  doc.line(MARGIN_MM, y, width - MARGIN_MM, y)
  return y + 3
}

export function generateReceiptPdf(sale: Sale): Blob {
  const doc = new jsPDF({ unit: 'mm', format: [PAGE_WIDTH_MM, PAGE_HEIGHT_MM] })
  const width = PAGE_WIDTH_MM
  let y = 8

  // Encabezado
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(14)
  doc.text('SUSHI LOCAL', width / 2, y, { align: 'center' })
  y += 5
  doc.setFontSize(9)
  doc.setFont('helvetica', 'normal')
  doc.text('Boleta - Comprobante de venta', width / 2, y, { align: 'center' })
  y += 4
  doc.setFontSize(8)
  doc.text(`Folio: ${sale.number}`, width / 2, y, { align: 'center' })
  y += 4
  doc.text(formatDate(sale.createdAt), width / 2, y, { align: 'center' })
  y += 4
  doc.text(`Cajero: ${sale.cashierName}`, width / 2, y, { align: 'center' })
  y += 4

  y = line(doc, y, width)

  // Encabezado de tabla
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(8)
  doc.text('PRODUCTO', MARGIN_MM, y)
  doc.text('CANT', 42, y, { align: 'center' })
  doc.text('TOTAL', width - MARGIN_MM, y, { align: 'right' })
  y += 3
  y = line(doc, y, width)

  // Items
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  for (const item of sale.items) {
    // Nombre del producto (con salto de línea si es largo)
    const nameLines = doc.splitTextToSize(item.productName, 36)
    const startY = y
    nameLines.forEach((text: string, index: number) => {
      doc.text(text, MARGIN_MM, y + index * 3.2)
    })
    const blockHeight = nameLines.length * 3.2
    y = Math.max(y + blockHeight, startY + 4.5)

    doc.text(String(item.quantity), 42, startY, { align: 'center' })
    doc.text(money(item.lineTotal), width - MARGIN_MM, startY, { align: 'right' })

    // Detalle precio unitario en letra pequeña si el nombre es corto
    if (nameLines.length === 1) {
      y += 1.5
    }
  }

  y += 2
  y = line(doc, y, width)

  // Totales con desglose de IVA (Ley chilena - SII)
  doc.setFontSize(8)
  const rightX = width - MARGIN_MM
  const labelX = 48
  doc.text('Subtotal', labelX, y)
  doc.text(money(sale.subtotal), rightX, y, { align: 'right' })
  y += 4

  if (sale.discount > 0) {
    doc.text('Descuento', labelX, y)
    doc.text(`-${money(sale.discount)}`, rightX, y, { align: 'right' })
    y += 4
  }

  // Neto (sin IVA)
  doc.text('Neto', labelX, y)
  doc.text(money(sale.netAmount), rightX, y, { align: 'right' })
  y += 4

  // IVA 19%
  doc.text('IVA (19%)', labelX, y)
  doc.text(money(sale.ivaAmount), rightX, y, { align: 'right' })
  y += 4

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(10)
  doc.text('TOTAL', labelX, y)
  doc.text(money(sale.total), rightX, y, { align: 'right' })
  y += 5
  y = line(doc, y, width)

  // Pago
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.text('Pago', labelX, y)
  doc.text(paymentLabel(sale.paymentMethod), rightX, y, { align: 'right' })
  y += 4
  doc.text('Recibido', labelX, y)
  doc.text(money(sale.amountReceived), rightX, y, { align: 'right' })
  y += 4
  doc.text('Vuelto', labelX, y)
  doc.text(money(sale.changeAmount), rightX, y, { align: 'right' })
  y += 6

  y = line(doc, y, width)

  doc.setFontSize(8)
  doc.setFont('helvetica', 'italic')
  doc.text('Gracias por su compra', width / 2, y, { align: 'center' })
  y += 4
  doc.setFontSize(7)
  doc.setFont('helvetica', 'normal')
  doc.text('Documento generado automáticamente', width / 2, y, { align: 'center' })
  y += 3.5
  doc.text('Sin valor tributario', width / 2, y, { align: 'center' })

  const pdfBlob = doc.output('blob')
  return pdfBlob
}

export function previewReceiptPdf(sale: Sale) {
  const blob = generateReceiptPdf(sale)
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
}

export function downloadReceiptPdf(sale: Sale) {
  const blob = generateReceiptPdf(sale)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `boleta-${sale.number}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
