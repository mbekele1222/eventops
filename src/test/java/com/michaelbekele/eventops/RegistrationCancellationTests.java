package com.michaelbekele.eventops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationCancellationTests {

    private final RegistrationTokenRequest request =
            new RegistrationTokenRequest("A".repeat(43));

    private EventRepository events;
    private RegistrationRepository registrations;
    private RegistrationService service;
    private Event event;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        registrations = mock(RegistrationRepository.class);
        service = new RegistrationService(events, registrations);
        event = mock(Event.class);
        when(event.getId()).thenReturn(7L);
        when(event.getTitle()).thenReturn("Cancellation Test");
    }

    private Registration registration(RegistrationStatus status) {
        return new Registration(event, "Test Attendee", "test@example.com",
                status, "0".repeat(64));
    }

    private void prepareCancellation(Registration target) {
        when(registrations.findEventIdByTokenHash(anyString()))
                .thenReturn(Optional.of(7L));
        when(events.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(registrations.findForCancellation(eq(7L), anyString()))
                .thenReturn(Optional.of(target));
    }

    @Test
    void confirmedCancellationPromotesTheSelectedWaiter() {
        Registration target = registration(RegistrationStatus.CONFIRMED);
        Registration waiter = registration(RegistrationStatus.WAITLISTED);
        prepareCancellation(target);
        when(registrations.findOldestWaitlistedForUpdate(7L))
                .thenReturn(Optional.of(waiter));

        RegistrationView result = service.cancelRegistration(request);

        assertEquals(RegistrationStatus.CANCELLED, result.status());
        assertEquals(RegistrationStatus.CANCELLED, target.getStatus());
        assertEquals(RegistrationStatus.CONFIRMED, waiter.getStatus());

        // A fresh registration read must happen AFTER acquiring the event
        // lock, so a repeated/concurrent cancellation sees committed status.
        InOrder order = inOrder(events, registrations);
        order.verify(registrations).findEventIdByTokenHash(anyString());
        order.verify(events).findByIdForUpdate(7L);
        order.verify(registrations).findForCancellation(eq(7L), anyString());
    }

    @Test
    void waitlistedCancellationDoesNotPromoteAnyone() {
        Registration target = registration(RegistrationStatus.WAITLISTED);
        prepareCancellation(target);

        RegistrationView result = service.cancelRegistration(request);

        assertEquals(RegistrationStatus.CANCELLED, result.status());
        verify(registrations, never()).findOldestWaitlistedForUpdate(anyLong());
    }

    @Test
    void repeatingCancellationDoesNotPromoteASecondWaiter() {
        Registration target = registration(RegistrationStatus.CONFIRMED);
        Registration firstWaiter = registration(RegistrationStatus.WAITLISTED);
        Registration secondWaiter = registration(RegistrationStatus.WAITLISTED);
        prepareCancellation(target);
        when(registrations.findOldestWaitlistedForUpdate(7L))
                .thenReturn(Optional.of(firstWaiter))
                .thenReturn(Optional.of(secondWaiter));

        service.cancelRegistration(request);
        RegistrationView repeated = service.cancelRegistration(request);

        assertEquals(RegistrationStatus.CANCELLED, repeated.status());
        assertEquals(RegistrationStatus.CONFIRMED, firstWaiter.getStatus());
        assertEquals(RegistrationStatus.WAITLISTED, secondWaiter.getStatus());
        verify(registrations, times(1)).findOldestWaitlistedForUpdate(7L);
    }

    @Test
    void checkedInRegistrationCannotBeCancelled() {
        Registration target = spy(registration(RegistrationStatus.CONFIRMED));
        doReturn(true).when(target).isCheckedIn();
        prepareCancellation(target);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancelRegistration(request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(RegistrationStatus.CONFIRMED, target.getStatus());
        verify(target, never()).cancel();
        verify(registrations, never()).findOldestWaitlistedForUpdate(anyLong());
    }

    @Test
    void unknownCodeReturnsNotFoundBeforeLockingAnEvent() {
        when(registrations.findEventIdByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cancelRegistration(request));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(events, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void cancellationWithAnEmptyWaitlistStillSucceeds() {
        Registration target = registration(RegistrationStatus.CONFIRMED);
        prepareCancellation(target);
        when(registrations.findOldestWaitlistedForUpdate(7L))
                .thenReturn(Optional.empty());

        RegistrationView result = service.cancelRegistration(request);

        assertEquals(RegistrationStatus.CANCELLED, result.status());
    }

    @Test
    void aConfirmedRegistrationCannotBePromotedAgain() {
        Registration target = registration(RegistrationStatus.CONFIRMED);

        assertThrows(IllegalStateException.class, target::confirmFromWaitlist);
        assertEquals(RegistrationStatus.CONFIRMED, target.getStatus());
    }
}
