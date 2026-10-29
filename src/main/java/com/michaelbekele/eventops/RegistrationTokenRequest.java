package com.michaelbekele.eventops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrationTokenRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        String cancellationToken
) {
    public RegistrationTokenRequest {
        if (cancellationToken != null) {
            cancellationToken = cancellationToken.trim();
        }
    }
}
