package com.michaelbekele.eventops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
