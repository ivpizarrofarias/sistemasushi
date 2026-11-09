package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.SaleItem;

import java.math.BigDecimal;

public record SaleItemDto(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
    public static SaleItemDto from(SaleItem item) {
        return new SaleItemDto(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
