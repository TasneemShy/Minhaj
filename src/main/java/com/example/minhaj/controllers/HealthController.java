package com.example.minhaj.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "Liveness check. Detailed health is at /actuator/health.")
public class HealthController {

    // Simple liveness alias kept for the README and Postman collection.
    // Real health checks (database, liveness/readiness probes) live at /actuator/health.
    @Operation(summary = "Liveness check", description = "Public. Plain-text confirmation that the app is running.")
    @ApiResponse(responseCode = "200", description = "Running",
            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Minhaj Backend is Running perfectly")))
    @GetMapping("/health")
    public String checkHealth() {
        return "Minhaj Backend is Running perfectly";
    }
}