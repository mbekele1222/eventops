package com.michaelbekele.eventops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Uses the configured PostgreSQL database. Each fixture is rolled back.
@SpringBootTest(properties = "EVENTOPS_ORGANIZER_PASSWORD=eventops-test-password")
@Transactional
class OrganizerRepositoryTests {

    private static final OffsetDateTime STARTS_AT =
            OffsetDateTime.parse("2026-10-31T22:00:00Z");

    @Autowired
    private EventRepository events;

    @Autowired
    private RegistrationRepository registrations;

    @Autowired
    private OrganizerService organizer;

    private Event event() {
        return events.saveAndFlush(new Event(
                "Organizer repository test", "Rollback fixture", STARTS_AT,
                "Demo Room", 2
        ));
    }

    private Registration attendee(Event event, RegistrationStatus status,
                                  boolean checkedIn) {
        Registration attendee = new Registration(
                event, "Repository Attendee", UUID.randomUUID() + "@example.com",
                status, UUID.randomUUID().toString().replace("-", "").repeat(2)
        );
        if (checkedIn) attendee.checkIn();
        return registrations.saveAndFlush(attendee);
    }

    @Test
    void emptyEventHasZeroCountsAndItsTimestampCanBeProjected() {
        Event event = event();

        OrganizerEventDetail detail = organizer.getEvent(event.getId());

        assertEquals(STARTS_AT, detail.event().startsAt());
        assertEquals(0, detail.event().confirmedCount());
        assertEquals(0, detail.event().waitlistedCount());
        assertEquals(0, detail.event().cancelledCount());
        assertEquals(0, detail.event().checkedInCount());
        assertEquals(2, detail.event().availableSeats());
        assertTrue(detail.registrations().isEmpty());
        assertTrue(organizer.listEvents().stream()
                .anyMatch(summary -> summary.eventId().equals(event.getId())));
    }

    @Test
    void summaryCountsAndRosterIncludeEveryStatusInSignupOrder() {
        Event event = event();
        Registration confirmed = attendee(event, RegistrationStatus.CONFIRMED, false);
        Registration arrived = attendee(event, RegistrationStatus.CONFIRMED, true);
        Registration waiter = attendee(event, RegistrationStatus.WAITLISTED, false);
        Registration cancelled = attendee(event, RegistrationStatus.CANCELLED, false);

        OrganizerEventDetail detail = organizer.getEvent(event.getId());

        assertEquals(2, detail.event().confirmedCount());
        assertEquals(1, detail.event().waitlistedCount());
        assertEquals(1, detail.event().cancelledCount());
        assertEquals(1, detail.event().checkedInCount());
        assertEquals(0, detail.event().availableSeats());
        assertEquals(List.of(confirmed.getId(), arrived.getId(), waiter.getId(), cancelled.getId()),
                detail.registrations().stream().map(OrganizerAttendee::registrationId).toList());
        assertTrue(detail.registrations().get(1).checkedIn());
        assertEquals(RegistrationStatus.WAITLISTED, detail.registrations().get(2).status());
        assertNotNull(detail.registrations().get(0).createdAt());
    }

    @Test
    void checkInQueryCannotMatchARegistrationFromAnotherEvent() {
        Event first = event();
        Event second = event();
        Registration attendee = attendee(second, RegistrationStatus.CONFIRMED, false);

        assertTrue(registrations.findForCheckIn(first.getId(), attendee.getId()).isEmpty());
        assertTrue(registrations.findForCheckIn(second.getId(), attendee.getId()).isPresent());
        assertTrue(organizer.getEvent(first.getId()).registrations().isEmpty());
    }
}
