package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 50, message = "El usuario no puede superar 50 caracteres")
        String username,
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String password,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String fullName,
        @NotNull(message = "El rol es obligatorio") Role role,
        Boolean active
) {
}
