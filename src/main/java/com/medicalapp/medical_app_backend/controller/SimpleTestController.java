package com.medicalapp.medical_app_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/test")  
public class SimpleTestController {
    
    @GetMapping("/hello")
    public ResponseEntity<Map<String, Object>> hello() {
        return ResponseEntity.ok(Map.of(
            "message", "Hello World", 
            "success", true,
            "timestamp", LocalDateTime.now().toString()
        ));
    }
    
    @GetMapping("/plain")
    public String plainText() {
        return "Hello World - Plain Text";
    }
}