// src/main/java/com/medicalapp/medical_app_backend/controller/HomeController.java

package com.medicalapp.medical_app_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "message", "Medical App Backend is running!",
            "status", "online",
            "timestamp", LocalDateTime.now(),
            "version", "1.0.0",
            "documentation", "Visit /api/support/status for system status"
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "Medical App Backend",
            "timestamp", LocalDateTime.now()
        );
    }
}
