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

import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import java.util.Optional;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

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

    // ── Mark missed (called from mobile) ─────────────────────────────────────
    @PatchMapping("/{id}/mark-missed")
    public ResponseEntity<?> markMissed(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Optional<Appointment> aptOpt = appointmentRepository.findById(id);
            if (aptOpt.isEmpty())
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Appointment not found"));

            Appointment apt = aptOpt.get();

            // Idempotent
            if (apt.getStatus() == Appointment.Status.MISSED)
                return ResponseEntity.ok(Map.of("success", true, "message", "Already marked missed", "status", "MISSED"));

            apt.setStatus(Appointment.Status.MISSED);
            apt.setUpdatedAt(java.time.LocalDateTime.now());
            appointmentRepository.save(apt);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Appointment marked as missed",
                    "status",  "MISSED"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Patient requests a refund
@PatchMapping("/{id}/request-refund")
public ResponseEntity<?> requestRefund(
        @PathVariable Long id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails userDetails) {
    try {
        Optional<Appointment> aptOpt = appointmentRepository.findById(id);
        if (aptOpt.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Appointment not found"));

        Appointment apt = aptOpt.get();

        // Only allow if paid
        if (apt.getPaymentStatus() != Appointment.PaymentStatus.PAID)
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Only paid appointments can be refunded"));

        if (apt.getRefundStatus() == Appointment.RefundStatus.REQUESTED ||
            apt.getRefundStatus() == Appointment.RefundStatus.APPROVED)
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Refund already requested or approved"));

        apt.setRefundStatus(Appointment.RefundStatus.REQUESTED);
        apt.setRefundReason(body.getOrDefault("reason", "Patient requested refund"));
        apt.setRefundRequestedAt(java.time.LocalDateTime.now());
        apt.setUpdatedAt(java.time.LocalDateTime.now());
        appointmentRepository.save(apt);

        return ResponseEntity.ok(Map.of("success", true, "message", "Refund request submitted successfully"));
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", e.getMessage()));
    }
}

// Admin approves or rejects a refund
@PatchMapping("/{id}/process-refund")
public ResponseEntity<?> processRefund(
        @PathVariable Long id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails userDetails) {
    try {
        Optional<Appointment> aptOpt = appointmentRepository.findById(id);
        if (aptOpt.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Appointment not found"));

        Appointment apt = aptOpt.get();
        String action = body.getOrDefault("action", "APPROVED").toUpperCase();

        apt.setRefundStatus(action.equals("APPROVED")
                ? Appointment.RefundStatus.APPROVED
                : Appointment.RefundStatus.REJECTED);
        apt.setRefundApprovedAt(java.time.LocalDateTime.now());
        apt.setRefundApprovedBy(userDetails.getUsername());
        apt.setUpdatedAt(java.time.LocalDateTime.now());

        if (action.equals("APPROVED")) {
            apt.setPaymentStatus(Appointment.PaymentStatus.UNPAID);
        }

        appointmentRepository.save(apt);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Refund " + action.toLowerCase() + " successfully"));
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", e.getMessage()));
    }
}

}