package com.michaelbekele.eventops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrganizerController.class,
        properties = "EVENTOPS_ORGANIZER_PASSWORD=dashboard-test-password")
@Import(SecurityConfig.class)
class OrganizerSecurityTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrganizerService organizer;

    @Test
    void anonymousVisitorCannotReadOrganizerSummaries() throws Exception {
        mvc.perform(get("/api/organizer/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(organizer);
    }

    @Test
    void anonymousVisitorCannotReadAttendeeDetails() throws Exception {
        mvc.perform(get("/api/organizer/events/7"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(organizer);
    }

    @Test
    void signedInUserWithoutOrganizerRoleCannotReadAttendeeDetails() throws Exception {
        mvc.perform(get("/api/organizer/events/7")
                        .with(user("guest").roles("ATTENDEE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(organizer);
    }

    @Test
    void organizerCanReadSummariesWithoutCachingThem() throws Exception {
        when(organizer.listEvents()).thenReturn(List.of());

        mvc.perform(get("/api/organizer/events")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().json("[]"));
    }

    @Test
    void organizerCheckInRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/organizer/events/7/registrations/42/check-in")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(organizer);
    }

    @Test
    void csrfTokenDoesNotGrantAttendeesOrganizerAccess() throws Exception {
        mvc.perform(post("/api/organizer/events/7/registrations/42/check-in")
                        .with(user("guest").roles("ATTENDEE")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(organizer);
    }

    @Test
    void organizerCanCheckInWithoutReceivingPrivateManagementCodes() throws Exception {
        when(organizer.checkIn(7L, 42L)).thenReturn(new OrganizerAttendee(
                42L, "Alex Test", "alex@example.com", RegistrationStatus.CONFIRMED,
                true, OffsetDateTime.parse("2026-10-29T12:00:00Z")
        ));

        mvc.perform(post("/api/organizer/events/7/registrations/42/check-in")
                        .with(user("organizer").roles("ORGANIZER")).with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(true))
                .andExpect(jsonPath("$.registrationId").value(42))
                .andExpect(jsonPath("$.cancellationToken").doesNotExist())
                .andExpect(jsonPath("$.cancellationTokenHash").doesNotExist());
    }

    @Test
    void dashboardRequiresOrganizerLogin() throws Exception {
        mvc.perform(get("/dashboard.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void organizerCanOpenTheDashboard() throws Exception {
        mvc.perform(get("/dashboard.html").with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Organizer dashboard")));
    }
}
