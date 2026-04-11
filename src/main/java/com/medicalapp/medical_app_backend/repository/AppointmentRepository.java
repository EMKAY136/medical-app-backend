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

    // ── Existing methods (unchanged) ─────────────────────────────────────────

    List<Appointment> findByPatient(User patient);
    List<Appointment> findByPatientId(Long patientId);
    List<Appointment> findByStatus(Appointment.Status status);
    List<Appointment> findByPatientAndAppointmentDateAfter(User patient, LocalDateTime date);

    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = CURRENT_DATE")
    List<Appointment> findTodaysAppointments();

    List<Appointment> findByAppointmentDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    long countByPatient(User patient);
    List<Appointment> findTop5ByPatientOrderByCreatedAtDesc(User patient);

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :userId ORDER BY a.appointmentDate DESC")
    List<Appointment> findByUserIdOrderByScheduledDateDesc(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    long countByScheduledDate(@Param("date") LocalDate date);

    long countByStatus(Appointment.Status status);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status = :status AND a.createdAt BETWEEN :start AND :end")
    long countByStatusAndCreatedAtBetween(
        @Param("status") Appointment.Status status,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    Page<Appointment> findByScheduledDate(@Param("date") LocalDate date, Pageable pageable);

    Page<Appointment> findByStatus(Appointment.Status status, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date AND a.status = :status")
    Page<Appointment> findByScheduledDateAndStatus(
        @Param("date") LocalDate date,
        @Param("status") Appointment.Status status,
        Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE " +
           "DATE(a.appointmentDate) = :date AND HOUR(a.appointmentDate) = HOUR(:time) AND MINUTE(a.appointmentDate) = MINUTE(:time)")
    boolean existsByScheduledDateAndScheduledTime(
        @Param("date") LocalDate date,
        @Param("time") LocalTime time);

    Page<Appointment> findByAppointmentDateBetweenAndStatus(
        LocalDateTime start, LocalDateTime end,
        Appointment.Status status, Pageable pageable);

    Page<Appointment> findByAppointmentDateBetween(
        LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE " +
           "LOWER(a.reason) LIKE LOWER(CONCAT('%', :reason, '%')) OR " +
           "LOWER(a.notes)  LIKE LOWER(CONCAT('%', :notes,  '%'))")
    List<Appointment> findByReasonContainingOrNotesContaining(
        @Param("reason") String reason,
        @Param("notes")  String notes);

    @Query("SELECT a FROM Appointment a WHERE a.patient.firstName LIKE %:name% OR a.patient.lastName LIKE %:name%")
    List<Appointment> findByPatientNameContaining(@Param("name") String name);

    @Query("SELECT a FROM Appointment a WHERE DATE(a.appointmentDate) = :date")
    Page<Appointment> findByAppointmentDate(@Param("date") LocalDate date, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE DATE(a.appointmentDate) = CURRENT_DATE")
    long countTodaysAppointments();

    @Query("SELECT a FROM Appointment a WHERE a.patient.email = :email")
    List<Appointment> findByPatientEmail(@Param("email") String email);

    // ── NEW: Payment status queries ──────────────────────────────────────────

    /** All appointments with a given payment status — used by pending-payments endpoint. */
    List<Appointment> findByPaymentStatus(Appointment.PaymentStatus paymentStatus);

    /** Count by payment status — useful for admin dashboard badges. */
    long countByPaymentStatus(Appointment.PaymentStatus paymentStatus);

    /** All SCHEDULED appointments whose date is in the past — used by scheduled missed-checker. */
    @Query("SELECT a FROM Appointment a WHERE a.status = 'SCHEDULED' AND a.appointmentDate < :now")
    List<Appointment> findScheduledAppointmentsBefore(@Param("now") LocalDateTime now);

    /** Appointments for a patient filtered by payment status. */
    List<Appointment> findByPatientAndPaymentStatus(User patient, Appointment.PaymentStatus paymentStatus);
}