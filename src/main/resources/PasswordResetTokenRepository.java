package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.PasswordResetToken;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    Optional<PasswordResetToken> findByUser(User user);
    
    Optional<PasswordResetToken> findByUserEmail(String email);
    
    void deleteByUser(User user);
    
    void deleteByUserEmail(String email);
    
    void deleteByExpiryDateBefore(LocalDateTime dateTime);
    
    // Find valid (unused and not expired) tokens
    Optional<PasswordResetToken> findByTokenAndUsedFalseAndExpiryDateAfter(String token, LocalDateTime currentTime);
}