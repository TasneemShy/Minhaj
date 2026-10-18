package com.example.minhaj.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    // Simple liveness alias kept for the README and Postman collection.
    // Real health checks (database, liveness/readiness probes) live at /actuator/health.
    @GetMapping("/health")
    public String checkHealth() {
        return "Minhaj Backend is Running perfectly";
    }
}