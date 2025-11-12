// DataExportRepository.java
package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.DataExport;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataExportRepository extends JpaRepository<DataExport, Long> {

    // ADDED: Missing method that was causing compilation error
    Optional<DataExport> findByIdAndUser(Long id, User user);

    // Find exports by user ordered by most recent
    List<DataExport> findByUserOrderByRequestedAtDesc(User user);

    // Find exports by user and status
    List<DataExport> findByUserAndStatus(User user, DataExport.ExportStatus status);

    // Find exports by user in last N days
    @Query("SELECT e FROM DataExport e WHERE e.user = ?1 AND e.requestedAt >= ?2 ORDER BY e.requestedAt DESC")
    List<DataExport> findByUserAndRequestedAtAfter(User user, LocalDateTime date);

    // Find expired exports for cleanup
    @Query("SELECT e FROM DataExport e WHERE e.expiresAt < ?1 AND e.status = 'COMPLETED'")
    List<DataExport> findExpiredExports(LocalDateTime now);

    // Count exports by user
    long countByUser(User user);

    // Count exports by user and date range
    @Query("SELECT COUNT(e) FROM DataExport e WHERE e.user = ?1 AND e.requestedAt BETWEEN ?2 AND ?3")
    long countByUserAndDateRange(User user, LocalDateTime startDate, LocalDateTime endDate);

    // Find large exports (for cleanup/monitoring)
    @Query("SELECT e FROM DataExport e WHERE e.fileSize LIKE '%MB' ORDER BY e.requestedAt DESC")
    List<DataExport> findLargeExports();
}