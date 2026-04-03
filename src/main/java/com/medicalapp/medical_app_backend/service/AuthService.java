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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(User.Role.PATIENT);
        user.setEmailVerified(true);   // auto-verified — email disabled temporarily
        user.setResetCode(null);
        user.setResetCodeExpiry(null);

        userRepository.save(user);

        response.put("success", true);
        response.put("message", "Registration successful! You can now log in.");
        response.put("email", email);

        return response;
    }

    // ─── Verify OTP (disabled — kept for future use) ────────────────────────────

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
            response.put("success", true);
            response.put("message", "Email is already verified. You can log in.");
            return response;
        }

        response.put("success", false);
        response.put("message", "Email verification is currently unavailable. Please contact support.");
        return response;
    }

    // ─── Resend OTP (disabled — kept for future use) ─────────────────────────────

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
            response.put("success", true);
            response.put("message", "Email is already verified. You can log in.");
            return response;
        }

        response.put("success", false);
        response.put("message", "Email verification is currently unavailable. Please contact support.");
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