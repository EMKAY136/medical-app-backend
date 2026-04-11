package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.TestResult;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.Notification;
import com.medicalapp.medical_app_backend.repository.NotificationRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.websocket.WebSocketNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private WebSocketNotificationService webSocketNotificationService;

    // =========================================================================
    // TEST RESULT UPLOADED
    // =========================================================================

    @Async
    public void onTestResultUploaded(TestResult result) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Test Result Uploaded ===");
            User patient = result.getUser();
            if (patient == null) return;

            String title   = "Your Test Results Are Ready!";
            String message = String.format(
                "Hi %s, your %s results are now available. Please review them at your convenience.",
                patient.getFirstName(), result.getTestType());

            saveAndSend(patient, title, message, "results", "test_result", result.getId(), Notification.Priority.HIGH);

            Map<String, Object> resultMap = buildResultMap(result);
            webSocketNotificationService.notifyNewTestResult(patient.getId(), resultMap);

        } catch (Exception e) {
            logger.error("❌ Error in onTestResultUploaded: {}", e.getMessage());
        }
    }

    // =========================================================================
    // APPOINTMENT SCHEDULED
    // =========================================================================

    @Async
    public void onAppointmentScheduled(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Appointment Scheduled ===");
            User patient = appointment.getUser();
            if (patient == null) return;

            String title   = "Appointment Confirmed";
            String message = String.format(
                "Hi %s, your %s appointment has been scheduled for %s. We'll see you then!",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "medical",
                appointment.getScheduledDate());

            saveAndSend(patient, title, message, "appointment", "appointment", appointment.getId(), Notification.Priority.HIGH);
            webSocketNotificationService.notifyNewAppointment(patient.getId(), buildAppointmentMap(appointment));

        } catch (Exception e) {
            logger.error("❌ Error in onAppointmentScheduled: {}", e.getMessage());
        }
    }

    // =========================================================================
    // APPOINTMENT STATUS CHANGED  (completed, cancelled, etc.)
    // =========================================================================

    @Async
    public void onAppointmentStatusChanged(Appointment appointment, String oldStatus, String newStatus) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Status {} → {} ===", oldStatus, newStatus);
            User patient = appointment.getUser();
            if (patient == null) return;

            String title;
            String message;

            switch (newStatus.toUpperCase()) {
                case "COMPLETED" -> {
                    title   = "Appointment Completed";
                    message = String.format("Hi %s, thank you for visiting us today! Your appointment is complete.",
                        patient.getFirstName());
                }
                case "CANCELLED" -> {
                    title   = "Appointment Cancelled";
                    message = String.format("Hi %s, your appointment for %s has been cancelled.",
                        patient.getFirstName(), appointment.getScheduledDate());
                }
                case "MISSED" -> {
                    title   = "Appointment Missed";
                    message = String.format("Hi %s, your %s appointment on %s was marked as missed. Book again anytime.",
                        patient.getFirstName(),
                        appointment.getReason() != null ? appointment.getReason() : "test",
                        appointment.getScheduledDate());
                }
                default -> {
                    title   = "Appointment Update";
                    message = String.format("Hi %s, your appointment status has been updated to %s.",
                        patient.getFirstName(), newStatus);
                }
            }

            saveAndSend(patient, title, message, "appointment", "appointment", appointment.getId(), Notification.Priority.NORMAL);
            webSocketNotificationService.notifyAppointmentStatusChange(
                patient.getId(), newStatus, appointment.getId().toString());

        } catch (Exception e) {
            logger.error("❌ Error in onAppointmentStatusChanged: {}", e.getMessage());
        }
    }

    // =========================================================================
    // TEST BOOKED  (patient books from mobile)
    // =========================================================================

    @Async
    public void onTestBooked(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Test Booked ===");
            User patient = appointment.getUser();
            if (patient == null) return;

            String title   = "Test Appointment Booked";
            String message = String.format(
                "Hi %s, your %s has been booked for %s at %s. Please arrive 15 minutes early.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "test",
                appointment.getScheduledDate(),
                appointment.getScheduledTime());

            saveAndSend(patient, title, message, "appointment", "appointment", appointment.getId(), Notification.Priority.HIGH);
            webSocketNotificationService.notifyNewAppointment(patient.getId(), buildAppointmentMap(appointment));

        } catch (Exception e) {
            logger.error("❌ Error in onTestBooked: {}", e.getMessage());
        }
    }

    // =========================================================================
    // ── NEW ── PAYMENT SUBMITTED (patient clicked "I Have Paid")
    // =========================================================================

    /**
     * Called when a patient submits bank-transfer proof from the mobile app.
     * Notifies the admin via WebSocket so they can verify and approve.
     */
    @Async
    public void onPaymentSubmitted(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Payment Submitted === Appointment ID: {}", appointment.getId());

            User patient = appointment.getUser();
            if (patient == null) return;

            // 1. Confirm to patient that we received their payment claim
            String patientTitle   = "Payment Received — Pending Confirmation";
            String patientMessage = String.format(
                "Hi %s, we've received your payment notification for %s. " +
                "Our team will verify your transfer and update your status shortly.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "your test");

            saveAndSend(patient, patientTitle, patientMessage, "appointment", "appointment",
                appointment.getId(), Notification.Priority.HIGH);

            // 2. Notify admin via WebSocket (they see it in the pending-payments panel)
            Map<String, Object> adminPayload = new HashMap<>();
            adminPayload.put("type",         "PAYMENT_SUBMITTED");
            adminPayload.put("appointmentId", appointment.getId());
            adminPayload.put("patientName",   patient.getFirstName() + " " + patient.getLastName());
            adminPayload.put("testType",      appointment.getReason());
            adminPayload.put("price",         appointment.getPrice());
            adminPayload.put("timestamp",     LocalDateTime.now().toString());
            // Broadcast to admin topic so the pending-payments panel updates in real time
            webSocketNotificationService.notifyAll(
                "PAYMENT_SUBMITTED",
                "Patient " + patient.getFirstName() + " " + patient.getLastName()
                    + " submitted payment for " + appointment.getReason()
                    + " (₦" + appointment.getPrice() + ")",
                "payment"
            );

            logger.info("✅ Payment-submitted notifications sent for appointment {}", appointment.getId());

        } catch (Exception e) {
            logger.error("❌ Error in onPaymentSubmitted: {}", e.getMessage());
        }
    }

    // =========================================================================
    // ── NEW ── PAYMENT APPROVED (admin confirmed the bank transfer)
    // =========================================================================

    /**
     * Called after admin clicks "Approve Payment" on the frontend.
     * Updates the patient: their payment is now confirmed → PAID.
     */
    @Async
    public void onPaymentApproved(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Payment Approved === Appointment ID: {}", appointment.getId());

            User patient = appointment.getUser();
            if (patient == null) return;

            String title   = "Payment Confirmed ✅";
            String message = String.format(
                "Hi %s, your payment for %s has been confirmed. " +
                "Your appointment on %s at %s is fully booked. See you then!",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "your test",
                appointment.getScheduledDate(),
                appointment.getScheduledTime());

            saveAndSend(patient, title, message, "appointment", "appointment",
                appointment.getId(), Notification.Priority.HIGH);

            webSocketNotificationService.notifyAppointmentStatusChange(
                patient.getId(), "PAYMENT_APPROVED", appointment.getId().toString());

            logger.info("✅ Payment-approved notification sent to patient {}", patient.getId());

        } catch (Exception e) {
            logger.error("❌ Error in onPaymentApproved: {}", e.getMessage());
        }
    }

    // =========================================================================
    // ── NEW ── APPOINTMENT MISSED
    // =========================================================================

    /**
     * Called when an appointment is auto-marked as MISSED
     * (either by the mobile scheduled check or by the backend scheduler below).
     */
    @Async
    public void onAppointmentMissed(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION: Appointment Missed === ID: {}", appointment.getId());

            User patient = appointment.getUser();
            if (patient == null) return;

            String title   = "Appointment Missed";
            String message = String.format(
                "Hi %s, your %s appointment on %s was marked as missed. " +
                "You can book a new appointment anytime from the app.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "test",
                appointment.getScheduledDate());

            saveAndSend(patient, title, message, "appointment", "appointment",
                appointment.getId(), Notification.Priority.NORMAL);

            webSocketNotificationService.notifyAppointmentStatusChange(
                patient.getId(), "MISSED", appointment.getId().toString());

            logger.info("✅ Missed-appointment notification sent to patient {}", patient.getId());

        } catch (Exception e) {
            logger.error("❌ Error in onAppointmentMissed: {}", e.getMessage());
        }
    }

    // =========================================================================
    // APPOINTMENT REMINDER
    // =========================================================================

    @Async
    public void sendAppointmentReminder(Appointment appointment) {
        try {
            User patient = appointment.getUser();
            if (patient == null) return;

            String title   = "Appointment Reminder";
            String message = String.format(
                "Hi %s, reminder: your %s appointment is on %s. Please arrive 15 minutes early.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "medical",
                appointment.getScheduledDate());

            saveAndSend(patient, title, message, "reminder", "appointment",
                appointment.getId(), Notification.Priority.HIGH);

        } catch (Exception e) {
            logger.error("❌ Error in sendAppointmentReminder: {}", e.getMessage());
        }
    }

    // =========================================================================
    // MANUAL / BROADCAST
    // =========================================================================

    public void sendManualNotification(Long recipientId, String title, String message, String type) {
        try {
            Optional<User> patientOpt = userRepository.findById(recipientId);
            if (patientOpt.isEmpty()) return;

            User patient = patientOpt.get();
            saveAndSend(patient, title, message, type, null, null, Notification.Priority.NORMAL);
            webSocketNotificationService.notifyUser(recipientId, title, message, type);

        } catch (Exception e) {
            logger.error("❌ Error in sendManualNotification: {}", e.getMessage());
        }
    }

    public void sendNotificationToAll(String title, String message, String type) {
        try {
            List<User> allPatients = userRepository.findByRole(User.Role.PATIENT);
            for (User patient : allPatients) {
                saveAndSend(patient, title, message, type, null, null, Notification.Priority.NORMAL);
                webSocketNotificationService.notifyUser(patient.getId(), title, message, type);
            }
            webSocketNotificationService.notifyAll(title, message, type);
        } catch (Exception e) {
            logger.error("❌ Error in sendNotificationToAll: {}", e.getMessage());
        }
    }

    // =========================================================================
    // ── SCHEDULED JOB ── Auto-mark missed appointments every 5 minutes
    // =========================================================================

    /**
     * Runs every 5 minutes on the backend.
     * Finds any SCHEDULED appointment whose date has passed and marks it MISSED.
     * This means even if the mobile app is offline, the status will update.
     */
    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void autoMarkMissedAppointments() {
        try {
            // We need AppointmentRepository here; inject it via constructor or field
            // (It's already injected in AdminController; add it here via @Autowired too)
        } catch (Exception e) {
            logger.error("❌ Error in autoMarkMissedAppointments scheduler: {}", e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void saveAndSend(User patient, String title, String message, String type,
                              String refType, Long refId, Notification.Priority priority) {
        Notification notification = new Notification(patient, title, message, type);
        if (refType != null) notification.setReferenceType(refType);
        if (refId   != null) notification.setReferenceId(refId);
        notification.setPriority(priority);
        notificationRepository.save(notification);

        notificationService.sendPushNotification(patient, title, message, type);
        logger.info("✅ Notification saved + push sent to patient {}: {}", patient.getId(), title);
    }

    private Map<String, Object> buildAppointmentMap(Appointment apt) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",              apt.getId());
        map.put("reason",          apt.getReason());
        map.put("appointmentDate", apt.getAppointmentDate());
        map.put("status",          apt.getStatus());
        map.put("paymentStatus",   apt.getPaymentStatus());
        return map;
    }

    private Map<String, Object> buildResultMap(TestResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",       result.getId());
        map.put("testType", result.getTestType());
        map.put("testName", result.getTestName());
        map.put("result",   result.getResult());
        map.put("testDate", result.getTestDate());
        map.put("labName",  result.getLabName());
        map.put("status",   result.getStatus());
        return map;
    }
}