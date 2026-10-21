package com.example.minhaj.config;

import com.example.minhaj.services.RateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;
    private final SecurityProblemHandler securityProblemHandler;
    private final RateLimitFilter rateLimitFilter;

    private final List<String> allowedOrigins;

    public SecurityConfig(JwtRequestFilter jwtRequestFilter, SecurityProblemHandler securityProblemHandler,
                          RateLimiter rateLimiter,
                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtRequestFilter = jwtRequestFilter;
        this.securityProblemHandler = securityProblemHandler;
        this.rateLimitFilter = new RateLimitFilter(rateLimiter, resolver);
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must list at least one origin");
        }
        if (this.allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must not contain wildcards ('*'); "
                            + "list the exact origins instead, e.g. https://app.example.com");
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/courses/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityProblemHandler).accessDeniedHandler(securityProblemHandler))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                // After CORS (so a 429 still carries CORS headers), before any authentication work
                .addFilterBefore(rateLimitFilter, JwtRequestFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // The JWT travels in the Authorization header, not in cookies.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}