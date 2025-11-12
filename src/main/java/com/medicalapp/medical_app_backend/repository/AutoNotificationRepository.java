package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.AutoNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AutoNotificationRepository extends JpaRepository<AutoNotification, Long> {

    // Find auto-notifications by trigger type
    List<AutoNotification> findByTrigger(String trigger);
    
    // Find enabled auto-notifications by trigger type
    List<AutoNotification> findByTriggerAndEnabled(String trigger, Boolean enabled);
    
    // Find all enabled auto-notifications
    List<AutoNotification> findByEnabled(Boolean enabled);
    
    // Find auto-notifications by type
    List<AutoNotification> findByType(String type);
    
    // Find auto-notifications by trigger and type
    List<AutoNotification> findByTriggerAndType(String trigger, String type);
    
    // Find auto-notifications created by specific admin
    List<AutoNotification> findByCreatedBy(String createdBy);
    
    // Count enabled auto-notifications
    long countByEnabled(Boolean enabled);
    
    // Count auto-notifications by trigger
    long countByTrigger(String trigger);
    
    // Find most frequently triggered auto-notifications
    @Query("SELECT a FROM AutoNotification a WHERE a.enabled = true ORDER BY a.timesTriggered DESC")
    List<AutoNotification> findMostTriggered();
    
    // Find recently triggered auto-notifications
    @Query("SELECT a FROM AutoNotification a WHERE a.lastTriggered >= :since ORDER BY a.lastTriggered DESC")
    List<AutoNotification> findRecentlyTriggered(@Param("since") LocalDateTime since);
    
    // Find auto-notifications that have never been triggered
    @Query("SELECT a FROM AutoNotification a WHERE a.timesTriggered = 0 AND a.enabled = true")
    List<AutoNotification> findNeverTriggered();
    
    // Check if an auto-notification exists for a specific trigger
    boolean existsByTriggerAndEnabled(String trigger, Boolean enabled);
    
    // Find auto-notifications with delay
    @Query("SELECT a FROM AutoNotification a WHERE a.enabled = true AND a.delayMinutes > 0")
    List<AutoNotification> findWithDelay();
    
    // Get statistics for auto-notifications
    @Query("SELECT a.trigger, COUNT(a), SUM(a.timesTriggered) FROM AutoNotification a GROUP BY a.trigger")
    List<Object[]> getStatsByTrigger();
}