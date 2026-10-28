package com.michaelbekele.eventops;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record CreateEventRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 5000) String description,
        @NotNull OffsetDateTime startsAt,
        @NotBlank @Size(max = 200) String location,
        @NotNull @Positive Integer capacity
) {
}
