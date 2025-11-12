package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // ===== YOUR EXISTING METHODS (unchanged) =====
    
    // Find user by username (for login)
    Optional<User> findByUsername(String username);
    
    // Find user by email (for login/signup validation)  
    Optional<User> findByEmail(String email);
    
    // Check if username exists (for signup validation)
    boolean existsByUsername(String username);
    
    // Check if email exists (for signup validation)
    boolean existsByEmail(String email);
    
    // Find users by role (if needed later)
    List<User> findByRole(User.Role role);
    
    // ===== CORRECTED METHODS FOR ADMIN DASHBOARD =====
    
    /**
     * Search users by name or email with pagination (CORRECTED - simplified method name)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> findBySearchTerm(@Param("search") String search, Pageable pageable);
    
    /**
     * Search users by name or email without pagination (CORRECTED - simplified method name)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> findBySearchTerm(@Param("search") String search);
    
    /**
     * Count users created between dates (for dashboard statistics)
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Find users created after a specific date
     */
    List<User> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Find users by role with pagination (useful for admin management)
     */
    Page<User> findByRole(User.Role role, Pageable pageable);
    
    /**
     * Search users by full name (firstName + lastName combined)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :fullName, '%'))")
    List<User> findByFullNameContaining(@Param("fullName") String fullName);
    
    /**
     * Find users by email domain (useful for organization filtering)
     */
    @Query("SELECT u FROM User u WHERE u.email LIKE CONCAT('%@', :domain)")
    List<User> findByEmailDomain(@Param("domain") String domain);
    
    /**
     * Count active users (CORRECTED - your User entity doesn't have 'enabled' field, 
     * but implements isEnabled() method which always returns true)
     * This counts all users since they're all considered "enabled"
     */
    @Query("SELECT COUNT(u) FROM User u")
    long countActiveUsers();
    
    /**
     * Find recently registered users (for admin dashboard)
     */
    @Query("SELECT u FROM User u WHERE u.createdAt >= :since ORDER BY u.createdAt DESC")
    List<User> findRecentUsers(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Advanced search for admin dashboard
     */
    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:role IS NULL OR u.role = :role)")
    Page<User> findBySearchAndRole(@Param("search") String search, @Param("role") User.Role role, Pageable pageable);
    
    /**
     * REMOVED - findByBloodType method because your User entity doesn't have bloodType field
     */
    
    /**
     * Count users by role (for dashboard statistics)
     */
    long countByRole(User.Role role);
    
    /**
     * CORRECTED - Find users with appointments count 
     * (Your User entity doesn't have appointments relationship, so this needs to be done differently)
     * This will be handled by a service method combining UserRepository and AppointmentRepository
     */
    
    /**
     * CORRECTED - Find users without recent appointments
     * (Your User entity doesn't have appointments relationship, so this needs to be done differently)
     * This will be handled by a service method combining UserRepository and AppointmentRepository
     */
    
    /**
     * CORRECTED - Custom query to get user statistics by date range
     */
    @Query("SELECT " +
           "COUNT(u) as totalUsers, " +
           "COUNT(CASE WHEN u.createdAt >= :since THEN 1 END) as newUsers " +
           "FROM User u WHERE u.createdAt BETWEEN :start AND :end")
    Object[] getUserStatistics(@Param("start") LocalDateTime start, 
                              @Param("end") LocalDateTime end, 
                              @Param("since") LocalDateTime since);
    
    // ===== ADDITIONAL METHODS BASED ON YOUR USER ENTITY FIELDS =====
    
    /**
     * Find users by email verification status
     */
    List<User> findByEmailVerified(boolean emailVerified);
    
    /**
     * Find users by phone verification status
     */
    List<User> findByPhoneVerified(boolean phoneVerified);
    
    /**
     * Find users with two-factor authentication enabled
     */
    List<User> findByTwoFactorEnabled(boolean twoFactorEnabled);
    
    /**
     * Find users who have logged in recently
     */
    List<User> findByLastLoginAfter(LocalDateTime since);
    
    /**
     * Find users by device platform (for push notifications)
     */
    List<User> findByDevicePlatform(String devicePlatform);
    
    /**
     * Count unverified users (either email or phone not verified)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.emailVerified = false OR " +
           "(u.phone IS NOT NULL AND u.phoneVerified = false)")
    long countUnverifiedUsers();
    
    /**
     * Find users with backup email
     */
    @Query("SELECT u FROM User u WHERE u.backupEmail IS NOT NULL")
    List<User> findUsersWithBackupEmail();
    
    /**
     * Search by phone number
     */
    List<User> findByPhoneContaining(String phone);
    
    /**
     * Advanced verification status search
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:emailVerified IS NULL OR u.emailVerified = :emailVerified) AND " +
           "(:phoneVerified IS NULL OR u.phoneVerified = :phoneVerified) AND " +
           "(:twoFactorEnabled IS NULL OR u.twoFactorEnabled = :twoFactorEnabled)")
    Page<User> findByVerificationStatus(@Param("emailVerified") Boolean emailVerified,
                                      @Param("phoneVerified") Boolean phoneVerified,
                                      @Param("twoFactorEnabled") Boolean twoFactorEnabled,
                                      Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        @Param("search") String firstName, 
        @Param("search") String lastName, 
        @Param("search") String email, 
        Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        @Param("search") String firstName, 
        @Param("search") String lastName, 
        @Param("search") String email);
}