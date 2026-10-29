package com.michaelbekele.eventops;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Attendee details are returned only by organizer endpoints. Private
// management codes and their hashes are never part of this response.
public record OrganizerAttendee(
        Long registrationId,
        String fullName,
        String email,
        RegistrationStatus status,
        boolean checkedIn,
        OffsetDateTime createdAt) {

    static OrganizerAttendee from(RegistrationRepository.OrganizerAttendeeRow row) {
        return new OrganizerAttendee(
                row.getRegistrationId(), row.getFullName(), row.getEmail(),
                RegistrationStatus.valueOf(row.getStatus()), row.getCheckedIn(),
                row.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    static OrganizerAttendee from(Registration registration) {
        return new OrganizerAttendee(
                registration.getId(), registration.getFullName(),
                registration.getEmail(), registration.getStatus(),
                registration.isCheckedIn(), registration.getCreatedAt()
        );
    }
}
