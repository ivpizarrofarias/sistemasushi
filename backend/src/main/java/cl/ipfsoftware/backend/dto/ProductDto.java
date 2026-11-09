package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.Product;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        int minStock,
        boolean active,
        Long categoryId,
        String categoryName,
        String categoryColor
) {
    public static ProductDto from(Product product) {
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getMinStock(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getColor()
        );
    }
}
