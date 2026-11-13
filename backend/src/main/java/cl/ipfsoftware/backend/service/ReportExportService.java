package cl.ipfsoftware.backend.service;

import cl.ipfsoftware.backend.domain.PaymentMethod;
import cl.ipfsoftware.backend.domain.Sale;
import cl.ipfsoftware.backend.domain.SaleItem;
import cl.ipfsoftware.backend.domain.SaleStatus;
import cl.ipfsoftware.backend.repository.SaleRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el reporte de ventas como archivo Excel (.xlsx):
 * - Resumen: totales del período
 * - Productos: producto, cantidad vendida y total por producto
 * - Ventas: detalle de cada venta del período
 */
@Service
public class ReportExportService {

    // Colores de marca (sushi)
    private static final IndexedColors COLOR_PRIMARY = IndexedColors.DARK_RED;      // #e11d48 aproximado
    private static final IndexedColors COLOR_HEADER_BG = IndexedColors.GREY_25_PERCENT;
    private static final IndexedColors COLOR_ZEBRA = IndexedColors.GREY_25_PERCENT;
    private static final IndexedColors COLOR_TOTAL_BG = IndexedColors.LIGHT_YELLOW;
    private static final IndexedColors COLOR_TITLE_BG = IndexedColors.DARK_RED;
    private static final IndexedColors COLOR_TITLE_TEXT = IndexedColors.WHITE;

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SaleRepository saleRepository;

    public ReportExportService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    public String excelFileName(LocalDate from, LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now(SaleService.BUSINESS_ZONE) : from;
        LocalDate endDate = to == null ? startDate : to;
        if (startDate.equals(endDate)) {
            return "ventas-del-dia-" + DATE_DISPLAY_FORMAT.format(startDate) + ".xlsx";
        }
        return "reporte-ventas-" + DATE_DISPLAY_FORMAT.format(startDate) + "_a_" + DATE_DISPLAY_FORMAT.format(endDate) + ".xlsx";
    }

    @Transactional(readOnly = true)
    public byte[] generateExcel(LocalDate from, LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now(SaleService.BUSINESS_ZONE) : from;
        LocalDate endDate = to == null ? startDate : to;
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha hasta no puede ser anterior a la fecha desde");
        }

        Instant start = startDate.atStartOfDay(SaleService.BUSINESS_ZONE).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(SaleService.BUSINESS_ZONE).toInstant();
        List<Sale> sales = saleRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusOrderByCreatedAtDesc(
                        start, end, SaleStatus.COMPLETED);

        BigDecimal total = BigDecimal.ZERO;
        Map<PaymentMethod, BigDecimal> paymentTotals = new HashMap<>();
        Map<PaymentMethod, Long> paymentCounts = new HashMap<>();
        Map<String, long[]> quantityByProduct = new HashMap<>();
        Map<String, BigDecimal> revenueByProduct = new HashMap<>();
        Map<String, String> nameByKey = new HashMap<>();
        long totalUnits = 0;

        for (Sale sale : sales) {
            total = total.add(sale.getTotal());
            paymentTotals.merge(sale.getPaymentMethod(), sale.getTotal(), BigDecimal::add);
            paymentCounts.merge(sale.getPaymentMethod(), 1L, Long::sum);
            for (SaleItem item : sale.getItems()) {
                Long productId = item.getProduct() == null ? null : item.getProduct().getId();
                String key = productId == null ? "name:" + item.getProductName() : "id:" + productId;
                quantityByProduct.computeIfAbsent(key, k -> new long[1])[0] += item.getQuantity();
                revenueByProduct.merge(key, item.getLineTotal(), BigDecimal::add);
                nameByKey.putIfAbsent(key, item.getProductName());
                totalUnits += item.getQuantity();
            }
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        long count = sales.size();

        List<ProductRow> products = quantityByProduct.entrySet().stream()
                .map(entry -> new ProductRow(
                        nameByKey.get(entry.getKey()),
                        entry.getValue()[0],
                        revenueByProduct.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparingLong(ProductRow::quantity).reversed()
                        .thenComparing(ProductRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet summarySheet = workbook.createSheet("Resumen");
            writeSummarySheet(summarySheet, startDate, endDate, total, count, totalUnits,
                    paymentTotals, paymentCounts);

            Sheet productsSheet = workbook.createSheet("Productos más vendidos");
            writeProductsSheet(productsSheet, products, totalUnits, total);

            Sheet salesSheet = workbook.createSheet("Detalle de ventas");
            writeSalesSheet(salesSheet, sales, total);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel del reporte", ex);
        }
    }

    /* ========================= RESUMEN ========================= */

    private void writeSummarySheet(
            Sheet sheet,
            LocalDate from,
            LocalDate to,
            BigDecimal total,
            long count,
            long totalUnits,
            Map<PaymentMethod, BigDecimal> paymentTotals,
            Map<PaymentMethod, Long> paymentCounts
    ) {
        Workbook wb = sheet.getWorkbook();
        CellStyle titleStyle = titleStyle(wb);
        CellStyle subtitleStyle = subtitleStyle(wb);
        CellStyle headerStyle = headerStyle(wb);
        CellStyle labelStyle = labelStyle(wb);
        CellStyle moneyStyle = moneyStyle(wb);
        CellStyle totalStyle = totalStyle(wb);

        // Título con fondo de marca
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(24);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Reporte de ventas - Sushi Local");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress.valueOf("A1:B1"));

        // Subtítulo con período
        Row periodRow = sheet.createRow(1);
        periodRow.setHeightInPoints(18);
        String periodText = from.equals(to)
                ? "Ventas del día: " + DATE_DISPLAY_FORMAT.format(from)
                : "Período: " + DATE_DISPLAY_FORMAT.format(from) + " a " + DATE_DISPLAY_FORMAT.format(to);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue(periodText);
        periodCell.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress.valueOf("A2:B2"));

        // Encabezado de tabla
        Row header = sheet.createRow(3);
        header.setHeightInPoints(20);
        setText(header, 0, "Concepto", headerStyle);
        setText(header, 1, "Valor", headerStyle);

        int rowIndex = 4;

        // Ventas completadas
        rowIndex = addSummaryRow(sheet, rowIndex, "Ventas completadas", String.valueOf(count), labelStyle, null);

        // Medios de pago (cada uno con su total y cantidad)
        for (PaymentMethod method : PaymentMethod.values()) {
            BigDecimal methodTotal = paymentTotals.getOrDefault(method, BigDecimal.ZERO);
            Long methodCount = paymentCounts.getOrDefault(method, 0L);

            rowIndex = addSummaryRow(sheet, rowIndex, paymentLabel(method) + " (total)",
                    formatMoney(methodTotal), labelStyle, moneyStyle);

            rowIndex = addSummaryRow(sheet, rowIndex, paymentLabel(method) + " (cantidad de ventas)",
                    String.valueOf(methodCount), labelStyle, null);
        }

        rowIndex = addSummaryRow(sheet, rowIndex, "Total de productos vendidos (unidades)",
                String.valueOf(totalUnits), labelStyle, null);

        // Fila de total general destacada
        Row totalRow = sheet.createRow(rowIndex);
        totalRow.setHeightInPoints(20);
        Cell totalLabel = totalRow.createCell(0);
        totalLabel.setCellValue("Suma total de las ventas");
        totalLabel.setCellStyle(totalStyle);
        Cell totalValue = totalRow.createCell(1);
        totalValue.setCellValue(formatMoney(total));
        totalValue.setCellStyle(totalStyle);

        sheet.setColumnWidth(0, 46 * 256);
        sheet.setColumnWidth(1, 22 * 256);
    }

    /* ========================= PRODUCTOS ========================= */

    private void writeProductsSheet(Sheet sheet, List<ProductRow> products, long totalUnits, BigDecimal total) {
        Workbook wb = sheet.getWorkbook();
        CellStyle headerStyle = headerStyle(wb);
        CellStyle moneyStyle = moneyStyle(wb);
        CellStyle labelStyle = labelStyle(wb);
        CellStyle totalStyle = totalStyle(wb);
        CellStyle zebraStyle = zebraStyle(wb);
        CellStyle zebraMoneyStyle = zebraMoneyStyle(wb);

        Row header = sheet.createRow(0);
        header.setHeightInPoints(20);
        setText(header, 0, "Producto", headerStyle);
        setText(header, 1, "Cantidad", headerStyle);
        setText(header, 2, "Total", headerStyle);

        int rowIndex = 1;
        for (int i = 0; i < products.size(); i++) {
            ProductRow product = products.get(i);
            Row row = sheet.createRow(rowIndex++);
            boolean zebra = i % 2 == 1;
            CellStyle nameStyle = zebra ? zebraStyle : labelStyle;
            CellStyle qtyStyle = zebra ? zebraStyle : labelStyle;
            CellStyle monStyle = zebra ? zebraMoneyStyle : moneyStyle;

            setText(row, 0, product.name(), nameStyle);
            setNumber(row, 1, product.quantity(), qtyStyle);
            setMoney(row, 2, product.revenue(), monStyle);
        }

        // Fila de total destacada
        Row footer = sheet.createRow(rowIndex);
        footer.setHeightInPoints(20);
        Cell totalLabel = footer.createCell(0);
        totalLabel.setCellValue("TOTAL");
        totalLabel.setCellStyle(totalStyle);
        Cell totalQty = footer.createCell(1);
        totalQty.setCellValue(totalUnits);
        totalQty.setCellStyle(totalStyle);
        Cell totalRev = footer.createCell(2);
        totalRev.setCellValue(formatMoney(total));
        totalRev.setCellStyle(totalStyle);

        sheet.setColumnWidth(0, 52 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 20 * 256);

        // Congelar encabezado
        sheet.createFreezePane(0, 1);
    }

    /* ========================= DETALLE DE VENTAS ========================= */

    private void writeSalesSheet(Sheet sheet, List<Sale> sales, BigDecimal total) {
        Workbook wb = sheet.getWorkbook();
        CellStyle headerStyle = headerStyle(wb);
        CellStyle labelStyle = labelStyle(wb);
        CellStyle moneyStyle = moneyStyle(wb);
        CellStyle totalStyle = totalStyle(wb);
        CellStyle zebraStyle = zebraStyle(wb);
        CellStyle zebraMoneyStyle = zebraMoneyStyle(wb);

        String[] columns = {
                "Número", "Fecha", "Hora", "Cajero", "Método de pago", "Total", "Estado", "Productos"
        };

        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < columns.length; i++) {
            setText(header, i, columns[i], headerStyle);
        }

        List<Sale> chronological = new ArrayList<>(sales);
        chronological.sort(Comparator.comparing(Sale::getCreatedAt));

        int rowIndex = 1;
        for (int i = 0; i < chronological.size(); i++) {
            Sale sale = chronological.get(i);
            LocalDateTime dateTime = LocalDateTime.ofInstant(sale.getCreatedAt(), SaleService.BUSINESS_ZONE);
            String productsText = sale.getItems().stream()
                    .map(item -> item.getQuantity() + " x " + item.getProductName())
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("");

            Row row = sheet.createRow(rowIndex++);
            boolean zebra = i % 2 == 1;
            CellStyle baseStyle = zebra ? zebraStyle : labelStyle;
            CellStyle moneyBaseStyle = zebra ? zebraMoneyStyle : moneyStyle;

            setText(row, 0, sale.getNumber(), baseStyle);
            setText(row, 1, DATE_DISPLAY_FORMAT.format(dateTime.toLocalDate()), baseStyle);
            setText(row, 2, dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), baseStyle);
            setText(row, 3, sale.getCashier() != null ? sale.getCashier().getFullName() : "", baseStyle);
            setText(row, 4, paymentLabel(sale.getPaymentMethod()), baseStyle);
            setMoney(row, 5, sale.getTotal(), moneyBaseStyle);
            setText(row, 6, sale.getStatus() != null ? saleStatusLabel(sale.getStatus()) : "", baseStyle);
            // Los productos van con ajuste de texto para que entren dentro de la fila.
            setText(row, 7, productsText, wrapStyle(wb));
            row.setHeightInPoints(15 + (productsText.split("\\|").length - 1) * 12);
        }

        // Fila de total destacada
        Row footer = sheet.createRow(rowIndex);
        footer.setHeightInPoints(20);
        Cell totalLabel = footer.createCell(0);
        totalLabel.setCellValue("TOTAL");
        totalLabel.setCellStyle(totalStyle);
        Cell totalValue = footer.createCell(5);
        totalValue.setCellValue(formatMoney(total));
        totalValue.setCellStyle(totalStyle);

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 10 * 256);
        sheet.setColumnWidth(3, 20 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 12 * 256);
        sheet.setColumnWidth(7, 60 * 256);

        // Congelar encabezado
        sheet.createFreezePane(0, 1);
    }

    /* ========================= ESTILOS ========================= */

    private int addSummaryRow(Sheet sheet, int rowIndex, String label, String textValue,
                              CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(18);
        setText(row, 0, label, labelStyle);
        Cell value = row.createCell(1);
        value.setCellStyle(valueStyle);
        if (textValue != null) {
            value.setCellValue(textValue);
        }
        return rowIndex + 1;
    }

    private void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void setNumber(Row row, int column, long value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void setMoney(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(formatMoney(value));
        } else {
            cell.setCellValue("$0");
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * Formatea un monto en estilo chileno: $32.780 (punto como separador de miles, sin decimales).
     */
    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "$0";
        }
        return "$" + String.format("%,d", value.longValue()).replace(",", ".");
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        font.setColor(COLOR_TITLE_TEXT.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(COLOR_TITLE_BG.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle subtitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(COLOR_PRIMARY.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle labelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle zebraStyle(Workbook workbook) {
        CellStyle style = labelStyle(workbook);
        style.setFillForegroundColor(COLOR_ZEBRA.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle zebraMoneyStyle(Workbook workbook) {
        CellStyle style = moneyStyle(workbook);
        style.setFillForegroundColor(COLOR_ZEBRA.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle wrapStyle(Workbook workbook) {
        CellStyle style = labelStyle(workbook);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle boldLabelStyle(Workbook workbook) {
        CellStyle style = labelStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("\"$\"#,##0"));
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle totalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex()); // letra negra sobre fondo claro para buen contraste
        style.setFont(font);
        style.setFillForegroundColor(COLOR_TOTAL_BG.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    static String paymentLabel(PaymentMethod method) {
        if (method == null) {
            return "";
        }
        return switch (method) {
            case CASH -> "Efectivo";
            case DEBIT -> "Débito";
            case CREDIT -> "Crédito";
            case TRANSFER -> "Transferencia";
        };
    }

    private String saleStatusLabel(cl.ipfsoftware.backend.domain.SaleStatus status) {
        return switch (status) {
            case COMPLETED -> "Completado";
            case VOIDED -> "Anulado";
        };
    }

    private record ProductRow(String name, long quantity, BigDecimal revenue) {
    }
}
