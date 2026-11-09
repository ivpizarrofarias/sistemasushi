package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.StockMovement;

import java.time.Instant;

public record StockMovementDto(
        Long id,
        Long productId,
        String productName,
        String type,
        int quantity,
        int previousStock,
        int newStock,
        String reason,
        String userName,
        Instant createdAt
) {
    public static StockMovementDto from(StockMovement movement) {
        return new StockMovementDto(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getName(),
                movement.getType().name(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getReason(),
                movement.getUser() != null ? movement.getUser().getFullName() : null,
                movement.getCreatedAt()
        );
    }
}
