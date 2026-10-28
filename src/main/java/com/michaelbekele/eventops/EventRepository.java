package com.michaelbekele.eventops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query(value = """
            SELECT id, title, description, starts_at,
                   location, capacity, created_at
            FROM events
            ORDER BY starts_at, id
            """, nativeQuery = true)
    List<Event> findAllChronologically();
}
