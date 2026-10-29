package com.michaelbekele.eventops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface EventRepository extends JpaRepository<Event, Long> {

    String ORGANIZER_SUMMARY_SQL = """
        SELECT e.id AS "eventId", e.title AS "title",
               e.starts_at AS "startsAt", e.location AS "location",
               e.capacity AS "capacity",
               COUNT(r.id) FILTER (WHERE r.status = 'CONFIRMED') AS "confirmedCount",
               COUNT(r.id) FILTER (WHERE r.status = 'WAITLISTED') AS "waitlistedCount",
               COUNT(r.id) FILTER (WHERE r.status = 'CANCELLED') AS "cancelledCount",
               COUNT(r.id) FILTER (WHERE r.checked_in) AS "checkedInCount"
        FROM events e
        LEFT JOIN registrations r ON r.event_id = e.id
        """;

    interface OrganizerSummaryRow {
        Long getEventId();
        String getTitle();
        Instant getStartsAt();
        String getLocation();
        int getCapacity();
        long getConfirmedCount();
        long getWaitlistedCount();
        long getCancelledCount();
        long getCheckedInCount();
    }

    @Query(value = ORGANIZER_SUMMARY_SQL + """
        GROUP BY e.id
        ORDER BY e.starts_at, e.id
        """, nativeQuery = true)
    List<OrganizerSummaryRow> findOrganizerSummaries();

    @Query(value = ORGANIZER_SUMMARY_SQL + """
        WHERE e.id = :eventId
        GROUP BY e.id
        """, nativeQuery = true)
    Optional<OrganizerSummaryRow> findOrganizerSummary(
            @Param("eventId") Long eventId
    );

    @Query(value = """
        SELECT id, title, description, starts_at,
               location, capacity, created_at
        FROM events
        ORDER BY starts_at, id
        """, nativeQuery = true)
    List<Event> findAllChronologically();

    @Query(value = """
        SELECT id, title, description, starts_at,
               location, capacity, created_at
        FROM events
        WHERE id = :id
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
