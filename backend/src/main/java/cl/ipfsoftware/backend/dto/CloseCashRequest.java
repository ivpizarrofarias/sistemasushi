package cl.ipfsoftware.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashRequest(
        @NotNull(message = "El monto de cierre es obligatorio")
        @DecimalMin(value = "0.0", message = "El monto de cierre no puede ser negativo")
        BigDecimal closingAmount,
        String notes
) {
}
