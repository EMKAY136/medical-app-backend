package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.dto.AppointmentDto;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;
    
@Autowired
private AutoNotificationService autoNotificationService; // ✅ ADD THIS

@Autowired
private SimpMessagingTemplate messagingTemplate;

public void notifyAdminOfNewAppointment(Appointment appointment) {
    Map<String, Object> notification = new HashMap<>();
    notification.put("patientName", appointment.getUser().getFullName());
    notification.put("testType", appointment.getReason());
    notification.put("appointmentId", appointment.getId());
    notification.put("timestamp", LocalDateTime.now());
    
    messagingTemplate.convertAndSend("/topic/admin/appointments", notification);
}

// Create new appointment
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

            if (appointmentDto.getAppointmentDate().isBefore(LocalDateTime.now())) {
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

            Appointment savedAppointment = appointmentRepository.save(appointment);

            response.put("success", true);
            response.put("message", "Appointment booked successfully!");
            response.put("appointment", convertToDto(savedAppointment));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating appointment: " + e.getMessage());
        }

        return response;
    }

    // Get all appointments for current user
    public List<AppointmentDto> getUserAppointments(UserDetails userDetails) {
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return List.of();
        }

        User patient = userOpt.get();
        List<Appointment> appointments = appointmentRepository.findByPatient(patient);

        return appointments.stream()
                .<AppointmentDto>map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // Get upcoming appointments for current user
    public List<AppointmentDto> getUpcomingAppointments(UserDetails userDetails) {
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return List.of();
        }

        User patient = userOpt.get();
        List<Appointment> appointments = appointmentRepository
                .findByPatientAndAppointmentDateAfter(patient, LocalDateTime.now());

        return appointments.stream()
                .<AppointmentDto>map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // Update appointment
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

            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            if (appointmentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Appointment not found");
                return response;
            }

            Appointment appointment = appointmentOpt.get();

            if (!appointment.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized to update this appointment");
                return response;
            }

            if (appointmentDto.getAppointmentDate() != null) {
                appointment.setAppointmentDate(appointmentDto.getAppointmentDate());
            }
            if (appointmentDto.getReason() != null) {
                appointment.setReason(appointmentDto.getReason());
            }
            if (appointmentDto.getNotes() != null) {
                appointment.setNotes(appointmentDto.getNotes());
            }
            if (appointmentDto.getStatus() != null) {
                appointment.setStatus(Appointment.Status.valueOf(appointmentDto.getStatus()));
            }
            appointment.setUpdatedAt(LocalDateTime.now());

            Appointment updatedAppointment = appointmentRepository.save(appointment);

            response.put("success", true);
            response.put("message", "Appointment updated successfully!");
            response.put("appointment", convertToDto(updatedAppointment));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating appointment: " + e.getMessage());
        }

        return response;
    }

    // ✅ NEW METHOD: Update appointment status WITH auto-notification
    public void updateAppointmentStatus(Long appointmentId, String newStatus) {
        try {
            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            
            if (appointmentOpt.isPresent()) {
                Appointment appointment = appointmentOpt.get();
                String oldStatus = appointment.getStatus().toString();
                
                logger.info("Updating appointment {} status from {} to {}", appointmentId, oldStatus, newStatus);
                
                // Update status
                appointment.setStatus(Appointment.Status.valueOf(newStatus));
                appointment.setUpdatedAt(LocalDateTime.now());
                appointmentRepository.save(appointment);
                
                // ✅ AUTOMATICALLY TRIGGER NOTIFICATION
                if ("CONFIRMED".equalsIgnoreCase(newStatus)) {
                    logger.info("Triggering auto-notification for appointment confirmed");
                    autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, newStatus);
                } else if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                    logger.info("Triggering auto-notification for appointment completed");
                    autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, newStatus);
                } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    logger.info("Triggering auto-notification for appointment cancelled");
                    autoNotificationService.onAppointmentStatusChanged(appointment, oldStatus, newStatus);
                }
            } else {
                logger.warn("Appointment not found: {}", appointmentId);
            }
        } catch (Exception e) {
            logger.error("Error updating appointment status: {}", e.getMessage());
        }
    }

    // ✅ NEW METHOD: Book test WITH auto-notification
    public Appointment bookTest(Appointment appointment) {
        try {
            Appointment saved = appointmentRepository.save(appointment);
            
            logger.info("Test booked for patient: {}", saved.getPatient().getId());
            
            // ✅ AUTOMATICALLY TRIGGER TEST BOOKED NOTIFICATION
            autoNotificationService.onTestBooked(saved);
            
            return saved;
        } catch (Exception e) {
            logger.error("Error booking test: {}", e.getMessage());
            throw e;
        }
    }

    // Cancel appointment
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

            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            if (appointmentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Appointment not found");
                return response;
            }

            Appointment appointment = appointmentOpt.get();

            if (!appointment.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized to cancel this appointment");
                return response;
            }

            appointment.setStatus(Appointment.Status.CANCELLED);
            appointment.setUpdatedAt(LocalDateTime.now());

            appointmentRepository.save(appointment);

            response.put("success", true);
            response.put("message", "Appointment cancelled successfully!");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error cancelling appointment: " + e.getMessage());
        }

        return response;
    }

    // Convert entity to DTO
    private AppointmentDto convertToDto(Appointment appointment) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(appointment.getId());
        dto.setAppointmentDate(appointment.getAppointmentDate());
        dto.setReason(appointment.getReason());
        dto.setNotes(appointment.getNotes());
        dto.setStatus(appointment.getStatus().name());
        dto.setPatientId(appointment.getPatient().getId());
        dto.setPatientName(appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName());
        dto.setCreatedAt(appointment.getCreatedAt());
        return dto;
    }
}