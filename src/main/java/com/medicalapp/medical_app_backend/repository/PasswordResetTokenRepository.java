
package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.PasswordResetToken;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    // Method used in your service: passwordResetTokenRepository.deleteByUser(user)
    @Modifying
    @Transactional
    void deleteByUser(User user);
    
    // Method used in your service: passwordResetTokenRepository.findByUserAndTokenAndUsed(user, code, false)
    Optional<PasswordResetToken> findByUserAndTokenAndUsed(User user, String token, boolean used);
    
    // Method used in your service: passwordResetTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now())
    @Modifying
    @Transactional
    void deleteByExpiryDateBefore(LocalDateTime expiryDate);
    
    // Additional useful methods for future features
    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);
    
    Optional<PasswordResetToken> findByUserAndUsedFalse(User user);
    
    @Query("SELECT COUNT(p) FROM PasswordResetToken p WHERE p.user = :user AND p.createdAt > :since")
    long countByUserAndCreatedAtAfter(@Param("user") User user, @Param("since") LocalDateTime since);
    
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.user = :user")
    void markAllTokensAsUsedForUser(@Param("user") User user);
}

