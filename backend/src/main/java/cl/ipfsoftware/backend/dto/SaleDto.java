package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.Sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleDto(
        Long id,
        String number,
        Instant createdAt,
        String cashierName,
        String paymentMethod,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal amountReceived,
        BigDecimal changeAmount,
        BigDecimal netAmount,
        BigDecimal ivaAmount,
        List<SaleItemDto> items
) {
    private static final BigDecimal DIVISOR = new BigDecimal("1.19");

    public static SaleDto from(Sale sale) {
        BigDecimal total = sale.getTotal();
        BigDecimal netAmount = total == null
                ? BigDecimal.ZERO
                : total.divide(DIVISOR, 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal ivaAmount = total == null
                ? BigDecimal.ZERO
                : total.subtract(netAmount);

        return new SaleDto(
                sale.getId(),
                sale.getNumber(),
                sale.getCreatedAt(),
                sale.getCashier().getFullName(),
                sale.getPaymentMethod().name(),
                sale.getStatus().name(),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTotal(),
                sale.getAmountReceived(),
                sale.getChangeAmount(),
                netAmount,
                ivaAmount,
                sale.getItems().stream().map(SaleItemDto::from).toList()
        );
    }
}
