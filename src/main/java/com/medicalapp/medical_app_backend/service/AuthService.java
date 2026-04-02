package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    // ─── Register ──────────────────────────────────────────────────────────────

    public Map<String, Object> registerUser(String username, String email, String password,
                                            String firstName, String lastName) {
        Map<String, Object> response = new HashMap<>();

        if (userRepository.existsByUsername(username)) {
            response.put("success", false);
            response.put("message", "Username is already taken!");
            return response;
        }

        if (userRepository.existsByEmail(email)) {
            response.put("success", false);
            response.put("message", "Email is already in use!");
            return response;
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(10);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(User.Role.PATIENT);
        user.setEmailVerified(false);
        user.setResetCode(otp);
        user.setResetCodeExpiry(otpExpiry);

        userRepository.save(user);

        emailService.sendVerificationEmail(email, otp);

        response.put("success", true);
        response.put("message", "Registration successful! Please check your email for the verification code.");
        response.put("email", email);

        return response;
    }

    // ─── Verify OTP ─────────────────────────────────────────────────────────────

    public Map<String, Object> verifyEmail(String email, String code) {
        Map<String, Object> response = new HashMap<>();

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found!");
            return response;
        }

        User user = userOpt.get();

        if (user.isEmailVerified()) {
            response.put("success", false);
            response.put("message", "Email is already verified!");
            return response;
        }

        if (user.getResetCodeExpiry().isBefore(LocalDateTime.now())) {
            response.put("success", false);
            response.put("message", "Verification code has expired. Please request a new one.");
            return response;
        }

        if (!user.getResetCode().equals(code)) {
            response.put("success", false);
            response.put("message", "Invalid verification code!");
            return response;
        }

        user.setEmailVerified(true);
        user.setResetCode(null);
        user.setResetCodeExpiry(null);
        userRepository.save(user);

        emailService.sendWelcomeEmail(email, user.getFirstName());

        response.put("success", true);
        response.put("message", "Email verified successfully! You can now log in.");

        return response;
    }

    // ─── Resend OTP ──────────────────────────────────────────────────────────────

    public Map<String, Object> resendVerificationCode(String email) {
        Map<String, Object> response = new HashMap<>();

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found!");
            return response;
        }

        User user = userOpt.get();

        if (user.isEmailVerified()) {
            response.put("success", false);
            response.put("message", "Email is already verified!");
            return response;
        }

        String newOtp = String.format("%06d", new Random().nextInt(999999));
        user.setResetCode(newOtp);
        user.setResetCodeExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        emailService.sendVerificationEmail(email, newOtp);

        response.put("success", true);
        response.put("message", "New verification code sent to your email.");

        return response;
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    public Map<String, Object> loginUser(String username, String password) {
        Map<String, Object> response = new HashMap<>();

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();

                if (!user.isEmailVerified()) {
                    response.put("success", false);
                    response.put("message", "Please verify your email before logging in.");
                    response.put("requiresVerification", true);
                    response.put("email", user.getEmail());
                    return response;
                }

                String token = jwtUtil.generateToken(userDetails);

                response.put("success", true);
                response.put("message", "Login successful!");
                response.put("token", token);
                response.put("user", createUserResponse(user));
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Invalid username or password!");
        }

        return response;
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("phone", user.getPhone());
        userResponse.put("address", user.getAddress());
        userResponse.put("role", user.getRole().name());
        userResponse.put("createdAt", user.getCreatedAt());
        return userResponse;
    }
}