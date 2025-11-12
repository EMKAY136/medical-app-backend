package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {
    
    // Methods needed by AdminService
    List<Result> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    long countByCreatedAtAfter(LocalDateTime date);
    
    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    long countByUserId(Long userId);
    
    Page<Result> findByStatus(String status, Pageable pageable);
    
    Page<Result> findByTestType(String testType, Pageable pageable);
    
    Page<Result> findByStatusAndTestType(String status, String testType, Pageable pageable);
    
    @Query("SELECT r FROM Result r WHERE " +
           "LOWER(r.testType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.result) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.notes) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Result> findByTestTypeContainingIgnoreCaseOrResultContainingIgnoreCaseOrNotesContainingIgnoreCase(
        @Param("query") String testType, @Param("query") String result, @Param("query") String notes);
    
    // Additional useful methods
    List<Result> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    List<Result> findByUserIdAndTestTypeOrderByCreatedAtDesc(Long userId, String testType);
    
    @Query("SELECT r FROM Result r WHERE r.user.id = :userId AND r.testDate >= :date ORDER BY r.createdAt DESC")
    List<Result> findRecentResultsByUserId(@Param("userId") Long userId, @Param("date") java.time.LocalDate date);
    
    // Statistics methods
    @Query("SELECT r.testType, COUNT(r) FROM Result r WHERE r.user.id = :userId GROUP BY r.testType")
    List<Object[]> getTestTypeStatsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT r.status, COUNT(r) FROM Result r WHERE r.user.id = :userId GROUP BY r.status")
    List<Object[]> getStatusStatsByUserId(@Param("userId") Long userId);
    
    // Find abnormal results for alerts
    List<Result> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses);
    
    // Count by doctor (for admin analytics)
    long countByDoctorName(String doctorName);
    
    @Query("SELECT r.doctorName, COUNT(r) FROM Result r GROUP BY r.doctorName")
    List<Object[]> getResultCountByDoctor();
}