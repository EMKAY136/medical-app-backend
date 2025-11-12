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
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AutoNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AutoNotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WebSocketNotificationService webSocketNotificationService;

    /**
     * Trigger notification when test result is uploaded
     * Sends automatically WITHOUT needing any rules
     */
    @Async
    public void onTestResultUploaded(TestResult result) {
        try {
            logger.info("=== AUTO-NOTIFICATION TRIGGERED: Test Result Uploaded ===");
            logger.info("Test Type: {}, Result ID: {}", result.getTestType(), result.getId());
            
            User patient = result.getUser();
            if (patient == null) {
                logger.warn("Patient not found for test result ID: {}", result.getId());
                return;
            }

            // Send notification directly WITHOUT checking rules
            String title = "Your Test Results Are Ready!";
            String message = String.format("Hi %s, your %s results are now available in the app. Please review them at your convenience.",
                patient.getFirstName(), result.getTestType());

            Notification notification = new Notification(patient, title, message, "results");
            notification.setReferenceType("test_result");
            notification.setReferenceId(result.getId());
            notification.setPriority(Notification.Priority.HIGH);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, "result");
            
            // Send WebSocket notification
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("id", result.getId());
            resultMap.put("testType", result.getTestType());
            resultMap.put("testName", result.getTestName());
            resultMap.put("result", result.getResult());
            resultMap.put("testDate", result.getTestDate());
            resultMap.put("labName", result.getLabName());
            resultMap.put("status", result.getStatus());
            webSocketNotificationService.notifyNewTestResult(patient.getId(), resultMap);
            
            logger.info("✅ Auto-notification sent to patient {}: {}", patient.getId(), title);
        } catch (Exception e) {
            logger.error("❌ Error sending auto-notification for test result: {}", e.getMessage());
        }
    }

    /**
     * Trigger notification when appointment is scheduled
     * Sends automatically WITHOUT needing any rules
     */
    @Async
    public void onAppointmentScheduled(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION TRIGGERED: Appointment Scheduled ===");
            logger.info("Appointment ID: {}, Patient ID: {}", appointment.getId(), appointment.getUser().getId());
            
            User patient = appointment.getUser();
            if (patient == null) {
                logger.warn("Patient not found for appointment ID: {}", appointment.getId());
                return;
            }

            // Send notification directly WITHOUT checking rules
            String title = "Appointment Confirmed";
            String message = String.format("Hi %s, your %s appointment has been scheduled for %s. We'll see you then!",
                patient.getFirstName(), 
                appointment.getReason() != null ? appointment.getReason() : "medical",
                appointment.getScheduledDate());

            Notification notification = new Notification(patient, title, message, "appointment");
            notification.setReferenceType("appointment");
            notification.setReferenceId(appointment.getId());
            notification.setPriority(Notification.Priority.HIGH);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, "appointment");
            
            // Send WebSocket notification
            Map<String, Object> appointmentMap = new HashMap<>();
            appointmentMap.put("id", appointment.getId());
            appointmentMap.put("reason", appointment.getReason());
            appointmentMap.put("appointmentDate", appointment.getAppointmentDate());
            appointmentMap.put("status", appointment.getStatus());
            appointmentMap.put("doctorName", appointment.getDoctorName());
            webSocketNotificationService.notifyNewAppointment(patient.getId(), appointmentMap);
            
            logger.info("✅ Auto-notification sent to patient {}: {}", patient.getId(), title);
        } catch (Exception e) {
            logger.error("❌ Error sending auto-notification for appointment: {}", e.getMessage());
        }
    }

    /**
     * Trigger notification when appointment status changes
     * Sends automatically WITHOUT needing any rules
     */
    @Async
    public void onAppointmentStatusChanged(Appointment appointment, String oldStatus, String newStatus) {
        try {
            logger.info("=== AUTO-NOTIFICATION TRIGGERED: Appointment Status Changed ===");
            logger.info("Appointment ID: {}, Old Status: {}, New Status: {}", appointment.getId(), oldStatus, newStatus);
            
            User patient = appointment.getUser();
            if (patient == null) {
                logger.warn("Patient not found for appointment ID: {}", appointment.getId());
                return;
            }

            // Send notification directly WITHOUT checking rules
            String title = "";
            String message = "";
            
            if ("CONFIRMED".equalsIgnoreCase(newStatus)) {
                title = "Appointment Confirmed";
                message = String.format("Hi %s, your appointment has been confirmed for %s.",
                    patient.getFirstName(), appointment.getScheduledDate());
            } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                title = "Appointment Cancelled";
                message = String.format("Hi %s, your appointment for %s has been cancelled.",
                    patient.getFirstName(), appointment.getScheduledDate());
            } else if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                title = "Appointment Completed";
                message = String.format("Hi %s, thank you for visiting us today!", patient.getFirstName());
            } else {
                title = "Appointment Update";
                message = String.format("Hi %s, your appointment status has been updated to %s.",
                    patient.getFirstName(), newStatus);
            }

            Notification notification = new Notification(patient, title, message, "appointment");
            notification.setReferenceType("appointment");
            notification.setReferenceId(appointment.getId());
            notification.setPriority(Notification.Priority.NORMAL);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, "appointment");
            
            // Send WebSocket notification
            webSocketNotificationService.notifyAppointmentStatusChange(
                patient.getId(), newStatus, appointment.getId().toString()
            );
            
            logger.info("✅ Auto-notification sent to patient {}: {}", patient.getId(), title);
        } catch (Exception e) {
            logger.error("❌ Error sending auto-notification for status change: {}", e.getMessage());
        }
    }

    /**
     * Trigger notification when test is booked
     * Sends automatically WITHOUT needing any rules
     */
    @Async
    public void onTestBooked(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION TRIGGERED: Test Booked ===");
            logger.info("Test Type: {}, Patient ID: {}", appointment.getReason(), appointment.getUser().getId());
            
            User patient = appointment.getUser();
            if (patient == null) {
                logger.warn("Patient not found for appointment ID: {}", appointment.getId());
                return;
            }

            // Send notification directly WITHOUT checking rules
            String title = "Test Appointment Booked";
            String message = String.format("Hi %s, your %s has been booked for %s at %s. Please arrive 15 minutes early.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "test",
                appointment.getScheduledDate(),
                appointment.getScheduledTime());

            Notification notification = new Notification(patient, title, message, "appointment");
            notification.setReferenceType("appointment");
            notification.setReferenceId(appointment.getId());
            notification.setPriority(Notification.Priority.HIGH);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, "appointment");
            
            // Send WebSocket notification
            Map<String, Object> appointmentMap = new HashMap<>();
            appointmentMap.put("id", appointment.getId());
            appointmentMap.put("reason", appointment.getReason());
            appointmentMap.put("appointmentDate", appointment.getAppointmentDate());
            appointmentMap.put("status", appointment.getStatus());
            webSocketNotificationService.notifyNewAppointment(patient.getId(), appointmentMap);
            
            logger.info("✅ Auto-notification sent to patient {}: {}", patient.getId(), title);
        } catch (Exception e) {
            logger.error("❌ Error sending auto-notification for test booking: {}", e.getMessage());
        }
    }

    /**
     * Send appointment reminder
     * Sends automatically WITHOUT needing any rules
     */
    @Async
    public void sendAppointmentReminder(Appointment appointment) {
        try {
            logger.info("=== AUTO-NOTIFICATION TRIGGERED: Appointment Reminder ===");
            logger.info("Appointment ID: {}, Patient ID: {}", appointment.getId(), appointment.getUser().getId());
            
            User patient = appointment.getUser();
            if (patient == null) {
                logger.warn("Patient not found for appointment ID: {}", appointment.getId());
                return;
            }

            // Send notification directly WITHOUT checking rules
            String title = "Appointment Reminder";
            String message = String.format("Hi %s, this is a reminder about your %s appointment scheduled for %s. Please arrive 15 minutes early.",
                patient.getFirstName(),
                appointment.getReason() != null ? appointment.getReason() : "medical",
                appointment.getScheduledDate());

            Notification notification = new Notification(patient, title, message, "reminder");
            notification.setReferenceType("appointment");
            notification.setReferenceId(appointment.getId());
            notification.setPriority(Notification.Priority.HIGH);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, "reminder");
            
            // Send WebSocket notification
            Map<String, Object> appointmentMap = new HashMap<>();
            appointmentMap.put("id", appointment.getId());
            appointmentMap.put("reason", appointment.getReason());
            appointmentMap.put("appointmentDate", appointment.getAppointmentDate());
            appointmentMap.put("isReminder", true);
            webSocketNotificationService.notifyNewAppointment(patient.getId(), appointmentMap);
            
            logger.info("✅ Appointment reminder sent to patient {}", patient.getId());
        } catch (Exception e) {
            logger.error("❌ Error sending appointment reminder: {}", e.getMessage());
        }
    }

    /**
     * Manual notification sending (for admin-created notifications)
     */
    public void sendManualNotification(Long recipientId, String title, String message, String type) {
        try {
            logger.info("=== MANUAL NOTIFICATION ===");
            logger.info("Sending to patient ID: {}", recipientId);
            
            Optional<User> patientOpt = userRepository.findById(recipientId);
            if (patientOpt.isEmpty()) {
                logger.warn("Patient not found for ID: {}", recipientId);
                return;
            }
            
            User patient = patientOpt.get();
            
            Notification notification = new Notification(patient, title, message, type);
            notification.setPriority(Notification.Priority.NORMAL);
            
            notificationRepository.save(notification);
            
            // Send push notification
            notificationService.sendPushNotification(patient, title, message, type);
            
            // Send WebSocket notification
            webSocketNotificationService.notifyUser(recipientId, title, message, type);
            
            logger.info("✅ Manual notification sent to patient {}", recipientId);
        } catch (Exception e) {
            logger.error("❌ Error sending manual notification: {}", e.getMessage());
        }
    }

    /**
     * Send notification to all patients
     */
    public void sendNotificationToAll(String title, String message, String type) {
        try {
            logger.info("=== BROADCAST NOTIFICATION ===");
            logger.info("Sending to all patients");
            
            List<User> allPatients = userRepository.findByRole(User.Role.PATIENT);
            
            for (User patient : allPatients) {
                Notification notification = new Notification(patient, title, message, type);
                notification.setPriority(Notification.Priority.NORMAL);
                
                notificationRepository.save(notification);
                
                // Send push notification
                notificationService.sendPushNotification(patient, title, message, type);
                
                // Send WebSocket notification
                webSocketNotificationService.notifyUser(patient.getId(), title, message, type);
            }
            
            // Also send broadcast to all connected users
            webSocketNotificationService.notifyAll(title, message, type);
            
            logger.info("✅ Broadcast notification sent to {} patients", allPatients.size());
        } catch (Exception e) {
            logger.error("❌ Error sending broadcast notification: {}", e.getMessage());
        }
    }
}