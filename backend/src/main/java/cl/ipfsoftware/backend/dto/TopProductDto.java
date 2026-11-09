package cl.ipfsoftware.backend.dto;

import java.math.BigDecimal;

public record TopProductDto(
        Long productId,
        String name,
        long quantity,
        BigDecimal revenue
) {
}
