package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.AdminService;
import com.medicalapp.medical_app_backend.service.AppointmentService;
import com.medicalapp.medical_app_backend.service.AutoNotificationService;
import com.medicalapp.medical_app_backend.service.NotificationService;
import com.medicalapp.medical_app_backend.service.TestResultService;
import com.medicalapp.medical_app_backend.dto.*;
import com.medicalapp.medical_app_backend.entity.AutoNotification;
import com.medicalapp.medical_app_backend.entity.Notification;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.AutoNotificationRepository;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import com.medicalapp.medical_app_backend.repository.NotificationRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.TestResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired private AdminService adminService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AutoNotificationRepository autoNotificationRepository;
    @Autowired private AutoNotificationService autoNotificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;   // ← NEW
    @Autowired private TestResultService testResultService;

    // ==================== DASHBOARD ====================

    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== ADMIN STATS REQUEST === Admin: {}", userDetails != null ? userDetails.getUsername() : "null");
            if (userDetails == null) return unauth();
            Map<String, Object> stats = adminService.getDashboardStats(userDetails);
            return ResponseEntity.ok(Map.of("success", true, "data", stats));
        } catch (Exception e) {
            logger.error("Error getting dashboard stats: {}", e.getMessage());
            return error500("Error loading dashboard statistics");
        }
    }

    // ==================== PATIENTS ====================

    @GetMapping("/patients")
    public ResponseEntity<?> getAllPatients(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> data = adminService.getAllPatients(userDetails, page, size, search);
            return ResponseEntity.ok(Map.of("success", true,
                    "patients", data.get("patients"),
                    "totalCount", data.get("totalCount"),
                    "currentPage", page));
        } catch (Exception e) {
            logger.error("Error getting patients: {}", e.getMessage());
            return error500("Error loading patients data");
        }
    }

    @GetMapping("/patients/{patientId}")
    public ResponseEntity<?> getPatientDetails(
            @PathVariable Long patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> data = adminService.getPatientDetails(patientId, userDetails);
            return ResponseEntity.ok(Map.of("success", true,
                    "patient", data.get("patient"),
                    "medicalHistory", data.get("medicalHistory"),
                    "testResults", data.get("testResults"),
                    "appointments", data.get("appointments")));
        } catch (Exception e) {
            logger.error("Error getting patient details: {}", e.getMessage());
            return error500("Error loading patient details");
        }
    }

    // ==================== APPOINTMENTS ====================

    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> data = adminService.getAllAppointments(userDetails, date, status, page, size);
            return ResponseEntity.ok(Map.of("success", true,
                    "appointments", data.get("appointments"),
                    "totalCount", data.get("totalCount"),
                    "currentPage", page));
        } catch (Exception e) {
            logger.error("Error getting appointments: {}", e.getMessage());
            return error500("Error loading appointments");
        }
    }

    // ── NEW: Appointments awaiting payment confirmation ────────────────────
    /**
     * Returns every appointment where the patient tapped "I Have Paid"
     * and is waiting for the admin to verify the bank transfer.
     * Admin frontend polls / displays these in a dedicated "Pending Payments" tab.
     */
    @GetMapping("/appointments/pending-payment")
    public ResponseEntity<?> getPendingPaymentAppointments(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== PENDING PAYMENT APPOINTMENTS === Admin: {}",
                    userDetails != null ? userDetails.getUsername() : "null");
            if (userDetails == null) return unauth();

            List<Appointment> pending =
                    appointmentRepository.findByPaymentStatus(Appointment.PaymentStatus.PENDING_CONFIRMATION);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Appointment apt : pending) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",            apt.getId());
                row.put("patientId",     apt.getPatient().getId());
                row.put("patientName",   apt.getPatient().getFirstName() + " " + apt.getPatient().getLastName());
                row.put("patientPhone",  apt.getPatient().getPhone() != null ? apt.getPatient().getPhone() : "");
                row.put("testType",      apt.getTestType() != null ? apt.getTestType() : apt.getReason());
                row.put("price",         apt.getPrice());
                row.put("paymentStatus", apt.getPaymentStatus().name());
                row.put("paymentMethod", apt.getPaymentMethod());
                row.put("scheduledDate", apt.getScheduledDate());
                row.put("scheduledTime", apt.getScheduledTime());
                row.put("appointmentDate", apt.getAppointmentDate());
                row.put("status",        apt.getStatus().name());
                row.put("createdAt",     apt.getCreatedAt());
                result.add(row);
            }

            logger.info("✅ Found {} appointments pending payment confirmation", result.size());
            return ResponseEntity.ok(Map.of("success", true, "appointments", result, "count", result.size()));

        } catch (Exception e) {
            logger.error("Error fetching pending payment appointments: {}", e.getMessage());
            return error500(e.getMessage());
        }
    }

    // ── NEW: Approve payment ───────────────────────────────────────────────
    /**
     * Admin confirms they received the Sterling Bank transfer.
     * Sets paymentStatus = PAID and fires a push + in-app notification to the patient.
     *
     * PATCH /api/admin/appointments/{id}/approve-payment
     */
    @PatchMapping("/appointments/{appointmentId}/approve-payment")
    public ResponseEntity<?> approvePayment(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== APPROVE PAYMENT === Appointment: {}, Admin: {}",
                    appointmentId, userDetails != null ? userDetails.getUsername() : "null");
            if (userDetails == null) return unauth();

            Optional<Appointment> aptOpt = appointmentRepository.findById(appointmentId);
            if (aptOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Appointment not found"));

            Appointment appointment = aptOpt.get();

            // Guard: only approve appointments that are actually pending
            if (appointment.getPaymentStatus() != Appointment.PaymentStatus.PENDING_CONFIRMATION) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Appointment is not awaiting payment confirmation. Current: "
                                + appointment.getPaymentStatus()));
            }

            appointment.setPaymentStatus(Appointment.PaymentStatus.PAID);
            appointment.setPaymentApprovedAt(LocalDateTime.now());
            appointment.setPaymentApprovedBy(userDetails.getUsername());
            appointment.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

            // Fire notification to patient
            autoNotificationService.onPaymentApproved(appointment);

            logger.info("✅ Payment approved for appointment {}", appointmentId);
            return ResponseEntity.ok(Map.of(
                    "success",       true,
                    "message",       "Payment approved — patient has been notified",
                    "appointmentId", appointmentId,
                    "paymentStatus", "PAID",
                    "approvedBy",    userDetails.getUsername(),
                    "approvedAt",    LocalDateTime.now().toString()));

        } catch (Exception e) {
            logger.error("Error approving payment: {}", e.getMessage());
            return error500(e.getMessage());
        }
    }

    // ── NEW: Mark appointment as missed ────────────────────────────────────
    /**
     * Called by:
     *   - The mobile app automatically (when the date passes)
     *   - The admin manually via the admin panel
     *
     * PATCH /api/admin/appointments/{id}/mark-missed
     * Also reachable at /api/appointments/{id}/mark-missed from the mobile (add to AppointmentController too).
     */
    @PatchMapping("/appointments/{appointmentId}/mark-missed")
    public ResponseEntity<?> markAppointmentMissed(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== MARK MISSED === Appointment: {}", appointmentId);
            // Note: we allow this even without admin token because the mobile app calls it;
            // the mobile request still carries the patient JWT which passes the security filter.

            Optional<Appointment> aptOpt = appointmentRepository.findById(appointmentId);
            if (aptOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Appointment not found"));

            Appointment appointment = aptOpt.get();

            // Idempotent: if already missed/completed just return success
            if (appointment.getStatus() != Appointment.Status.SCHEDULED) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Appointment already in status: " + appointment.getStatus(),
                        "status",  appointment.getStatus().name()));
            }

            // Admin can always mark an appointment missed — no date guard
            LocalDateTime now = LocalDateTime.now();

            appointment.setStatus(Appointment.Status.MISSED);
            appointment.setUpdatedAt(now);
            appointmentRepository.save(appointment);

            // Notify the patient
            autoNotificationService.onAppointmentMissed(appointment);

            logger.info("✅ Appointment {} marked as MISSED", appointmentId);
            return ResponseEntity.ok(Map.of(
                    "success",       true,
                    "message",       "Appointment marked as missed",
                    "appointmentId", appointmentId,
                    "status",        "MISSED"));

        } catch (Exception e) {
            logger.error("Error marking appointment as missed: {}", e.getMessage());
            return error500(e.getMessage());
        }
    }

    // ── Refund requests ───────────────────────────────────────────────────────

    @GetMapping("/refund-requests")
public ResponseEntity<?> getRefundRequests(@AuthenticationPrincipal UserDetails userDetails) {
    try {
        if (userDetails == null) return unauth();
        // Fetch ALL refund statuses, not just REQUESTED
        List<Appointment> list = appointmentRepository.findByRefundStatusIn(
            List.of(Appointment.RefundStatus.REQUESTED, 
                    Appointment.RefundStatus.APPROVED, 
                    Appointment.RefundStatus.REFUNDED)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Appointment apt : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id",            apt.getId());
            row.put("appointmentId", apt.getId());
            row.put("patientId",     apt.getPatient().getId());
            row.put("patientName",   apt.getPatient().getFirstName() + " " + apt.getPatient().getLastName());
            row.put("testType",      apt.getTestType() != null ? apt.getTestType() : apt.getReason());
            row.put("testName",      apt.getTestType() != null ? apt.getTestType() : apt.getReason());
            row.put("amount",        apt.getPrice());
            row.put("price",         apt.getPrice());
            row.put("reason",        apt.getRefundReason());
            // Use actual refund status instead of hardcoding "PENDING"
            row.put("status",        apt.getRefundStatus().name());
            row.put("refundStatus",  apt.getRefundStatus().name());
            row.put("requestedAt",   apt.getRefundRequestedAt());
            row.put("approvedAt",    apt.getRefundApprovedAt());
            row.put("approvedBy",    apt.getRefundApprovedBy());
            row.put("paymentStatus", apt.getPaymentStatus() != null ? apt.getPaymentStatus().name() : "PAID");
            row.put("appointmentDate", apt.getAppointmentDate());
            row.put("createdAt",     apt.getCreatedAt());
            result.add(row);
        }
        return ResponseEntity.ok(Map.of("success", true, "refundRequests", result, "count", result.size()));
    } catch (Exception e) {
        logger.error("Error fetching refund requests: {}", e.getMessage());
        return error500(e.getMessage());
    }
}


    @PostMapping("/refund-requests/{id}/approve")
    public ResponseEntity<?> approveRefundRequest(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Optional<Appointment> aptOpt = appointmentRepository.findById(id);
            if (aptOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not found"));
            Appointment apt = aptOpt.get();
            apt.setRefundStatus(Appointment.RefundStatus.APPROVED);
            apt.setRefundApprovedAt(LocalDateTime.now());
            apt.setRefundApprovedBy(userDetails.getUsername());
            apt.setPaymentStatus(Appointment.PaymentStatus.UNPAID);
            apt.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(apt);
            try { autoNotificationService.sendManualNotification(apt.getPatient().getId(), "Refund Approved", "Your refund has been approved.", "payment"); } catch (Exception ignored) {}
            return ResponseEntity.ok(Map.of("success", true, "message", "Refund approved"));
        } catch (Exception e) { return error500(e.getMessage()); }
    }

    @PostMapping("/refund-requests/{id}/mark-refunded")
public ResponseEntity<?> markRefundCompleted(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
    try {
        if (userDetails == null) return unauth();
        Optional<Appointment> aptOpt = appointmentRepository.findById(id);
        if (aptOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not found"));
        Appointment apt = aptOpt.get();
        apt.setRefundStatus(Appointment.RefundStatus.REFUNDED);
        apt.setUpdatedAt(LocalDateTime.now());
        appointmentRepository.save(apt);
        return ResponseEntity.ok(Map.of("success", true, "message", "Refund marked as completed"));
    } catch (Exception e) { return error500(e.getMessage()); }
}


    @PostMapping("/refund-requests/{id}/decline")
    public ResponseEntity<?> declineRefundRequest(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Optional<Appointment> aptOpt = appointmentRepository.findById(id);
            if (aptOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not found"));
            Appointment apt = aptOpt.get();
            apt.setRefundStatus(Appointment.RefundStatus.REJECTED);
            apt.setRefundApprovedAt(LocalDateTime.now());
            apt.setRefundApprovedBy(userDetails.getUsername());
            apt.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(apt);
            try { autoNotificationService.sendManualNotification(apt.getPatient().getId(), "Refund Update", "Your refund request was not approved.", "payment"); } catch (Exception ignored) {}
            return ResponseEntity.ok(Map.of("success", true, "message", "Refund declined"));
        } catch (Exception e) { return error500(e.getMessage()); }
    }

    // ── Reschedule requests ───────────────────────────────────────────────────

    @GetMapping("/reschedule-requests")
    public ResponseEntity<?> getRescheduleRequests(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            List<Appointment> list = appointmentRepository.findByRescheduleStatus(Appointment.RescheduleStatus.REQUESTED);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Appointment apt : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",                     apt.getId());
                row.put("appointmentId",           apt.getId());
                row.put("patientId",               apt.getPatient().getId());
                row.put("patientName",             apt.getPatient().getFirstName() + " " + apt.getPatient().getLastName());
                row.put("testType",                apt.getTestType() != null ? apt.getTestType() : apt.getReason());
                row.put("testName",                apt.getTestType() != null ? apt.getTestType() : apt.getReason());
                row.put("originalDate",            apt.getAppointmentDate());
                row.put("appointmentDate",         apt.getAppointmentDate());
                row.put("status",                  apt.getStatus().name());
                row.put("rescheduleStatus",        apt.getRescheduleStatus().name());
                row.put("reason",                  apt.getRescheduleReason());
                row.put("requestedDate",           apt.getReschedulePreferredDate());
                row.put("reschedulePreferredDate", apt.getReschedulePreferredDate());
                row.put("reschedulePreferredTime", apt.getReschedulePreferredTime());
                row.put("rescheduleRequestedAt",   apt.getRescheduleRequestedAt());
                row.put("price",                   apt.getPrice());
                row.put("paymentStatus",           apt.getPaymentStatus() != null ? apt.getPaymentStatus().name() : "UNPAID");
                row.put("createdAt",               apt.getCreatedAt());
                result.add(row);
            }
            return ResponseEntity.ok(Map.of("success", true, "rescheduleRequests", result, "count", result.size()));
        } catch (Exception e) {
            logger.error("Error fetching reschedule requests: {}", e.getMessage());
            return error500(e.getMessage());
        }
    }

    @PatchMapping("/reschedule-requests/{id}/approve")
    public ResponseEntity<?> approveRescheduleRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Optional<Appointment> aptOpt = appointmentRepository.findById(id);
            if (aptOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not found"));
            Appointment apt = aptOpt.get();
            apt.setRescheduleStatus(Appointment.RescheduleStatus.APPROVED);
            apt.setRescheduleApprovedAt(LocalDateTime.now());
            apt.setRescheduleApprovedBy(userDetails.getUsername());
            String newDate = body.get("newDate");
            String newTime = body.get("newTime");
            if (newDate != null && newTime != null) {
                java.time.LocalDate ld = java.time.LocalDate.parse(newDate);
                java.time.LocalTime lt = java.time.LocalTime.parse(newTime);
                apt.setScheduledDate(ld);
                apt.setScheduledTime(lt);
                apt.setAppointmentDate(java.time.LocalDateTime.of(ld, lt));
            }
            apt.setStatus(Appointment.Status.SCHEDULED);
            apt.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(apt);
            try { autoNotificationService.sendManualNotification(apt.getPatient().getId(), "Reschedule Confirmed", "Your appointment has been rescheduled. Please check your updated date.", "appointment"); } catch (Exception ignored) {}
            return ResponseEntity.ok(Map.of("success", true, "message", "Reschedule approved"));
        } catch (Exception e) { return error500(e.getMessage()); }
    }

    @PostMapping("/book-test")
    public ResponseEntity<?> bookTest(
            @Valid @RequestBody BookTestRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> result = adminService.bookTest(request, userDetails);
            return ResponseEntity.ok(Map.of("success", true, "message", "Test booked successfully", "appointment", result));
        } catch (Exception e) {
            logger.error("Error booking test: {}", e.getMessage());
            return error500("Error booking test: " + e.getMessage());
        }
    }

    @PutMapping("/appointments/{appointmentId}/status")
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== UPDATE STATUS === ID: {}, Status: {}, Admin: {}",
                    appointmentId, status, userDetails != null ? userDetails.getUsername() : "null");
            if (userDetails == null) return unauth();
            appointmentService.updateAppointmentStatus(appointmentId, status);
            return ResponseEntity.ok(Map.of("success", true, "message", "Appointment updated and notification sent"));
        } catch (Exception e) {
            logger.error("Error updating appointment status: {}", e.getMessage());
            return error500("Error updating appointment: " + e.getMessage());
        }
    }

    /**
     * Patient-initiated booking from the mobile app.
     * Now carries price + paymentStatus + paymentMethod from the booking flow.
     */
    @PostMapping("/book-test-with-notification")
    public ResponseEntity<?> bookTestWithNotification(
            @Valid @RequestBody BookTestRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== BOOK TEST WITH NOTIFICATION === Patient: {}, Test: {}, PaymentStatus: {}",
                    request.getPatientId(), request.getTestType(), request.getPaymentStatus());
            if (userDetails == null) return unauth();

            Optional<User> patientOpt = userRepository.findById(request.getPatientId());
            if (patientOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Patient not found"));

            Appointment appointment = new Appointment();
            appointment.setPatient(patientOpt.get());
            appointment.setReason(request.getTestType());
            appointment.setTestType(request.getTestType());
            appointment.setNotes(request.getNotes() != null ? request.getNotes() : "");
            appointment.setStatus(Appointment.Status.SCHEDULED);

            // ── Payment fields ──────────────────────────────────────────
            if (request.getPrice() != null)
                appointment.setPrice(request.getPrice());
            if (request.getPaymentStatus() != null)
                appointment.setPaymentStatus(request.getPaymentStatus());
            else
                appointment.setPaymentStatus(Appointment.PaymentStatus.UNPAID);
            if (request.getPaymentMethod() != null)
                appointment.setPaymentMethod(request.getPaymentMethod());

            // ── Date + time ─────────────────────────────────────────────
            LocalDateTime dt;
            if (request.getScheduledDate() != null && request.getScheduledTime() != null) {
                dt = LocalDateTime.of(request.getScheduledDate(), request.getScheduledTime());
            } else if (request.getScheduledDate() != null) {
                dt = request.getScheduledDate().atTime(9, 0);
                logger.warn("⚠️ No time provided, defaulting to 09:00");
            } else {
                dt = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
                logger.warn("⚠️ No date/time, defaulting to tomorrow 09:00");
            }
            appointment.setAppointmentDate(dt);
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setUpdatedAt(LocalDateTime.now());

            // bookTest also fires the "test booked" push notification
            Appointment saved = appointmentService.bookTest(appointment);

            logger.info("✅ Booked. ID: {}, Time: {}, PaymentStatus: {}",
                    saved.getId(), saved.getAppointmentDate(), saved.getPaymentStatus());

            return ResponseEntity.ok(Map.of(
                    "success",     true,
                    "message",     "Test booked successfully",
                    "appointment", Map.of(
                            "id",            saved.getId(),
                            "appointmentDate", saved.getAppointmentDate().toString(),
                            "reason",        saved.getReason() != null ? saved.getReason() : "",
                            "status",        saved.getStatus().name(),
                            "paymentStatus", saved.getPaymentStatus().name(),
                            "price",         saved.getPrice() != null ? saved.getPrice() : "")));

        } catch (Exception e) {
            logger.error("❌ Error booking test: {}", e.getMessage(), e);
            return error500("Error booking test: " + e.getMessage());
        }
    }

    @PostMapping("/schedule-appointment")
    public ResponseEntity<?> scheduleAppointment(
            @Valid @RequestBody ScheduleAppointmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> result = adminService.scheduleAppointment(request, userDetails);
            return ResponseEntity.ok(Map.of("success", true, "message", "Appointment scheduled successfully", "appointment", result));
        } catch (Exception e) {
            logger.error("Error scheduling appointment: {}", e.getMessage());
            return error500("Error scheduling appointment: " + e.getMessage());
        }
    }

    // ==================== TEST RESULTS ====================

    @GetMapping("/test-results")
    public ResponseEntity<?> getAllTestResults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String testType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> data = adminService.getAllTestResults(userDetails, status, testType, page, size);
            return ResponseEntity.ok(Map.of("success", true,
                    "results", data.get("results"),
                    "totalCount", data.get("totalCount"),
                    "currentPage", page));
        } catch (Exception e) {
            logger.error("Error getting test results: {}", e.getMessage());
            return error500("Error loading test results");
        }
    }

    @PostMapping("/add-result")
    public ResponseEntity<?> addTestResult(
            @Valid @RequestBody AddTestResultRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Optional<User> patientOpt = userRepository.findById(request.getPatientId());
            if (patientOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Patient not found"));
            TestResult testResult = new TestResult();
            testResult.setUser(patientOpt.get());
            testResult.setTestType(request.getTestType());
            testResult.setResult(request.getResult());
            testResult.setStatus(request.getStatus() != null ? request.getStatus() : "Completed");
            testResult.setNotes(request.getNotes());
            testResult.setLabName(request.getLabName());
            testResult.setDoctorComments(request.getDoctorComments());
            TestResult saved = testResultService.addTestResult(testResult);
            logger.info("✅ Test result added. ID: {}", saved.getId());
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Test result added and notification sent",
                    "resultId", saved.getId()));
        } catch (Exception e) {
            logger.error("Error adding test result: {}", e.getMessage());
            return error500("Error adding test result: " + e.getMessage());
        }
    }

    @PostMapping("/upload-result-with-file")
    public ResponseEntity<?> uploadResultWithFile(
            @RequestParam("patientId") Long patientId,
            @RequestParam("testType") String testType,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "testDate", required = false) String testDate,
            @RequestParam(value = "appointmentId", required = false) Long appointmentId,
            @RequestParam(value = "markCompleted", defaultValue = "false") boolean markCompleted,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Map<String, Object> uploadResult = adminService.uploadResultWithFile(
                    patientId, testType, result, notes, category, status,
                    doctorName, testDate, appointmentId, markCompleted, file, userDetails);
            if (uploadResult.get("success") != null && !(Boolean) uploadResult.get("success"))
                return ResponseEntity.status(400).body(uploadResult);
            return ResponseEntity.ok(uploadResult);
        } catch (Exception e) {
            logger.error("Error uploading result with file: {}", e.getMessage(), e);
            return error500("Error uploading test result: " + e.getMessage());
        }
    }

    // ==================== NOTIFICATIONS ====================

    @PostMapping("/notifications/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> payload,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            Long recipientId = ((Number) payload.get("recipientId")).longValue();
            String title   = (String) payload.get("title");
            String message = (String) payload.get("message");
            String type    = (String) payload.get("type");
            autoNotificationService.sendManualNotification(recipientId, title, message, type);
            logger.info("✅ Notification sent to patient {}", recipientId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Notification sent successfully"));
        } catch (Exception e) {
            logger.error("❌ Error sending notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/notifications/send-all")
    public ResponseEntity<?> sendNotificationToAll(@RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            autoNotificationService.sendNotificationToAll(
                    (String) payload.get("title"),
                    (String) payload.get("message"),
                    (String) payload.get("type"));
            logger.info("✅ Broadcast notification sent");
            return ResponseEntity.ok(Map.of("success", true, "message", "Broadcast notification sent"));
        } catch (Exception e) {
            logger.error("❌ Error sending broadcast: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getAllNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            List<Notification> notifications = notificationRepository.findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Notification n : notifications) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",            n.getId());
                row.put("title",         n.getTitle());
                row.put("message",       n.getMessage());
                row.put("type",          n.getType());
                row.put("recipientId",   n.getUser().getId());
                row.put("recipientName", n.getUser().getFirstName() + " " + n.getUser().getLastName());
                row.put("read",          n.isRead());
                row.put("sent",          n.isSent());
                row.put("createdAt",     n.getCreatedAt());
                list.add(row);
            }
            return ResponseEntity.ok(Map.of("success", true, "notifications", list));
        } catch (Exception e) {
            logger.error("Error getting notifications: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            notificationRepository.deleteById(id);
            logger.info("✅ Notification {} deleted", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Notification deleted"));
        } catch (Exception e) {
            logger.error("Error deleting notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== AUTO NOTIFICATIONS ====================

    @PostMapping("/auto-notifications")
    public ResponseEntity<?> createAutoNotification(@RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            AutoNotification a = new AutoNotification(
                    (String) payload.get("trigger"),
                    (String) payload.get("title"),
                    (String) payload.get("message"),
                    (String) payload.get("type"));
            a.setEnabled((Boolean) payload.getOrDefault("enabled", true));
            a.setDelayMinutes(payload.get("delayMinutes") != null ? ((Number) payload.get("delayMinutes")).intValue() : 0);
            a.setCreatedBy(userDetails.getUsername());
            autoNotificationRepository.save(a);
            logger.info("✅ Auto-notification created: {}", a.getTrigger());
            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification created successfully"));
        } catch (Exception e) {
            logger.error("❌ Error creating auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/auto-notifications")
    public ResponseEntity<?> getAllAutoNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            List<AutoNotification> all = autoNotificationRepository.findAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (AutoNotification a : all) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", a.getId()); row.put("trigger", a.getTrigger());
                row.put("title", a.getTitle()); row.put("message", a.getMessage());
                row.put("type", a.getType()); row.put("enabled", a.getEnabled());
                row.put("delayMinutes", a.getDelayMinutes());
                row.put("timesTriggered", a.getTimesTriggered());
                row.put("lastTriggered", a.getLastTriggered());
                row.put("createdAt", a.getCreatedAt());
                list.add(row);
            }
            return ResponseEntity.ok(Map.of("success", true, "autoNotifications", list));
        } catch (Exception e) {
            logger.error("Error getting auto-notifications: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/auto-notifications/{id}/toggle")
    public ResponseEntity<?> toggleAutoNotification(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            AutoNotification a = autoNotificationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Not found"));
            a.setEnabled((Boolean) payload.get("enabled"));
            autoNotificationRepository.save(a);
            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification updated"));
        } catch (Exception e) {
            logger.error("Error toggling auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/auto-notifications/{id}")
    public ResponseEntity<?> deleteAutoNotification(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            autoNotificationRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification deleted"));
        } catch (Exception e) {
            logger.error("Error deleting auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/notifications/stats")
    public ResponseEntity<?> getNotificationStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) return unauth();
            return ResponseEntity.ok(Map.of("success", true, "stats", Map.of(
                    "totalNotifications", notificationRepository.count(),
                    "totalAutoRules",     autoNotificationRepository.count(),
                    "activeAutoRules",    autoNotificationRepository.countByEnabled(true),
                    "totalTriggered",     autoNotificationRepository.findAll().stream().mapToLong(AutoNotification::getTimesTriggered).sum()
            )));
        } catch (Exception e) {
            logger.error("Error getting notification stats: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== REPORTS & SEARCH ====================

    @GetMapping("/reports")
    public ResponseEntity<?> getReports(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String reportType) {
        try {
            if (userDetails == null) return unauth();
            return ResponseEntity.ok(Map.of("success", true,
                    "reports", adminService.generateReports(userDetails, startDate, endDate, reportType)));
        } catch (Exception e) {
            logger.error("Error generating reports: {}", e.getMessage());
            return error500("Error generating reports");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> globalSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String query,
            @RequestParam(defaultValue = "all") String type) {
        try {
            if (userDetails == null) return unauth();
            return ResponseEntity.ok(Map.of("success", true,
                    "results", adminService.globalSearch(query, type, userDetails)));
        } catch (Exception e) {
            logger.error("Error performing search: {}", e.getMessage());
            return error500("Error performing search");
        }
    }

    // ==================== HELPERS ====================

    private ResponseEntity<?> unauth() {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "Admin authentication required"));
    }

    private ResponseEntity<?> error500(String msg) {
        return ResponseEntity.status(500).body(Map.of("success", false, "message", msg));
    }

    private boolean isAdmin(UserDetails userDetails) {
        try {
            Optional<User> u = userRepository.findByUsername(userDetails.getUsername());
            return u.isPresent() && u.get().getRole() == User.Role.ADMIN;
        } catch (Exception e) {
            return false;
        }
    }
}