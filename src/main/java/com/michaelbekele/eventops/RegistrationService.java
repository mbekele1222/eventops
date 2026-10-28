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
