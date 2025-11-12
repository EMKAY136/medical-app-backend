package com.medicalapp.medical_app_backend.config;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class AdminUserInitializer {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @PostConstruct
    public void createDefaultAdmin() {
        try {
            // Check if any admin user exists
            long adminCount = userRepository.countByRole(User.Role.ADMIN);
            
            if (adminCount == 0) {
                System.out.println("No admin user found. Creating default admin...");
                
                User admin = new User();
                
                // Required fields based on your User entity
                admin.setUsername("admin");
                admin.setEmail("admin@hospital.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFirstName("System");
                admin.setLastName("Admin");
                admin.setRole(User.Role.ADMIN);
                
                // Set verification status
                admin.setEmailVerified(true);
                admin.setPhoneVerified(true);
                
                // Optional fields
                admin.setPhone("+1-555-0100");
                admin.setAddress("Hospital Administration");
                admin.setPreferredLanguage("English");
                
                userRepository.save(admin);
                
                System.out.println("=================================");
                System.out.println("DEFAULT ADMIN USER CREATED:");
                System.out.println("Email: admin@hospital.com");
                System.out.println("Password: admin123");
                System.out.println("=================================");
                
            } else {
                System.out.println("Admin user already exists. Skipping creation.");
            }
            
        } catch (Exception e) {
            System.err.println("Error creating admin user: " + e.getMessage());
            e.printStackTrace();
        }
    }
}