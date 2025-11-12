// ChatMessageRepository.java - COMPLETE WITH ALL REQUIRED METHODS
package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.ChatMessage;
import com.medicalapp.medical_app_backend.entity.SupportTicket;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // ========== BASIC QUERIES ==========
    
    // Find recent chat messages by user (standard JPA method)
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentChatMessagesByUser(@Param("user") User user);

    // Find messages by ticket ordered by creation time (ascending)
    List<ChatMessage> findByTicketOrderByCreatedAtAsc(SupportTicket ticket);

    // Find messages by user ordered by creation time (ascending)
    List<ChatMessage> findByUserOrderByCreatedAtAsc(User user);

    // ========== METHODS REQUIRED BY SUPPORT SERVICE (CRITICAL) ==========
    
    // Method called in createPatientInfo() with limit parameter
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user ORDER BY m.createdAt DESC")
    List<ChatMessage> findTopByUserOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);
    
    // Helper method to call with int limit
    default List<ChatMessage> findByUserOrderByCreatedAtDesc(User user, int limit) {
        return findTopByUserOrderByCreatedAtDesc(user, PageRequest.of(0, limit));
    }

    // Method called in createChatInfo() to get last message from ticket
    @Query("SELECT m FROM ChatMessage m WHERE m.ticket = :ticket ORDER BY m.createdAt DESC")
    List<ChatMessage> findTopByTicketOrderByCreatedAtDesc(@Param("ticket") SupportTicket ticket, Pageable pageable);
    
    // Helper method to call with int limit
    default List<ChatMessage> findByTicketOrderByCreatedAtDesc(SupportTicket ticket, int limit) {
        return findTopByTicketOrderByCreatedAtDesc(ticket, PageRequest.of(0, limit));
    }

    // ========== USER & UNREAD MESSAGES ==========
    
    // Count unread messages for user from support agents
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.user = :user AND m.isRead = false AND m.senderType = 'SUPPORT_AGENT'")
    long countUnreadMessagesForUser(@Param("user") User user);

    // Find unread messages by user
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user AND m.isRead = false AND m.senderType != 'USER' ORDER BY m.createdAt DESC")
    List<ChatMessage> findUnreadMessagesByUser(@Param("user") User user);

    // Mark messages as read
    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.user = :user AND m.isRead = false AND m.senderType != 'USER'")
    void markAllMessagesAsReadForUser(@Param("user") User user);

    // ========== SENDER TYPE QUERIES ==========
    
    // Find messages by sender type
    List<ChatMessage> findBySenderTypeOrderByCreatedAtDesc(ChatMessage.SenderType senderType);

    // Find messages by user and sender type
    List<ChatMessage> findByUserAndSenderTypeOrderByCreatedAtDesc(User user, ChatMessage.SenderType senderType);

    // Find bot messages
    @Query("SELECT m FROM ChatMessage m WHERE m.senderType = 'BOT' ORDER BY m.createdAt DESC")
    List<ChatMessage> findBotMessages();

    // ========== DATE RANGE QUERIES ==========
    
    // Find messages within date range
    @Query("SELECT m FROM ChatMessage m WHERE m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt DESC")
    List<ChatMessage> findMessagesByDateRange(@Param("startDate") LocalDateTime startDate, 
                                             @Param("endDate") LocalDateTime endDate);

    // Find messages by user in date range
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user AND m.createdAt BETWEEN :startDate AND :endDate ORDER BY m.createdAt ASC")
    List<ChatMessage> findByUserAndDateRange(@Param("user") User user, 
                                           @Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);

    // Count messages created today
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.createdAt >= :startOfDay AND m.createdAt < :endOfDay")
    long countMessagesCreatedToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    // ========== SUPPORT AGENT QUERIES ==========
    
    // Get recent user messages for support dashboard
    @Query("SELECT m FROM ChatMessage m WHERE m.senderType = 'USER' AND m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentUserMessages(@Param("since") LocalDateTime since);

    // Find messages needing attention (user messages without agent response)
    @Query("SELECT m FROM ChatMessage m WHERE m.senderType = 'USER' AND NOT EXISTS (SELECT a FROM ChatMessage a WHERE a.user = m.user AND a.senderType = 'SUPPORT_AGENT' AND a.createdAt > m.createdAt) ORDER BY m.createdAt ASC")
    List<ChatMessage> findMessagesNeedingResponse();

    // Find users needing response
    @Query("SELECT DISTINCT m.user FROM ChatMessage m WHERE m.senderType = 'USER' AND m.createdAt > :since ORDER BY m.createdAt DESC")
    List<User> findUsersWithRecentMessages(@Param("since") LocalDateTime since);

    // Find active conversations (conversations with messages in last 24 hours)
    @Query("SELECT DISTINCT m.user FROM ChatMessage m WHERE m.createdAt >= :since GROUP BY m.user ORDER BY MAX(m.createdAt) DESC")
    List<User> findActiveConversations(@Param("since") LocalDateTime since);

    // ========== CONVERSATION QUERIES ==========
    
    // Get conversation between user and agents
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user AND (m.senderType = 'USER' OR m.senderType = 'SUPPORT_AGENT' OR m.senderType = 'BOT') ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversationByUser(@Param("user") User user);

    // Find messages by ticket and sender type
    List<ChatMessage> findByTicketAndSenderTypeOrderByCreatedAtAsc(SupportTicket ticket, ChatMessage.SenderType senderType);

    // Find latest message for each user
    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (SELECT MAX(m2.id) FROM ChatMessage m2 GROUP BY m2.user) ORDER BY m.createdAt DESC")
    List<ChatMessage> findLatestMessagePerUser();

    // Get conversation summary (last N messages per user)
    @Query(value = "SELECT * FROM chat_messages m WHERE m.user_id = :userId ORDER BY m.created_at DESC LIMIT :limit", nativeQuery = true)
    List<ChatMessage> findConversationSummary(@Param("userId") Long userId, @Param("limit") int limit);

    // ========== SEARCH QUERIES ==========
    
    // Search messages by content
    @Query("SELECT m FROM ChatMessage m WHERE LOWER(m.message) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY m.createdAt DESC")
    List<ChatMessage> searchMessagesByContent(@Param("searchTerm") String searchTerm);

    // Find messages that mention urgent keywords
    @Query("SELECT m FROM ChatMessage m WHERE LOWER(m.message) LIKE '%urgent%' OR LOWER(m.message) LIKE '%emergency%' OR LOWER(m.message) LIKE '%critical%' OR LOWER(m.message) LIKE '%help%' ORDER BY m.createdAt DESC")
    List<ChatMessage> findUrgentMessages();

    // ========== STATISTICS QUERIES ==========
    
    // Count messages by user
    long countByUser(User user);

    // Get chat statistics
    @Query("SELECT m.senderType, COUNT(m) FROM ChatMessage m GROUP BY m.senderType")
    List<Object[]> getChatStatistics();

    // Count total messages
    @Query("SELECT COUNT(m) FROM ChatMessage m")
    long getTotalMessageCount();

    // Count unread system messages
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.user = :user AND m.isRead = false AND m.senderType = 'SYSTEM'")
    long countUnreadSystemMessages(@Param("user") User user);

    // ========== BULK OPERATIONS ==========
    
    // Find messages by multiple users
    @Query("SELECT m FROM ChatMessage m WHERE m.user IN :users ORDER BY m.createdAt DESC")
    List<ChatMessage> findMessagesByUsers(@Param("users") List<User> users);

    // Delete old messages
    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.createdAt < :cutoffDate")
    void deleteOldMessages(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== PAGINATION SUPPORT ==========
    
    // Find messages by user with pagination
    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user ORDER BY m.createdAt DESC")
    List<ChatMessage> findByUserWithPagination(@Param("user") User user, Pageable pageable);
}