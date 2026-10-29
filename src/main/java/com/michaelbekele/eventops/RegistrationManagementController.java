package com.michaelbekele.eventops;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationManagementController {

    private final RegistrationService registrationService;

    public RegistrationManagementController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/status")
    public ResponseEntity<RegistrationView> getStatus(
            @Valid @RequestBody RegistrationTokenRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(registrationService.getRegistration(request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<RegistrationView> cancel(
            @Valid @RequestBody RegistrationTokenRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(registrationService.cancelRegistration(request));
    }
}
