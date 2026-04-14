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
@CrossOrigin(
    originPatterns = {"*"},
    allowedHeaders = {"*"},
    methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS
    },
    allowCredentials = "true"
)
public class SupportController {

    @Autowired
    private SupportService supportService;

    // =========================================================================
    // HELPERS
    // =========================================================================

    private ResponseEntity<Map<String, Object>> unauth() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Authentication required"));
    }

    private ResponseEntity<Map<String, Object>> err500(String msg) {
        System.err.println("❌ SupportController error: " + msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", msg));
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> res) {
        return (Boolean) res.get("success")
                ? ResponseEntity.ok(res)
                : ResponseEntity.badRequest().body(res);
    }

    // =========================================================================
    // PUBLIC ENDPOINTS
    // =========================================================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "status", "Support system operational",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "routes", Arrays.asList(
                        "GET  /api/support/health",
                        "GET  /api/support/status",
                        "GET  /api/support/faq",
                        "POST /api/support/ticket",
                        "POST /api/support/chat/message",
                        "POST /api/support/chat/send",
                        "POST /api/support/chat/end",
                        "GET  /api/support/chat/history",
                        "GET  /api/support/tickets",
                        "GET  /api/support/stats",
                        "GET  /api/support/admin/all-chats",
                        "GET  /api/support/admin/active-chats",
                        "GET  /api/support/admin/chat/{userId}",
                        "GET  /api/support/admin/search-patients",
                        "GET  /api/support/admin/dashboard",
                        "GET  /api/support/admin/debug-info",
                        "POST /api/support/admin/reply",
                        "POST /api/support/admin/end-session/{userId}"
                )
        ));
    }

    @GetMapping("/faq")
    public ResponseEntity<Map<String, Object>> getFAQ() {
        try {
            Map<String, Object> categories = new HashMap<>();

            categories.put("Medical Tests & Booking", Map.of("items", Arrays.asList(
                    Map.of("question", "How do I book a medical test?",
                            "answer", "Go to 'Book Test' → select category → pick date/time → confirm. You'll receive an SMS confirmation."),
                    Map.of("question", "Can I reschedule my appointment?",
                            "answer", "Yes — 'My Appointments' → find it → 'Reschedule'. Must be at least 24 hours before."),
                    Map.of("question", "What should I bring?",
                            "answer", "Valid ID, insurance card, previous test results if relevant, list of current medications.")
            )));

            categories.put("Test Results & Reports", Map.of("items", Arrays.asList(
                    Map.of("question", "When will my results be ready?",
                            "answer", "Basic blood: 24-48h | Complex: 3-7 days | Imaging: 1-3 days. You'll get an SMS notification."),
                    Map.of("question", "How do I access my results?",
                            "answer", "'Results' tab → tap your test → view/download."),
                    Map.of("question", "I don't understand my results",
                            "answer", "Check normal ranges in the report. For abnormal results please consult your doctor or call us.")
            )));

            categories.put("Account & Technical Issues", Map.of("items", Arrays.asList(
                    Map.of("question", "I can't log in",
                            "answer", "1. Check connection\n2. Use 'Forgot Password'\n3. Clear app cache\n4. Update the app"),
                    Map.of("question", "The app is running slowly",
                            "answer", "Close & restart → restart device → check storage → update app.")
            )));

            categories.put("Privacy & Security", Map.of("items", Arrays.asList(
                    Map.of("question", "How is my data protected?",
                            "answer", "End-to-end encryption, secure cloud storage, HIPAA compliance, regular audits. We never share without consent."),
                    Map.of("question", "Can I delete my records?",
                            "answer", "Contact support with identity verification. Some records may be retained for legal reasons.")
            )));

            return ResponseEntity.ok(Map.of("success", true, "categories", categories));
        } catch (Exception e) {
            return err500("Error fetching FAQ: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSupportStatus() {
        try {
            LocalTime now = LocalTime.now();
            DayOfWeek day = LocalDate.now().getDayOfWeek();
            boolean isWeekend       = day == DayOfWeek.SUNDAY;
            boolean isBusinessHours = now.isAfter(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(19, 0));
            boolean isOnline        = !isWeekend && isBusinessHours;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "isOnline", isOnline,
                    "status", isOnline ? "online" : "offline",
                    "supportHours", "8:00 AM - 7:00 PM (Mon-Sat)",
                    "emergencyContact", "08034720230",
                    "estimatedResponseTime", isOnline ? "15-30 minutes" : "4-6 hours",
                    "availableChannels", Arrays.asList("Chat", "Email", "Phone", "Support Ticket"),
                    "currentTime", now.toString()
            ));
        } catch (Exception e) {
            return err500("Error fetching support status: " + e.getMessage());
        }
    }

    // =========================================================================
    // PATIENT ENDPOINTS
    // =========================================================================

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, Object>> createSupportTicket(
            @RequestBody Map<String, Object> ticketData,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            Map<String, Object> res = supportService.createSupportTicket(ticketData, userDetails);
            return (Boolean) res.get("success")
                    ? ResponseEntity.status(HttpStatus.CREATED).body(res)
                    : ResponseEntity.badRequest().body(res);
        } catch (Exception e) {
            return err500("Error creating support ticket: " + e.getMessage());
        }
    }

    @PostMapping("/chat/message")
    public ResponseEntity<Map<String, Object>> sendChatMessage(
            @RequestBody Map<String, String> messageData,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.sendChatMessage(messageData.get("message"), userDetails));
        } catch (Exception e) {
            return err500("Error sending message: " + e.getMessage());
        }
    }

    // Alias so old mobile builds hitting /chat/send still work
    @PostMapping("/chat/send")
    public ResponseEntity<Map<String, Object>> sendChatMessageAlias(
            @RequestBody Map<String, String> messageData,
            @AuthenticationPrincipal UserDetails userDetails) {
        return sendChatMessage(messageData, userDetails);
    }

    /**
     * POST /api/support/chat/end
     * Patient ends their own session.
     * Deletes all their chat messages from DB + any open tickets, then notifies admin via WebSocket.
     */
    @PostMapping("/chat/end")
    public ResponseEntity<Map<String, Object>> patientEndChatSession(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            System.out.println("=== PATIENT END CHAT SESSION === User: " + userDetails.getUsername());
            return ok(supportService.endChatSession(userDetails));
        } catch (Exception e) {
            return err500("Error ending session: " + e.getMessage());
        }
    }

    @GetMapping("/chat/history")
    public ResponseEntity<Map<String, Object>> getChatHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getChatHistory(userDetails));
        } catch (Exception e) {
            return err500("Error fetching chat history: " + e.getMessage());
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> getUserSupportTickets(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getUserSupportTickets(userDetails));
        } catch (Exception e) {
            return err500("Error fetching support tickets: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSupportStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getSupportStats(userDetails));
        } catch (Exception e) {
            return err500("Error fetching support stats: " + e.getMessage());
        }
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<Map<String, Object>> getTicketById(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getTicketById(ticketId, userDetails));
        } catch (Exception e) {
            return err500("Error fetching ticket: " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMIN / SUPPORT-AGENT ENDPOINTS
    // =========================================================================

    /**
     * GET /api/support/admin/all-chats
     * Returns every conversation (ticket-based + chat-only).
     * Protected: ADMIN or DOCTOR role required (set in SecurityConfig).
     */
    @GetMapping("/admin/all-chats")
    public ResponseEntity<Map<String, Object>> getAllChats(
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== GET ALL CHATS === User: " + (userDetails != null ? userDetails.getUsername() : "null"));
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getAllChats(userDetails));
        } catch (Exception e) {
            return err500("Error fetching all chats: " + e.getMessage());
        }
    }

    /**
     * GET /api/support/admin/active-chats
     * Returns only conversations that need attention.
     */
    @GetMapping("/admin/active-chats")
    public ResponseEntity<Map<String, Object>> getActiveChats(
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== GET ACTIVE CHATS === User: " + (userDetails != null ? userDetails.getUsername() : "null"));
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getActiveChats(userDetails));
        } catch (Exception e) {
            return err500("Error fetching active chats: " + e.getMessage());
        }
    }

    /**
     * GET /api/support/admin/chat/{userId}
     * Returns full chat + ticket history for a specific patient.
     */
    @GetMapping("/admin/chat/{userId}")
    public ResponseEntity<Map<String, Object>> getChatByUserId(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== GET CHAT FOR USER " + userId + " === Admin: " + (userDetails != null ? userDetails.getUsername() : "null"));
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getChatByUserId(userId, userDetails));
        } catch (Exception e) {
            return err500("Error fetching user chat: " + e.getMessage());
        }
    }

    /**
     * GET /api/support/admin/search-patients?query=john
     */
    @GetMapping("/admin/search-patients")
    public ResponseEntity<Map<String, Object>> searchPatients(
            @RequestParam(required = false, defaultValue = "") String query,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== SEARCH PATIENTS === Query: '" + query + "'");
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.searchPatients(query, userDetails));
        } catch (Exception e) {
            return err500("Error searching patients: " + e.getMessage());
        }
    }

    /**
     * POST /api/support/admin/reply
     * Admin/agent sends a message to a patient.
     * Body: { userId, message, ticketId? }
     */
    @PostMapping("/admin/reply")
    public ResponseEntity<Map<String, Object>> sendAdminReply(
            @RequestBody Map<String, Object> replyData,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== ADMIN REPLY === Data: " + replyData + " | Agent: " + (userDetails != null ? userDetails.getUsername() : "null"));
        if (userDetails == null) return unauth();
        try {
            if (!replyData.containsKey("userId") || !replyData.containsKey("message")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "userId and message are required"));
            }
            return ok(supportService.sendAgentReply(replyData, userDetails));
        } catch (Exception e) {
            return err500("Error sending reply: " + e.getMessage());
        }
    }

    /**
     * POST /api/support/admin/end-session/{userId}
     * Admin ends a patient's session — deletes all their messages + tickets on both sides.
     * Notifies patient via WebSocket so their UI clears instantly.
     */
    @PostMapping("/admin/end-session/{userId}")
    public ResponseEntity<Map<String, Object>> adminEndSession(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("=== ADMIN END SESSION === PatientId: " + userId + " | Admin: " + (userDetails != null ? userDetails.getUsername() : "null"));
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.adminEndChatSession(userId, userDetails));
        } catch (Exception e) {
            return err500("Error ending session: " + e.getMessage());
        }
    }

    /**
     * GET /api/support/admin/dashboard
     */
    @GetMapping("/admin/dashboard")
    public ResponseEntity<Map<String, Object>> getSupportDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getSupportDashboard(userDetails));
        } catch (Exception e) {
            return err500("Error fetching dashboard: " + e.getMessage());
        }
    }

    /**
     * GET /api/support/admin/debug-info
     */
    @GetMapping("/admin/debug-info")
    public ResponseEntity<Map<String, Object>> getDebugInfo(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauth();
        try {
            return ok(supportService.getDebugInfo(userDetails));
        } catch (Exception e) {
            return err500("Error fetching debug info: " + e.getMessage());
        }
    }
}