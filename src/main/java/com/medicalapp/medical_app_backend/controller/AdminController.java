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
@RequestMapping("/admin")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AutoNotificationRepository autoNotificationRepository;

    @Autowired
    private AutoNotificationService autoNotificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private TestResultService testResultService; // ✅ ADD THIS


    // ==================== DASHBOARD ====================

    /**
     * Get dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== ADMIN STATS REQUEST ===");
            logger.info("Admin user: {}", userDetails != null ? userDetails.getUsername() : "null");
            
            if (userDetails == null) {
                logger.error("Unauthorized access attempt to admin stats");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> stats = adminService.getDashboardStats(userDetails);
            
            logger.info("Stats loaded successfully for admin: {}", userDetails.getUsername());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", stats
            ));

        } catch (Exception e) {
            logger.error("Error getting dashboard stats: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error loading dashboard statistics"
            ));
        }
    }

    // ==================== PATIENTS ====================

    /**
     * Get all patients for admin view
     */
    @GetMapping("/patients")
    public ResponseEntity<?> getAllPatients(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            @RequestParam(required = false) String search) {
        
        try {
            logger.info("=== ADMIN PATIENTS REQUEST ===");
            logger.info("Admin user: {}", userDetails != null ? userDetails.getUsername() : "null");
            logger.info("Page: {}, Size: {}, Search: {}", page, size, search);
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> patientsData = adminService.getAllPatients(userDetails, page, size, search);
            
            logger.info("Patients loaded successfully. Count: {}", 
                       ((java.util.List<?>) patientsData.get("patients")).size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "patients", patientsData.get("patients"),
                "totalCount", patientsData.get("totalCount"),
                "currentPage", page
            ));

        } catch (Exception e) {
            logger.error("Error getting patients: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error loading patients data"
            ));
        }
    }

    /**
     * Get specific patient details by ID
     */
    @GetMapping("/patients/{patientId}")
    public ResponseEntity<?> getPatientDetails(
            @PathVariable Long patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            logger.info("=== PATIENT DETAILS REQUEST ===");
            logger.info("Patient ID: {}, Admin: {}", patientId, userDetails.getUsername());
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> patientData = adminService.getPatientDetails(patientId, userDetails);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "patient", patientData.get("patient"),
                "medicalHistory", patientData.get("medicalHistory"),
                "testResults", patientData.get("testResults"),
                "appointments", patientData.get("appointments")
            ));

        } catch (Exception e) {
            logger.error("Error getting patient details: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error loading patient details"
            ));
        }
    }

    // ==================== APPOINTMENTS ====================

    /**
     * Get all appointments for admin view
     */
    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        
        try {
            logger.info("=== ADMIN APPOINTMENTS REQUEST ===");
            logger.info("Admin: {}, Date: {}, Status: {}", userDetails.getUsername(), date, status);
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> appointmentsData = adminService.getAllAppointments(
                userDetails, date, status, page, size);
            
            logger.info("Appointments loaded: {}", 
                       ((java.util.List<?>) appointmentsData.get("appointments")).size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "appointments", appointmentsData.get("appointments"),
                "totalCount", appointmentsData.get("totalCount"),
                "currentPage", page
            ));

        } catch (Exception e) {
            logger.error("Error getting appointments: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error loading appointments"
            ));
        }
    }

    /**
     * Book a new test appointment
     */
    @PostMapping("/book-test")
    public ResponseEntity<?> bookTest(
            @Valid @RequestBody BookTestRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            logger.info("=== BOOK TEST REQUEST ===");
            logger.info("Admin: {}, Patient ID: {}, Test: {}", 
                       userDetails.getUsername(), request.getPatientId(), request.getTestType());
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> result = adminService.bookTest(request, userDetails);
            
            logger.info("Test booked successfully. Appointment ID: {}", result.get("appointmentId"));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test booked successfully",
                "appointment", result
            ));

        } catch (Exception e) {
            logger.error("Error booking test: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error booking test: " + e.getMessage()
            ));
        }
    }

    @PutMapping("/appointments/{appointmentId}/status")
public ResponseEntity<?> updateAppointmentStatus(
        @PathVariable Long appointmentId,
        @RequestParam String status,
        @AuthenticationPrincipal UserDetails userDetails) {
    
    try {
        logger.info("=== UPDATE APPOINTMENT STATUS ===");
        logger.info("Appointment ID: {}, New Status: {}, Admin: {}", 
                   appointmentId, status, userDetails.getUsername());
        
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Admin authentication required"
            ));
        }
        
        // ✅ This now automatically triggers notification
        appointmentService.updateAppointmentStatus(appointmentId, status);
        
        logger.info("✅ Appointment status updated and notification triggered automatically");
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Appointment updated and notification sent automatically"
        ));
    } catch (Exception e) {
        logger.error("Error updating appointment status: {}", e.getMessage());
        return ResponseEntity.status(500).body(Map.of(
            "success", false,
            "message", "Error updating appointment: " + e.getMessage()
        ));
    }
}

@PostMapping("/book-test-with-notification")
public ResponseEntity<?> bookTestWithNotification(
        @Valid @RequestBody BookTestRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    
    try {
        logger.info("=== BOOK TEST WITH AUTO-NOTIFICATION ===");
        logger.info("Admin: {}, Patient ID: {}, Test: {}", 
                   userDetails.getUsername(), request.getPatientId(), request.getTestType());
        logger.info("📅 Received Date: {}, Time: {}", request.getScheduledDate(), request.getScheduledTime());
        
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Admin authentication required"
            ));
        }

        // Get patient
        Optional<User> patientOpt = userRepository.findById(request.getPatientId());
        if (patientOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Patient not found"
            ));
        }

        // Create appointment
        Appointment appointment = new Appointment();
        appointment.setPatient(patientOpt.get());
        appointment.setReason(request.getTestType());
        appointment.setNotes(request.getNotes() != null ? request.getNotes() : "Test booked by admin");
        appointment.setStatus(Appointment.Status.SCHEDULED);
        
        // ✅ FIXED: Combine date and time properly
        LocalDateTime appointmentDateTime;
        if (request.getScheduledDate() != null && request.getScheduledTime() != null) {
            // Combine the date and time
            appointmentDateTime = LocalDateTime.of(request.getScheduledDate(), request.getScheduledTime());
            logger.info("✅ Combined DateTime: {}", appointmentDateTime);
        } else if (request.getScheduledDate() != null) {
            // If only date is provided, default to 9 AM
            appointmentDateTime = request.getScheduledDate().atTime(9, 0);
            logger.warn("⚠️ No time provided, defaulting to 9:00 AM");
        } else {
            // Default to tomorrow at 9 AM
            appointmentDateTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            logger.warn("⚠️ No date/time provided, defaulting to tomorrow at 9:00 AM");
        }
        
        appointment.setAppointmentDate(appointmentDateTime);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());

        // ✅ This automatically triggers "test booked" notification
        Appointment saved = appointmentService.bookTest(appointment);
        
        logger.info("✅ Test booked successfully with time: {}", saved.getAppointmentDate());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Test booked successfully and notification sent",
            "appointment", Map.of(
                "id", saved.getId(),
                "appointmentDate", saved.getAppointmentDate(),
                "reason", saved.getReason(),
                "status", saved.getStatus()
            )
        ));

    } catch (Exception e) {
        logger.error("❌ Error booking test: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of(
            "success", false,
            "message", "Error booking test: " + e.getMessage()
        ));
    }
}
/**
 * Schedule a new appointment
 */
@PostMapping("/schedule-appointment")
public ResponseEntity<?> scheduleAppointment(
            @Valid @RequestBody ScheduleAppointmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            logger.info("=== SCHEDULE APPOINTMENT REQUEST ===");
            logger.info("Admin: {}, Patient ID: {}, Type: {}", 
                       userDetails.getUsername(), request.getPatientId(), request.getAppointmentType());
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> result = adminService.scheduleAppointment(request, userDetails);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Appointment scheduled successfully",
                "appointment", result
            ));

        } catch (Exception e) {
            logger.error("Error scheduling appointment: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error scheduling appointment: " + e.getMessage()
            ));
        }
    }

    // ==================== TEST RESULTS ====================

    /**
     * Get all test results for admin view
     */
    @GetMapping("/test-results")
    public ResponseEntity<?> getAllTestResults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String testType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        
        try {
            logger.info("=== TEST RESULTS REQUEST ===");
            logger.info("Admin: {}, Status: {}, Type: {}", userDetails.getUsername(), status, testType);
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> resultsData = adminService.getAllTestResults(
                userDetails, status, testType, page, size);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "results", resultsData.get("results"),
                "totalCount", resultsData.get("totalCount"),
                "currentPage", page
            ));

        } catch (Exception e) {
            logger.error("Error getting test results: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error loading test results"
            ));
        }
    }

    /**
     * Add a new test result
     */

// Then update your addTestResult endpoint:
@PostMapping("/add-result")
public ResponseEntity<?> addTestResult(
        @Valid @RequestBody AddTestResultRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    
    try {
        logger.info("=== ADD TEST RESULT REQUEST ===");
        logger.info("Admin: {}, Patient ID: {}, Test: {}", 
                   userDetails.getUsername(), request.getPatientId(), request.getTestType());
        
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Admin authentication required"
            ));
        }

        // Get patient
        Optional<User> patientOpt = userRepository.findById(request.getPatientId());
        if (patientOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Patient not found"
            ));
        }

        // Create test result
        TestResult testResult = new TestResult();
        testResult.setUser(patientOpt.get());
        testResult.setTestType(request.getTestType());
        testResult.setResult(request.getResult());
        testResult.setStatus(request.getStatus() != null ? request.getStatus() : "Completed");
        testResult.setNotes(request.getNotes());
        testResult.setLabName(request.getLabName());
        testResult.setDoctorComments(request.getDoctorComments());

        // ✅ This automatically triggers notification
        TestResult saved = testResultService.addTestResult(testResult);
        
        logger.info("✅ Test result added and notification triggered");
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Test result added successfully and notification sent",
            "resultId", saved.getId()
        ));

    } catch (Exception e) {
        logger.error("Error adding test result: {}", e.getMessage());
        return ResponseEntity.status(500).body(Map.of(
            "success", false,
            "message", "Error adding test result: " + e.getMessage()
        ));
    }
}
    /**
     * Upload result with file
     */
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
            @RequestParam(value = "markCompleted", required = false, defaultValue = "false") boolean markCompleted,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            logger.info("=== UPLOAD RESULT WITH FILE ===");
            logger.info("Admin: {}, Patient ID: {}, Test: {}", 
                       userDetails != null ? userDetails.getUsername() : "null", patientId, testType);
            logger.info("File received: {}", file != null ? file.getOriginalFilename() : "null");
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> uploadResult = adminService.uploadResultWithFile(
                patientId, testType, result, notes, category, status, 
                doctorName, testDate, appointmentId, markCompleted, 
                file, userDetails);
            
            if (uploadResult.get("success") != null && !(Boolean) uploadResult.get("success")) {
                return ResponseEntity.status(400).body(uploadResult);
            }
            
            logger.info("Result uploaded successfully. Result ID: {}", uploadResult.get("resultId"));
            
            return ResponseEntity.ok(uploadResult);

        } catch (Exception e) {
            logger.error("Error uploading result with file: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error uploading test result: " + e.getMessage()
            ));
        }
    }

    // ==================== NOTIFICATIONS ====================

    /**
     * Send notification to specific patient
     */
    @PostMapping("/notifications/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> payload,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== SEND NOTIFICATION REQUEST ===");
            logger.info("Admin: {}", userDetails != null ? userDetails.getUsername() : "null");
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            Long recipientId = ((Number) payload.get("recipientId")).longValue();
            String title = (String) payload.get("title");
            String message = (String) payload.get("message");
            String type = (String) payload.get("type");

            autoNotificationService.sendManualNotification(recipientId, title, message, type);
            
            logger.info("✅ Notification sent to patient {}", recipientId);

            return ResponseEntity.ok(Map.of("success", true, "message", "Notification sent successfully"));
        } catch (Exception e) {
            logger.error("❌ Error sending notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Send notification to all patients
     */
    @PostMapping("/notifications/send-all")
    public ResponseEntity<?> sendNotificationToAll(@RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== BROADCAST NOTIFICATION REQUEST ===");
            logger.info("Admin: {}", userDetails != null ? userDetails.getUsername() : "null");
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            String title = (String) payload.get("title");
            String message = (String) payload.get("message");
            String type = (String) payload.get("type");

            autoNotificationService.sendNotificationToAll(title, message, type);
            
            logger.info("✅ Broadcast notification sent");

            return ResponseEntity.ok(Map.of("success", true, "message", "Broadcast notification sent"));
        } catch (Exception e) {
            logger.error("❌ Error sending broadcast: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Get all sent notifications
     */
    @GetMapping("/notifications")
    public ResponseEntity<?> getAllNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== GET NOTIFICATIONS REQUEST ===");
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            List<Notification> notifications = notificationRepository.findAll();
            
            List<Map<String, Object>> notificationList = new ArrayList<>();
            for (Notification notif : notifications) {
                Map<String, Object> notifData = new HashMap<>();
                notifData.put("id", notif.getId());
                notifData.put("title", notif.getTitle());
                notifData.put("message", notif.getMessage());
                notifData.put("type", notif.getType());
                notifData.put("recipientId", notif.getUser().getId());
                notifData.put("recipientName", notif.getUser().getFirstName() + " " + notif.getUser().getLastName());
                notifData.put("read", notif.isRead());
                notifData.put("sent", notif.isSent());
                notifData.put("createdAt", notif.getCreatedAt());
                notificationList.add(notifData);
            }

            return ResponseEntity.ok(Map.of("success", true, "notifications", notificationList));
        } catch (Exception e) {
            logger.error("Error getting notifications: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Delete a notification
     */
    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            notificationRepository.deleteById(id);
            
            logger.info("✅ Notification {} deleted", id);

            return ResponseEntity.ok(Map.of("success", true, "message", "Notification deleted"));
        } catch (Exception e) {
            logger.error("Error deleting notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== AUTO NOTIFICATIONS ====================

    /**
     * Create auto-notification rule
     */
    @PostMapping("/auto-notifications")
    public ResponseEntity<?> createAutoNotification(@RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("=== CREATE AUTO-NOTIFICATION REQUEST ===");
            logger.info("Admin: {}", userDetails != null ? userDetails.getUsername() : "null");
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            String trigger = (String) payload.get("trigger");
            String title = (String) payload.get("title");
            String message = (String) payload.get("message");
            String type = (String) payload.get("type");
            Boolean enabled = (Boolean) payload.getOrDefault("enabled", true);
            Integer delayMinutes = payload.get("delayMinutes") != null ? 
                ((Number) payload.get("delayMinutes")).intValue() : 0;

            AutoNotification autoNotif = new AutoNotification(trigger, title, message, type);
            autoNotif.setEnabled(enabled);
            autoNotif.setDelayMinutes(delayMinutes);
            autoNotif.setCreatedBy(userDetails.getUsername());

            autoNotificationRepository.save(autoNotif);
            
            logger.info("✅ Auto-notification created: {}", trigger);

            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification created successfully"));
        } catch (Exception e) {
            logger.error("❌ Error creating auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Get all auto-notification rules
     */
    @GetMapping("/auto-notifications")
    public ResponseEntity<?> getAllAutoNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            List<AutoNotification> autoNotifications = autoNotificationRepository.findAll();
            
            List<Map<String, Object>> autoNotifList = new ArrayList<>();
            for (AutoNotification autoNotif : autoNotifications) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", autoNotif.getId());
                data.put("trigger", autoNotif.getTrigger());
                data.put("title", autoNotif.getTitle());
                data.put("message", autoNotif.getMessage());
                data.put("type", autoNotif.getType());
                data.put("enabled", autoNotif.getEnabled());
                data.put("delayMinutes", autoNotif.getDelayMinutes());
                data.put("timesTriggered", autoNotif.getTimesTriggered());
                data.put("lastTriggered", autoNotif.getLastTriggered());
                data.put("createdAt", autoNotif.getCreatedAt());
                autoNotifList.add(data);
            }

            return ResponseEntity.ok(Map.of("success", true, "autoNotifications", autoNotifList));
        } catch (Exception e) {
            logger.error("Error getting auto-notifications: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Toggle auto-notification rule (enable/disable)
     */
    @PutMapping("/auto-notifications/{id}/toggle")
    public ResponseEntity<?> toggleAutoNotification(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> payload,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            Optional<AutoNotification> autoNotifOpt = autoNotificationRepository.findById(id);
            if (autoNotifOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Auto-notification not found"));
            }

            AutoNotification autoNotif = autoNotifOpt.get();
            Boolean enabled = (Boolean) payload.get("enabled");
            autoNotif.setEnabled(enabled);
            autoNotificationRepository.save(autoNotif);
            
            logger.info("✅ Auto-notification {} {}", id, enabled ? "enabled" : "disabled");

            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification updated"));
        } catch (Exception e) {
            logger.error("Error toggling auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Delete auto-notification rule
     */
    @DeleteMapping("/auto-notifications/{id}")
    public ResponseEntity<?> deleteAutoNotification(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            autoNotificationRepository.deleteById(id);
            
            logger.info("✅ Auto-notification {} deleted", id);

            return ResponseEntity.ok(Map.of("success", true, "message", "Auto-notification deleted"));
        } catch (Exception e) {
            logger.error("Error deleting auto-notification: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/notifications/stats")
    public ResponseEntity<?> getNotificationStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
            }

            long totalNotifications = notificationRepository.count();
            long totalAutoRules = autoNotificationRepository.count();
            long activeAutoRules = autoNotificationRepository.countByEnabled(true);
            
            List<AutoNotification> autoNotifs = autoNotificationRepository.findAll();
            long totalTriggered = autoNotifs.stream().mapToLong(AutoNotification::getTimesTriggered).sum();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalNotifications", totalNotifications);
            stats.put("totalAutoRules", totalAutoRules);
            stats.put("activeAutoRules", activeAutoRules);
            stats.put("totalTriggered", totalTriggered);

            return ResponseEntity.ok(Map.of("success", true, "stats", stats));
        } catch (Exception e) {
            logger.error("Error getting notification stats: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== REPORTS & SEARCH ====================

    /**
     * Get reports and analytics
     */
    @GetMapping("/reports")
    public ResponseEntity<?> getReports(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String reportType) {
        
        try {
            logger.info("=== ADMIN REPORTS REQUEST ===");
            logger.info("Admin: {}, Report Type: {}, Date Range: {} to {}", 
                       userDetails.getUsername(), reportType, startDate, endDate);
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> reports = adminService.generateReports(
                userDetails, startDate, endDate, reportType);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "reports", reports
            ));

        } catch (Exception e) {
            logger.error("Error generating reports: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error generating reports"
            ));
        }
    }

    /**
     * Search patients, appointments, or results
     */
    @GetMapping("/search")
    public ResponseEntity<?> globalSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "all") String type) {
        
        try {
            logger.info("=== ADMIN SEARCH REQUEST ===");
            logger.info("Admin: {}, Query: '{}', Type: {}", userDetails.getUsername(), query, type);
            
            if (userDetails == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Admin authentication required"
                ));
            }

            Map<String, Object> searchResults = adminService.globalSearch(query, type, userDetails);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "results", searchResults
            ));

        } catch (Exception e) {
            logger.error("Error performing search: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error performing search"
            ));
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if user is admin
     */
    private boolean isAdmin(UserDetails userDetails) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            return userOpt.isPresent() && userOpt.get().getRole() == User.Role.ADMIN;
        } catch (Exception e) {
            return false;
        }
    }
}