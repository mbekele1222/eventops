package com.michaelbekele.eventops;

public record RegistrationReceipt(
        Long registrationId,
        Long eventId,
        String eventTitle,
        RegistrationStatus status,
        String cancellationToken
) {
}
