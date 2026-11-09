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
 * - Ventas: detalle de cada venta del período (ventas del día incluido)
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SaleRepository saleRepository;

    public ReportExportService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    public String excelFileName(LocalDate from, LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now(SaleService.BUSINESS_ZONE) : from;
        LocalDate endDate = to == null ? startDate : to;
        if (startDate.equals(endDate)) {
            return "ventas-del-dia-" + startDate + ".xlsx";
        }
        return "reporte-ventas-" + startDate + "_a_" + endDate + ".xlsx";
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

            Sheet productsSheet = workbook.createSheet("Productos");
            writeProductsSheet(productsSheet, products, totalUnits, total);

            Sheet salesSheet = workbook.createSheet("Ventas");
            writeSalesSheet(salesSheet, sales, total);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel del reporte", ex);
        }
    }

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
        CellStyle titleStyle = titleStyle(sheet.getWorkbook());
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        CellStyle labelStyle = labelStyle(sheet.getWorkbook());
        CellStyle boldStyle = boldLabelStyle(sheet.getWorkbook());
        CellStyle moneyStyle = moneyStyle(sheet.getWorkbook());

        Row title = sheet.createRow(0);
        setText(title, 0, "Reporte de ventas", titleStyle);
        Row period = sheet.createRow(1);
        String periodText = from.equals(to)
                ? "Ventas del día: " + from
                : "Período: " + from + " a " + to;
        setText(period, 0, periodText, labelStyle);

        Row header = sheet.createRow(3);
        setText(header, 0, "Concepto", headerStyle);
        setText(header, 1, "Valor", headerStyle);

        int rowIndex = 4;
        rowIndex = addSummaryRow(sheet, rowIndex, "Ventas completadas", String.valueOf(count), labelStyle, null);

        for (PaymentMethod method : PaymentMethod.values()) {
            rowIndex = addSummaryRow(sheet, rowIndex, paymentLabel(method), null, labelStyle, moneyStyle);
            sheet.getRow(rowIndex - 1).getCell(1)
                    .setCellValue(paymentTotals.getOrDefault(method, BigDecimal.ZERO).doubleValue());
            rowIndex = addSummaryRow(sheet, rowIndex, paymentLabel(method) + " (cantidad de ventas)",
                    String.valueOf(paymentCounts.getOrDefault(method, 0L)), labelStyle, null);
        }

        rowIndex = addSummaryRow(sheet, rowIndex, "Total de productos vendidos (unidades)",
                String.valueOf(totalUnits), labelStyle, null);
        rowIndex = addSummaryRow(sheet, rowIndex, "Suma total de las ventas", null, boldStyle, moneyStyle);
        sheet.getRow(rowIndex - 1).getCell(1).setCellValue(total.doubleValue());

        sheet.setColumnWidth(0, 42 * 256);
        sheet.setColumnWidth(1, 22 * 256);
    }

    private void writeProductsSheet(Sheet sheet, List<ProductRow> products, long totalUnits, BigDecimal total) {
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        CellStyle moneyStyle = moneyStyle(sheet.getWorkbook());
        CellStyle boldStyle = boldLabelStyle(sheet.getWorkbook());

        Row header = sheet.createRow(0);
        setText(header, 0, "Producto", headerStyle);
        setText(header, 1, "Cantidad", headerStyle);
        setText(header, 2, "Total", headerStyle);

        int rowIndex = 1;
        for (ProductRow product : products) {
            Row row = sheet.createRow(rowIndex++);
            setText(row, 0, product.name(), null);
            setNumber(row, 1, product.quantity());
            setMoney(row, 2, product.revenue(), moneyStyle);
        }

        Row footer = sheet.createRow(rowIndex);
        setText(footer, 0, "TOTAL", boldStyle);
        setNumber(footer, 1, totalUnits);
        setMoney(footer, 2, total, moneyStyle);

        sheet.setColumnWidth(0, 48 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 20 * 256);
    }

    private void writeSalesSheet(Sheet sheet, List<Sale> sales, BigDecimal total) {
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        CellStyle moneyStyle = moneyStyle(sheet.getWorkbook());
        CellStyle boldStyle = boldLabelStyle(sheet.getWorkbook());

        Row header = sheet.createRow(0);
        setText(header, 0, "Número", headerStyle);
        setText(header, 1, "Fecha", headerStyle);
        setText(header, 2, "Hora", headerStyle);
        setText(header, 3, "Cajero", headerStyle);
        setText(header, 4, "Método de pago", headerStyle);
        setText(header, 5, "Total", headerStyle);
        setText(header, 6, "Estado", headerStyle);
        setText(header, 7, "Productos", headerStyle);

        List<Sale> chronological = new ArrayList<>(sales);
        chronological.sort(Comparator.comparing(Sale::getCreatedAt));
        int rowIndex = 1;
        for (Sale sale : chronological) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(sale.getCreatedAt(), SaleService.BUSINESS_ZONE);
            String productsText = sale.getItems().stream()
                    .map(item -> item.getQuantity() + " x " + item.getProductName())
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("");
            Row row = sheet.createRow(rowIndex++);
            setText(row, 0, sale.getNumber(), null);
            setText(row, 1, dateTime.toLocalDate().toString(), null);
            setText(row, 2, dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), null);
            setText(row, 3, sale.getCashier() != null ? sale.getCashier().getFullName() : "", null);
            setText(row, 4, paymentLabel(sale.getPaymentMethod()), null);
            setMoney(row, 5, sale.getTotal(), moneyStyle);
            setText(row, 6, sale.getStatus() != null ? sale.getStatus().name() : "", null);
            setText(row, 7, productsText, null);
        }

        Row footer = sheet.createRow(rowIndex);
        setText(footer, 0, "TOTAL", boldStyle);
        setMoney(footer, 5, total, moneyStyle);

        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 22 * 256);
        sheet.setColumnWidth(4, 18 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 14 * 256);
        sheet.setColumnWidth(7, 60 * 256);
    }

    private int addSummaryRow(Sheet sheet, int rowIndex, String label, String textValue,
                              CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
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

    private void setNumber(Row row, int column, long value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
    }

    private void setMoney(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(
                org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
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

    private record ProductRow(String name, long quantity, BigDecimal revenue) {
    }
}
