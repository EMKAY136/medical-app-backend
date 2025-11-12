package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.MedicalResult;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MedicalResultRepository extends JpaRepository<MedicalResult, Long> {

    // ========== BASIC QUERIES ==========
    
    // Find results by patient
    List<MedicalResult> findByPatient(User patient);
    
    // Find results by patient ordered by test date
    List<MedicalResult> findByPatientOrderByTestDateDesc(User patient);
    
    // Find results by patient ID
    List<MedicalResult> findByPatientId(Long patientId);
    
    // Find results by patient ID ordered by creation date
    List<MedicalResult> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    
    // Find top 5 results by patient ordered by test date
    List<MedicalResult> findTop5ByPatientOrderByTestDateDesc(User patient);
    
    // ========== FILTERED QUERIES ==========
    
    // Find visible and downloadable results
    @Query("SELECT mr FROM MedicalResult mr WHERE mr.patient = :patient " +
           "AND mr.isVisible = true AND mr.isDownloadable = true " +
           "ORDER BY mr.testDate DESC")
    List<MedicalResult> findVisibleAndDownloadableByPatient(@Param("patient") User patient);
    
    // Find downloadable results by patient (backward compatibility)
    @Query("SELECT mr FROM MedicalResult mr WHERE mr.patient = :patient AND mr.isDownloadable = true")
    List<MedicalResult> findDownloadableResultsByPatient(@Param("patient") User patient);
    
    // Find results by patient and test name containing
    List<MedicalResult> findByPatientAndTestNameContainingIgnoreCase(User patient, String testName);
    
    // Find results by patient and test date range
    List<MedicalResult> findByPatientAndTestDateBetween(User patient, LocalDateTime start, LocalDateTime end);
    
    // ========== STATUS-BASED QUERIES ==========
    
    // Find results by status (enum)
    @Query("SELECT mr FROM MedicalResult mr WHERE mr.status = :status")
    List<MedicalResult> findByResultStatus(@Param("status") MedicalResult.ResultStatus status);
    
    // Find results by status with pagination
    @Query("SELECT mr FROM MedicalResult mr WHERE mr.status = :status")
    Page<MedicalResult> findByResultStatus(@Param("status") MedicalResult.ResultStatus status, Pageable pageable);
    
    // Find results by test type
    List<MedicalResult> findByTestType(String testType);
    
    // Find results by test type with pagination
    Page<MedicalResult> findByTestType(String testType, Pageable pageable);
    
    // ========== COUNTING QUERIES ==========
    
    // Count results by patient
    long countByPatient(User patient);
    
    // Count results by patient ID
    long countByPatientId(Long patientId);
    
    // Count results by patient and status
    @Query("SELECT COUNT(mr) FROM MedicalResult mr WHERE mr.patient = :patient AND mr.status = :status")
    long countByPatientAndStatus(@Param("patient") User patient, 
                                  @Param("status") MedicalResult.ResultStatus status);
    
    // Count results by status
    @Query("SELECT COUNT(mr) FROM MedicalResult mr WHERE mr.status = :status")
    long countByStatus(@Param("status") MedicalResult.ResultStatus status);
    
    // Count distinct patients
    @Query("SELECT COUNT(DISTINCT mr.patient) FROM MedicalResult mr")
    long countDistinctPatients();
    
    // Count results by patient and test date range
    long countByPatientAndTestDateBetween(User patient, LocalDateTime startDate, LocalDateTime endDate);
    
    // Count results created after a specific date
    long countByCreatedAtAfter(LocalDateTime date);
    
    // Count results by creation date range
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Count results in current month (for a patient)
    @Query("SELECT COUNT(mr) FROM MedicalResult mr WHERE mr.patient = :patient " +
           "AND YEAR(mr.testDate) = YEAR(CURRENT_DATE) " +
           "AND MONTH(mr.testDate) = MONTH(CURRENT_DATE)")
    long countByPatientInCurrentMonth(@Param("patient") User patient);
    
    // Count results today
    @Query("SELECT COUNT(mr) FROM MedicalResult mr WHERE DATE(mr.testDate) = CURRENT_DATE")
    long countResultsToday();
    
    // Count results in current month (system-wide)
    @Query("SELECT COUNT(mr) FROM MedicalResult mr WHERE " +
           "YEAR(mr.testDate) = YEAR(CURRENT_DATE) " +
           "AND MONTH(mr.testDate) = MONTH(CURRENT_DATE)")
    long countResultsInCurrentMonth();
    
    // ========== SEARCH QUERIES ==========
    
    // Search results by test type, result content, or notes
    @Query("SELECT mr FROM MedicalResult mr WHERE " +
           "LOWER(mr.testType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(mr.result) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(mr.notes) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<MedicalResult> searchResults(@Param("query") String query);
    
    // Search results by patient with keyword
    @Query("SELECT mr FROM MedicalResult mr WHERE mr.patient = :patient AND (" +
           "LOWER(mr.testName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(mr.testType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(mr.result) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(mr.notes) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<MedicalResult> searchByPatientAndKeyword(@Param("patient") User patient, 
                                                   @Param("keyword") String keyword);
    
    // ========== OPTIMIZED QUERIES WITH FETCH JOINS ==========
    
    // Find all with patient eagerly loaded (prevents N+1 query problem)
    @Query("SELECT mr FROM MedicalResult mr JOIN FETCH mr.patient ORDER BY mr.testDate DESC")
    List<MedicalResult> findAllWithPatient();
    
    // Find all with patient eagerly loaded - paginated
    @Query("SELECT mr FROM MedicalResult mr JOIN FETCH mr.patient")
    Page<MedicalResult> findAllWithPatient(Pageable pageable);
    
    // Find by patient with appointment eagerly loaded
    @Query("SELECT mr FROM MedicalResult mr LEFT JOIN FETCH mr.appointment " +
           "WHERE mr.patient = :patient ORDER BY mr.testDate DESC")
    List<MedicalResult> findByPatientWithAppointment(@Param("patient") User patient);
    
    // ========== ADMIN DASHBOARD QUERIES ==========
    
    // Get recent results across all patients
    @Query("SELECT mr FROM MedicalResult mr JOIN FETCH mr.patient " +
           "ORDER BY mr.createdAt DESC")
    Page<MedicalResult> findRecentResults(Pageable pageable);
    
    // Find critical results
    @Query("SELECT mr FROM MedicalResult mr JOIN FETCH mr.patient " +
           "WHERE mr.status = 'CRITICAL' OR mr.priority = 'CRITICAL' " +
           "ORDER BY mr.testDate DESC")
    List<MedicalResult> findCriticalResults();
    
    // Find pending results
    @Query("SELECT mr FROM MedicalResult mr JOIN FETCH mr.patient " +
           "WHERE mr.status = 'PENDING' " +
           "ORDER BY mr.testDate ASC")
    List<MedicalResult> findPendingResults();
    
    // Get statistics by month
    @Query("SELECT FUNCTION('YEAR', mr.testDate) as year, " +
           "FUNCTION('MONTH', mr.testDate) as month, " +
           "COUNT(mr) as count " +
           "FROM MedicalResult mr " +
           "WHERE mr.testDate >= :startDate " +
           "GROUP BY FUNCTION('YEAR', mr.testDate), FUNCTION('MONTH', mr.testDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyStatistics(@Param("startDate") LocalDateTime startDate);
    
    // Get results by category with counts
    @Query("SELECT mr.category, COUNT(mr) FROM MedicalResult mr " +
           "GROUP BY mr.category ORDER BY COUNT(mr) DESC")
    List<Object[]> countByCategory();
    
    // Get results by status with counts
    @Query("SELECT mr.status, COUNT(mr) FROM MedicalResult mr " +
           "GROUP BY mr.status")
    List<Object[]> countByStatusGrouped();
}