package cl.ipfsoftware.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReportDto(
        LocalDate from,
        LocalDate to,
        BigDecimal total,
        long count,
        long totalUnits,
        BigDecimal cashTotal,
        BigDecimal cardTotal,
        BigDecimal transferTotal,
        List<PaymentBreakdownDto> payments,
        List<TopProductDto> topProducts
) {
}
