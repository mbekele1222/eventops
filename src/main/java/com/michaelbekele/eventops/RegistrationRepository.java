package com.michaelbekele.eventops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RegistrationRepository
        extends JpaRepository<Registration, Long> {

    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM registrations
            WHERE event_id = :eventId
              AND email = :email
              AND status IN ('CONFIRMED', 'WAITLISTED')
        )
        """, nativeQuery = true)
    boolean existsActiveRegistration(
            @Param("eventId") Long eventId,
            @Param("email") String email
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM registrations
        WHERE event_id = :eventId
          AND status = 'CONFIRMED'
        """, nativeQuery = true)
    long countConfirmedRegistrations(
            @Param("eventId") Long eventId
    );

    Optional<Registration> findByCancellationTokenHash(String tokenHash);

    @Query(value = """
        SELECT event_id
        FROM registrations
        WHERE cancellation_token_hash = :tokenHash
        """, nativeQuery = true)
    Optional<Long> findEventIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Query(value = """
        SELECT id, event_id, full_name, email, status,
               cancellation_token_hash, checked_in, created_at
        FROM registrations
        WHERE event_id = :eventId
          AND cancellation_token_hash = :tokenHash
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Registration> findForCancellation(
            @Param("eventId") Long eventId,
            @Param("tokenHash") String tokenHash
    );

    @Query(value = """
        SELECT id, event_id, full_name, email, status,
               cancellation_token_hash, checked_in, created_at
        FROM registrations
        WHERE event_id = :eventId
          AND status = 'WAITLISTED'
        ORDER BY id
        LIMIT 1
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Registration> findOldestWaitlistedForUpdate(
            @Param("eventId") Long eventId
    );
}
