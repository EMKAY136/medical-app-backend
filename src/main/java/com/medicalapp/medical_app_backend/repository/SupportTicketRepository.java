// SupportTicketRepository.java
package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.SupportTicket;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    // Find tickets by user ordered by creation date (newest first)
    List<SupportTicket> findByUserOrderByCreatedAtDesc(User user);

    // Find tickets by status
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus status);

    // Find tickets by user within date range
    @Query("SELECT t FROM SupportTicket t WHERE t.user = :user AND t.createdAt BETWEEN :startDate AND :endDate")
    List<SupportTicket> findByUserAndDateRange(@Param("user") User user, 
                                              @Param("startDate") LocalDateTime startDate, 
                                              @Param("endDate") LocalDateTime endDate);

    // Count open tickets by user with specific statuses
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.user = :user AND t.status IN :statuses")
    long countOpenTicketsByUser(@Param("user") User user, @Param("statuses") List<SupportTicket.TicketStatus> statuses);

    // Find tickets that need first response (no agent response yet)
    @Query("SELECT t FROM SupportTicket t WHERE t.firstResponseAt IS NULL AND t.status = 'OPEN' ORDER BY t.createdAt ASC")
    List<SupportTicket> findTicketsNeedingFirstResponse();

    // Find tickets assigned to specific agent
    List<SupportTicket> findByAssignedToOrderByCreatedAtDesc(String assignedTo);

    // Find tickets by priority
    List<SupportTicket> findByPriorityOrderByCreatedAtDesc(SupportTicket.Priority priority);

    // Find tickets by user and status
    List<SupportTicket> findByUserAndStatusOrderByCreatedAtDesc(User user, SupportTicket.TicketStatus status);

    // Find recent tickets (last 7 days)
    @Query("SELECT t FROM SupportTicket t WHERE t.createdAt >= :dateLimit ORDER BY t.createdAt DESC")
    List<SupportTicket> findRecentTickets(@Param("dateLimit") LocalDateTime dateLimit);

    // Count tickets by status
    long countByStatus(SupportTicket.TicketStatus status);

    // Find overdue tickets (created more than X hours ago without first response)
    @Query("SELECT t FROM SupportTicket t WHERE t.firstResponseAt IS NULL AND t.createdAt < :overdueLimit AND t.status = 'OPEN'")
    List<SupportTicket> findOverdueTickets(@Param("overdueLimit") LocalDateTime overdueLimit);

    // Find tickets by category
    List<SupportTicket> findByCategoryOrderByCreatedAtDesc(String category);

    // Count tickets created today
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE DATE(t.createdAt) = CURRENT_DATE")
    long countTicketsCreatedToday();

    // Find tickets resolved within time range
    @Query("SELECT t FROM SupportTicket t WHERE t.resolvedAt BETWEEN :startDate AND :endDate ORDER BY t.resolvedAt DESC")
    List<SupportTicket> findResolvedTicketsInRange(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);

    // Calculate average resolution time (in hours)
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, t.createdAt, t.resolvedAt)) FROM SupportTicket t WHERE t.status = 'RESOLVED' AND t.resolvedAt IS NOT NULL")
    Double calculateAverageResolutionTimeHours();

    // Find tickets by user and priority
    List<SupportTicket> findByUserAndPriorityOrderByCreatedAtDesc(User user, SupportTicket.Priority priority);

    // Find tickets that need attention (high priority or overdue)
    @Query("SELECT t FROM SupportTicket t WHERE (t.priority = 'HIGH' OR (t.firstResponseAt IS NULL AND t.createdAt < :overdueLimit)) AND t.status != 'RESOLVED' AND t.status != 'CLOSED' ORDER BY t.priority DESC, t.createdAt ASC")
    List<SupportTicket> findTicketsNeedingAttention(@Param("overdueLimit") LocalDateTime overdueLimit);

    // Search tickets by subject or description
    @Query("SELECT t FROM SupportTicket t WHERE LOWER(t.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY t.createdAt DESC")
    List<SupportTicket> searchTickets(@Param("searchTerm") String searchTerm);
}