package cl.ipfsoftware.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OpenCashRequest(
        @NotNull(message = "El monto inicial es obligatorio")
        @DecimalMin(value = "0.0", message = "El monto inicial no puede ser negativo")
        BigDecimal openingAmount
) {
}
