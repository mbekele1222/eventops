package com.michaelbekele.eventops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/index.html", "/assets/**", "/error"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET, "/api/events", "/api/events/*"
                ).permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/organizer.html").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/events").hasRole("ORGANIZER")
                .requestMatchers(
                         HttpMethod.POST,
                        "/api/events/*/registrations"
                ).permitAll()
                .anyRequest().denyAll()
        );

        http.formLogin(form -> form
                .defaultSuccessUrl("/organizer.html", true)
                .permitAll()
        );

        http.logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(
            PasswordEncoder encoder,
            @Value("${EVENTOPS_ORGANIZER_PASSWORD}") String password) {

        if (password.isBlank()) {
            throw new IllegalArgumentException(
                    "Organizer password must not be blank"
            );
        }

        return new InMemoryUserDetailsManager(
                User.withUsername("organizer")
                        .password(encoder.encode(password))
                        .roles("ORGANIZER")
                        .build()
        );
    }
}
