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

    @Autowired private UserRepository            userRepository;
    @Autowired private SupportTicketRepository   supportTicketRepository;
    @Autowired private ChatMessageRepository     chatMessageRepository;
    @Autowired private NotificationService       notificationService;
    @Autowired private SimpMessagingTemplate     messagingTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE SUPPORT TICKET
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> createSupportTicket(Map<String, Object> ticketData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            String name     = trimmed(ticketData, "name");
            String email    = trimmed(ticketData, "email");
            String subject  = trimmed(ticketData, "subject");
            String category = (String) ticketData.get("category");

            if (isBlank(name) || isBlank(email) || isBlank(subject)) {
                return fail(response, "Name, email, and subject are required");
            }
            if (!isValidEmail(email)) return fail(response, "Invalid email address");

            if (countUserTicketsToday(user) >= 5) {
                return fail(response, "Maximum tickets per day exceeded. Please wait or call our support line.");
            }

            SupportTicket ticket = new SupportTicket(user, name, email, subject, category);
            ticket.setDescription(subject);
            ticket.setPriority(determinePriority(subject));
            SupportTicket saved = supportTicketRepository.save(ticket);

            // System message inside ticket thread
            ChatMessage sys = new ChatMessage();
            sys.setUser(user);
            sys.setTicket(saved);
            sys.setSenderType(ChatMessage.SenderType.SYSTEM);
            sys.setMessage("Support ticket created. Our medical support team will respond within 4-6 hours during business hours (9 AM - 6 PM).");
            sys.setSenderName("System");
            chatMessageRepository.save(sys);

            // Notification (non-fatal)
            try { notificationService.notifyNewSupportTicket(saved); }
            catch (Exception e) { log("notify ticket failed: " + e.getMessage()); }

            // WebSocket → admin sidebar
            // FIX: include ticketId in the event so admin-websocket.js can deduplicate
            // by "NEW_TICKET:<ticketId>" rather than "NEW_TICKET:<userId>",
            // which was causing false deduplication when the same user opened multiple tickets.
            try {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("event",        "NEW_TICKET");
                evt.put("ticketId",     saved.getId());           // ← dedup key on client
                evt.put("userId",       user.getId());
                evt.put("userName",     fullName(user));
                evt.put("ticketNumber", saved.getTicketNumber());
                evt.put("subject",      subject);
                evt.put("timestamp",    now());
                messagingTemplate.convertAndSend("/topic/admin/support", evt);
            } catch (Exception e) { log("WS ticket event failed: " + e.getMessage()); }

            response.put("success",           true);
            response.put("message",           "Support ticket created successfully");
            response.put("ticketNumber",      saved.getTicketNumber());
            response.put("ticketId",          saved.getId());
            response.put("estimatedResponse", "4-6 hours during business hours");
        } catch (Exception e) {
            fail(response, "Error creating support ticket: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEND CHAT MESSAGE  (patient → backend)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> sendChatMessage(String message, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            if (isBlank(message)) return fail(response, "Message cannot be empty");

            ChatMessage chatMsg = new ChatMessage(user, message.trim(), ChatMessage.SenderType.USER);
            // Constructor already sets senderName from user, but be explicit for clarity
            chatMsg.setSenderName(fullName(user));
            ChatMessage saved = chatMessageRepository.save(chatMsg);

            // WebSocket → admin new-message topic
            // FIX: always include senderName so admin console log shows the patient name
            try {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("event",      "NEW_PATIENT_MESSAGE");
                evt.put("userId",     user.getId());
                evt.put("userName",   fullName(user));            // display in sidebar
                evt.put("senderName", fullName(user));            // ← was missing, caused "| undefined" in log
                evt.put("userEmail",  user.getEmail());
                evt.put("message",    message.trim());
                evt.put("messageId",  saved.getId());
                evt.put("timestamp",  now());
                messagingTemplate.convertAndSend("/topic/admin/new-message", evt);
            } catch (Exception e) { log("WS patient msg failed: " + e.getMessage()); }

            // Bot reply — only when no agent has ever responded in this user's history
            String botReply = null;
            long agentCount = chatMessageRepository.countByUserAndSenderType(user, ChatMessage.SenderType.SUPPORT_AGENT);
            if (agentCount == 0) {
                botReply = generateBotResponse(message.toLowerCase());
                if (botReply != null) {
                    ChatMessage bot = new ChatMessage();
                    bot.setUser(user);
                    bot.setSenderType(ChatMessage.SenderType.BOT);
                    bot.setMessage(botReply);
                    bot.setSenderName("Medical Support Bot");
                    chatMessageRepository.save(bot);
                }
            }

            response.put("success",     true);
            response.put("message",     "Message sent successfully");
            response.put("messageId",   saved.getId());
            response.put("botResponse", botReply);
        } catch (Exception e) {
            fail(response, "Error sending message: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEND AGENT REPLY  (admin → patient)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> sendAgentReply(Map<String, Object> replyData, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;

            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized: Not a support agent");
            }

            Long   userId   = toLong(replyData.get("userId"));
            String message  = trimmedStr((String) replyData.get("message"));
            Long   ticketId = replyData.get("ticketId") != null ? toLong(replyData.get("ticketId")) : null;

            if (isBlank(message)) return fail(response, "Message cannot be empty");

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return fail(response, "User not found");
            User patient = userOpt.get();

            ChatMessage agentMsg = new ChatMessage();
            agentMsg.setUser(patient);
            agentMsg.setSenderType(ChatMessage.SenderType.SUPPORT_AGENT);
            agentMsg.setMessage(message);
            agentMsg.setSenderName(fullName(agent));             // always the real agent name

            if (ticketId != null) {
                supportTicketRepository.findById(ticketId).ifPresent(ticket -> {
                    agentMsg.setTicket(ticket);
                    if (ticket.getFirstResponseAt() == null) ticket.setFirstResponseAt(LocalDateTime.now());
                    ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
                    ticket.setAssignedTo(agent.getUsername());
                    supportTicketRepository.save(ticket);
                });
            }

            ChatMessage saved = chatMessageRepository.save(agentMsg);

            // WebSocket → patient device
            // FIX: send on BOTH user-specific channels so mobile SockJS (user-destination)
            // and the global broadcast (/topic/notifications) both fire.
            try {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("event",      "NEW_AGENT_MESSAGE");
                evt.put("message",    message);
                evt.put("senderName", fullName(agent));
                evt.put("agentName",  fullName(agent));           // alias for older mobile code
                evt.put("messageId",  saved.getId());
                evt.put("timestamp",  now());
                // user-destination (requires spring security principal set to userId string)
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages",      evt);
                messagingTemplate.convertAndSendToUser(userId.toString(), "/topic/notifications", evt);
            } catch (Exception e) { log("WS agent reply push failed: " + e.getMessage()); }

            // Email / push notification (non-fatal)
            try { notificationService.notifyUserOfAgentReply(patient, message, agent.getFirstName()); }
            catch (Exception e) { log("notify user of agent reply failed: " + e.getMessage()); }

            response.put("success",   true);
            response.put("message",   "Agent reply sent successfully");
            response.put("messageId", saved.getId());
        } catch (Exception e) {
            fail(response, "Error sending agent reply: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATIENT ENDS SESSION
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> endChatSession(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            List<ChatMessage> msgs = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            int deleted = msgs.size();
            if (!msgs.isEmpty()) chatMessageRepository.deleteAll(msgs);
            log("Patient ended session — deleted " + deleted + " messages for user " + user.getId());

            // Notify admin sidebar
            try {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("event",     "SESSION_ENDED");
                evt.put("userId",    user.getId());
                evt.put("userName",  fullName(user));
                evt.put("timestamp", now());
                messagingTemplate.convertAndSend("/topic/admin/new-message", evt);
            } catch (Exception e) { log("WS session-end event failed: " + e.getMessage()); }

            response.put("success",         true);
            response.put("message",         "Session ended, " + deleted + " messages cleared");
            response.put("deletedMessages", deleted);
        } catch (Exception e) {
            fail(response, "Error ending session: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN ENDS SESSION
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> adminEndChatSession(Long userId, UserDetails agentDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return fail(result, "User not found: " + userId);
            User patient = userOpt.get();

            // 1. Delete all chat messages
            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(patient);
            int deletedMessages = messages.size();
            if (deletedMessages > 0) chatMessageRepository.deleteAll(messages);
            log("✅ Deleted " + deletedMessages + " messages for user " + userId);

            // 2. Resolve open/in-progress tickets
            int resolvedTickets = 0;
            try {
                List<SupportTicket> open = supportTicketRepository.findByUserAndStatusIn(
                        patient,
                        Arrays.asList(SupportTicket.TicketStatus.OPEN, SupportTicket.TicketStatus.IN_PROGRESS));
                for (SupportTicket t : open) {
                    t.setStatus(SupportTicket.TicketStatus.RESOLVED);
                    t.setResolvedAt(LocalDateTime.now());
                    supportTicketRepository.save(t);
                    resolvedTickets++;
                }
                log("✅ Resolved " + resolvedTickets + " tickets for user " + userId);
            } catch (Exception e) {
                log("⚠️ Could not resolve tickets for user " + userId + ": " + e.getMessage());
            }

            // 3. Notify admin dashboard (remove patient row from sidebar)
            try {
                Map<String, Object> adminEvt = new LinkedHashMap<>();
                adminEvt.put("event",     "SESSION_ENDED");
                adminEvt.put("userId",    userId);
                adminEvt.put("userName",  fullName(patient));
                adminEvt.put("timestamp", now());
                messagingTemplate.convertAndSend("/topic/admin/new-message", adminEvt);
            } catch (Exception e) { log("WS admin SESSION_ENDED failed: " + e.getMessage()); }

            // 4. Notify patient device — SESSION_ENDED clears mobile UI immediately
            // FIX: fire on BOTH queue and topic so the mobile app receives it regardless
            // of which subscription it registered (useFocusEffect vs background subscribe).
            try {
                Map<String, Object> patientEvt = new LinkedHashMap<>();
                patientEvt.put("event",     "SESSION_ENDED");
                patientEvt.put("timestamp", now());
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages",      patientEvt);
                messagingTemplate.convertAndSendToUser(userId.toString(), "/topic/notifications", patientEvt);
                log("✅ SESSION_ENDED pushed to patient userId: " + userId);
            } catch (Exception e) { log("WS patient SESSION_ENDED push failed: " + e.getMessage()); }

            result.put("success",         true);
            result.put("message",         "Session ended and cleared successfully");
            result.put("deletedMessages", deletedMessages);
            result.put("resolvedTickets", resolvedTickets);
            result.put("userId",          userId);
            result.put("clearedBy",       agentDetails.getUsername());
        } catch (Exception e) {
            log("❌ adminEndChatSession error for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
            fail(result, "Error ending session: " + e.getMessage());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET ALL CHATS  (admin sidebar)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getAllChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized: Not a support agent");
            }

            List<Map<String, Object>> allChats   = new ArrayList<>();
            Set<Long>                 seenUserIds = new HashSet<>();

            // Only OPEN + IN_PROGRESS tickets
            List<SupportTicket> active = new ArrayList<>();
            try {
                active.addAll(supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN));
                active.addAll(supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS));
            } catch (Exception e) {
                log("⚠️ Status-filtered query failed, falling back to findAll: " + e.getMessage());
                active = supportTicketRepository.findAll();
            }

            for (SupportTicket ticket : active) {
                Long uid = ticket.getUser().getId();
                if (seenUserIds.add(uid)) {
                    allChats.add(createChatInfo(ticket, ticket.getStatus().name()));
                }
            }

            // Also include chat-only users (no active ticket but have messages)
            List<ChatMessage> allMsgs = chatMessageRepository.findAll();
            Map<Long, List<ChatMessage>> byUser = new HashMap<>();
            for (ChatMessage m : allMsgs) {
                byUser.computeIfAbsent(m.getUser().getId(), k -> new ArrayList<>()).add(m);
            }

            for (Map.Entry<Long, List<ChatMessage>> entry : byUser.entrySet()) {
                Long uid = entry.getKey();
                if (seenUserIds.add(uid)) {
                    entry.getValue().stream()
                            .max(Comparator.comparing(ChatMessage::getCreatedAt))
                            .ifPresent(latest -> {
                                Map<String, Object> info = createChatInfoFromMessage(latest, "CHAT_ONLY");
                                info.put("totalMessages", entry.getValue().size());
                                allChats.add(info);
                            });
                }
            }

            allChats.sort((a, b) -> ((String) b.get("lastActivity")).compareTo((String) a.get("lastActivity")));

            response.put("success",    true);
            response.put("chats",      allChats);
            response.put("totalCount", allChats.size());
            response.put("debug", Map.of(
                    "activeTickets", active.size(),
                    "totalMessages", allMsgs.size(),
                    "uniqueUsers",   seenUserIds.size()));
        } catch (Exception e) {
            log("Error fetching all chats: " + e.getMessage());
            e.printStackTrace();
            fail(response, "Error fetching all chats: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SEARCH PATIENTS  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> searchPatients(String query, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized: Not a support agent");
            }

            String term = (query == null || query.trim().isEmpty()) ? null : query.toLowerCase().trim();
            List<Map<String, Object>> patients = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.PATIENT)
                    .filter(u -> {
                        if (term == null) return true;
                        return (u.getFirstName() + " " + u.getLastName()).toLowerCase().contains(term)
                                || u.getEmail().toLowerCase().contains(term);
                    })
                    .map(this::createPatientInfo)
                    .collect(Collectors.toList());

            response.put("success",    true);
            response.put("patients",   patients);
            response.put("totalCount", patients.size());
        } catch (Exception e) {
            fail(response, "Error searching patients: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET CHAT BY USER ID  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getChatByUserId(Long userId, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized: Not a support agent");
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return fail(response, "User not found");
            User user = userOpt.get();

            List<Map<String, Object>> msgList = chatMessageRepository
                    .findByUserOrderByCreatedAtAsc(user).stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            List<Map<String, Object>> ticketList = supportTicketRepository
                    .findByUserOrderByCreatedAtDesc(user).stream()
                    .map(this::convertTicketToResponse)
                    .collect(Collectors.toList());

            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id",          user.getId());
            userInfo.put("name",        fullName(user));
            userInfo.put("email",       user.getEmail());
            userInfo.put("phoneNumber", user.getPhone() != null ? user.getPhone() : "");
            userInfo.put("role",        user.getRole().name());

            response.put("success",       true);
            response.put("user",          userInfo);
            response.put("messages",      msgList);
            response.put("tickets",       ticketList);
            response.put("totalMessages", msgList.size());
            response.put("totalTickets",  ticketList.size());
        } catch (Exception e) {
            fail(response, "Error fetching user chat: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEBUG INFO  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getDebugInfo(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized: Not a support agent");
            }

            List<User>        allUsers  = userRepository.findAll();
            List<ChatMessage> allMsgs   = chatMessageRepository.findAll();

            Set<Long> withTickets  = supportTicketRepository.findAll().stream().map(t -> t.getUser().getId()).collect(Collectors.toSet());
            Set<Long> withMessages = allMsgs.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
            Set<Long> chatOnly     = new HashSet<>(withMessages);
            chatOnly.removeAll(withTickets);

            response.put("success",  true);
            response.put("database", Map.of(
                    "totalUsers",    userRepository.count(),
                    "totalTickets",  supportTicketRepository.count(),
                    "totalMessages", chatMessageRepository.count()));
            response.put("users", Map.of(
                    "patients", allUsers.stream().filter(u -> u.getRole() == User.Role.PATIENT).count(),
                    "doctors",  allUsers.stream().filter(u -> u.getRole() == User.Role.DOCTOR).count(),
                    "admins",   allUsers.stream().filter(u -> u.getRole() == User.Role.ADMIN).count()));
            response.put("tickets", Map.of(
                    "open",       supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size(),
                    "inProgress", supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS).size(),
                    "resolved",   supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size()));
            response.put("messages", Map.of(
                    "fromUsers",  allMsgs.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.USER).count(),
                    "fromBots",   allMsgs.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.BOT).count(),
                    "fromAgents", allMsgs.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.SUPPORT_AGENT).count()));
            response.put("analysis", Map.of(
                    "usersWithTickets",  withTickets.size(),
                    "usersWithMessages", withMessages.size(),
                    "chatOnlyUsers",     chatOnly.size()));
            response.put("timestamp", now());
        } catch (Exception e) {
            fail(response, "Error generating debug info: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET CHAT HISTORY  (patient)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getChatHistory(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            List<Map<String, Object>> msgs = chatMessageRepository
                    .findByUserOrderByCreatedAtAsc(user).stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            response.put("success",  true);
            response.put("messages", msgs);
        } catch (Exception e) {
            fail(response, "Error fetching chat history: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET USER SUPPORT TICKETS  (patient)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getUserSupportTickets(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            List<Map<String, Object>> tickets = supportTicketRepository
                    .findByUserOrderByCreatedAtDesc(user).stream()
                    .map(this::convertTicketToResponse)
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("tickets", tickets);
        } catch (Exception e) {
            fail(response, "Error fetching support tickets: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUPPORT STATS  (patient)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getSupportStats(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            List<SupportTicket.TicketStatus> openStatuses = Arrays.asList(
                    SupportTicket.TicketStatus.OPEN,
                    SupportTicket.TicketStatus.IN_PROGRESS,
                    SupportTicket.TicketStatus.PENDING_USER);

            response.put("success",             true);
            response.put("totalTickets",        supportTicketRepository.findByUserOrderByCreatedAtDesc(user).size());
            response.put("openTickets",         supportTicketRepository.countOpenTicketsByUser(user, openStatuses));
            response.put("unreadMessages",      chatMessageRepository.countUnreadMessagesForUser(user));
            response.put("averageResponseTime", "4.2 hours");
            response.put("supportQuality",      4.7);
        } catch (Exception e) {
            fail(response, "Error fetching support stats: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET TICKET BY ID
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getTicketById(Long ticketId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;

            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) return fail(response, "Ticket not found");
            SupportTicket ticket = ticketOpt.get();

            if (!ticket.getUser().getId().equals(user.getId())
                    && !hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                return fail(response, "Unauthorized access");
            }

            List<Map<String, Object>> msgs = chatMessageRepository
                    .findByTicketOrderByCreatedAtAsc(ticket).stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            response.put("success",  true);
            response.put("ticket",   convertTicketToResponse(ticket));
            response.put("messages", msgs);
        } catch (Exception e) {
            fail(response, "Error fetching ticket: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UPDATE TICKET STATUS  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> updateTicketStatus(Long ticketId, Map<String, String> statusData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;
            if (!hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                return fail(response, "Unauthorized");
            }

            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) return fail(response, "Ticket not found");
            SupportTicket ticket = ticketOpt.get();

            try {
                SupportTicket.TicketStatus status = SupportTicket.TicketStatus.valueOf(
                        statusData.get("status").toUpperCase());
                ticket.setStatus(status);
                if (status == SupportTicket.TicketStatus.RESOLVED) ticket.setResolvedAt(LocalDateTime.now());
                supportTicketRepository.save(ticket);
                response.put("success", true);
                response.put("message", "Ticket status updated");
                response.put("ticket",  convertTicketToResponse(ticket));
            } catch (IllegalArgumentException e) {
                fail(response, "Invalid status value");
            }
        } catch (Exception e) {
            fail(response, "Error updating ticket status: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET ACTIVE CHATS  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getActiveChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User agent = resolveUser(agentDetails, response);
            if (agent == null) return response;
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                return fail(response, "Unauthorized");
            }

            List<Map<String, Object>> active   = new ArrayList<>();
            Set<Long>                 seenUids  = new HashSet<>();

            for (SupportTicket t : supportTicketRepository.findTicketsNeedingFirstResponse()) {
                if (seenUids.add(t.getUser().getId()))
                    active.add(createChatInfo(t, "NEEDS_FIRST_RESPONSE"));
            }
            for (SupportTicket t : supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS)) {
                if (seenUids.add(t.getUser().getId()))
                    active.add(createChatInfo(t, "IN_PROGRESS"));
            }

            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            for (ChatMessage m : chatMessageRepository.findRecentUserMessages(yesterday)) {
                Long uid = m.getUser().getId();
                if (seenUids.add(uid) && m.getSenderType() == ChatMessage.SenderType.USER) {
                    active.add(createChatInfoFromMessage(m, "CHAT_ONLY"));
                }
            }

            active.sort((a, b) -> {
                if ("NEEDS_FIRST_RESPONSE".equals(a.get("conversationType")) && !"NEEDS_FIRST_RESPONSE".equals(b.get("conversationType"))) return -1;
                if ("NEEDS_FIRST_RESPONSE".equals(b.get("conversationType")) && !"NEEDS_FIRST_RESPONSE".equals(a.get("conversationType"))) return 1;
                return ((String) b.get("lastActivity")).compareTo((String) a.get("lastActivity"));
            });

            response.put("success",     true);
            response.put("activeChats", active);
            response.put("totalCount",  active.size());
        } catch (Exception e) {
            fail(response, "Error fetching active chats: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUPPORT DASHBOARD  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> getSupportDashboard(UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User admin = resolveUser(adminDetails, response);
            if (admin == null) return response;
            if (!hasRole(admin, "ADMIN") && !hasRole(admin, "SUPPORT_MANAGER")) {
                return fail(response, "Unauthorized");
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
            fail(response, "Error fetching dashboard: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ASSIGN TICKET  (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public Map<String, Object> assignTicketToAgent(Long ticketId, Map<String, Object> data, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = resolveUser(userDetails, response);
            if (user == null) return response;
            if (!hasRole(user, "ADMIN") && !hasRole(user, "SUPPORT_MANAGER")) {
                return fail(response, "Unauthorized");
            }

            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) return fail(response, "Ticket not found");
            SupportTicket ticket = ticketOpt.get();

            String agentUsername = (String) data.get("agentUsername");
            Optional<User> agentOpt = userRepository.findByUsername(agentUsername);
            if (agentOpt.isEmpty() || !hasRole(agentOpt.get(), "SUPPORT_AGENT")) {
                return fail(response, "Agent not found or not a support agent");
            }

            ticket.setAssignedTo(agentUsername);
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
            supportTicketRepository.save(ticket);

            response.put("success", true);
            response.put("message", "Ticket assigned successfully");
            response.put("ticket",  convertTicketToResponse(ticket));
        } catch (Exception e) {
            fail(response, "Error assigning ticket: " + e.getMessage());
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Resolve UserDetails → User entity, writing failure into response if absent. */
    private User resolveUser(UserDetails ud, Map<String, Object> response) {
        Optional<User> opt = userRepository.findByUsername(ud.getUsername());
        if (opt.isEmpty()) { fail(response, "User not found"); return null; }
        return opt.get();
    }

    private Map<String, Object> fail(Map<String, Object> r, String msg) {
        r.put("success", false);
        r.put("message", msg);
        return r;
    }

    private String fullName(User u) {
        return (u.getFirstName() + " " + u.getLastName()).trim();
    }

    private String now() {
        return LocalDateTime.now().toString();
    }

    private void log(String msg) {
        System.out.println("[SupportService] " + msg);
    }

    private String trimmed(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? ((String) v).trim() : null;
    }

    private String trimmedStr(String s) {
        return s == null ? null : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private boolean isValidEmail(String e) {
        return e.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
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
            case "SUPPORT_AGENT":   return user.getRole() == User.Role.DOCTOR  || user.getRole() == User.Role.ADMIN;
            case "ADMIN":           return user.getRole() == User.Role.ADMIN;
            case "SUPPORT_MANAGER": return user.getRole() == User.Role.ADMIN;
            default:                return false;
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

    // ── Map builders ──────────────────────────────────────────────────────────

    private Map<String, Object> createPatientInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId",      user.getId());
        info.put("name",        fullName(user));
        info.put("email",       user.getEmail());
        info.put("phoneNumber", user.getPhone() != null ? user.getPhone() : "");

        List<SupportTicket> tickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
        long openCount = tickets.stream()
                .filter(t -> t.getStatus() != SupportTicket.TicketStatus.RESOLVED
                          && t.getStatus() != SupportTicket.TicketStatus.CLOSED)
                .count();
        info.put("totalTickets", tickets.size());
        info.put("openTickets",  openCount);

        if (!tickets.isEmpty()) {
            info.put("lastActivity",     tickets.get(0).getUpdatedAt().toString());
            info.put("lastTicketStatus", tickets.get(0).getStatus().name());
        } else {
            List<ChatMessage> recent = chatMessageRepository.findByUserOrderByCreatedAtDesc(user, 1);
            if (!recent.isEmpty()) {
                info.put("lastActivity",     recent.get(0).getCreatedAt().toString());
                info.put("lastTicketStatus", "CHAT_ONLY");
            } else {
                info.put("lastActivity",     null);
                info.put("lastTicketStatus", "NO_ACTIVITY");
            }
        }
        return info;
    }

    private Map<String, Object> createChatInfo(SupportTicket ticket, String conversationType) {
        Map<String, Object> info = new LinkedHashMap<>();
        User user = ticket.getUser();
        info.put("userId",           user.getId());
        info.put("userName",         fullName(user));
        info.put("userEmail",        user.getEmail());
        info.put("ticketId",         ticket.getId());
        info.put("ticketNumber",     ticket.getTicketNumber());
        info.put("subject",          ticket.getSubject());
        info.put("category",         ticket.getCategory());
        info.put("priority",         ticket.getPriority().name());
        info.put("status",           ticket.getStatus().name());
        info.put("conversationType", conversationType);
        info.put("createdAt",        ticket.getCreatedAt().toString());
        info.put("lastActivity",     ticket.getUpdatedAt().toString());
        info.put("assignedTo",       ticket.getAssignedTo());

        // Most-recent message preview (1 row)
        try {
            List<ChatMessage> last = chatMessageRepository.findByTicketOrderByCreatedAtDesc(ticket, 1);
            if (!last.isEmpty()) {
                ChatMessage m = last.get(0);
                info.put("lastMessage",       m.getMessage());
                info.put("lastMessageTime",   m.getCreatedAt().toString());
                info.put("lastMessageSender", m.getSenderType().name());
            }
        } catch (Exception ignored) {}
        return info;
    }

    private Map<String, Object> createChatInfoFromMessage(ChatMessage message, String conversationType) {
        Map<String, Object> info = new LinkedHashMap<>();
        User user = message.getUser();
        info.put("userId",            user.getId());
        info.put("userName",          fullName(user));
        info.put("userEmail",         user.getEmail());
        info.put("ticketId",          null);
        info.put("ticketNumber",      null);
        info.put("subject",           "Chat Conversation");
        info.put("category",          "General");
        info.put("priority",          "NORMAL");
        info.put("status",            "CHAT_ACTIVE");
        info.put("conversationType",  conversationType);
        info.put("createdAt",         message.getCreatedAt().toString());
        info.put("lastActivity",      message.getCreatedAt().toString());
        info.put("assignedTo",        null);
        info.put("lastMessage",       message.getMessage());
        info.put("lastMessageTime",   message.getCreatedAt().toString());
        info.put("lastMessageSender", message.getSenderType().name());
        return info;
    }

    private Map<String, Object> convertChatMessageToResponse(ChatMessage m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id",         m.getId());
        r.put("message",    m.getMessage());
        r.put("senderType", m.getSenderType().name());
        r.put("senderName", m.getSenderName());
        r.put("timestamp",  m.getCreatedAt().toString());
        r.put("isRead",     m.isRead());
        return r;
    }

    private Map<String, Object> convertTicketToResponse(SupportTicket t) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id",           t.getId());
        r.put("ticketNumber", t.getTicketNumber());
        r.put("subject",      t.getSubject());
        r.put("category",     t.getCategory());
        r.put("status",       t.getStatus().getDisplayName());
        r.put("priority",     t.getPriority().getDisplayName());
        r.put("createdAt",    t.getCreatedAt().toString());
        r.put("updatedAt",    t.getUpdatedAt().toString());
        r.put("isOpen",       t.isOpen());
        r.put("assignedTo",   t.getAssignedTo());
        r.put("resolvedAt",   t.getResolvedAt() != null ? t.getResolvedAt().toString() : null);
        return r;
    }
}