package cl.ipfsoftware.backend.dto;

import cl.ipfsoftware.backend.domain.Category;

public record CategoryDto(
        Long id,
        String name,
        String color,
        boolean active
) {
    public static CategoryDto from(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getColor(), category.isActive());
    }
}
