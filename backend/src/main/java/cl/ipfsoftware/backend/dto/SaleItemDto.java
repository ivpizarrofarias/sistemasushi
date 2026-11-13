package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.SaleItem;

import java.math.BigDecimal;

public record SaleItemDto(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        BigDecimal netAmount,
        BigDecimal ivaAmount
) {
    private static final BigDecimal IVA_RATE = new BigDecimal("0.19");
    private static final BigDecimal DIVISOR = new BigDecimal("1.19");

    public static SaleItemDto from(SaleItem item) {
        BigDecimal lineTotal = item.getLineTotal();
        BigDecimal netAmount = lineTotal == null
                ? BigDecimal.ZERO
                : lineTotal.divide(DIVISOR, 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal ivaAmount = lineTotal == null
                ? BigDecimal.ZERO
                : lineTotal.subtract(netAmount);

        return new SaleItemDto(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                lineTotal,
                netAmount,
                ivaAmount
        );
    }
}
