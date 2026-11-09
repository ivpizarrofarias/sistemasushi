package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.User;

public record UserDto(
        Long id,
        String username,
        String fullName,
        String role,
        boolean active
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive()
        );
    }
}
