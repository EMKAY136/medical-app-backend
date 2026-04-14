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

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

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
     *   "paymentStatus": "PENDING_CONFIRMATION",
     *   "paymentMethod": "PAY_NOW"
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createAppointmentFromMobile(
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null)
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required"));

            Map<String, Object> response = appointmentService.createAppointmentFromMobile(requestBody, userDetails);
            return (Boolean) response.get("success")
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error creating appointment: " + e.getMessage()));
        }
    }

    /**
     * POST /api/appointments
     * Legacy DTO path — kept for backwards compatibility.
     */
    @PostMapping
    public ResponseEntity<?> createAppointment(
            @Valid @RequestBody AppointmentDto appointmentDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.createAppointment(appointmentDto, userDetails);
            return (Boolean) response.get("success")
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating appointment: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebSocket
    // ─────────────────────────────────────────────────────────────────────────

    @MessageMapping("/admin/appointment")
    @SendTo("/topic/admin/appointments")
    public AppointmentNotification notifyAdminAppointment(AppointmentNotification notification) {
        return notification;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/appointments — all appointments for the current user */
    @GetMapping
    public ResponseEntity<?> getUserAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUserAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching appointments: " + e.getMessage());
        }
    }

    /** GET /api/appointments/upcoming */
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUpcomingAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching upcoming appointments: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /** PUT /api/appointments/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentDto appointmentDto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.updateAppointment(id, appointmentDto, userDetails);
            return (Boolean) response.get("success")
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating appointment: " + e.getMessage());
        }
    }

    /** PATCH /api/appointments/{id}/status */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            AppointmentDto dto = new AppointmentDto();
            dto.setStatus(status);
            Map<String, Object> response = appointmentService.updateAppointment(id, dto, userDetails);
            return (Boolean) response.get("success")
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating appointment status: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCEL
    // ─────────────────────────────────────────────────────────────────────────

    /** DELETE /api/appointments/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = appointmentService.cancelAppointment(id, userDetails);
            return (Boolean) response.get("success")
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error cancelling appointment: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK MISSED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/appointments/{id}/mark-missed
     * Called from the mobile app when the appointment date passes.
     */
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

    // ─────────────────────────────────────────────────────────────────────────
    // REFUND
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/appointments/{id}/request-refund
     * Patient submits a refund request.
     *
     * Body: { "reason": "..." }
     */
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

    /**
     * PATCH /api/appointments/{id}/process-refund
     * Admin approves or rejects a refund.
     *
     * Body: { "action": "APPROVED"|"REJECTED" }
     */
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

            if (action.equals("APPROVED"))
                apt.setPaymentStatus(Appointment.PaymentStatus.UNPAID);

            appointmentRepository.save(apt);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Refund " + action.toLowerCase() + " successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESCHEDULE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/appointments/{id}/request-reschedule
     * Patient submits a reschedule request for a MISSED or SCHEDULED appointment.
     *
     * Body:
     * {
     *   "reason":        "I can't make it that day",
     *   "preferredDate": "2025-07-10",
     *   "preferredTime": "10:00"
     * }
     */
    @PatchMapping("/{id}/request-reschedule")
    public ResponseEntity<?> requestReschedule(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Optional<Appointment> aptOpt = appointmentRepository.findById(id);
            if (aptOpt.isEmpty())
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Appointment not found"));

            Appointment apt = aptOpt.get();

            // Only MISSED or SCHEDULED appointments may be rescheduled
            if (apt.getStatus() != Appointment.Status.MISSED &&
                apt.getStatus() != Appointment.Status.SCHEDULED)
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "Only missed or scheduled appointments can be rescheduled"));

            // Prevent duplicate requests
            if (apt.getRescheduleStatus() == Appointment.RescheduleStatus.REQUESTED ||
                apt.getRescheduleStatus() == Appointment.RescheduleStatus.APPROVED)
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "A reschedule request already exists for this appointment"));

            apt.setRescheduleStatus(Appointment.RescheduleStatus.REQUESTED);
            apt.setRescheduleReason(body.getOrDefault("reason", "Patient requested reschedule"));
            apt.setReschedulePreferredDate(body.getOrDefault("preferredDate", null));
            apt.setReschedulePreferredTime(body.getOrDefault("preferredTime", null));
            apt.setRescheduleRequestedAt(java.time.LocalDateTime.now());
            apt.setUpdatedAt(java.time.LocalDateTime.now());
            appointmentRepository.save(apt);

            return ResponseEntity.ok(Map.of("success", true, "message", "Reschedule request submitted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/appointments/{id}/process-reschedule
     * Admin approves or rejects a reschedule request.
     * If approved, the appointment date/time is updated and status reset to SCHEDULED.
     *
     * Body:
     * {
     *   "action":  "APPROVED"|"REJECTED",
     *   "newDate": "2025-07-10",
     *   "newTime": "10:00"
     * }
     */
    @PatchMapping("/{id}/process-reschedule")
    public ResponseEntity<?> processReschedule(
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

            apt.setRescheduleStatus(action.equals("APPROVED")
                    ? Appointment.RescheduleStatus.APPROVED
                    : Appointment.RescheduleStatus.REJECTED);
            apt.setRescheduleApprovedAt(java.time.LocalDateTime.now());
            apt.setRescheduleApprovedBy(userDetails.getUsername());
            apt.setUpdatedAt(java.time.LocalDateTime.now());

            if (action.equals("APPROVED")) {
                String newDate = body.getOrDefault("newDate", null);
                String newTime = body.getOrDefault("newTime", null);
                if (newDate != null && newTime != null) {
                    java.time.LocalDate ld = java.time.LocalDate.parse(newDate);
                    java.time.LocalTime lt = java.time.LocalTime.parse(newTime);
                    apt.setScheduledDate(ld);
                    apt.setScheduledTime(lt);
                    apt.setAppointmentDate(java.time.LocalDateTime.of(ld, lt));
                }
                // Reset to SCHEDULED with the new date/time
                apt.setStatus(Appointment.Status.SCHEDULED);
            }

            appointmentRepository.save(apt);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Reschedule request " + action.toLowerCase() + " successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}