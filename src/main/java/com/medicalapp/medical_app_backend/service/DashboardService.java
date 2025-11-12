package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.MedicalResult;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import com.medicalapp.medical_app_backend.repository.MedicalResultRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MedicalResultRepository medicalResultRepository;

    public Map<String, Object> getDashboardData(UserDetails userDetails) {
        Map<String, Object> dashboardData = new HashMap<>();

        // Get current user
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            dashboardData.put("error", "User not found");
            return dashboardData;
        }

        User user = userOpt.get();

        // Get user statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAppointments", appointmentRepository.countByPatient(user));
        stats.put("totalResults", medicalResultRepository.countByPatient(user));

        // Get upcoming appointments
        List<Appointment> upcomingAppointments = appointmentRepository
            .findByPatientAndAppointmentDateAfter(user, LocalDateTime.now());

        // Get recent appointments (last 5)
        List<Appointment> recentAppointments = appointmentRepository
            .findTop5ByPatientOrderByCreatedAtDesc(user);

        // Get recent results (last 5)
        List<MedicalResult> recentResults = medicalResultRepository
            .findTop5ByPatientOrderByTestDateDesc(user);

        // Today's appointments
        List<Appointment> todaysAppointments = appointmentRepository.findTodaysAppointments()
            .stream()
            .filter(apt -> apt.getPatient().getId().equals(user.getId()))
            .toList();

        // Build response
        dashboardData.put("user", createUserSummary(user));
        dashboardData.put("statistics", stats);
        dashboardData.put("upcomingAppointments", upcomingAppointments);
        dashboardData.put("recentAppointments", recentAppointments);
        dashboardData.put("recentResults", recentResults);
        dashboardData.put("todaysAppointments", todaysAppointments);

        return dashboardData;
    }

    private Map<String, Object> createUserSummary(User user) {
        Map<String, Object> userSummary = new HashMap<>();
        userSummary.put("id", user.getId());
        userSummary.put("firstName", user.getFirstName());
        userSummary.put("lastName", user.getLastName());
        userSummary.put("email", user.getEmail());
        userSummary.put("role", user.getRole().name());
        return userSummary;
    }
}