package com.michaelbekele.eventops;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public List<Event> getEvents() {
        return eventRepository.findAllChronologically();
    }

    @GetMapping("/{id}")
    public Event getEvent(@PathVariable("id") Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found"
                ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = new Event(
                request.title().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.startsAt(),
                request.location().trim(),
                request.capacity()
        );

        return eventRepository.save(event);
    }
}
