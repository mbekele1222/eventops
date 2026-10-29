package com.michaelbekele.eventops;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OrganizerService {

    private final EventRepository events;
    private final RegistrationRepository registrations;

    public OrganizerService(EventRepository events,
                            RegistrationRepository registrations) {
        this.events = events;
        this.registrations = registrations;
    }

    @Transactional(readOnly = true)
    public List<OrganizerEventSummary> listEvents() {
        return events.findOrganizerSummaries().stream()
                .map(OrganizerEventSummary::from)
                .toList();
    }

    // The counts and roster use the same database snapshot, even when
    // another request registers or cancels while this page is loading.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrganizerEventDetail getEvent(Long eventId) {
        OrganizerEventSummary event = events.findOrganizerSummary(eventId)
                .map(OrganizerEventSummary::from)
                .orElseThrow(this::eventNotFound);
        List<OrganizerAttendee> attendees = registrations
                .findOrganizerAttendees(eventId).stream()
                .map(OrganizerAttendee::from)
                .toList();
        return new OrganizerEventDetail(event, attendees);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizerAttendee checkIn(Long eventId, Long registrationId) {
        // Use the same lock order as signup and cancellation. Read the
        // registration only after the event lock has been acquired.
        events.findByIdForUpdate(eventId).orElseThrow(this::eventNotFound);
        Registration attendee = registrations
                .findForCheckIn(eventId, registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Registration not found for this event"
                ));

        if (attendee.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only confirmed attendees can check in"
            );
        }

        attendee.checkIn();
        registrations.flush();
        return OrganizerAttendee.from(attendee);
    }

    private ResponseStatusException eventNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
    }
}
