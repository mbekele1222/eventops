package com.michaelbekele.eventops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizerCheckInTests {

    private EventRepository events;
    private RegistrationRepository registrations;
    private OrganizerService organizer;
    private Event event;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        registrations = mock(RegistrationRepository.class);
        organizer = new OrganizerService(events, registrations);
        event = mock(Event.class);
    }

    private Registration attendee(RegistrationStatus status) {
        Registration attendee = new Registration(event, "Alex Test", "alex@example.com",
                status, "0".repeat(64));
        when(events.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(registrations.findForCheckIn(7L, 42L)).thenReturn(Optional.of(attendee));
        return attendee;
    }

    @Test
    void confirmedAttendeeChecksInAfterTheEventLock() {
        Registration attendee = attendee(RegistrationStatus.CONFIRMED);

        OrganizerAttendee result = organizer.checkIn(7L, 42L);

        assertTrue(result.checkedIn());
        assertTrue(attendee.isCheckedIn());
        assertEquals(RegistrationStatus.CONFIRMED, attendee.getStatus());
        InOrder order = inOrder(events, registrations);
        order.verify(events).findByIdForUpdate(7L);
        order.verify(registrations).findForCheckIn(7L, 42L);
        order.verify(registrations).flush();
    }

    @Test
    void repeatingCheckInLeavesOneConfirmedAttendanceRecord() {
        Registration attendee = attendee(RegistrationStatus.CONFIRMED);

        organizer.checkIn(7L, 42L);
        OrganizerAttendee repeated = organizer.checkIn(7L, 42L);

        assertTrue(repeated.checkedIn());
        assertEquals(RegistrationStatus.CONFIRMED, attendee.getStatus());
        verify(registrations, never()).save(any());
        verify(registrations, never()).findOldestWaitlistedForUpdate(anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"WAITLISTED", "CANCELLED"})
    void inactiveOrWaitlistedAttendeeCannotCheckIn(RegistrationStatus status) {
        Registration attendee = attendee(status);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> organizer.checkIn(7L, 42L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertFalse(attendee.isCheckedIn());
        assertEquals(status, attendee.getStatus());
        verify(registrations, never()).flush();
    }

    @Test
    void missingEventReturnsNotFoundWithoutReadingAnAttendee() {
        when(events.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> organizer.checkIn(7L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(registrations);
    }

    @Test
    void registrationMustBelongToTheRequestedEvent() {
        when(events.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(registrations.findForCheckIn(7L, 42L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> organizer.checkIn(7L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(registrations, never()).findById(anyLong());
        verify(registrations, never()).flush();
    }

    @Test
    void checkedInAttendeeCannotReleaseASeatThroughCancellation() {
        Registration attendee = attendee(RegistrationStatus.CONFIRMED);
        organizer.checkIn(7L, 42L);
        RegistrationService registrationService = new RegistrationService(events, registrations);
        when(event.getId()).thenReturn(7L);
        when(registrations.findEventIdByTokenHash(anyString())).thenReturn(Optional.of(7L));
        when(registrations.findForCancellation(eq(7L), anyString()))
                .thenReturn(Optional.of(attendee));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> registrationService.cancelRegistration(
                        new RegistrationTokenRequest("A".repeat(43))));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(attendee.isCheckedIn());
        assertEquals(RegistrationStatus.CONFIRMED, attendee.getStatus());
        verify(registrations, never()).findOldestWaitlistedForUpdate(anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"WAITLISTED", "CANCELLED"})
    void entityAlsoRejectsInvalidAttendance(RegistrationStatus status) {
        Registration attendee = new Registration(event, "Alex", "alex@example.com",
                status, "0".repeat(64));

        assertThrows(IllegalStateException.class, attendee::checkIn);
        assertFalse(attendee.isCheckedIn());
    }
}
