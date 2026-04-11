package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.dto.AppointmentDto;
import com.medicalapp.medical_app_backend.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import com.medicalapp.medical_app_backend.model.AppointmentNotification;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    // ── NEW ──────────────────────────────────────────────────────────────────
    /**
     * POST /api/appointments/create
     *
     * Called from mobile bookings.jsx after the patient selects a payment method.
     * Accepts payment fields: paymentStatus, paymentMethod, price.
     *
     * Body example:
     * {
     *   "patientId":     1,
     *   "testType":      "FBC (FULL BLOOD COUNT)",
     *   "scheduledDate": "2025-06-20",
     *   "scheduledTime": "09:30",
     *   "price":         "₦7,000.00",
     *   "paymentStatus": "PENDING_CONFIRMATION",   // or "PAY_ON_ARRIVAL" / "UNPAID"
     *   "paymentMethod": "PAY_NOW"                 // or "PAY_ON_ARRIVAL"
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createAppointmentFromMobile(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required"));
            }

            Map<String, Object> response = appointmentService.createAppointmentFromMobile(requestBody, userDetails);

            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error creating appointment: " + e.getMessage()
            ));
        }
    }

    // ── Existing: Create appointment (old DTO path) ───────────────────────────
    @PostMapping
    public ResponseEntity<?> createAppointment(
            @Valid @RequestBody AppointmentDto appointmentDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.createAppointment(appointmentDto, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating appointment: " + e.getMessage());
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────
    @MessageMapping("/admin/appointment")
    @SendTo("/topic/admin/appointments")
    public AppointmentNotification notifyAdminAppointment(AppointmentNotification notification) {
        return notification;
    }

    // ── Get all appointments for current user ─────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getUserAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUserAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching appointments: " + e.getMessage());
        }
    }

    // ── Upcoming only ─────────────────────────────────────────────────────────
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUpcomingAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching upcoming appointments: " + e.getMessage());
        }
    }

    // ── Update appointment ────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentDto appointmentDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.updateAppointment(id, appointmentDto, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating appointment: " + e.getMessage());
        }
    }

    // ── Cancel appointment ────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.cancelAppointment(id, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error cancelling appointment: " + e.getMessage());
        }
    }

    // ── Status-only patch ─────────────────────────────────────────────────────
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            AppointmentDto dto = new AppointmentDto();
            dto.setStatus(status);
            Map<String, Object> response = appointmentService.updateAppointment(id, dto, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating appointment status: " + e.getMessage());
        }
    }
}