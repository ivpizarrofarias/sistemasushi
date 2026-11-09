package cl.ipfsoftware.backend.dto;

import java.util.List;

public record ApiError(
        String message,
        List<String> details
) {
    public ApiError(String message) {
        this(message, List.of());
    }
}
