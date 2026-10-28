package com.michaelbekele.eventops;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(
        name = "cancellation_token_hash",
        nullable = false,
        length = 64,
        updatable = false
    )
    private String cancellationTokenHash;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Registration() {
    }

    public Registration(
            Event event,
            String fullName,
            String email,
            RegistrationStatus status,
            String cancellationTokenHash) {
        this.event = event;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
        this.cancellationTokenHash = cancellationTokenHash;
    }

    @PrePersist
    private void setCreationTime() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
