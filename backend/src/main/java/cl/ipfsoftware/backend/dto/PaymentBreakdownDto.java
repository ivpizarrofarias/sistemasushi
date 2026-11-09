package cl.ipfsoftware.backend.dto;

import java.math.BigDecimal;

public record PaymentBreakdownDto(
        String method,
        BigDecimal total,
        long count
) {
}
