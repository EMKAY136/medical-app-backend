package com.medicalapp.medical_app_backend.repository;

import com.medicalapp.medical_app_backend.entity.TestResult;
import com.medicalapp.medical_app_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByUser(User user);
    List<TestResult> findByStatus(String status);
}