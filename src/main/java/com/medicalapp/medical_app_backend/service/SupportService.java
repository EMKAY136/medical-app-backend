package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.*;
import com.medicalapp.medical_app_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SupportService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ── Create support ticket ─────────────────────────────────────────────────
    public Map<String, Object> createSupportTicket(Map<String, Object> ticketData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();

            String name     = (String) ticketData.get("name");
            String email    = (String) ticketData.get("email");
            String subject  = (String) ticketData.get("subject");
            String category = (String) ticketData.get("category");

            if (name == null || name.trim().isEmpty() || email == null || email.trim().isEmpty() || subject == null || subject.trim().isEmpty()) {
                response.put("success", false); response.put("message", "Name, email, and subject are required"); return response;
            }
            if (!isValidEmail(email)) { response.put("success", false); response.put("message", "Invalid email address"); return response; }

            long todayTickets = countUserTicketsToday(user);
            if (todayTickets >= 5) {
                response.put("success", false); response.put("message", "Maximum tickets per day exceeded. Please wait or call our support line."); return response;
            }

            SupportTicket ticket = new SupportTicket(user, name.trim(), email.trim(), subject.trim(), category);
            ticket.setDescription(subject);
            ticket.setPriority(determinePriority(subject));
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setUser(user);
            systemMessage.setTicket(savedTicket);
            systemMessage.setSenderType(ChatMessage.SenderType.SYSTEM);
            systemMessage.setMessage("Support ticket created. Our medical support team will respond within 4-6 hours during business hours (9 AM - 6 PM).");
            systemMessage.setSenderName("System");
            chatMessageRepository.save(systemMessage);

            try { notificationService.notifyNewSupportTicket(savedTicket); } catch (Exception e) { System.err.println("Failed to send notification: " + e.getMessage()); }

            try {
                Map<String, Object> ticketEvent = new HashMap<>();
                ticketEvent.put("event", "NEW_TICKET");
                ticketEvent.put("userId", user.getId());
                ticketEvent.put("userName", user.getFirstName() + " " + user.getLastName());
                ticketEvent.put("ticketNumber", savedTicket.getTicketNumber());
                ticketEvent.put("subject", subject.trim());
                ticketEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/admin/support", ticketEvent);
            } catch (Exception e) { System.err.println("WebSocket ticket event failed: " + e.getMessage()); }

            response.put("success", true);
            response.put("message", "Support ticket created successfully");
            response.put("ticketNumber", savedTicket.getTicketNumber());
            response.put("ticketId", savedTicket.getId());
            response.put("estimatedResponse", "4-6 hours during business hours");
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error creating support ticket: " + e.getMessage());
        }
        return response;
    }

    // ── Send chat message (patient → admin) ───────────────────────────────────
    public Map<String, Object> sendChatMessage(String message, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();

            if (message == null || message.trim().isEmpty()) { response.put("success", false); response.put("message", "Message cannot be empty"); return response; }

            ChatMessage chatMessage = new ChatMessage(user, message.trim(), ChatMessage.SenderType.USER);
            ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

            try {
                Map<String, Object> chatEvent = new HashMap<>();
                chatEvent.put("event", "NEW_PATIENT_MESSAGE");
                chatEvent.put("userId", user.getId());
                chatEvent.put("userName", user.getFirstName() + " " + user.getLastName());
                chatEvent.put("userEmail", user.getEmail());
                chatEvent.put("message", message.trim());
                chatEvent.put("messageId", savedMessage.getId());
                chatEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/admin/new-message", chatEvent);
            } catch (Exception e) { System.err.println("WebSocket publish failed: " + e.getMessage()); }

            String botResponse = null;
            long agentMessageCount = chatMessageRepository.countByUserAndSenderType(user, ChatMessage.SenderType.SUPPORT_AGENT);
            if (agentMessageCount == 0) {
                botResponse = generateBotResponse(message.toLowerCase());
                if (botResponse != null) {
                    ChatMessage botMessage = new ChatMessage();
                    botMessage.setUser(user);
                    botMessage.setSenderType(ChatMessage.SenderType.BOT);
                    botMessage.setMessage(botResponse);
                    botMessage.setSenderName("Medical Support Bot");
                    chatMessageRepository.save(botMessage);
                }
            }

            response.put("success", true);
            response.put("message", "Message sent successfully");
            response.put("messageId", savedMessage.getId());
            response.put("botResponse", botResponse);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error sending message: " + e.getMessage());
        }
        return response;
    }

    // ── Send agent reply (admin → patient) ────────────────────────────────────
    public Map<String, Object> sendAgentReply(Map<String, Object> replyData, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();

            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized: Not a support agent"); return response;
            }

            Long userId   = ((Number) replyData.get("userId")).longValue();
            String message = (String) replyData.get("message");
            Long ticketId  = replyData.get("ticketId") != null ? ((Number) replyData.get("ticketId")).longValue() : null;

            if (message == null || message.trim().isEmpty()) { response.put("success", false); response.put("message", "Message cannot be empty"); return response; }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();

            ChatMessage agentMessage = new ChatMessage();
            agentMessage.setUser(user);
            agentMessage.setSenderType(ChatMessage.SenderType.SUPPORT_AGENT);
            agentMessage.setMessage(message.trim());
            agentMessage.setSenderName(agent.getFirstName() + " " + agent.getLastName());

            if (ticketId != null) {
                Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
                if (ticketOpt.isPresent()) {
                    agentMessage.setTicket(ticketOpt.get());
                    SupportTicket ticket = ticketOpt.get();
                    if (ticket.getFirstResponseAt() == null) ticket.setFirstResponseAt(LocalDateTime.now());
                    ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
                    ticket.setAssignedTo(agent.getUsername());
                    supportTicketRepository.save(ticket);
                }
            }

            ChatMessage savedMessage = chatMessageRepository.save(agentMessage);

            try {
                Map<String, Object> replyEvent = new HashMap<>();
                replyEvent.put("event", "NEW_AGENT_MESSAGE");
                replyEvent.put("message", message.trim());
                replyEvent.put("senderName", agent.getFirstName() + " " + agent.getLastName());
                replyEvent.put("messageId", savedMessage.getId());
                replyEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSendToUser(userId.toString(), "/topic/notifications", replyEvent);
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", replyEvent);
            } catch (Exception e) { System.err.println("WebSocket agent reply push failed: " + e.getMessage()); }

            try { notificationService.notifyUserOfAgentReply(user, message, agent.getFirstName()); } catch (Exception e) { System.err.println("Failed to send user notification: " + e.getMessage()); }

            response.put("success", true);
            response.put("message", "Agent reply sent successfully");
            response.put("messageId", savedMessage.getId());
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error sending agent reply: " + e.getMessage());
        }
        return response;
    }

    // ── Patient ends session ──────────────────────────────────────────────────
    public Map<String, Object> endChatSession(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();

            List<ChatMessage> userMessages = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            int deleted = userMessages.size();
            if (!userMessages.isEmpty()) chatMessageRepository.deleteAll(userMessages);
            System.out.println("Patient ended session — deleted " + deleted + " messages for user " + user.getId());

            try {
                Map<String, Object> event = new HashMap<>();
                event.put("event", "SESSION_ENDED");
                event.put("userId", user.getId());
                event.put("userName", user.getFirstName() + " " + user.getLastName());
                event.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/admin/new-message", event);
            } catch (Exception e) { System.err.println("WebSocket session-end event failed: " + e.getMessage()); }

            response.put("success", true);
            response.put("message", "Session ended, " + deleted + " messages cleared");
            response.put("deletedMessages", deleted);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error ending session: " + e.getMessage());
        }
        return response;
    }

    // ── Admin ends session ────────────────────────────────────────────────────
    // Deletes all chat messages + RESOLVES open tickets for the patient.
    // Also notifies the patient via WebSocket so their mobile UI clears instantly.
    public Map<String, Object> adminEndChatSession(Long userId, UserDetails agentDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) { result.put("success", false); result.put("message", "User not found: " + userId); return result; }
            User patient = userOpt.get();

            // 1. Delete all chat messages
            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(patient);
            int deletedMessages = messages.size();
            if (deletedMessages > 0) chatMessageRepository.deleteAll(messages);
            System.out.println("✅ Deleted " + deletedMessages + " messages for user " + userId);

            // 2. Resolve open/in-progress tickets so getAllChats stops returning them
            int resolvedTickets = 0;
            try {
                List<SupportTicket> openTickets = supportTicketRepository.findByUserAndStatusIn(
                    patient,
                    Arrays.asList(SupportTicket.TicketStatus.OPEN, SupportTicket.TicketStatus.IN_PROGRESS)
                );
                for (SupportTicket ticket : openTickets) {
                    ticket.setStatus(SupportTicket.TicketStatus.RESOLVED);
                    ticket.setResolvedAt(LocalDateTime.now());
                    supportTicketRepository.save(ticket);
                    resolvedTickets++;
                }
                System.out.println("✅ Resolved " + resolvedTickets + " tickets for user " + userId);
            } catch (Exception ticketEx) {
                System.err.println("⚠️ Could not resolve tickets for user " + userId + ": " + ticketEx.getMessage());
            }

            // 3. Notify admin dashboard sidebar (so it removes the patient row)
            try {
                Map<String, Object> adminEvent = new HashMap<>();
                adminEvent.put("event", "SESSION_ENDED");
                adminEvent.put("userId", userId);
                adminEvent.put("userName", patient.getFirstName() + " " + patient.getLastName());
                adminEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/admin/new-message", adminEvent);
            } catch (Exception e) { System.err.println("WebSocket admin event failed: " + e.getMessage()); }

            // 4. Notify patient so their mobile UI clears immediately
            try {
                Map<String, Object> patientEvent = new HashMap<>();
                patientEvent.put("event", "SESSION_ENDED");
                patientEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", patientEvent);
                messagingTemplate.convertAndSendToUser(userId.toString(), "/topic/notifications", patientEvent);
                System.out.println("✅ SESSION_ENDED pushed to patient userId: " + userId);
            } catch (Exception e) { System.err.println("WebSocket patient session-end push failed: " + e.getMessage()); }

            result.put("success",         true);
            result.put("message",         "Session ended and cleared successfully");
            result.put("deletedMessages", deletedMessages);
            result.put("resolvedTickets", resolvedTickets);
            result.put("userId",          userId);
            result.put("clearedBy",       agentDetails.getUsername());
            return result;

        } catch (Exception e) {
            System.err.println("❌ adminEndChatSession error for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Error ending session: " + e.getMessage());
            return result;
        }
    }

    // ── Get all chats (admin) ─────────────────────────────────────────────────
    // KEY FIX: Only returns OPEN or IN_PROGRESS tickets.
    // RESOLVED / CLOSED tickets are excluded so cleared sessions never reappear.
    public Map<String, Object> getAllChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized: Not a support agent"); return response;
            }

            System.out.println("=== FETCHING ALL CHATS ===");

            List<Map<String, Object>> allChats = new ArrayList<>();
            Set<Long> processedUserIds = new HashSet<>();

            // ── Only show ACTIVE (non-resolved) tickets ───────────────────
            // This is the key change: we no longer call findAll() on tickets.
            // Only OPEN and IN_PROGRESS tickets surface in the sidebar.
            List<SupportTicket> activeStatuses = new ArrayList<>();
            try {
                activeStatuses.addAll(supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN));
                activeStatuses.addAll(supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS));
            } catch (Exception e) {
                // Fallback: use findAll if the above queries fail
                System.err.println("⚠️ Status-filtered query failed, falling back to findAll: " + e.getMessage());
                activeStatuses = supportTicketRepository.findAll();
            }

            System.out.println("Active tickets (OPEN+IN_PROGRESS) in database: " + activeStatuses.size());

            for (SupportTicket ticket : activeStatuses) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    Map<String, Object> chatInfo = createChatInfo(ticket, ticket.getStatus().name());
                    allChats.add(chatInfo);
                    processedUserIds.add(ticket.getUser().getId());
                }
            }

            // ── Also include users with recent chat messages but no active ticket ──
            List<ChatMessage> allMessages = chatMessageRepository.findAll();
            System.out.println("Total chat messages in database: " + allMessages.size());

            Map<Long, List<ChatMessage>> messagesByUser = new HashMap<>();
            for (ChatMessage message : allMessages) {
                Long uid = message.getUser().getId();
                messagesByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(message);
            }
            System.out.println("Users with chat messages: " + messagesByUser.size());

            for (Map.Entry<Long, List<ChatMessage>> entry : messagesByUser.entrySet()) {
                Long uid = entry.getKey();
                if (!processedUserIds.contains(uid)) {
                    List<ChatMessage> userMessages = entry.getValue();
                    ChatMessage latestMessage = userMessages.stream()
                        .max(Comparator.comparing(ChatMessage::getCreatedAt)).orElse(null);
                    if (latestMessage != null) {
                        Map<String, Object> chatInfo = createChatInfoFromMessage(latestMessage, "CHAT_ONLY");
                        chatInfo.put("totalMessages", userMessages.size());
                        allChats.add(chatInfo);
                        processedUserIds.add(uid);
                    }
                }
            }

            allChats.sort((a, b) -> {
                String timeA = (String) a.get("lastActivity");
                String timeB = (String) b.get("lastActivity");
                return timeB.compareTo(timeA);
            });

            System.out.println("Total chats compiled: " + allChats.size());

            response.put("success", true);
            response.put("chats", allChats);
            response.put("totalCount", allChats.size());
            response.put("debug", Map.of(
                "activeTickets", activeStatuses.size(),
                "totalMessages", allMessages.size(),
                "uniqueUsers", processedUserIds.size()
            ));
        } catch (Exception e) {
            System.err.println("Error fetching all chats: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error fetching all chats: " + e.getMessage());
        }
        return response;
    }

    // ── Search patients ───────────────────────────────────────────────────────
    public Map<String, Object> searchPatients(String query, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized: Not a support agent"); return response;
            }

            List<Map<String, Object>> patients = new ArrayList<>();
            List<User> allUsers = userRepository.findAll();
            String searchTerm = (query == null || query.trim().isEmpty()) ? null : query.toLowerCase().trim();

            for (User user : allUsers) {
                if (user.getRole() == User.Role.PATIENT) {
                    if (searchTerm == null) {
                        patients.add(createPatientInfo(user));
                    } else {
                        String fullName = (user.getFirstName() + " " + user.getLastName()).toLowerCase();
                        String email    = user.getEmail().toLowerCase();
                        if (fullName.contains(searchTerm) || email.contains(searchTerm)) {
                            patients.add(createPatientInfo(user));
                        }
                    }
                }
            }

            response.put("success", true);
            response.put("patients", patients);
            response.put("totalCount", patients.size());
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error searching patients: " + e.getMessage());
        }
        return response;
    }

    // ── Get chat by user ID (admin) ───────────────────────────────────────────
    public Map<String, Object> getChatByUserId(Long userId, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized: Not a support agent"); return response;
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();

            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            List<Map<String, Object>> messageList = messages.stream().map(this::convertChatMessageToResponse).collect(Collectors.toList());

            List<SupportTicket> tickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
            List<Map<String, Object>> ticketList = tickets.stream().map(this::convertTicketToResponse).collect(Collectors.toList());

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id",          user.getId());
            userInfo.put("name",        user.getFirstName() + " " + user.getLastName());
            userInfo.put("email",       user.getEmail());
            userInfo.put("phoneNumber", user.getPhone() != null ? user.getPhone() : "");
            userInfo.put("role",        user.getRole().name());

            response.put("success",       true);
            response.put("user",          userInfo);
            response.put("messages",      messageList);
            response.put("tickets",       ticketList);
            response.put("totalMessages", messages.size());
            response.put("totalTickets",  tickets.size());
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching user chat: " + e.getMessage());
        }
        return response;
    }

    // ── Debug info ────────────────────────────────────────────────────────────
    public Map<String, Object> getDebugInfo(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized: Not a support agent"); return response;
            }

            long totalUsers    = userRepository.count();
            long totalTickets  = supportTicketRepository.count();
            long totalMessages = chatMessageRepository.count();

            List<User> allUsers = userRepository.findAll();
            long patientCount = allUsers.stream().filter(u -> u.getRole() == User.Role.PATIENT).count();
            long doctorCount  = allUsers.stream().filter(u -> u.getRole() == User.Role.DOCTOR).count();
            long adminCount   = allUsers.stream().filter(u -> u.getRole() == User.Role.ADMIN).count();

            long openTickets       = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size();
            long inProgressTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS).size();
            long resolvedTickets   = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size();

            List<ChatMessage> allMessages = chatMessageRepository.findAll();
            long userMessages  = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.USER).count();
            long botMessages   = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.BOT).count();
            long agentMessages = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.SUPPORT_AGENT).count();

            Set<Long> usersWithTickets  = supportTicketRepository.findAll().stream().map(t -> t.getUser().getId()).collect(Collectors.toSet());
            Set<Long> usersWithMessages = allMessages.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
            Set<Long> chatOnlyUsers     = new HashSet<>(usersWithMessages);
            chatOnlyUsers.removeAll(usersWithTickets);

            response.put("success",  true);
            response.put("database", Map.of("totalUsers", totalUsers, "totalTickets", totalTickets, "totalMessages", totalMessages));
            response.put("users",    Map.of("patients", patientCount, "doctors", doctorCount, "admins", adminCount));
            response.put("tickets",  Map.of("open", openTickets, "inProgress", inProgressTickets, "resolved", resolvedTickets));
            response.put("messages", Map.of("fromUsers", userMessages, "fromBots", botMessages, "fromAgents", agentMessages));
            response.put("analysis", Map.of("usersWithTickets", usersWithTickets.size(), "usersWithMessages", usersWithMessages.size(), "chatOnlyUsers", chatOnlyUsers.size()));
            response.put("timestamp", LocalDateTime.now().toString());
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error generating debug info: " + e.getMessage());
        }
        return response;
    }

    // ── Get chat history (patient) ────────────────────────────────────────────
    public Map<String, Object> getChatHistory(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            response.put("success",  true);
            response.put("messages", messages.stream().map(this::convertChatMessageToResponse).collect(Collectors.toList()));
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching chat history: " + e.getMessage());
        }
        return response;
    }

    // ── Get user support tickets ──────────────────────────────────────────────
    public Map<String, Object> getUserSupportTickets(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            List<SupportTicket> tickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
            response.put("success", true);
            response.put("tickets", tickets.stream().map(this::convertTicketToResponse).collect(Collectors.toList()));
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching support tickets: " + e.getMessage());
        }
        return response;
    }

    // ── Support stats ─────────────────────────────────────────────────────────
    public Map<String, Object> getSupportStats(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            List<SupportTicket.TicketStatus> openStatuses = Arrays.asList(
                SupportTicket.TicketStatus.OPEN, SupportTicket.TicketStatus.IN_PROGRESS, SupportTicket.TicketStatus.PENDING_USER);
            long totalTickets  = supportTicketRepository.findByUserOrderByCreatedAtDesc(user).size();
            long openTickets   = supportTicketRepository.countOpenTicketsByUser(user, openStatuses);
            long unreadMessages = chatMessageRepository.countUnreadMessagesForUser(user);
            response.put("success",              true);
            response.put("totalTickets",         totalTickets);
            response.put("openTickets",          openTickets);
            response.put("unreadMessages",       unreadMessages);
            response.put("averageResponseTime",  "4.2 hours");
            response.put("supportQuality",       4.7);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching support stats: " + e.getMessage());
        }
        return response;
    }

    // ── Get ticket by ID ──────────────────────────────────────────────────────
    public Map<String, Object> getTicketById(Long ticketId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) { response.put("success", false); response.put("message", "Ticket not found"); return response; }
            SupportTicket ticket = ticketOpt.get();
            if (!ticket.getUser().getId().equals(user.getId()) && !hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized access"); return response;
            }
            List<ChatMessage> messages = chatMessageRepository.findByTicketOrderByCreatedAtAsc(ticket);
            response.put("success",  true);
            response.put("ticket",   convertTicketToResponse(ticket));
            response.put("messages", messages.stream().map(this::convertChatMessageToResponse).collect(Collectors.toList()));
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching ticket: " + e.getMessage());
        }
        return response;
    }

    // ── Update ticket status ──────────────────────────────────────────────────
    public Map<String, Object> updateTicketStatus(Long ticketId, Map<String, String> statusData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            if (!hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized"); return response;
            }
            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) { response.put("success", false); response.put("message", "Ticket not found"); return response; }
            SupportTicket ticket = ticketOpt.get();
            try {
                SupportTicket.TicketStatus status = SupportTicket.TicketStatus.valueOf(statusData.get("status").toUpperCase());
                ticket.setStatus(status);
                if (status == SupportTicket.TicketStatus.RESOLVED) ticket.setResolvedAt(LocalDateTime.now());
                supportTicketRepository.save(ticket);
                response.put("success", true); response.put("message", "Ticket status updated"); response.put("ticket", convertTicketToResponse(ticket));
            } catch (IllegalArgumentException e) {
                response.put("success", false); response.put("message", "Invalid status value");
            }
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error updating ticket status: " + e.getMessage());
        }
        return response;
    }

    // ── Get active chats (admin) ──────────────────────────────────────────────
    public Map<String, Object> getActiveChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) { response.put("success", false); response.put("message", "Agent not found"); return response; }
            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false); response.put("message", "Unauthorized"); return response;
            }

            List<Map<String, Object>> activeChats = new ArrayList<>();
            Set<Long> processedUserIds = new HashSet<>();

            for (SupportTicket ticket : supportTicketRepository.findTicketsNeedingFirstResponse()) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    activeChats.add(createChatInfo(ticket, "NEEDS_FIRST_RESPONSE"));
                    processedUserIds.add(ticket.getUser().getId());
                }
            }
            for (SupportTicket ticket : supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS)) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    activeChats.add(createChatInfo(ticket, "IN_PROGRESS"));
                    processedUserIds.add(ticket.getUser().getId());
                }
            }

            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            for (ChatMessage message : chatMessageRepository.findRecentUserMessages(yesterday)) {
                Long uid = message.getUser().getId();
                if (!processedUserIds.contains(uid) && message.getSenderType() == ChatMessage.SenderType.USER) {
                    activeChats.add(createChatInfoFromMessage(message, "CHAT_ONLY"));
                    processedUserIds.add(uid);
                }
            }

            activeChats.sort((a, b) -> {
                if ("NEEDS_FIRST_RESPONSE".equals(a.get("conversationType")) && !"NEEDS_FIRST_RESPONSE".equals(b.get("conversationType"))) return -1;
                if ("NEEDS_FIRST_RESPONSE".equals(b.get("conversationType")) && !"NEEDS_FIRST_RESPONSE".equals(a.get("conversationType"))) return 1;
                return ((String) b.get("lastActivity")).compareTo((String) a.get("lastActivity"));
            });

            response.put("success", true);
            response.put("activeChats", activeChats);
            response.put("totalCount", activeChats.size());
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching active chats: " + e.getMessage());
        }
        return response;
    }

    // ── Support dashboard ─────────────────────────────────────────────────────
    public Map<String, Object> getSupportDashboard(UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> adminOpt = userRepository.findByUsername(adminDetails.getUsername());
            if (adminOpt.isEmpty()) { response.put("success", false); response.put("message", "Admin not found"); return response; }
            User admin = adminOpt.get();
            if (!hasRole(admin, "ADMIN") && !hasRole(admin, "SUPPORT_MANAGER")) {
                response.put("success", false); response.put("message", "Unauthorized"); return response;
            }
            response.put("success",              true);
            response.put("totalTickets",         supportTicketRepository.count());
            response.put("openTickets",          supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size());
            response.put("inProgressTickets",    supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS).size());
            response.put("resolvedTickets",      supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size());
            response.put("needingResponse",      supportTicketRepository.findTicketsNeedingFirstResponse().size());
            response.put("averageResponseTime",  "4.2 hours");
            response.put("customerSatisfaction", 4.7);
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error fetching dashboard: " + e.getMessage());
        }
        return response;
    }

    // ── Assign ticket to agent ────────────────────────────────────────────────
    public Map<String, Object> assignTicketToAgent(Long ticketId, Map<String, Object> assignmentData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) { response.put("success", false); response.put("message", "User not found"); return response; }
            User user = userOpt.get();
            if (!hasRole(user, "ADMIN") && !hasRole(user, "SUPPORT_MANAGER")) {
                response.put("success", false); response.put("message", "Unauthorized"); return response;
            }
            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) { response.put("success", false); response.put("message", "Ticket not found"); return response; }
            SupportTicket ticket = ticketOpt.get();
            String agentUsername = (String) assignmentData.get("agentUsername");
            Optional<User> agentOpt = userRepository.findByUsername(agentUsername);
            if (agentOpt.isEmpty() || !hasRole(agentOpt.get(), "SUPPORT_AGENT")) {
                response.put("success", false); response.put("message", "Agent not found or not a support agent"); return response;
            }
            ticket.setAssignedTo(agentUsername);
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
            supportTicketRepository.save(ticket);
            response.put("success", true); response.put("message", "Ticket assigned successfully"); response.put("ticket", convertTicketToResponse(ticket));
        } catch (Exception e) {
            response.put("success", false); response.put("message", "Error assigning ticket: " + e.getMessage());
        }
        return response;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> createPatientInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("userId",      user.getId());
        info.put("name",        user.getFirstName() + " " + user.getLastName());
        info.put("email",       user.getEmail());
        info.put("phoneNumber", user.getPhone() != null ? user.getPhone() : "");
        List<SupportTicket> userTickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
        long openCount = userTickets.stream().filter(t -> t.getStatus() != SupportTicket.TicketStatus.RESOLVED && t.getStatus() != SupportTicket.TicketStatus.CLOSED).count();
        info.put("totalTickets", userTickets.size());
        info.put("openTickets",  openCount);
        if (!userTickets.isEmpty()) {
            SupportTicket latest = userTickets.get(0);
            info.put("lastActivity",    latest.getUpdatedAt().toString());
            info.put("lastTicketStatus", latest.getStatus().name());
        } else {
            List<ChatMessage> msgs = chatMessageRepository.findByUserOrderByCreatedAtDesc(user, 1);
            if (!msgs.isEmpty()) {
                info.put("lastActivity",    msgs.get(0).getCreatedAt().toString());
                info.put("lastTicketStatus", "CHAT_ONLY");
            } else {
                info.put("lastActivity",    null);
                info.put("lastTicketStatus", "NO_ACTIVITY");
            }
        }
        return info;
    }

    private Map<String, Object> createChatInfo(SupportTicket ticket, String conversationType) {
        Map<String, Object> chatInfo = new HashMap<>();
        User user = ticket.getUser();
        chatInfo.put("userId",           user.getId());
        chatInfo.put("userName",         user.getFirstName() + " " + user.getLastName());
        chatInfo.put("userEmail",        user.getEmail());
        chatInfo.put("ticketId",         ticket.getId());
        chatInfo.put("ticketNumber",     ticket.getTicketNumber());
        chatInfo.put("subject",          ticket.getSubject());
        chatInfo.put("category",         ticket.getCategory());
        chatInfo.put("priority",         ticket.getPriority().name());
        chatInfo.put("status",           ticket.getStatus().name());
        chatInfo.put("conversationType", conversationType);
        chatInfo.put("createdAt",        ticket.getCreatedAt().toString());
        chatInfo.put("lastActivity",     ticket.getUpdatedAt().toString());
        chatInfo.put("assignedTo",       ticket.getAssignedTo());
        try {
            List<ChatMessage> messages = chatMessageRepository.findByTicketOrderByCreatedAtDesc(ticket, 1);
            if (!messages.isEmpty()) {
                ChatMessage last = messages.get(0);
                chatInfo.put("lastMessage",       last.getMessage());
                chatInfo.put("lastMessageTime",   last.getCreatedAt().toString());
                chatInfo.put("lastMessageSender", last.getSenderType().name());
            }
        } catch (Exception e) { /* non-fatal */ }
        return chatInfo;
    }

    private Map<String, Object> createChatInfoFromMessage(ChatMessage message, String conversationType) {
        Map<String, Object> chatInfo = new HashMap<>();
        User user = message.getUser();
        chatInfo.put("userId",            user.getId());
        chatInfo.put("userName",          user.getFirstName() + " " + user.getLastName());
        chatInfo.put("userEmail",         user.getEmail());
        chatInfo.put("ticketId",          null);
        chatInfo.put("ticketNumber",      null);
        chatInfo.put("subject",           "Chat Conversation");
        chatInfo.put("category",          "General");
        chatInfo.put("priority",          "NORMAL");
        chatInfo.put("status",            "CHAT_ACTIVE");
        chatInfo.put("conversationType",  conversationType);
        chatInfo.put("createdAt",         message.getCreatedAt().toString());
        chatInfo.put("lastActivity",      message.getCreatedAt().toString());
        chatInfo.put("assignedTo",        null);
        chatInfo.put("lastMessage",       message.getMessage());
        chatInfo.put("lastMessageTime",   message.getCreatedAt().toString());
        chatInfo.put("lastMessageSender", message.getSenderType().name());
        return chatInfo;
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    private long countUserTicketsToday(User user) {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        return supportTicketRepository.findByUserAndDateRange(user, start, start.plusDays(1)).size();
    }

    private SupportTicket.Priority determinePriority(String subject) {
        String l = subject.toLowerCase();
        if (l.contains("urgent") || l.contains("emergency") || l.contains("critical") || l.contains("can't access")) {
            return SupportTicket.Priority.HIGH;
        }
        return SupportTicket.Priority.NORMAL;
    }

    private boolean hasRole(User user, String roleName) {
        if (user.getRole() == null) return false;
        switch (roleName) {
            case "SUPPORT_AGENT":  return user.getRole() == User.Role.DOCTOR || user.getRole() == User.Role.ADMIN;
            case "ADMIN":          return user.getRole() == User.Role.ADMIN;
            case "SUPPORT_MANAGER":return user.getRole() == User.Role.ADMIN;
            default:               return false;
        }
    }

    private String generateBotResponse(String msg) {
        if (msg.contains("booking") || msg.contains("appointment"))
            return "I can help with test booking. Check 'My Appointments' or call +234-XXX-XXXX. What specific issue are you experiencing?";
        if (msg.contains("result") || msg.contains("report"))
            return "Results are in the 'Results' tab, typically ready in 24-48 hours. Need more help?";
        if (msg.contains("payment") || msg.contains("bill"))
            return "For billing: billing@qualitest.com or +234-XXX-XXXX. What payment issue can I help with?";
        if (msg.contains("login") || msg.contains("password"))
            return "For login issues: use 'Forgot Password', clear app cache, or update the app. Still stuck?";
        return "Thank you for contacting Qualitest Support. Our team will assist you shortly.\n\n• FAQ section\n• Call: +234-XXX-XXXX\n• Email: support@qualitest.com\n\nWhat can I help you with?";
    }

    private Map<String, Object> convertChatMessageToResponse(ChatMessage message) {
        Map<String, Object> r = new HashMap<>();
        r.put("id",         message.getId());
        r.put("message",    message.getMessage());
        r.put("senderType", message.getSenderType().name());
        r.put("senderName", message.getSenderName());
        r.put("timestamp",  message.getCreatedAt().toString());
        r.put("isRead",     message.isRead());
        return r;
    }

    private Map<String, Object> convertTicketToResponse(SupportTicket ticket) {
        Map<String, Object> r = new HashMap<>();
        r.put("id",           ticket.getId());
        r.put("ticketNumber", ticket.getTicketNumber());
        r.put("subject",      ticket.getSubject());
        r.put("category",     ticket.getCategory());
        r.put("status",       ticket.getStatus().getDisplayName());
        r.put("priority",     ticket.getPriority().getDisplayName());
        r.put("createdAt",    ticket.getCreatedAt().toString());
        r.put("updatedAt",    ticket.getUpdatedAt().toString());
        r.put("isOpen",       ticket.isOpen());
        r.put("assignedTo",   ticket.getAssignedTo());
        r.put("resolvedAt",   ticket.getResolvedAt() != null ? ticket.getResolvedAt().toString() : null);
        return r;
    }
}