package com.medicalapp.medical_app_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})  // ✅ Added this line
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    private String username;
    
    @NotBlank
    @Size(max = 100)
    @Email
    @Column(unique = true)
    private String email;
    
    @NotBlank
    @Size(max = 120)
    private String password;
    
    @NotBlank
    @Size(max = 50)
    private String firstName;
    
    @NotBlank
    @Size(max = 50)
    private String lastName;
    
    private String phone;
    private String address;

    // ===== HEALTH INFORMATION FIELDS =====
    @Column(name = "gender")
    private String gender;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "genotype")
    private String genotype;

    
    @Email
    @Column(name = "backup_email")
    private String backupEmail;
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.PATIENT;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // ===== MEDICAL FIELDS (Added for Admin Dashboard) =====
    
    
    @Column(name = "medical_history", columnDefinition = "TEXT")
    private String medicalHistory;
    
    @Column(name = "current_medications", columnDefinition = "TEXT")
    private String currentMedications;
    
    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;
    
    @Column(name = "height")
    private Double height; // in cm
    
    @Column(name = "weight")
    private Double weight; // in kg
    
    @Column(name = "last_visit")
    private LocalDateTime lastVisit;
    
    @Column(name = "emergency_contact")
    private String emergencyContact;
    
    @Column(name = "emergency_phone")
    private String emergencyPhone;
    
    @Column(name = "insurance_provider")
    private String insuranceProvider;
    
    @Column(name = "insurance_number")
    private String insuranceNumber;
    
    @Column(name = "primary_doctor")
    private String primaryDoctor;
    
    @Column(name = "preferred_language")
    private String preferredLanguage = "English";
    
    // ===== EXISTING FIELDS =====
    
    // Two-Factor Authentication fields
    @Column(name = "two_factor_enabled")
    private boolean twoFactorEnabled = false;
    
    @Column(name = "two_factor_method")
    private String twoFactorMethod;
    
    @Column(name = "two_factor_secret")
    private String twoFactorSecret;
    
    @Column(name = "backup_codes", columnDefinition = "TEXT")
    private String backupCodes;
    
    // Contact verification tracking
    @Column(name = "email_verified")
    private boolean emailVerified = false;
    
    @Column(name = "phone_verified")
    private boolean phoneVerified = false;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Notification settings
    @Column(name = "notification_settings", columnDefinition = "TEXT")
    private String notificationSettings;

    @Column(name = "schedule_settings", columnDefinition = "TEXT")
    private String scheduleSettings;

    // Device information for push notifications
    @Column(name = "device_token")
    private String deviceToken;
    
    @Column(name = "device_platform")
    private String devicePlatform; // "ios" or "android"

    // Constructors
    public User() {}
    
    public User(String username, String email, String password, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailVerified = false;
        this.phoneVerified = false;
    }
    
    // ===== BASIC GETTERS AND SETTERS =====
    
    public Long getId() { 
        return id; 
    }
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getUsername() { 
        return username; 
    }
    public void setUsername(String username) { 
        this.username = username; 
    }
    
    public String getEmail() { 
        return email; 
    }
    public void setEmail(String email) { 
        this.email = email; 
    }
    
    public String getPassword() { 
        return password; 
    }
    public void setPassword(String password) { 
        this.password = password; 
    }
    
    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getGenotype() {
        return genotype;
    }

    public void setGenotype(String genotype) {
        this.genotype = genotype;
    }

    
    public String getFirstName() { 
        return firstName; 
    }
    public void setFirstName(String firstName) { 
        this.firstName = firstName; 
    }
    
    public String getLastName() { 
        return lastName; 
    }
    public void setLastName(String lastName) { 
        this.lastName = lastName; 
    }
    
    public String getPhone() { 
        return phone; 
    }
    public void setPhone(String phone) { 
        this.phone = phone; 
    }
    
    public String getAddress() { 
        return address; 
    }
    public void setAddress(String address) { 
        this.address = address; 
    }
    
    public String getBackupEmail() {
        return backupEmail;
    }
    public void setBackupEmail(String backupEmail) {
        this.backupEmail = backupEmail;
    }
    
    public Role getRole() { 
        return role; 
    }
    public void setRole(Role role) { 
        this.role = role; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    // ===== MEDICAL FIELD GETTERS AND SETTERS =====    
    public String getMedicalHistory() {
        return medicalHistory;
    }
    public void setMedicalHistory(String medicalHistory) {
        this.medicalHistory = medicalHistory;
    }
    
    public String getCurrentMedications() {
        return currentMedications;
    }
    public void setCurrentMedications(String currentMedications) {
        this.currentMedications = currentMedications;
    }
    
    public String getAllergies() {
        return allergies;
    }
    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }
    
    public Double getHeight() {
        return height;
    }
    public void setHeight(Double height) {
        this.height = height;
    }
    
    public Double getWeight() {
        return weight;
    }
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public LocalDateTime getLastVisit() {
        return lastVisit;
    }
    public void setLastVisit(LocalDateTime lastVisit) {
        this.lastVisit = lastVisit;
    }
    
    public String getEmergencyContact() {
        return emergencyContact;
    }
    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }
    
    public String getEmergencyPhone() {
        return emergencyPhone;
    }
    public void setEmergencyPhone(String emergencyPhone) {
        this.emergencyPhone = emergencyPhone;
    }
    
    public String getInsuranceProvider() {
        return insuranceProvider;
    }
    public void setInsuranceProvider(String insuranceProvider) {
        this.insuranceProvider = insuranceProvider;
    }
    
    public String getInsuranceNumber() {
        return insuranceNumber;
    }
    public void setInsuranceNumber(String insuranceNumber) {
        this.insuranceNumber = insuranceNumber;
    }
    
    public String getPrimaryDoctor() {
        return primaryDoctor;
    }
    public void setPrimaryDoctor(String primaryDoctor) {
        this.primaryDoctor = primaryDoctor;
    }
    
    public String getPreferredLanguage() {
        return preferredLanguage;
    }
    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }
    
    // ===== TWO-FACTOR AUTHENTICATION GETTERS AND SETTERS =====
    
    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }
    
    public String getTwoFactorMethod() {
        return twoFactorMethod;
    }
    public void setTwoFactorMethod(String twoFactorMethod) {
        this.twoFactorMethod = twoFactorMethod;
    }
    
    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }
    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }
    
    public String getBackupCodes() {
        return backupCodes;
    }
    public void setBackupCodes(String backupCodes) {
        this.backupCodes = backupCodes;
    }
    
    // ===== CONTACT VERIFICATION GETTERS AND SETTERS =====
    
    public boolean isEmailVerified() {
        return emailVerified;
    }
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public boolean isPhoneVerified() {
        return phoneVerified;
    }
    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }
    
    // ===== NOTIFICATION SETTINGS GETTERS AND SETTERS =====
    
    public String getNotificationSettings() {
        return notificationSettings;
    }
    public void setNotificationSettings(String notificationSettings) {
        this.notificationSettings = notificationSettings;
    }
    
    public String getScheduleSettings() {
        return scheduleSettings;
    }
    public void setScheduleSettings(String scheduleSettings) {
        this.scheduleSettings = scheduleSettings;
    }
    
    // ===== DEVICE INFORMATION GETTERS AND SETTERS =====
    
    public String getDeviceToken() {
        return deviceToken;
    }
    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }
    
    public String getDevicePlatform() {
        return devicePlatform;
    }
    public void setDevicePlatform(String devicePlatform) {
        this.devicePlatform = devicePlatform;
    }
    
    // ===== USERDETAILS IMPLEMENTATION METHODS =====
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    // ===== LIFECYCLE CALLBACKS =====
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    // ===== UTILITY METHODS =====
    
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    public String getInitials() {
        String firstInitial = (firstName != null && !firstName.isEmpty()) ? String.valueOf(firstName.charAt(0)) : "";
        String lastInitial = (lastName != null && !lastName.isEmpty()) ? String.valueOf(lastName.charAt(0)) : "";
        return firstInitial + lastInitial;
    }
    
    public boolean hasValidContactInfo() {
        return email != null && !email.isEmpty() && (phone != null && !phone.isEmpty());
    }
    
    public boolean isContactVerified() {
        return emailVerified && (phone == null || phoneVerified);
    }
    
    public boolean hasDeviceToken() {
        return deviceToken != null && !deviceToken.isEmpty();
    }
    
    // ===== MEDICAL UTILITY METHODS =====
    
    public int getAge() {
        if (dob == null) return 0;
        return java.time.Period.between(dob, LocalDate.now()).getYears();
    }
    
    public Double getBMI() {
        if (height == null || weight == null || height <= 0) return null;
        double heightInMeters = height / 100.0;
        return weight / (heightInMeters * heightInMeters);
    }
    
    public String getBMICategory() {
        Double bmi = getBMI();
        if (bmi == null) return "Unknown";
        
        if (bmi < 18.5) return "Underweight";
        else if (bmi < 25.0) return "Normal weight";
        else if (bmi < 30.0) return "Overweight";
        else return "Obese";
    }
    
    public boolean hasMedicalInfo() {
        return bloodGroup != null || medicalHistory != null || 
               currentMedications != null || allergies != null;
    }
    
    public boolean hasEmergencyContact() {
        return emergencyContact != null && emergencyPhone != null;
    }
    
    public boolean isProfileComplete() {
        return hasValidContactInfo() && dob != null && 
               hasEmergencyContact() && hasMedicalInfo();
    }
    
    public enum Role {
        PATIENT, DOCTOR, ADMIN
    }
}