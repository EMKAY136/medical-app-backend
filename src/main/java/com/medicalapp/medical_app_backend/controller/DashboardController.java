package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> dashboardData = dashboardService.getDashboardData(userDetails);
            return ResponseEntity.ok(dashboardData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching dashboard data: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> dashboardData = dashboardService.getDashboardData(userDetails);
            return ResponseEntity.ok(dashboardData.get("statistics"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching stats: " + e.getMessage());
        }
    }
}