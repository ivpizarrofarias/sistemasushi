package cl.ipfsoftware.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String name,
        @Size(max = 20, message = "El color no puede superar 20 caracteres")
        String color
) {
}
