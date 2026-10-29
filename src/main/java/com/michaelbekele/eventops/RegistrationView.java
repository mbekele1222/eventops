package com.michaelbekele.eventops;

public record RegistrationView(
        Long registrationId,
        Long eventId,
        String eventTitle,
        RegistrationStatus status,
        boolean checkedIn
) {
}
