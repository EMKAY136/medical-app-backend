// Enhanced SupportController with detailed logging and fixes
package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.SupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@CrossOrigin(origins = "*")
public class SupportController {

    @Autowired
    private SupportService supportService;

    // ========== ADMIN ENDPOINTS - ENHANCED WITH DEBUG LOGGING ==========

    /**
     * Get active chats for admin dashboard
     * GET /api/support/admin/active-chats
     * Enhanced with detailed logging
     */
    @GetMapping("/admin/active-chats")
    public ResponseEntity<Map<String, Object>> getActiveChats(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== ADMIN ACTIVE CHATS ENDPOINT ===");
            System.out.println("Authenticated user: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                System.err.println("ERROR: No authenticated user found!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            Map<String, Object> response = supportService.getActiveChats(userDetails);
            System.out.println("Service response: " + response);
            
            if ((Boolean) response.get("success")) {
                System.out.println("SUCCESS: Active chats retrieved");
                return ResponseEntity.ok(response);
            } else {
                System.err.println("FAILED: " + response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in getActiveChats: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching active chats: " + e.getMessage()));
        }
    }

    /**
     * Get ALL chats (not just active) - NEW ENDPOINT for debugging
     * GET /api/support/admin/all-chats
     */
    @GetMapping("/admin/all-chats")
    public ResponseEntity<Map<String, Object>> getAllChats(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== ADMIN ALL CHATS ENDPOINT ===");
            System.out.println("Authenticated user: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            Map<String, Object> response = supportService.getAllChats(userDetails);
            System.out.println("All chats response: " + response);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in getAllChats: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching all chats: " + e.getMessage()));
        }
    }

    /**
     * Search patients by name or email - NEW ENDPOINT
     * GET /api/support/admin/search-patients?query=john
     */
    @GetMapping("/admin/search-patients")
    public ResponseEntity<Map<String, Object>> searchPatients(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== ADMIN SEARCH PATIENTS ENDPOINT ===");
            System.out.println("Search query: " + query);
            System.out.println("Authenticated user: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            Map<String, Object> response = supportService.searchPatients(query, userDetails);
            System.out.println("Search results count: " + 
                (response.containsKey("patients") ? ((java.util.List)response.get("patients")).size() : 0));
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in searchPatients: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error searching patients: " + e.getMessage()));
        }
    }

    /**
     * Get chat messages for specific user (admin only)
     * GET /api/support/admin/chat/{userId}
     * Enhanced with detailed logging
     */
    @GetMapping("/admin/chat/{userId}")
    public ResponseEntity<Map<String, Object>> getChatByUserId(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== ADMIN GET CHAT BY USER ID ===");
            System.out.println("Requested user ID: " + userId);
            System.out.println("Authenticated admin: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                System.err.println("ERROR: No authenticated user!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            if (userId == null || userId <= 0) {
                System.err.println("ERROR: Invalid user ID: " + userId);
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid user ID"));
            }
            
            Map<String, Object> response = supportService.getChatByUserId(userId, userDetails);
            System.out.println("Chat retrieval response: " + response);
            
            if ((Boolean) response.get("success")) {
                System.out.println("SUCCESS: Chat retrieved for user " + userId);
                return ResponseEntity.ok(response);
            } else {
                System.err.println("FAILED: " + response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in getChatByUserId: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching user chat: " + e.getMessage()));
        }
    }

    /**
     * Send admin reply to patient
     * POST /api/support/admin/reply
     * Enhanced with validation and logging
     */
    @PostMapping("/admin/reply")
    public ResponseEntity<Map<String, Object>> sendAdminReply(
            @RequestBody Map<String, Object> replyData,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== ADMIN SEND REPLY ENDPOINT ===");
            System.out.println("Reply data: " + replyData);
            System.out.println("Authenticated admin: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            // Validate required fields
            if (!replyData.containsKey("userId") || !replyData.containsKey("message")) {
                System.err.println("ERROR: Missing required fields");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "userId and message are required"));
            }
            
            Map<String, Object> response = supportService.sendAgentReply(replyData, userDetails);
            System.out.println("Send reply response: " + response);
            
            if ((Boolean) response.get("success")) {
                System.out.println("SUCCESS: Reply sent");
                return ResponseEntity.ok(response);
            } else {
                System.err.println("FAILED: " + response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in sendAdminReply: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error sending reply: " + e.getMessage()));
        }
    }

    /**
     * Get support dashboard statistics (admin only)
     * GET /api/support/admin/dashboard
     */
    @GetMapping("/admin/dashboard")
    public ResponseEntity<Map<String, Object>> getSupportDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== SUPPORT DASHBOARD ENDPOINT ===");
            System.out.println("Authenticated user: " + (userDetails != null ? userDetails.getUsername() : "null"));
            
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            Map<String, Object> response = supportService.getSupportDashboard(userDetails);
            System.out.println("Dashboard response: " + response);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            System.err.println("EXCEPTION in getSupportDashboard: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching dashboard: " + e.getMessage()));
        }
    }

    /**
     * Debug endpoint to check database content - TEMPORARY FOR DEBUGGING
     * GET /api/support/admin/debug-info
     */
    @GetMapping("/admin/debug-info")
    public ResponseEntity<Map<String, Object>> getDebugInfo(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            System.out.println("=== DEBUG INFO ENDPOINT ===");
            
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required"));
            }
            
            Map<String, Object> response = supportService.getDebugInfo(userDetails);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("EXCEPTION in getDebugInfo: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching debug info: " + e.getMessage()));
        }
    }

    // ========== USER SUPPORT ENDPOINTS (Keep existing) ==========

    @GetMapping("/faq")
    public ResponseEntity<Map<String, Object>> getFAQ() {
        try {
            Map<String, Object> categories = new HashMap<>();
            
            categories.put("Medical Tests & Booking", Map.of(
                "items", Arrays.asList(
                    Map.of("question", "How do I book a medical test?",
                           "answer", "To book a medical test:\n1. Go to the \"Book Test\" tab\n2. Select your test category\n3. Choose the specific test\n4. Pick your preferred date and time\n5. Confirm your booking\n\nYou'll receive a confirmation SMS with details."),
                    Map.of("question", "Can I reschedule my appointment?",
                           "answer", "Yes, you can reschedule:\n1. Go to \"My Appointments\"\n2. Find your appointment\n3. Tap \"Reschedule\"\n4. Select a new date/time\n\nPlease reschedule at least 24 hours before your appointment."),
                    Map.of("question", "How do I prepare for my test?",
                           "answer", "Preparation varies by test:\n• Blood tests: Fast for 8-12 hours\n• Urine tests: Stay hydrated\n• Scans: Follow specific instructions sent via SMS\n\nCheck your appointment details for specific instructions."),
                    Map.of("question", "What should I bring to my appointment?",
                           "answer", "Please bring:\n1. Valid government ID\n2. Insurance card (if applicable)\n3. Previous test results (if relevant)\n4. List of current medications\n5. Referral letter (if required)")
                )
            ));
            
            categories.put("Test Results & Reports", Map.of(
                "items", Arrays.asList(
                    Map.of("question", "When will my results be ready?",
                           "answer", "Result timelines vary:\n• Basic blood tests: 24-48 hours\n• Complex tests: 3-7 days\n• Imaging: 1-3 days\n\nYou'll be notified via SMS and the app when results are available."),
                    Map.of("question", "How do I access my results?",
                           "answer", "To view your results:\n1. Go to the \"Results\" tab\n2. Find your test in the list\n3. Tap to view detailed results\n4. Download or share as needed\n\nAll results are stored securely in your account."),
                    Map.of("question", "I don't understand my results",
                           "answer", "If your results are unclear:\n1. Check the normal ranges provided\n2. Look for doctor's notes in the report\n3. Schedule a consultation with your doctor\n4. Call our medical helpline for explanation\n\nNever ignore abnormal results - consult a healthcare provider.")
                )
            ));
            
            categories.put("Account & Technical Issues", Map.of(
                "items", Arrays.asList(
                    Map.of("question", "I can't log into my account",
                           "answer", "Try these steps:\n1. Check your internet connection\n2. Verify your email and password\n3. Use \"Forgot Password\" if needed\n4. Clear app cache and restart\n5. Update the app to latest version\n\nContact us if the problem persists."),
                    Map.of("question", "How do I update my profile information?",
                           "answer", "To update your profile:\n1. Go to \"Account\" tab\n2. Tap \"Edit Profile\"\n3. Update your information\n4. Save changes\n\nKeep your contact information current to receive important notifications."),
                    Map.of("question", "The app is running slowly",
                           "answer", "To improve app performance:\n1. Close and restart the app\n2. Restart your device\n3. Check available storage space\n4. Update to the latest version\n5. Clear app cache in device settings")
                )
            ));
            
            categories.put("Privacy & Security", Map.of(
                "items", Arrays.asList(
                    Map.of("question", "How is my medical data protected?",
                           "answer", "Your data is protected with:\n• End-to-end encryption\n• Secure cloud storage\n• HIPAA compliance\n• Regular security audits\n• Access controls\n\nWe never share your medical information without consent."),
                    Map.of("question", "Can I delete my medical records?",
                           "answer", "You can request data deletion:\n1. Contact our support team\n2. Provide identity verification\n3. Specify what data to delete\n\nNote: Some records may be retained for legal/medical reasons as required by law.")
                )
            ));

            return ResponseEntity.ok(Map.of("success", true, "categories", categories));
            
        } catch (Exception e) {
            System.err.println("FAQ endpoint error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching FAQ: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSupportStatus() {
        try {
            LocalTime now = LocalTime.now();
            LocalDate today = LocalDate.now();
            DayOfWeek dayOfWeek = today.getDayOfWeek();
            
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            boolean isBusinessHours = now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(18, 0));
            boolean isOnline = !isWeekend && isBusinessHours;

            Map<String, Object> status = Map.of(
                "success", true,
                "isOnline", isOnline,
                "supportHours", "9:00 AM - 6:00 PM (Mon-Fri)",
                "emergencyContact", "+234-XXX-XXXX",
                "estimatedResponseTime", isOnline ? "15-30 minutes" : "4-6 hours",
                "availableChannels", Arrays.asList("Chat", "Email", "Phone", "Support Ticket"),
                "currentTime", now.toString(),
                "isWeekend", isWeekend,
                "isBusinessHours", isBusinessHours
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            System.err.println("Status endpoint error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching support status: " + e.getMessage()));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = Map.of(
                "success", true,
                "status", "Support system operational",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "endpoints", Arrays.asList(
                    "POST /ticket - Create support ticket",
                    "POST /chat/message - Send chat message", 
                    "GET /chat/history - Get chat history",
                    "GET /tickets - Get user tickets",
                    "GET /stats - Get support statistics",
                    "GET /faq - Get FAQ content",
                    "GET /status - Get support status",
                    "GET /admin/active-chats - Get active chats (admin)",
                    "GET /admin/all-chats - Get all chats (admin)",
                    "GET /admin/search-patients - Search patients (admin)",
                    "GET /admin/chat/{userId} - Get user chat (admin)",
                    "POST /admin/reply - Send admin reply",
                    "GET /admin/dashboard - Get dashboard stats (admin)",
                    "GET /admin/debug-info - Debug information (admin)"
                )
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            System.err.println("Health endpoint error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error in health check: " + e.getMessage()));
        }
    }

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, Object>> createSupportTicket(
            @RequestBody Map<String, Object> ticketData,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = supportService.createSupportTicket(ticketData, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error creating support ticket: " + e.getMessage()));
        }
    }

    @PostMapping("/chat/message")
    public ResponseEntity<Map<String, Object>> sendChatMessage(
            @RequestBody Map<String, String> messageData,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String message = messageData.get("message");
            Map<String, Object> response = supportService.sendChatMessage(message, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error sending message: " + e.getMessage()));
        }
    }

    @GetMapping("/chat/history")
    public ResponseEntity<Map<String, Object>> getChatHistory(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = supportService.getChatHistory(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching chat history: " + e.getMessage()));
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> getUserSupportTickets(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = supportService.getUserSupportTickets(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching support tickets: " + e.getMessage()));
        }
    }


@PostMapping("/chat/send")
public ResponseEntity<Map<String, Object>> sendChatMessageAlternate(
        @RequestBody Map<String, String> messageData,
        @AuthenticationPrincipal UserDetails userDetails) {
    return sendChatMessage(messageData, userDetails);
}

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSupportStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = supportService.getSupportStats(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching support stats: " + e.getMessage()));
        }
    }
}