package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.dto.AppointmentDto;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AutoNotificationService autoNotificationService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // =========================================================================
    // WebSocket admin notification
    // =========================================================================

    public void notifyAdminOfNewAppointment(Appointment appointment) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("patientName", appointment.getUser().getFullName());
        notification.put("testType", appointment.getReason());
        notification.put("appointmentId", appointment.getId());
        notification.put("timestamp", LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/admin/appointments", notification);
    }

    // =========================================================================
    // CREATE APPOINTMENT  (called from mobile /api/appointments/create)
    // =========================================================================

    /**
     * Creates an appointment booked directly by the patient from the mobile app.
     * Accepts payment fields (paymentStatus, paymentMethod, price) sent from bookings.jsx.
     */
    public Map<String, Object> createAppointmentFromMobile(Map<String, Object> requestBody, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User patient = userOpt.get();

            // ── Parse date + time ────────────────────────────────────────────
            String scheduledDateStr = (String) requestBody.get("scheduledDate"); // "2025-06-15"
            String scheduledTimeStr = (String) requestBody.get("scheduledTime"); // "09:30"

            if (scheduledDateStr == null || scheduledTimeStr == null) {
                response.put("success", false);
                response.put("message", "scheduledDate and scheduledTime are required");
                return response;
            }

            LocalDate scheduledDate = LocalDate.parse(scheduledDateStr);
            LocalTime scheduledTime = LocalTime.parse(scheduledTimeStr);
            LocalDateTime appointmentDateTime = LocalDateTime.of(scheduledDate, scheduledTime);

            if (appointmentDateTime.isBefore(LocalDateTime.now())) {
                response.put("success", false);
                response.put("message", "Appointment date must be in the future");
                return response;
            }

            // ── Build entity ─────────────────────────────────────────────────
            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setAppointmentDate(appointmentDateTime);

            String testType = (String) requestBody.get("testType");
            appointment.setTestType(testType);
            appointment.setReason(testType);

            // ── Payment fields ───────────────────────────────────────────────
            String paymentStatusStr  = (String) requestBody.getOrDefault("paymentStatus",  "UNPAID");
            String paymentMethodStr  = (String) requestBody.getOrDefault("paymentMethod",  null);
            String price             = (String) requestBody.getOrDefault("price",          null);

            appointment.setPaymentStatus(paymentStatusStr);   // uses string overload in entity
            appointment.setPaymentMethod(paymentMethodStr);
            appointment.setPrice(price);

            appointment.setStatus(Appointment.Status.SCHEDULED);
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setUpdatedAt(LocalDateTime.now());

            Appointment saved = appointmentRepository.save(appointment);

            // ── Notifications ────────────────────────────────────────────────
            // Always notify patient that test is booked
            autoNotificationService.onTestBooked(saved);

            // If patient already paid (transfer submitted), notify admin
            if (Appointment.PaymentStatus.PENDING_CONFIRMATION.name().equals(paymentStatusStr)) {
                autoNotificationService.onPaymentSubmitted(saved);
            }

            response.put("success", true);
            response.put("message", "Appointment booked successfully!");
            response.put("appointment", convertToDto(saved));
            response.put("id", saved.getId());

        } catch (Exception e) {
            logger.error("❌ Error creating mobile appointment: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error creating appointment: " + e.getMessage());
        }
        return response;
    }

    // =========================================================================
    // CREATE APPOINTMENT  (old DTO-based path — keep for backward compat)
    // =========================================================================

    public Map<String, Object> createAppointment(AppointmentDto appointmentDto, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User patient = userOpt.get();

            if (appointmentDto.getAppointmentDate() != null &&
                appointmentDto.getAppointmentDate().isBefore(LocalDateTime.now())) {
                response.put("success", false);
                response.put("message", "Appointment date must be in the future");
                return response;
            }

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setAppointmentDate(appointmentDto.getAppointmentDate());
            appointment.setReason(appointmentDto.getReason());
            appointment.setNotes(appointmentDto.getNotes());
            appointment.setStatus(Appointment.Status.SCHEDULED);
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setUpdatedAt(LocalDateTime.now());

            Appointment saved = appointmentRepository.save(appointment);
            autoNotificationService.onTestBooked(saved);

            response.put("success", true);
            response.put("message", "Appointment booked successfully!");
            response.put("appointment", convertToDto(saved));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating appointment: " + e.getMessage());
        }
        return response;
    }

    // =========================================================================
    // GET appointments
    // =========================================================================

    public List<AppointmentDto> getUserAppointments(UserDetails userDetails) {
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) return List.of();
        return appointmentRepository.findByPatient(userOpt.get())
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    public List<AppointmentDto> getUpcomingAppointments(UserDetails userDetails) {
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) return List.of();
        return appointmentRepository
            .findByPatientAndAppointmentDateAfter(userOpt.get(), LocalDateTime.now())
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    public Map<String, Object> updateAppointment(Long appointmentId, AppointmentDto appointmentDto, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User patient = userOpt.get();
            Optional<Appointment> aptOpt = appointmentRepository.findById(appointmentId);
            if (aptOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Appointment not found");
                return response;
            }

            Appointment appointment = aptOpt.get();
            if (!appointment.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized to update this appointment");
                return response;
            }

            if (appointmentDto.getAppointmentDate() != null)
                appointment.setAppointmentDate(appointmentDto.getAppointmentDate());
            if (appointmentDto.getReason() != null)
                appointment.setReason(appointmentDto.getReason());
            if (appointmentDto.getNotes() != null)
                appointment.setNotes(appointmentDto.getNotes());
            if (appointmentDto.getStatus() != null)
                appointment.setStatus(Appointment.Status.valueOf(appointmentDto.getStatus()));

            appointment.setUpdatedAt(LocalDateTime.now());
            Appointment updated = appointmentRepository.save(appointment);

            response.put("success", true);
            response.put("message", "Appointment updated successfully!");
            response.put("appointment", convertToDto(updated));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating appointment: " + e.getMessage());
        }
        return response;
    }

    // =========================================================================
    // STATUS UPDATE (admin-facing — triggers notification automatically)
    // =========================================================================

    public void updateAppointmentStatus(Long appointmentId, String newStatus) {
        try {
            Optional<Appointment> aptOpt = appointmentRepository.findById(appointmentId);
            if (aptOpt.isEmpty()) {
                logger.warn("Appointment not found: {}", appointmentId);
                return;
            }

            Appointment appointment = aptOpt.get();
            String oldStatus = appointment.getStatus().toString();

            appointment.setStatus(newStatus);          // uses string overload
            appointment.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

            logger.info("Appointment {} status: {} → {}", appointmentId, oldStatus, newStatus);

            // Trigger notification for every meaningful status change
            switch (newStatus.toUpperCase()) {
                case "COMPLETED", "CANCELLED", "MISSED" ->
                    autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, newStatus);
                default ->
                    autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, newStatus);
            }

        } catch (Exception e) {
            logger.error("Error updating appointment status: {}", e.getMessage());
        }
    }

    // =========================================================================
    // BOOK TEST (admin-facing — used by AdminController)
    // =========================================================================

    public Appointment bookTest(Appointment appointment) {
        try {
            Appointment saved = appointmentRepository.save(appointment);
            logger.info("Test booked for patient: {}", saved.getPatient().getId());
            autoNotificationService.onTestBooked(saved);
            return saved;
        } catch (Exception e) {
            logger.error("Error booking test: {}", e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // CANCEL (patient-facing)
    // =========================================================================

    public Map<String, Object> cancelAppointment(Long appointmentId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User patient = userOpt.get();
            Optional<Appointment> aptOpt = appointmentRepository.findById(appointmentId);
            if (aptOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Appointment not found");
                return response;
            }

            Appointment appointment = aptOpt.get();
            if (!appointment.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized to cancel this appointment");
                return response;
            }

            String oldStatus = appointment.getStatus().toString();
            appointment.setStatus(Appointment.Status.CANCELLED);
            appointment.setUpdatedAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

            autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, "CANCELLED");

            response.put("success", true);
            response.put("message", "Appointment cancelled successfully!");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error cancelling appointment: " + e.getMessage());
        }
        return response;
    }

    // =========================================================================
    // DTO conversion
    // =========================================================================

    private AppointmentDto convertToDto(Appointment appointment) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(appointment.getId());
        dto.setAppointmentDate(appointment.getAppointmentDate());
        dto.setScheduledDate(appointment.getScheduledDate());
        dto.setScheduledTime(appointment.getScheduledTime());
        dto.setReason(appointment.getReason());
        dto.setTestType(appointment.getTestType());
        dto.setNotes(appointment.getNotes());
        dto.setStatus(appointment.getStatus().name());
        dto.setPatientId(appointment.getPatient().getId());
        dto.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        dto.setCreatedAt(appointment.getCreatedAt());

        // ── Payment fields ───────────────────────────────────────────────────
        dto.setPrice(appointment.getPrice());
        dto.setPaymentStatus(
            appointment.getPaymentStatus() != null
                ? appointment.getPaymentStatus().name()
                : Appointment.PaymentStatus.UNPAID.name()
        );
        dto.setPaymentMethod(appointment.getPaymentMethod());
        dto.setPaymentApprovedAt(appointment.getPaymentApprovedAt());
        dto.setPaymentApprovedBy(appointment.getPaymentApprovedBy());

        return dto;
    }
}