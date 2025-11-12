package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    // ===== YOUR EXISTING METHODS (unchanged) =====
    
    List<Appointment> findByPatient(User patient);
    List<Appointment> findByPatientId(Long patientId);
    List<Appointment> findByStatus(Appointment.Status status);
    List<Appointment> findByPatientAndAppointmentDateAfter(User patient, LocalDateTime date);
    
    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = CURRENT_DATE")
    List<Appointment> findTodaysAppointments();
    
    List<Appointment> findByAppointmentDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    long countByPatient(User patient);
    List<Appointment> findTop5ByPatientOrderByCreatedAtDesc(User patient);
    
    // ===== FIXED METHODS FOR ADMIN DASHBOARD =====
    
    // Bridge methods to match AdminService expectations
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :userId ORDER BY a.appointmentDate DESC")
    List<Appointment> findByUserIdOrderByScheduledDateDesc(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    // Count methods for dashboard stats
    @Query("SELECT COUNT(a) FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    long countByScheduledDate(@Param("date") LocalDate date);
    
    // Status counts using enum values
    long countByStatus(Appointment.Status status);
    
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status = :status AND a.createdAt BETWEEN :start AND :end")
    long countByStatusAndCreatedAtBetween(@Param("status") Appointment.Status status, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Pagination methods for admin dashboard with proper field names
    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    Page<Appointment> findByScheduledDate(@Param("date") LocalDate date, Pageable pageable);
    
    Page<Appointment> findByStatus(Appointment.Status status, Pageable pageable);
    
    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date AND a.status = :status")
    Page<Appointment> findByScheduledDateAndStatus(@Param("date") LocalDate date, @Param("status") Appointment.Status status, Pageable pageable);
    
    // Check for scheduling conflicts using appointmentDate
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE " +
           "DATE(a.appointmentDate) = :date AND HOUR(a.appointmentDate) = HOUR(:time) AND MINUTE(a.appointmentDate) = MINUTE(:time)")
    boolean existsByScheduledDateAndScheduledTime(@Param("date") LocalDate date, @Param("time") LocalTime time);
    
    // FIXED: Added missing methods that AdminService expects
    Page<Appointment> findByAppointmentDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, Appointment.Status status, Pageable pageable);
    
    Page<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Search appointments by reason or notes
    @Query("SELECT a FROM Appointment a WHERE " +
           "LOWER(a.reason) LIKE LOWER(CONCAT('%', :reason, '%')) OR " +
           "LOWER(a.notes) LIKE LOWER(CONCAT('%', :notes, '%'))")
    List<Appointment> findByReasonContainingOrNotesContaining(@Param("reason") String reason, @Param("notes") String notes);
    
    // REMOVED: Problematic query methods that were using incorrect syntax
    // These caused compilation errors with CAST and string conversion
    
    // Additional useful methods for admin dashboard
    @Query("SELECT a FROM Appointment a WHERE a.patient.firstName LIKE %:name% OR a.patient.lastName LIKE %:name%")
    List<Appointment> findByPatientNameContaining(@Param("name") String name);
    
    // Get appointments by date with pagination
    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    Page<Appointment> findByAppointmentDate(@Param("date") LocalDate date, Pageable pageable);
    
    // Get today's appointments count
    @Query("SELECT COUNT(a) FROM Appointment a WHERE DATE(a.appointmentDate) = CURRENT_DATE")
    long countTodaysAppointments();
    
    // Find appointments by patient email
    @Query("SELECT a FROM Appointment a WHERE a.patient.email = :email")
    List<Appointment> findByPatientEmail(@Param("email") String email);
}