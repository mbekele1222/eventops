package com.michaelbekele.eventops;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RegistrationService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RegistrationService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationReceipt register(
            Long eventId,
            CreateRegistrationRequest request) {

        // Hold this event's row lock until the transaction finishes.
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                ));

        boolean alreadyRegistered =
                registrationRepository.existsActiveRegistration(
                        eventId,
                        request.email()
                );

        if (alreadyRegistered) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email already has an active registration"
            );
        }

        long confirmedCount =
                registrationRepository.countConfirmedRegistrations(eventId);

        RegistrationStatus status =
                confirmedCount < event.getCapacity()
                        ? RegistrationStatus.CONFIRMED
                        : RegistrationStatus.WAITLISTED;

        String cancellationToken = generateCancellationToken();

        Registration registration = new Registration(
                event,
                request.fullName(),
                request.email(),
                status,
                hashToken(cancellationToken)
        );

        Registration saved =
                registrationRepository.saveAndFlush(registration);

        return new RegistrationReceipt(
                saved.getId(),
                event.getId(),
                event.getTitle(),
                saved.getStatus(),
                cancellationToken
        );
    }

    @Transactional(readOnly = true)
    public RegistrationView getRegistration(RegistrationTokenRequest request) {
        Registration registration = registrationRepository
                .findByCancellationTokenHash(hashToken(request.cancellationToken()))
                .orElseThrow(this::registrationNotFound);

        return toView(registration);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationView cancelRegistration(RegistrationTokenRequest request) {
        String tokenHash = hashToken(request.cancellationToken());

        // Read only the event ID before locking, so no stale Registration
        // entity is cached while we wait for another transaction.
        Long eventId = registrationRepository.findEventIdByTokenHash(tokenHash)
                .orElseThrow(this::registrationNotFound);

        // Every writer locks the event first, matching the signup transaction.
        eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(this::registrationNotFound);

        Registration registration = registrationRepository
                .findForCancellation(eventId, tokenHash)
                .orElseThrow(this::registrationNotFound);

        // Repeating a cancellation must not promote another attendee.
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            return toView(registration);
        }

        if (registration.isCheckedIn()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A checked-in registration cannot be cancelled"
            );
        }

        boolean releasesSeat =
                registration.getStatus() == RegistrationStatus.CONFIRMED;

        registration.cancel();
        registrationRepository.flush();

        if (releasesSeat) {
            registrationRepository.findOldestWaitlistedForUpdate(eventId)
                    .ifPresent(Registration::confirmFromWaitlist);
        }

        registrationRepository.flush();
        return toView(registration);
    }

    private RegistrationView toView(Registration registration) {
        Event event = registration.getEvent();
        return new RegistrationView(
                registration.getId(),
                event.getId(),
                event.getTitle(),
                registration.getStatus(),
                registration.isCheckedIn()
        );
    }

    private ResponseStatusException registrationNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Registration not found"
        );
    }

    private String generateCancellationToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }
}
