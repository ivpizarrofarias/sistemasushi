package cl.ipfsoftware.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashSessionDto(
        Long id,
        String status,
        Instant openedAt,
        Instant closedAt,
        String openedByName,
        String closedByName,
        BigDecimal openingAmount,
        BigDecimal closingAmount,
        BigDecimal expectedAmount,
        BigDecimal difference,
        BigDecimal cashSales,
        BigDecimal totalSales,
        long salesCount,
        String notes
) {
}
