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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    // ------------------------------------------------------------------ //
    //  NOTE: The old ConcurrentHashMap preSignupOtpStore has been REMOVED. //
    //  OTPs are now persisted in the DB via a PENDING User record so they  //
    //  survive restarts and work correctly across multiple app instances.   //
    // ------------------------------------------------------------------ //

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private UserDetailsService userDetailsService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // ==================== HEALTH CHECK ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(HttpServletRequest request) {
        logger.info("=== AUTH HEALTH CHECK ===");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "Medical App Authentication");
        response.put("timestamp", LocalDateTime.now());
        response.put("version", "1.0.0");
        response.put("endpoints", Map.of(
            "signup",                "POST /api/auth/signup",
            "login",                 "POST /api/auth/login",
            "send-verification",     "POST /api/auth/send-verification",
            "verify-email-code",     "POST /api/auth/verify-email-code",
            "send-verification-email","POST /api/auth/send-verification-email",
            "verify-pre-signup-code","POST /api/auth/verify-pre-signup-code",
            "check-user",            "GET  /api/auth/check-user",
            "validate-token",        "GET  /api/auth/validate-token"
        ));
        return ResponseEntity.ok(response);
    }

    // ==================== SIGNUP ====================

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> registerUser(
            @RequestBody Map<String, String> signupRequest,
            HttpServletRequest request) {

        logger.info("=== USER REGISTRATION ATTEMPT ===");

        try {
            String username  = signupRequest.get("username");
            String email     = signupRequest.get("email");
            String password  = signupRequest.get("password");
            String firstName = signupRequest.get("firstName");
            String lastName  = signupRequest.get("lastName");
            String phone     = signupRequest.get("phone");
            String address   = signupRequest.get("address");

            Map<String, Object> validationResult =
                    validateRegistrationInput(username, email, password, firstName, lastName);
            if (!(Boolean) validationResult.get("valid")) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse((String) validationResult.get("message")));
            }

            // Check for an existing ACTIVE (non-pending) account
            Optional<User> existingByUsername = userRepository.findByUsername(username.trim());
            if (existingByUsername.isPresent() &&
                    existingByUsername.get().getRole() != User.Role.PENDING) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(createErrorResponse("Username is already taken"));
            }

            Optional<User> existingByEmail =
                    userRepository.findByEmail(email.trim().toLowerCase());
            if (existingByEmail.isPresent() &&
                    existingByEmail.get().getRole() != User.Role.PENDING) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(createErrorResponse("Email is already registered"));
            }

            // If a PENDING record exists for this email, upgrade it to a real account
            User userToSave;
            if (existingByEmail.isPresent() &&
                    existingByEmail.get().getRole() == User.Role.PENDING) {
                userToSave = existingByEmail.get();
                userToSave.setUsername(username.trim());
                userToSave.setPassword(passwordEncoder.encode(password));
                userToSave.setFirstName(firstName.trim());
                userToSave.setLastName(lastName.trim());
                userToSave.setPhone(phone != null ? phone.trim() : null);
                userToSave.setAddress(address != null ? address.trim() : null);
                userToSave.setRole(User.Role.PATIENT);
                userToSave.setEmailVerified(true);   // already verified pre-signup
                userToSave.setResetCode(null);
                userToSave.setResetCodeExpiry(null);
                userToSave.setUpdatedAt(LocalDateTime.now());
            } else {
                userToSave = createUserEntity(
                        username, email, password, firstName, lastName, phone, address);
            }

            User savedUser = userRepository.save(userToSave);
            logger.info("User registered successfully - ID: {}, Username: {}",
                    savedUser.getId(), savedUser.getUsername());

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

    // ==================== LOGIN ====================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> authenticateUser(
            @RequestBody Map<String, String> loginRequest,
            HttpServletRequest request) {

        logger.info("=== USER LOGIN ATTEMPT ===");

        try {
            String identifier = loginRequest.get("email");
            if (identifier == null || identifier.trim().isEmpty()) {
                identifier = loginRequest.get("username");
            }
            String password = loginRequest.get("password");

            if (identifier == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Email/username and password are required"));
            }

            Optional<User> userOpt = findUserByIdentifier(identifier);
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Invalid credentials"));
            }

            User user = userOpt.get();

            // Reject PENDING accounts — they haven't finished email verification
            if (user.getRole() == User.Role.PENDING) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Please complete email verification before logging in"));
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), password));

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtTokenUtil.generateToken(userDetails);

            user.setLastLogin(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            // Send login notification asynchronously
            try {
                final String userEmail = user.getEmail();
                final String deviceInfo = extractDeviceInfo(request.getHeader("User-Agent"));
                final String ipAddress  = getClientIpAddress(request);
                final String location   = getLocationFromIP(ipAddress);

                CompletableFuture.runAsync(() -> {
                    try {
                        notificationService.sendLoginNotification(
                                userEmail, deviceInfo, ipAddress, location);
                    } catch (Exception e) {
                        logger.warn("Failed to send login notification: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.warn("Error preparing login notification: {}", e.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("token", token);
            response.put("tokenType", "Bearer");
            response.put("user", createUserResponse(user));
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Invalid credentials"));
        } catch (Exception e) {
            logger.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Authentication failed"));
        }
    }

    // ==================== SEND EMAIL VERIFICATION CODE (post-signup) ====================

    @PostMapping("/send-verification")
    public ResponseEntity<Map<String, Object>> sendVerificationCode(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {

        logger.info("=== SEND EMAIL VERIFICATION CODE ===");

        try {
            String email = requestBody.get("email");
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Email is required"));
            }

            Optional<User> userOpt =
                    userRepository.findByEmail(email.trim().toLowerCase());
            if (!userOpt.isPresent()) {
                // Avoid user enumeration — always return success
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "If this email exists, a verification code has been sent");
                response.put("timestamp", LocalDateTime.now());
                return ResponseEntity.ok(response);
            }

            User user = userOpt.get();
            String otp = generateOtp();
            user.setResetCode(otp);
            user.setResetCodeExpiry(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            final String userEmail  = user.getEmail();
            final String firstName  = user.getFirstName();
            CompletableFuture.runAsync(() -> {
                try {
                    notificationService.sendEmailVerificationCode(userEmail, firstName, otp);
                } catch (Exception e) {
                    logger.error("Failed to send verification email to {}: {}", userEmail, e.getMessage());
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Verification code sent to your email");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Send verification error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to send verification code"));
        }
    }

    // ==================== VERIFY EMAIL CODE (post-signup) ====================

    @PostMapping("/verify-email-code")
    public ResponseEntity<Map<String, Object>> verifyEmailCode(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {

        logger.info("=== VERIFY EMAIL CODE ===");

        try {
            String email = requestBody.get("email");
            String code  = requestBody.get("code");

            if (email == null || code == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Email and code are required"));
            }

            Optional<User> userOpt =
                    userRepository.findByEmail(email.trim().toLowerCase());
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("User not found"));
            }

            User user = userOpt.get();
            Map<String, Object> otpResult = validateOtp(user.getResetCode(),
                    user.getResetCodeExpiry(), code);
            if (!(Boolean) otpResult.get("valid")) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse((String) otpResult.get("message")));
            }

            user.setEmailVerified(true);
            user.setResetCode(null);
            user.setResetCodeExpiry(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Email verified successfully");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Email verification error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Verification failed"));
        }
    }

    // ==================== PRE-SIGNUP EMAIL VERIFICATION ====================

    /**
     * Step 1 of signup: send OTP to an email BEFORE the account is created.
     *
     * FIX: OTP is now stored in the DB on a PENDING User record instead of
     * an in-memory ConcurrentHashMap. This makes verification survive app
     * restarts and work correctly across multiple instances / load-balanced
     * deployments.
     *
     * The PENDING user is either created fresh or refreshed if one already
     * exists for this email.  A PENDING user is filtered out of normal login
     * and user-existence checks so it is invisible to the rest of the app
     * until the account is fully activated in /signup.
     */
    @PostMapping("/send-verification-email")
    public ResponseEntity<Map<String, Object>> sendPreSignupVerification(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {

        logger.info("=== SEND PRE-SIGNUP VERIFICATION EMAIL ===");

        try {
            String email     = requestBody.get("email");
            String firstName = requestBody.getOrDefault("firstName", "there");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Email is required"));
            }

            String normalizedEmail = email.trim().toLowerCase();

            // Reject if a real (non-pending) account already owns this email
            Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
            if (existingUser.isPresent() &&
                    existingUser.get().getRole() != User.Role.PENDING) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(createErrorResponse("This email is already registered. Please log in."));
            }

            // Generate OTP
            String otp = generateOtp();
            logger.info("Pre-signup OTP generated for: {} → [{}]", normalizedEmail, otp);

            // Upsert a PENDING User to persist the OTP in the DB
            User pendingUser;
            if (existingUser.isPresent()) {
                // Refresh the existing PENDING record
                pendingUser = existingUser.get();
            } else {
                // Use a short unique username that won't exceed @Size(max=50)
                // and won't collide: "p_" + first 8 chars of UUID
                String pendingUsername = "p_" + java.util.UUID.randomUUID()
                        .toString().replace("-", "").substring(0, 8);

                pendingUser = new User();
                pendingUser.setEmail(normalizedEmail);
                pendingUser.setUsername(pendingUsername);
                // Placeholder password — user cannot log in while PENDING
                pendingUser.setPassword(passwordEncoder.encode(
                        java.util.UUID.randomUUID().toString()));
                pendingUser.setFirstName(firstName.trim().isEmpty() ? "User" : firstName.trim());
                pendingUser.setLastName("Pending");
                pendingUser.setRole(User.Role.PENDING);
                pendingUser.setEmailVerified(false);
                pendingUser.setCreatedAt(LocalDateTime.now());
            }

            pendingUser.setResetCode(otp);
            pendingUser.setResetCodeExpiry(LocalDateTime.now().plusMinutes(10));
            pendingUser.setUpdatedAt(LocalDateTime.now());
            userRepository.save(pendingUser);

            logger.info("PENDING user record saved for: {}", normalizedEmail);

            // Send OTP email synchronously so errors surface immediately
            try {
                notificationService.sendEmailVerificationCode(
                        normalizedEmail, firstName.trim(), otp);
                logger.info("=== PRE-SIGNUP VERIFICATION EMAIL SENT to: {} ===", normalizedEmail);
            } catch (Exception e) {
                logger.error("Failed to send pre-signup verification email to {}: {}",
                        normalizedEmail, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(createErrorResponse(
                                "Failed to send verification email: " + e.getMessage()));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Verification code sent to your email");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Send pre-signup verification error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to send verification code"));
        }
    }

    /**
     * Step 2 of signup: verify the OTP before the account is fully created.
     *
     * FIX: Reads the OTP from the PENDING User record in the DB instead of
     * the old in-memory map.  On success the OTP fields are cleared so the
     * code cannot be reused, but the PENDING record stays until /signup
     * upgrades it to a real account.
     */
    @PostMapping("/verify-pre-signup-code")
    public ResponseEntity<Map<String, Object>> verifyPreSignupCode(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {

        logger.info("=== VERIFY PRE-SIGNUP CODE ===");

        try {
            String email = requestBody.get("email");
            String code  = requestBody.get("code");

            if (email == null || code == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Email and code are required"));
            }

            String normalizedEmail = email.trim().toLowerCase();
            Optional<User> pendingOpt = userRepository.findByEmail(normalizedEmail);

            if (!pendingOpt.isPresent() ||
                    pendingOpt.get().getRole() != User.Role.PENDING) {
                logger.warn("No PENDING record found for: {}", normalizedEmail);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse(
                                "No verification code found. Please request a new one."));
            }

            User pending = pendingOpt.get();

            logger.info("Checking OTP for: {} | stored=[{}] submitted=[{}] expiry={}",
                    normalizedEmail,
                    pending.getResetCode(),
                    code.trim(),
                    pending.getResetCodeExpiry());

            Map<String, Object> otpResult = validateOtp(
                    pending.getResetCode(), pending.getResetCodeExpiry(), code);

            if (!(Boolean) otpResult.get("valid")) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse((String) otpResult.get("message")));
            }

            // Clear OTP so it cannot be reused — keep PENDING record for /signup
            pending.setResetCode(null);
            pending.setResetCodeExpiry(null);
            pending.setEmailVerified(true);
            pending.setUpdatedAt(LocalDateTime.now());
            userRepository.save(pending);

            logger.info("Pre-signup email verified successfully for: {}", normalizedEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Email verified. You may now complete registration.");
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Verify pre-signup code error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Verification failed"));
        }
    }

    // ==================== CHECK USER ====================

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
                // Only count non-PENDING accounts as "existing"
                Optional<User> foundByUsername = userRepository.findByUsername(username.trim());
                boolean usernameExists = foundByUsername.isPresent()
                        && foundByUsername.get().getRole() != User.Role.PENDING;
                response.put("usernameExists", usernameExists);
                response.put("username", username.trim());
            }

            if (email != null && !email.trim().isEmpty()) {
                Optional<User> foundByEmail = userRepository.findByEmail(email.trim().toLowerCase());
                boolean emailExists = foundByEmail.isPresent()
                        && foundByEmail.get().getRole() != User.Role.PENDING;
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

    // ==================== VALIDATE TOKEN ====================

    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("No valid authorization header"));
            }

            String token    = authHeader.substring(7);
            String username = null;
            try {
                username = jwtTokenUtil.getUsernameFromToken(token);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Invalid token format"));
            }

            if (username != null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent() &&
                        userOpt.get().getRole() != User.Role.PENDING) {
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

    /**
     * Generates a zero-padded 6-digit OTP.
     * Uses 1_000_000 as the bound so every value 000000–999999 is possible.
     */
    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    /**
     * Validates a stored OTP against a submitted code.
     * Returns a map with "valid" (Boolean) and "message" (String).
     */
    private Map<String, Object> validateOtp(String storedCode,
                                             LocalDateTime expiry,
                                             String submittedCode) {
        Map<String, Object> result = new HashMap<>();

        if (storedCode == null) {
            result.put("valid", false);
            result.put("message", "No verification code found. Please request a new one.");
            return result;
        }
        if (expiry == null || LocalDateTime.now().isAfter(expiry)) {
            result.put("valid", false);
            result.put("message", "Verification code has expired. Please request a new one.");
            return result;
        }
        if (!storedCode.equals(submittedCode.trim())) {
            logger.warn("OTP mismatch — stored: [{}], submitted: [{}]",
                    storedCode, submittedCode.trim());
            result.put("valid", false);
            result.put("message", "Invalid verification code");
            return result;
        }

        result.put("valid", true);
        return result;
    }

    private Map<String, Object> validateRegistrationInput(String username, String email,
                                                          String password,
                                                          String firstName, String lastName) {
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
                                  String firstName, String lastName,
                                  String phone, String address) {
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
        Optional<User> user =
                userRepository.findByEmail(identifier.trim().toLowerCase());
        if (!user.isPresent()) {
            user = userRepository.findByUsername(identifier.trim());
        }
        return user;
    }

    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id",            user.getId());
        userInfo.put("username",      user.getUsername());
        userInfo.put("email",         user.getEmail());
        userInfo.put("firstName",     user.getFirstName());
        userInfo.put("lastName",      user.getLastName());
        userInfo.put("phone",         user.getPhone());
        userInfo.put("role",          user.getRole().toString());
        userInfo.put("emailVerified", user.isEmailVerified());
        userInfo.put("createdAt",     user.getCreatedAt());
        return userInfo;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success",   false);
        errorResponse.put("message",   message);
        errorResponse.put("timestamp", LocalDateTime.now());
        return errorResponse;
    }

    // ==================== SECURITY HELPER METHODS ====================

    private String extractDeviceInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "Unknown Device";
        if (userAgent.contains("iPhone"))    return "iPhone";
        if (userAgent.contains("iPad"))      return "iPad";
        if (userAgent.contains("Android"))
            return userAgent.contains("Mobile") ? "Android Phone" : "Android Tablet";
        if (userAgent.contains("Windows"))   return "Windows PC";
        if (userAgent.contains("Macintosh")) return "Mac";
        if (userAgent.contains("Linux"))     return "Linux PC";
        if (userAgent.contains("Chrome"))    return "Chrome Browser";
        if (userAgent.contains("Firefox"))   return "Firefox Browser";
        if (userAgent.contains("Safari"))    return "Safari Browser";
        if (userAgent.contains("Edge"))      return "Edge Browser";
        return "Web Browser";
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED",
            "HTTP_VIA", "REMOTE_ADDR"
        };
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String getLocationFromIP(String ipAddress) {
        if (ipAddress == null ||
                ipAddress.equals("0:0:0:0:0:0:0:1") ||
                ipAddress.equals("127.0.0.1")) {
            return "Local Development";
        }
        return "Nigeria";
    }
}