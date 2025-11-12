package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private ContactVerificationService contactVerificationService;

    // Get user profile
    public Map<String, Object> getUserProfile(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            response.put("success", true);
            response.put("user", createUserResponse(user));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching profile: " + e.getMessage());
        }

        return response;
    }

    // Get user profile by ID (for admin dashboard)
    public Map<String, Object> getUserProfileById(Long userId, UserDetails userDetails) {
    Map<String, Object> response = new HashMap<>();

    try {
        // Check if requesting user is admin
        Optional<User> requestingUserOpt = userRepository.findByUsername(userDetails.getUsername());
        if (requestingUserOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Requesting user not found");
            return response;
        }

        User requestingUser = requestingUserOpt.get();
        if (requestingUser.getRole() != User.Role.ADMIN) {
            response.put("success", false);
            response.put("message", "Unauthorized: Admin access required");
            return response;
        }

        // Fetch requested user
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return response;
        }

        User user = userOpt.get();
        
        // ✅ ADD THIS LOGGING
        System.out.println("===========================================");
        System.out.println("📊 getUserProfileById for userId: " + userId);
        System.out.println("First Name: " + user.getFirstName());
        System.out.println("Last Name: " + user.getLastName());
        System.out.println("Email: " + user.getEmail());
        System.out.println("Gender: " + user.getGender());
        System.out.println("DOB: " + user.getDob());
        System.out.println("Height: " + user.getHeight());
        System.out.println("Weight: " + user.getWeight());
        System.out.println("Blood Group: " + user.getBloodGroup());
        System.out.println("Genotype: " + user.getGenotype());
        System.out.println("===========================================");
        
        response.put("success", true);
        response.put("user", createUserResponse(user)); // ← This should include ALL fields
        
        return response;

    } catch (Exception e) {
        response.put("success", false);
        response.put("message", "Error fetching profile: " + e.getMessage());
    }

    return response;
}


    // Update user profile - Simple method with health fields support
    public Map<String, Object> updateUserProfileSimple(Map<String, Object> updates, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            boolean updated = false;

            // Update basic fields
            if (updates.containsKey("firstName") && updates.get("firstName") != null) {
                String firstName = updates.get("firstName").toString().trim();
                if (!firstName.isEmpty()) {
                    user.setFirstName(firstName);
                    updated = true;
                }
            }

            if (updates.containsKey("lastName") && updates.get("lastName") != null) {
                String lastName = updates.get("lastName").toString().trim();
                if (!lastName.isEmpty()) {
                    user.setLastName(lastName);
                    updated = true;
                }
            }

            if (updates.containsKey("phone")) {
                Object phone = updates.get("phone");
                if (phone != null && !phone.toString().trim().isEmpty()) {
                    user.setPhone(phone.toString().trim());
                } else {
                    user.setPhone(null);
                }
                updated = true;
            }

            if (updates.containsKey("address")) {
                Object address = updates.get("address");
                if (address != null && !address.toString().trim().isEmpty()) {
                    user.setAddress(address.toString().trim());
                } else {
                    user.setAddress(null);
                }
                updated = true;
            }

            // Handle email updates
            if (updates.containsKey("email") && updates.get("email") != null) {
                String newEmail = updates.get("email").toString().trim();
                if (!newEmail.equals(user.getEmail())) {
                    Optional<User> existingEmailUser = userRepository.findByEmail(newEmail);
                    if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(user.getId())) {
                        response.put("success", false);
                        response.put("message", "Email is already taken by another user");
                        return response;
                    }
                    
                    user.setEmail(newEmail);
                    user.setEmailVerified(false);
                    updated = true;
                }
            }

            // Handle health fields
            if (updates.containsKey("gender")) {
                Object gender = updates.get("gender");
                user.setGender(gender != null ? gender.toString() : null);
                updated = true;
            }

            if (updates.containsKey("dob") && updates.get("dob") != null) {
                try {
                    String dobStr = updates.get("dob").toString();
                    LocalDate dob = LocalDate.parse(dobStr);
                    user.setDob(dob);
                    updated = true;
                } catch (Exception e) {
                    System.err.println("Error parsing date of birth: " + e.getMessage());
                }
            }

            if (updates.containsKey("height")) {
                Object height = updates.get("height");
                if (height != null) {
                    try {
                        Double heightValue = height instanceof Number ? 
                            ((Number) height).doubleValue() : 
                            Double.parseDouble(height.toString());
                        user.setHeight(heightValue);
                        updated = true;
                    } catch (Exception e) {
                        System.err.println("Error parsing height: " + e.getMessage());
                    }
                }
            }

            if (updates.containsKey("weight")) {
                Object weight = updates.get("weight");
                if (weight != null) {
                    try {
                        Double weightValue = weight instanceof Number ? 
                            ((Number) weight).doubleValue() : 
                            Double.parseDouble(weight.toString());
                        user.setWeight(weightValue);
                        updated = true;
                    } catch (Exception e) {
                        System.err.println("Error parsing weight: " + e.getMessage());
                    }
                }
            }

            if (updates.containsKey("bloodGroup")) {
                Object bloodGroup = updates.get("bloodGroup");
                String bloodGroupStr = bloodGroup != null ? bloodGroup.toString() : null;
                user.setBloodGroup(bloodGroupStr);
                updated = true;
            }

            if (updates.containsKey("genotype")) {
                Object genotype = updates.get("genotype");
                user.setGenotype(genotype != null ? genotype.toString() : null);
                updated = true;
            }

            if (updated) {
                user.setUpdatedAt(LocalDateTime.now());
                User savedUser = userRepository.save(user);
                
                response.put("success", true);
                response.put("message", "Profile updated successfully!");
                response.put("user", createUserResponse(savedUser));
            } else {
                response.put("success", true);
                response.put("message", "No changes to update");
                response.put("user", createUserResponse(user));
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
        }

        return response;
    }

    // Update health information specifically
    public Map<String, Object> updateHealthInfo(Map<String, Object> healthData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            boolean updated = false;

            // Update health-specific fields
            if (healthData.containsKey("dateOfBirth") && healthData.get("dateOfBirth") != null) {
                try {
                    String dobStr = healthData.get("dateOfBirth").toString();
                    LocalDate dob = LocalDate.parse(dobStr);
                    user.setDob(dob);
                    updated = true;
                } catch (Exception e) {
                    System.err.println("Error parsing date of birth: " + e.getMessage());
                }
            }

            // Also handle "dob" field
            if (healthData.containsKey("dob") && healthData.get("dob") != null) {
                try {
                    String dobStr = healthData.get("dob").toString();
                    LocalDate dob = LocalDate.parse(dobStr);
                    user.setDob(dob);
                    updated = true;
                } catch (Exception e) {
                    System.err.println("Error parsing dob: " + e.getMessage());
                }
            }

            if (healthData.containsKey("bloodType")) {
                Object bloodType = healthData.get("bloodType");
                String bloodTypeStr = bloodType != null ? bloodType.toString() : null;
                user.setBloodGroup(bloodTypeStr);
                updated = true;
            }

            // Also handle "bloodGroup" field
            if (healthData.containsKey("bloodGroup")) {
                Object bloodGroup = healthData.get("bloodGroup");
                String bloodGroupStr = bloodGroup != null ? bloodGroup.toString() : null;
                user.setBloodGroup(bloodGroupStr);
                updated = true;
            }

            if (healthData.containsKey("height")) {
                Object height = healthData.get("height");
                if (height != null) {
                    try {
                        Double heightValue = height instanceof Number ? 
                            ((Number) height).doubleValue() : 
                            Double.parseDouble(height.toString());
                        user.setHeight(heightValue);
                        updated = true;
                    } catch (Exception e) {
                        System.err.println("Error parsing height: " + e.getMessage());
                    }
                }
            }

            if (healthData.containsKey("weight")) {
                Object weight = healthData.get("weight");
                if (weight != null) {
                    try {
                        Double weightValue = weight instanceof Number ? 
                            ((Number) weight).doubleValue() : 
                            Double.parseDouble(weight.toString());
                        user.setWeight(weightValue);
                        updated = true;
                    } catch (Exception e) {
                        System.err.println("Error parsing weight: " + e.getMessage());
                    }
                }
            }

            if (healthData.containsKey("medicalHistory")) {
                Object medicalHistory = healthData.get("medicalHistory");
                user.setMedicalHistory(medicalHistory != null ? medicalHistory.toString() : null);
                updated = true;
            }

            if (healthData.containsKey("currentMedications")) {
                Object medications = healthData.get("currentMedications");
                user.setCurrentMedications(medications != null ? medications.toString() : null);
                updated = true;
            }

            if (healthData.containsKey("allergies")) {
                Object allergies = healthData.get("allergies");
                user.setAllergies(allergies != null ? allergies.toString() : null);
                updated = true;
            }

            if (healthData.containsKey("emergencyContact")) {
                Object emergencyContact = healthData.get("emergencyContact");
                user.setEmergencyContact(emergencyContact != null ? emergencyContact.toString() : null);
                updated = true;
            }

            if (healthData.containsKey("emergencyPhone")) {
                Object emergencyPhone = healthData.get("emergencyPhone");
                user.setEmergencyPhone(emergencyPhone != null ? emergencyPhone.toString() : null);
                updated = true;
            }

            if (updated) {
                user.setUpdatedAt(LocalDateTime.now());
                User savedUser = userRepository.save(user);
                
                response.put("success", true);
                response.put("message", "Health information updated successfully!");
                response.put("user", createUserResponse(savedUser));
            } else {
                response.put("success", true);
                response.put("message", "No changes to update");
                response.put("user", createUserResponse(user));
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating health info: " + e.getMessage());
        }

        return response;
    }

    // Get health information by user ID (for admin)
    public Map<String, Object> getHealthInfo(Long userId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if requesting user is admin
            Optional<User> requestingUserOpt = userRepository.findByUsername(userDetails.getUsername());
            if (requestingUserOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Requesting user not found");
                return response;
            }

            User requestingUser = requestingUserOpt.get();
            if (requestingUser.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }

            // Fetch requested user's health info
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            Map<String, Object> healthInfo = new HashMap<>();
            
            healthInfo.put("dob", user.getDob());
            healthInfo.put("dateOfBirth", user.getDob());
            healthInfo.put("age", user.getAge());
            healthInfo.put("gender", user.getGender());
            healthInfo.put("bloodGroup", user.getBloodGroup());
            healthInfo.put("bloodType", user.getBloodGroup());
            healthInfo.put("genotype", user.getGenotype());
            healthInfo.put("height", user.getHeight());
            healthInfo.put("weight", user.getWeight());
            healthInfo.put("bmi", user.getBMI());
            healthInfo.put("bmiCategory", user.getBMICategory());
            healthInfo.put("medicalHistory", user.getMedicalHistory());
            healthInfo.put("currentMedications", user.getCurrentMedications());
            healthInfo.put("allergies", user.getAllergies());
            healthInfo.put("emergencyContact", user.getEmergencyContact());
            healthInfo.put("emergencyPhone", user.getEmergencyPhone());
            healthInfo.put("lastVisit", user.getLastVisit());

            response.put("success", true);
            response.put("healthInfo", healthInfo);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching health info: " + e.getMessage());
        }

        return response;
    }

    // ✅ FIXED: This is updateUserProfile (with verification), not updateUserProfileSimple
    public Map<String, Object> updateUserProfile(Map<String, Object> updates, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            // Verify current password if provided (required for sensitive updates)
            Object currentPasswordObj = updates.get("currentPassword");
            String currentPassword = currentPasswordObj != null ? currentPasswordObj.toString() : null;
            
            if (currentPassword != null) {
                if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                    response.put("success", false);
                    response.put("message", "Current password is incorrect");
                    return response;
                }
            }

            // Update basic fields that don't require verification
            if (updates.containsKey("firstName") && updates.get("firstName") != null) {
                user.setFirstName(updates.get("firstName").toString());
            }
            if (updates.containsKey("lastName") && updates.get("lastName") != null) {
                user.setLastName(updates.get("lastName").toString());
            }
            if (updates.containsKey("address")) {
                Object address = updates.get("address");
                user.setAddress(address != null ? address.toString() : null);
            }

            // Handle email updates with verification
            if (updates.containsKey("email") && updates.get("email") != null) {
                String newEmail = updates.get("email").toString();
                if (!newEmail.equals(user.getEmail())) {
                    if (currentPassword == null) {
                        response.put("success", false);
                        response.put("message", "Current password is required for email changes");
                        return response;
                    }
                    
                    if (!contactVerificationService.isContactUpdateVerified("email", newEmail, userDetails)) {
                        response.put("success", false);
                        response.put("message", "Email change must be verified first. Please complete the verification process.");
                        return response;
                    }
                    
                    if (userRepository.existsByEmail(newEmail)) {
                        response.put("success", false);
                        response.put("message", "Email is already in use by another account");
                        return response;
                    }
                    
                    user.setEmail(newEmail);
                    user.setEmailVerified(true);
                    contactVerificationService.clearVerificationSession("email", userDetails);
                }
            }

            // Handle phone updates with verification
            if (updates.containsKey("phone") && updates.get("phone") != null) {
                String newPhone = updates.get("phone").toString();
                if (!newPhone.equals(user.getPhone())) {
                    if (currentPassword == null) {
                        response.put("success", false);
                        response.put("message", "Current password is required for phone changes");
                        return response;
                    }
                    
                    if (!contactVerificationService.isContactUpdateVerified("phone", newPhone, userDetails)) {
                        response.put("success", false);
                        response.put("message", "Phone change must be verified first. Please complete the verification process.");
                        return response;
                    }
                    
                    user.setPhone(newPhone);
                    user.setPhoneVerified(true);
                    contactVerificationService.clearVerificationSession("phone", userDetails);
                }
            }

            user.setUpdatedAt(LocalDateTime.now());
            User updatedUser = userRepository.save(user);

            response.put("success", true);
            response.put("message", "Profile updated successfully!");
            response.put("user", createUserResponse(updatedUser));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating profile: " + e.getMessage());
        }

        return response;
    }

    // Change password
    public Map<String, Object> changePassword(String currentPassword, String newPassword, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                response.put("success", false);
                response.put("message", "Current password is incorrect");
                return response;
            }

            if (newPassword == null || newPassword.length() < 6) {
                response.put("success", false);
                response.put("message", "New password must be at least 6 characters long");
                return response;
            }

            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                response.put("success", false);
                response.put("message", "New password must be different from current password");
                return response;
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            response.put("success", true);
            response.put("message", "Password changed successfully!");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error changing password: " + e.getMessage());
        }

        return response;
    }

    // Delete user account
    public Map<String, Object> deleteUserAccount(String password, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(password, user.getPassword())) {
                response.put("success", false);
                response.put("message", "Password is incorrect");
                return response;
            }

            contactVerificationService.clearVerificationSession("email", userDetails);
            contactVerificationService.clearVerificationSession("phone", userDetails);
            contactVerificationService.clearVerificationSession("backup", userDetails);

            userRepository.delete(user);

            response.put("success", true);
            response.put("message", "Account deleted successfully!");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting account: " + e.getMessage());
        }

        return response;
    }

    // Get user statistics
    public Map<String, Object> getUserStatistics(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            Map<String, Object> stats = new HashMap<>();
            stats.put("userId", user.getId());
            stats.put("memberSince", user.getCreatedAt());
            stats.put("lastLogin", user.getLastLogin());
            stats.put("role", user.getRole().name());
            stats.put("accountStatus", "Active");
            stats.put("twoFactorEnabled", user.isTwoFactorEnabled());
            stats.put("emailVerified", user.isEmailVerified());
            stats.put("phoneVerified", user.isPhoneVerified());
            stats.put("hasBackupEmail", user.getBackupEmail() != null);
            stats.put("profileCompleteness", calculateProfileCompleteness(user));

            response.put("success", true);
            response.put("statistics", stats);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching statistics: " + e.getMessage());
        }

        return response;
    }

    // Update last login time
    public void updateLastLogin(UserDetails userDetails) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);
            }
        } catch (Exception e) {
            System.err.println("Error updating last login: " + e.getMessage());
        }
    }

    // Calculate profile completeness percentage
    private int calculateProfileCompleteness(User user) {
        int total = 0;
        int completed = 0;

        total += 4;
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) completed++;
        if (user.getLastName() != null && !user.getLastName().isEmpty()) completed++;
        if (user.getEmail() != null && !user.getEmail().isEmpty()) completed++;
        if (user.getUsername() != null && !user.getUsername().isEmpty()) completed++;

        total += 4;
        if (user.getPhone() != null && !user.getPhone().isEmpty()) completed++;
        if (user.getAddress() != null && !user.getAddress().isEmpty()) completed++;
        if (user.getBackupEmail() != null && !user.getBackupEmail().isEmpty()) completed++;
        if (user.isEmailVerified()) completed++;

        return Math.round((completed * 100.0f) / total);
    }

    // Helper methods
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    // Helper method to create user response with all fields
    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("fullName", user.getFullName());
        userResponse.put("initials", user.getInitials());
        userResponse.put("phone", user.getPhone());
        userResponse.put("address", user.getAddress());
        userResponse.put("backupEmail", user.getBackupEmail());
        userResponse.put("role", user.getRole().name());
        userResponse.put("createdAt", user.getCreatedAt());
        userResponse.put("updatedAt", user.getUpdatedAt());
        userResponse.put("lastLogin", user.getLastLogin());
        
        // Verification status
        userResponse.put("emailVerified", user.isEmailVerified());
        userResponse.put("phoneVerified", user.isPhoneVerified());
        userResponse.put("contactVerified", user.isContactVerified());
        
        // Two-Factor Authentication
        userResponse.put("twoFactorEnabled", user.isTwoFactorEnabled());
        userResponse.put("twoFactorMethod", user.getTwoFactorMethod());
        
        if (user.getBackupCodes() != null && !user.getBackupCodes().isEmpty()) {
            String[] codes = user.getBackupCodes().split(",");
            userResponse.put("backupCodesCount", codes.length);
        } else {
            userResponse.put("backupCodesCount", 0);
        }
        
        // Health information
        userResponse.put("gender", user.getGender());
        userResponse.put("dob", user.getDob());
        userResponse.put("dateOfBirth", user.getDob());
        userResponse.put("age", user.getAge());
        userResponse.put("bloodGroup", user.getBloodGroup());
        userResponse.put("bloodType", user.getBloodGroup());
        userResponse.put("genotype", user.getGenotype());
        userResponse.put("height", user.getHeight());
        userResponse.put("weight", user.getWeight());
        userResponse.put("bmi", user.getBMI());
        userResponse.put("bmiCategory", user.getBMICategory());
        userResponse.put("medicalHistory", user.getMedicalHistory());
        userResponse.put("currentMedications", user.getCurrentMedications());
        userResponse.put("allergies", user.getAllergies());
        userResponse.put("emergencyContact", user.getEmergencyContact());
        userResponse.put("emergencyPhone", user.getEmergencyPhone());
        userResponse.put("lastVisit", user.getLastVisit());
        
        userResponse.put("profileCompleteness", calculateProfileCompleteness(user));
        
        return userResponse;
    }
}