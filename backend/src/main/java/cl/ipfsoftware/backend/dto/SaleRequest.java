package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SaleRequest(
        @NotEmpty(message = "La venta debe tener al menos un producto")
        List<@NotNull(message = "El producto es obligatorio") @Valid SaleItemRequest> items,
        @NotNull(message = "El método de pago es obligatorio") PaymentMethod paymentMethod,
        @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo") BigDecimal discount,
        BigDecimal amountReceived
) {
}
