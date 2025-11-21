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
@RequestMapping("/appointments")
public class AppointmentController {

    @Autowired
    private AppointmentService appointmentService;

    // Create new appointment
    @PostMapping
    public ResponseEntity<?> createAppointment(@Valid @RequestBody AppointmentDto appointmentDto,
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

    @MessageMapping("/admin/appointment")
@SendTo("/topic/admin/appointments")
public AppointmentNotification notifyAdminAppointment(AppointmentNotification notification) {
    return notification;
}

    // Get all appointments for current user
    @GetMapping
    public ResponseEntity<?> getUserAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUserAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching appointments: " + e.getMessage());
        }
    }

    // Get upcoming appointments for current user
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingAppointments(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<AppointmentDto> appointments = appointmentService.getUpcomingAppointments(userDetails);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching upcoming appointments: " + e.getMessage());
        }
    }

    // Update appointment
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointment(@PathVariable Long id,
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

    // Cancel appointment
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id,
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

    // Change appointment status
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAppointmentStatus(@PathVariable Long id,
                                                   @RequestParam String status,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            AppointmentDto appointmentDto = new AppointmentDto();
            appointmentDto.setStatus(status);
            
            Map<String, Object> response = appointmentService.updateAppointment(id, appointmentDto, userDetails);
            
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