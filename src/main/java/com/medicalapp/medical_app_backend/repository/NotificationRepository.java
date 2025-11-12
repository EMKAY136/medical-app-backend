package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.Notification;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Find notifications by user
    List<Notification> findByUser(User user);
    
    // Find notifications by user with pagination
    Page<Notification> findByUser(User user, Pageable pageable);
    
    // Find notifications by user ID
    List<Notification> findByUserId(Long userId);

    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    
    // Find notifications by user ID with pagination
    Page<Notification> findByUserId(Long userId, Pageable pageable);
    
    // Find unread notifications for user
    List<Notification> findByUserAndReadFalse(User user);
    
    // Find unread notifications for user with pagination
    Page<Notification> findByUserAndReadFalse(User user, Pageable pageable);
    
    // Find notifications by type for user
    List<Notification> findByUserAndType(User user, String type);
    
    // Find notifications by type for user with pagination
    Page<Notification> findByUserAndType(User user, String type, Pageable pageable);
    
    // Find notifications by priority
    List<Notification> findByUserAndPriority(User user, Notification.Priority priority);
    
    // Find recent notifications (last 30 days)
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(@Param("user") User user, @Param("since") LocalDateTime since);
    
    // Count unread notifications for user
    long countByUserAndReadFalse(User user);
    
    // Count total notifications for user
    long countByUser(User user);
    
    // Count notifications by type for user
    long countByUserAndType(User user, String type);
    
    // Find notifications by delivery status
    List<Notification> findByDeliveryStatus(Notification.DeliveryStatus status);
    
    // Find failed notifications that should be retried
    @Query("SELECT n FROM Notification n WHERE n.deliveryStatus = 'FAILED' AND n.deliveryAttempts < 3")
    List<Notification> findNotificationsForRetry();
    
    // Find notifications by reference
    List<Notification> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
    
    // Find notifications sent within a time range
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.sentAt BETWEEN :start AND :end ORDER BY n.sentAt DESC")
    List<Notification> findNotificationsSentBetween(
        @Param("user") User user, 
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );
    
    // Mark notification as read
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt, n.updatedAt = :updatedAt WHERE n.id = :id AND n.user = :user")
    int markAsRead(@Param("id") Long id, @Param("user") User user, @Param("readAt") LocalDateTime readAt, @Param("updatedAt") LocalDateTime updatedAt);
    
    // Mark all notifications as read for user
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt, n.updatedAt = :updatedAt WHERE n.user = :user AND n.read = false")
    int markAllAsRead(@Param("user") User user, @Param("readAt") LocalDateTime readAt, @Param("updatedAt") LocalDateTime updatedAt);
    
    // Delete old notifications (older than specified date)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :before")
    int deleteOldNotifications(@Param("before") LocalDateTime before);
    
    // Delete read notifications older than specified date
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :before")
    int deleteOldReadNotifications(@Param("before") LocalDateTime before);
    
    // Find top N recent notifications for user
    @Query("SELECT n FROM Notification n WHERE n.user = :user ORDER BY n.createdAt DESC")
    List<Notification> findTopRecentNotifications(@Param("user") User user, Pageable pageable);
    
    // Find notifications by user and multiple types
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.type IN :types ORDER BY n.createdAt DESC")
    List<Notification> findByUserAndTypeIn(@Param("user") User user, @Param("types") List<String> types);
    
    // Find unsent notifications
    List<Notification> findBySentFalse();
    
    // Find notifications pending delivery
    List<Notification> findByDeliveryStatusAndSentFalse(Notification.DeliveryStatus status);
    
    // Update delivery status
    @Modifying
    @Query("UPDATE Notification n SET n.deliveryStatus = :status, n.deliveryAttempts = :attempts, " +
           "n.lastDeliveryAttempt = :attemptTime, n.deliveryError = :error, n.updatedAt = :updatedAt " +
           "WHERE n.id = :id")
    int updateDeliveryStatus(
        @Param("id") Long id,
        @Param("status") Notification.DeliveryStatus status,
        @Param("attempts") int attempts,
        @Param("attemptTime") LocalDateTime attemptTime,
        @Param("error") String error,
        @Param("updatedAt") LocalDateTime updatedAt
    );
    
    // Mark notification as sent
    @Modifying
    @Query("UPDATE Notification n SET n.sent = true, n.sentAt = :sentAt, n.deliveryStatus = 'DELIVERED', n.updatedAt = :updatedAt WHERE n.id = :id")
    int markAsSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt, @Param("updatedAt") LocalDateTime updatedAt);
    
    // Get notification statistics for user
    @Query("SELECT n.type, COUNT(n) as count, " +
           "SUM(CASE WHEN n.read = true THEN 1 ELSE 0 END) as readCount, " +
           "SUM(CASE WHEN n.sent = true THEN 1 ELSE 0 END) as sentCount " +
           "FROM Notification n WHERE n.user = :user GROUP BY n.type")
    List<Object[]> getNotificationStatsByType(@Param("user") User user);
    
    // Find notifications created today
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND DATE(n.createdAt) = CURRENT_DATE ORDER BY n.createdAt DESC")
    List<Notification> findTodaysNotifications(@Param("user") User user);
    
    // Find critical unread notifications
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.read = false AND n.priority IN ('HIGH', 'URGENT') ORDER BY n.priority DESC, n.createdAt DESC")
    List<Notification> findCriticalUnreadNotifications(@Param("user") User user);
    
    // Check if notification exists for reference
    boolean existsByUserAndReferenceTypeAndReferenceId(User user, String referenceType, Long referenceId);
    
    // Find duplicate notifications (same title and type within time window)
    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.title = :title AND n.type = :type AND n.createdAt >= :since")
    List<Notification> findPossibleDuplicates(
        @Param("user") User user,
        @Param("title") String title,
        @Param("type") String type,
        @Param("since") LocalDateTime since
    );
}