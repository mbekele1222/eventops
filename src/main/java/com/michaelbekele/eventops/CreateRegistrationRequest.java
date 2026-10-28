package com.michaelbekele.eventops;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateRegistrationRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email
) {
    public CreateRegistrationRequest {
        if (fullName != null) {
            fullName = fullName.trim();
        }

        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
