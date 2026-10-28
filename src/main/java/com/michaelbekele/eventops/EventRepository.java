package com.michaelbekele.eventops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

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
