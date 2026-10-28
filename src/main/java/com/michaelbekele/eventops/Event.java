package com.michaelbekele.eventops;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Event() {
    }

    public Event(String title, String description, OffsetDateTime startsAt,
                 String location, int capacity) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.location = location;
        this.capacity = capacity;
    }

    @PrePersist
    private void setCreationTime() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getLocation() { return location; }
    public int getCapacity() { return capacity; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
