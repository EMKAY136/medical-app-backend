// com/medicalapp/medical_app_backend/repository/SecuritySettingsRepository.java
package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.SecuritySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecuritySettingsRepository extends JpaRepository<SecuritySettings, Long> {
    Optional<SecuritySettings> findByEmail(String email);
    boolean existsByEmail(String email);
}