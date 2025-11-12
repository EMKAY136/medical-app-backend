package com.medicalapp.medical_app_backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.medicalapp.medical_app_backend.config.JwtTokenUtil;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private NotificationService notificationService;

    /**
     * Health check endpoint for authentication service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(HttpServletRequest request) {
        logger.info("=== AUTH HEALTH CHECK ===");
        logger.info("Request from: {}", request.getRemoteAddr());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "Medical App Authentication");
        response.put("timestamp", LocalDateTime.now());
        response.put("version", "1.0.0");
        response.put("endpoints", Map.of(
            "signup", "POST /api/auth/signup",
            "login", "POST /api/auth/login",
            "check-user", "GET /api/auth/check-user",
            "validate-token", "GET /api/auth/validate-token"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * User registration endpoint
     * Supports both email and username-based registration
     */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> registerUser(
            @RequestBody Map<String, String> signupRequest, 
            HttpServletRequest request) {
        
        logger.info("=== USER REGISTRATION ATTEMPT ===");
        logger.info("Request from: {} | Origin: {}", 
                   request.getRemoteAddr(), 
                   request.getHeader("Origin"));

        try {
            // Extract and validate input
            String username = signupRequest.get("username");
            String email = signupRequest.get("email");
            String password = signupRequest.get("password");
            String firstName = signupRequest.get("firstName");
            String lastName = signupRequest.get("lastName");
            String phone = signupRequest.get("phone");
            String address = signupRequest.get("address");

            // Input validation
            Map<String, Object> validationResult = validateRegistrationInput(
                username, email, password, firstName, lastName
            );
            if (!(Boolean) validationResult.get("valid")) {
                logger.warn("Registration validation failed: {}", validationResult.get("message"));
                return ResponseEntity.badRequest().body(createErrorResponse(
                    (String) validationResult.get("message")
                ));
            }

            logger.info("Registration attempt - Username: {}, Email: {}", username, email);

            // Check for existing users
            if (userRepository.findByUsername(username).isPresent()) {
                logger.warn("Username already exists: {}", username);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse("Username is already taken"));
            }

            if (userRepository.findByEmail(email).isPresent()) {
                logger.warn("Email already exists: {}", email);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse("Email is already registered"));
            }

            // Create and save new user
            User newUser = createUserEntity(username, email, password, firstName, lastName, phone, address);
            User savedUser = userRepository.save(newUser);

            logger.info("User registered successfully - ID: {}, Username: {}", 
                       savedUser.getId(), savedUser.getUsername());

            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration successful");
            response.put("user", createUserResponse(savedUser));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logger.error("Registration failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Registration failed: " + e.getMessage()));
        }
    }

    /**
     * User login endpoint with security notifications
     * Supports login with either email or username
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> authenticateUser(
            @RequestBody Map<String, String> loginRequest, 
            HttpServletRequest request) {
        
        logger.info("=== USER LOGIN ATTEMPT ===");
        logger.info("Request from: {} | Origin: {}", 
                   request.getRemoteAddr(), 
                   request.getHeader("Origin"));

        try {
            // Get credentials (support both email and username)
            String identifier = loginRequest.get("email");
            if (identifier == null || identifier.trim().isEmpty()) {
                identifier = loginRequest.get("username");
            }
            String password = loginRequest.get("password");

            if (identifier == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Email/username and password are required"));
            }

            logger.info("Login attempt for identifier: {}", identifier);

            // Find user by email or username
            Optional<User> userOpt = findUserByIdentifier(identifier);
            if (!userOpt.isPresent()) {
                logger.warn("User not found: {}", identifier);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid credentials"));
            }

            User user = userOpt.get();
            
            // Authenticate user
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), password)
            );

            // Generate auth token
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtTokenUtil.generateToken(userDetails);

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            // ========== SEND LOGIN NOTIFICATION ==========
            try {
                String userAgent = request.getHeader("User-Agent");
                String ipAddress = getClientIpAddress(request);
                String deviceInfo = extractDeviceInfo(userAgent);
                String location = getLocationFromIP(ipAddress);
                
                // Send notification asynchronously (non-blocking)
                final String userEmail = user.getEmail();
                CompletableFuture.runAsync(() -> {
                    try {
                        notificationService.sendLoginNotification(
                            userEmail, 
                            deviceInfo, 
                            ipAddress, 
                            location
                        );
                    } catch (Exception e) {
                        logger.warn("Failed to send login notification: {}", e.getMessage());
                    }
                });
                
                logger.info("Login notification queued for: {}", user.getEmail());
            } catch (Exception e) {
                logger.warn("Error preparing login notification: {}", e.getMessage());
                // Don't fail login if notification fails
            }
            // =============================================

            // Create success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("token", token);
            response.put("tokenType", "Bearer");
            response.put("user", createUserResponse(user));
            response.put("timestamp", LocalDateTime.now());

            logger.info("Login successful for: {} (Role: {})", identifier, user.getRole());
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            logger.warn("Login failed - invalid credentials for: {}", 
                       loginRequest.get("email") != null ? loginRequest.get("email") : loginRequest.get("username"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Invalid credentials"));

        } catch (Exception e) {
            logger.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Authentication failed"));
        }
    }

    /**
     * Check if username or email exists
     */
    @GetMapping("/check-user")
    public ResponseEntity<Map<String, Object>> checkUserExists(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            HttpServletRequest request) {

        logger.info("=== USER EXISTENCE CHECK ===");
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());

            if (username != null && !username.trim().isEmpty()) {
                boolean usernameExists = userRepository.findByUsername(username.trim()).isPresent();
                response.put("usernameExists", usernameExists);
                response.put("username", username.trim());
            }

            if (email != null && !email.trim().isEmpty()) {
                boolean emailExists = userRepository.findByEmail(email.trim()).isPresent();
                response.put("emailExists", emailExists);
                response.put("email", email.trim());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error checking user existence: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Error checking user existence"));
        }
    }

    /**
     * Validate auth token
     */
    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("No valid authorization header"));
            }

            String token = authHeader.substring(7);
            String username = null;
            
            try {
                username = jwtTokenUtil.getUsernameFromToken(token);
            } catch (Exception e) {
                logger.warn("Token parsing failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid token format"));
            }
            
            if (username != null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("valid", true);
                    response.put("user", createUserResponse(userOpt.get()));
                    response.put("timestamp", LocalDateTime.now());
                    return ResponseEntity.ok(response);
                }
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Invalid or expired token"));

        } catch (Exception e) {
            logger.error("Token validation error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse("Token validation failed"));
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> validateRegistrationInput(String username, String email, 
                                                         String password, String firstName, String lastName) {
        Map<String, Object> result = new HashMap<>();
        
        if (username == null || username.trim().length() < 3) {
            result.put("valid", false);
            result.put("message", "Username must be at least 3 characters long");
            return result;
        }
        
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            result.put("valid", false);
            result.put("message", "Please provide a valid email address");
            return result;
        }
        
        if (password == null || password.length() < 6) {
            result.put("valid", false);
            result.put("message", "Password must be at least 6 characters long");
            return result;
        }
        
        if (firstName == null || firstName.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "First name is required");
            return result;
        }
        
        if (lastName == null || lastName.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "Last name is required");
            return result;
        }
        
        result.put("valid", true);
        return result;
    }

    private User createUserEntity(String username, String email, String password, 
                                 String firstName, String lastName, String phone, String address) {
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(phone != null ? phone.trim() : null);
        user.setAddress(address != null ? address.trim() : null);
        user.setRole(User.Role.PATIENT);
        user.setEmailVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        // Try email first, then username
        Optional<User> user = userRepository.findByEmail(identifier.trim().toLowerCase());
        if (!user.isPresent()) {
            user = userRepository.findByUsername(identifier.trim());
        }
        return user;
    }

    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("firstName", user.getFirstName());
        userInfo.put("lastName", user.getLastName());
        userInfo.put("phone", user.getPhone());
        userInfo.put("role", user.getRole().toString());
        userInfo.put("emailVerified", user.isEmailVerified());
        userInfo.put("createdAt", user.getCreatedAt());
        return userInfo;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now());
        return errorResponse;
    }

    // ==================== SECURITY HELPER METHODS ====================

    /**
     * Extract device information from User-Agent header
     */
    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }
        
        // Mobile devices
        if (userAgent.contains("iPhone")) return "iPhone";
        if (userAgent.contains("iPad")) return "iPad";
        if (userAgent.contains("Android")) {
            if (userAgent.contains("Mobile")) return "Android Phone";
            return "Android Tablet";
        }
        
        // Desktop browsers
        if (userAgent.contains("Windows")) return "Windows PC";
        if (userAgent.contains("Macintosh")) return "Mac";
        if (userAgent.contains("Linux")) return "Linux PC";
        
        // Browsers
        if (userAgent.contains("Chrome")) return "Chrome Browser";
        if (userAgent.contains("Firefox")) return "Firefox Browser";
        if (userAgent.contains("Safari")) return "Safari Browser";
        if (userAgent.contains("Edge")) return "Edge Browser";
        
        return "Web Browser";
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle multiple IPs (take first one)
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Get location from IP address
     * Currently returns default location - can be enhanced with IP geolocation service
     */
    private String getLocationFromIP(String ipAddress) {
        // For localhost/development
        if (ipAddress == null || ipAddress.equals("0:0:0:0:0:0:0:1") || ipAddress.equals("127.0.0.1")) {
            return "Local Development";
        }
        
        // You can integrate with IP geolocation services like:
        // - ipapi.co
        // - ip-api.com
        // - ipgeolocation.io
        // - MaxMind GeoIP2
        
        // For now, return a default
        return "Nigeria"; // Or "Unknown Location"
    }
}