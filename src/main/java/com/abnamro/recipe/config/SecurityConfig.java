package com.abnamro.recipe.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal HTTP Basic security for the service. The intent is deliberately small:
 * demonstrate that authentication and authorization are in place, not to provide a
 * production identity system.
 *
 * <p>The single built-in user is configured via {@code spring.security.user.*} in
 * {@code application.properties} (username {@code recipes}, roles {@code USER} and
 * {@code ADMIN}); Spring Boot's default user auto-configuration supplies it because no
 * custom {@code UserDetailsService} bean is defined here.
 *
 * <p>Per-endpoint authorization is expressed with {@code @PreAuthorize} on the controller
 * methods, enabled by {@link EnableMethodSecurity}: reads require {@code USER}, writes
 * ({@code POST}/{@code DELETE}) require {@code ADMIN}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless API consumed by non-browser clients: CSRF protection is not
                // applicable, and no HTTP session is created.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Every request must be authenticated; the concrete role required per
                // endpoint is enforced by the @PreAuthorize annotations on the controllers.
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(withDefaults())
                .build();
    }
}
