package com.michaelbekele.eventops;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizer/events")
public class OrganizerController {

    private final OrganizerService organizer;

    public OrganizerController(OrganizerService organizer) {
        this.organizer = organizer;
    }

    @GetMapping
    public ResponseEntity<List<OrganizerEventSummary>> listEvents() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(organizer.listEvents());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<OrganizerEventDetail> getEvent(
            @PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(organizer.getEvent(eventId));
    }

    @PostMapping("/{eventId}/registrations/{registrationId}/check-in")
    public ResponseEntity<OrganizerAttendee> checkIn(
            @PathVariable("eventId") Long eventId,
            @PathVariable("registrationId") Long registrationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(organizer.checkIn(eventId, registrationId));
    }
}
