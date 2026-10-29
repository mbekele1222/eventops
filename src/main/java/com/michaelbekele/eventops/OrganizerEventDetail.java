package com.michaelbekele.eventops;

import java.util.List;

public record OrganizerEventDetail(
        OrganizerEventSummary event,
        List<OrganizerAttendee> registrations) {
}
