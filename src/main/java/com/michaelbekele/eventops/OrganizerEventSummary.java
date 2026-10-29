package com.michaelbekele.eventops;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record OrganizerEventSummary(
        Long eventId,
        String title,
        OffsetDateTime startsAt,
        String location,
        int capacity,
        long confirmedCount,
        long waitlistedCount,
        long cancelledCount,
        long checkedInCount,
        long availableSeats) {

    static OrganizerEventSummary from(EventRepository.OrganizerSummaryRow row) {
        return new OrganizerEventSummary(
                row.getEventId(), row.getTitle(), row.getStartsAt().atOffset(ZoneOffset.UTC),
                row.getLocation(), row.getCapacity(), row.getConfirmedCount(),
                row.getWaitlistedCount(), row.getCancelledCount(),
                row.getCheckedInCount(),
                Math.max(0L, row.getCapacity() - row.getConfirmedCount())
        );
    }
}
