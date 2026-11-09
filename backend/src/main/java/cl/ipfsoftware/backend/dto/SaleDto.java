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
        List<SaleItemDto> items
) {
    public static SaleDto from(Sale sale) {
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
                sale.getItems().stream().map(SaleItemDto::from).toList()
        );
    }
}
