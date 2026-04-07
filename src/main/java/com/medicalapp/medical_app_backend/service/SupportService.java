// Complete SupportService.java
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

    // ── NEW: injected for real-time WebSocket publishing ────────────────────
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Create support ticket
    public Map<String, Object> createSupportTicket(Map<String, Object> ticketData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            String name = (String) ticketData.get("name");
            String email = (String) ticketData.get("email");
            String subject = (String) ticketData.get("subject");
            String category = (String) ticketData.get("category");

            if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                subject == null || subject.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Name, email, and subject are required");
                return response;
            }

            if (!isValidEmail(email)) {
                response.put("success", false);
                response.put("message", "Invalid email address");
                return response;
            }

            long todayTickets = countUserTicketsToday(user);
            if (todayTickets >= 5) {
                response.put("success", false);
                response.put("message", "Maximum tickets per day exceeded. Please wait or call our support line.");
                return response;
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

            try {
                notificationService.notifyNewSupportTicket(savedTicket);
            } catch (Exception e) {
                System.err.println("Failed to send notification: " + e.getMessage());
            }

            // ── NEW: notify admin panel of new ticket via WebSocket ──────────
            try {
                Map<String, Object> ticketEvent = new HashMap<>();
                ticketEvent.put("event", "NEW_TICKET");
                ticketEvent.put("userId", user.getId());
                ticketEvent.put("userName", user.getFirstName() + " " + user.getLastName());
                ticketEvent.put("ticketNumber", savedTicket.getTicketNumber());
                ticketEvent.put("subject", subject.trim());
                ticketEvent.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/admin/support", ticketEvent);
            } catch (Exception e) {
                System.err.println("WebSocket ticket event failed: " + e.getMessage());
            }

            response.put("success", true);
            response.put("message", "Support ticket created successfully");
            response.put("ticketNumber", savedTicket.getTicketNumber());
            response.put("ticketId", savedTicket.getId());
            response.put("estimatedResponse", "4-6 hours during business hours");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating support ticket: " + e.getMessage());
        }

        return response;
    }

    // Send chat message (patient → admin)
    public Map<String, Object> sendChatMessage(String message, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            if (message == null || message.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Message cannot be empty");
                return response;
            }

            ChatMessage chatMessage = new ChatMessage(user, message.trim(), ChatMessage.SenderType.USER);
            ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

            // ── NEW: broadcast patient message to all connected admins ────────
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
                System.out.println("✅ Patient message published to /topic/admin/new-message for userId: " + user.getId());
            } catch (Exception e) {
                System.err.println("WebSocket publish failed: " + e.getMessage());
            }

            // Generate bot response
            String botResponse = generateBotResponse(message.toLowerCase());
            if (botResponse != null) {
                ChatMessage botMessage = new ChatMessage();
                botMessage.setUser(user);
                botMessage.setSenderType(ChatMessage.SenderType.BOT);
                botMessage.setMessage(botResponse);
                botMessage.setSenderName("Medical Support Bot");
                chatMessageRepository.save(botMessage);
            }

            response.put("success", true);
            response.put("message", "Message sent successfully");
            response.put("messageId", savedMessage.getId());
            response.put("botResponse", botResponse);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending message: " + e.getMessage());
        }

        return response;
    }

    // Send agent reply to patient (admin → patient)
    public Map<String, Object> sendAgentReply(Map<String, Object> replyData, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            Long userId = ((Number) replyData.get("userId")).longValue();
            String message = (String) replyData.get("message");
            Long ticketId = replyData.get("ticketId") != null ? ((Number) replyData.get("ticketId")).longValue() : null;

            if (message == null || message.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Message cannot be empty");
                return response;
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

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
                    if (ticket.getFirstResponseAt() == null) {
                        ticket.setFirstResponseAt(LocalDateTime.now());
                    }
                    ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
                    ticket.setAssignedTo(agent.getUsername());
                    supportTicketRepository.save(ticket);
                }
            }

            ChatMessage savedMessage = chatMessageRepository.save(agentMessage);

            // ── NEW: push agent reply to the patient's WebSocket session ─────
            try {
                Map<String, Object> replyEvent = new HashMap<>();
                replyEvent.put("event", "NEW_AGENT_MESSAGE");
                replyEvent.put("message", message.trim());
                replyEvent.put("senderName", agent.getFirstName() + " " + agent.getLastName());
                replyEvent.put("messageId", savedMessage.getId());
                replyEvent.put("timestamp", LocalDateTime.now().toString());

                // Send to patient so their mobile/web app updates in real time
                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/topic/notifications",
                    replyEvent
                );
                messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/messages",
                    replyEvent
                );
                System.out.println("✅ Agent reply pushed to patient userId: " + userId);
            } catch (Exception e) {
                System.err.println("WebSocket agent reply push failed: " + e.getMessage());
            }

            try {
                notificationService.notifyUserOfAgentReply(user, message, agent.getFirstName());
            } catch (Exception e) {
                System.err.println("Failed to send user notification: " + e.getMessage());
            }

            response.put("success", true);
            response.put("message", "Agent reply sent successfully");
            response.put("messageId", savedMessage.getId());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending agent reply: " + e.getMessage());
        }

        return response;
    }


    public Map<String, Object> getAllChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            System.out.println("=== FETCHING ALL CHATS ===");

            List<Map<String, Object>> allChats = new ArrayList<>();
            Set<Long> processedUserIds = new HashSet<>();

            List<SupportTicket> allTickets = supportTicketRepository.findAll();
            System.out.println("Total tickets in database: " + allTickets.size());
            
            for (SupportTicket ticket : allTickets) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    Map<String, Object> chatInfo = createChatInfo(ticket, ticket.getStatus().name());
                    allChats.add(chatInfo);
                    processedUserIds.add(ticket.getUser().getId());
                }
            }

            List<ChatMessage> allMessages = chatMessageRepository.findAll();
            System.out.println("Total chat messages in database: " + allMessages.size());
            
            Map<Long, List<ChatMessage>> messagesByUser = new HashMap<>();
            for (ChatMessage message : allMessages) {
                Long userId = message.getUser().getId();
                messagesByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(message);
            }
            
            System.out.println("Users with chat messages: " + messagesByUser.size());

            for (Map.Entry<Long, List<ChatMessage>> entry : messagesByUser.entrySet()) {
                Long userId = entry.getKey();
                if (!processedUserIds.contains(userId)) {
                    List<ChatMessage> userMessages = entry.getValue();
                    ChatMessage latestMessage = userMessages.stream()
                        .max(Comparator.comparing(ChatMessage::getCreatedAt))
                        .orElse(null);
                    
                    if (latestMessage != null) {
                        Map<String, Object> chatInfo = createChatInfoFromMessage(latestMessage, "CHAT_ONLY");
                        chatInfo.put("totalMessages", userMessages.size());
                        allChats.add(chatInfo);
                        processedUserIds.add(userId);
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
                "totalTickets", allTickets.size(),
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

    public Map<String, Object> searchPatients(String query, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            System.out.println("=== SEARCHING PATIENTS === Query: " + query);

            List<Map<String, Object>> patients = new ArrayList<>();

            List<User> allUsers = userRepository.findAll();

            if (query == null || query.trim().isEmpty()) {
                for (User user : allUsers) {
                    if (user.getRole() == User.Role.PATIENT) {
                        patients.add(createPatientInfo(user));
                    }
                }
            } else {
                String searchTerm = query.toLowerCase().trim();
                for (User user : allUsers) {
                    if (user.getRole() == User.Role.PATIENT) {
                        String fullName = (user.getFirstName() + " " + user.getLastName()).toLowerCase();
                        String email = user.getEmail().toLowerCase();
                        if (fullName.contains(searchTerm) || email.contains(searchTerm)) {
                            patients.add(createPatientInfo(user));
                        }
                    }
                }
            }

            System.out.println("Patients found: " + patients.size());

            response.put("success", true);
            response.put("patients", patients);
            response.put("totalCount", patients.size());

        } catch (Exception e) {
            System.err.println("Error searching patients: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error searching patients: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getChatByUserId(Long userId, UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            System.out.println("=== FETCHING CHAT FOR USER " + userId + " ===");

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            System.out.println("Messages found: " + messages.size());

            List<Map<String, Object>> messageList = messages.stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            List<SupportTicket> tickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
            List<Map<String, Object>> ticketList = tickets.stream()
                    .map(this::convertTicketToResponse)
                    .collect(Collectors.toList());

            Map<String, Object> userInfo = Map.of(
                "id", user.getId(),
                "name", user.getFirstName() + " " + user.getLastName(),
                "email", user.getEmail(),
                "phoneNumber", user.getPhone() != null ? user.getPhone() : "",
                "role", user.getRole().name()
            );

            response.put("success", true);
            response.put("user", userInfo);
            response.put("messages", messageList);
            response.put("tickets", ticketList);
            response.put("totalMessages", messages.size());
            response.put("totalTickets", tickets.size());

        } catch (Exception e) {
            System.err.println("Error fetching user chat: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error fetching user chat: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getDebugInfo(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            long totalUsers = userRepository.count();
            long totalTickets = supportTicketRepository.count();
            long totalMessages = chatMessageRepository.count();

            List<User> allUsers = userRepository.findAll();
            long patientCount = allUsers.stream().filter(u -> u.getRole() == User.Role.PATIENT).count();
            long doctorCount = allUsers.stream().filter(u -> u.getRole() == User.Role.DOCTOR).count();
            long adminCount = allUsers.stream().filter(u -> u.getRole() == User.Role.ADMIN).count();

            long openTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size();
            long inProgressTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS).size();
            long resolvedTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size();

            List<ChatMessage> allMessages = chatMessageRepository.findAll();
            long userMessages = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.USER).count();
            long botMessages = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.BOT).count();
            long agentMessages = allMessages.stream().filter(m -> m.getSenderType() == ChatMessage.SenderType.SUPPORT_AGENT).count();

            Set<Long> usersWithTickets = supportTicketRepository.findAll().stream()
                .map(t -> t.getUser().getId()).collect(Collectors.toSet());
            Set<Long> usersWithMessages = allMessages.stream()
                .map(m -> m.getUser().getId()).collect(Collectors.toSet());
            Set<Long> chatOnlyUsers = new HashSet<>(usersWithMessages);
            chatOnlyUsers.removeAll(usersWithTickets);

            response.put("success", true);
            response.put("database", Map.of("totalUsers", totalUsers, "totalTickets", totalTickets, "totalMessages", totalMessages));
            response.put("users", Map.of("patients", patientCount, "doctors", doctorCount, "admins", adminCount));
            response.put("tickets", Map.of("open", openTickets, "inProgress", inProgressTickets, "resolved", resolvedTickets));
            response.put("messages", Map.of("fromUsers", userMessages, "fromBots", botMessages, "fromAgents", agentMessages));
            response.put("analysis", Map.of("usersWithTickets", usersWithTickets.size(), "usersWithMessages", usersWithMessages.size(), "chatOnlyUsers", chatOnlyUsers.size()));
            response.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            System.err.println("Error generating debug info: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error generating debug info: " + e.getMessage());
        }

        return response;
    }

    private Map<String, Object> createPatientInfo(User user) {
        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("userId", user.getId());
        patientInfo.put("name", user.getFirstName() + " " + user.getLastName());
        patientInfo.put("email", user.getEmail());
        patientInfo.put("phoneNumber", user.getPhone() != null ? user.getPhone() : "");

        List<SupportTicket> userTickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
        long openTicketsCount = userTickets.stream()
            .filter(t -> t.getStatus() != SupportTicket.TicketStatus.RESOLVED && t.getStatus() != SupportTicket.TicketStatus.CLOSED)
            .count();
        patientInfo.put("totalTickets", userTickets.size());
        patientInfo.put("openTickets", openTicketsCount);

        if (!userTickets.isEmpty()) {
            SupportTicket latestTicket = userTickets.get(0);
            patientInfo.put("lastActivity", latestTicket.getUpdatedAt().toString());
            patientInfo.put("lastTicketStatus", latestTicket.getStatus().name());
        } else {
            List<ChatMessage> userMessages = chatMessageRepository.findByUserOrderByCreatedAtDesc(user, 1);
            if (!userMessages.isEmpty()) {
                patientInfo.put("lastActivity", userMessages.get(0).getCreatedAt().toString());
                patientInfo.put("lastTicketStatus", "CHAT_ONLY");
            } else {
                patientInfo.put("lastActivity", null);
                patientInfo.put("lastTicketStatus", "NO_ACTIVITY");
            }
        }

        return patientInfo;
    }

    public Map<String, Object> getChatHistory(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            List<ChatMessage> messages = chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
            List<Map<String, Object>> messageList = messages.stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("messages", messageList);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching chat history: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getUserSupportTickets(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            List<SupportTicket> tickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user);
            List<Map<String, Object>> ticketList = tickets.stream()
                    .map(this::convertTicketToResponse)
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("tickets", ticketList);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching support tickets: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getSupportStats(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            List<SupportTicket.TicketStatus> openStatuses = Arrays.asList(
                SupportTicket.TicketStatus.OPEN,
                SupportTicket.TicketStatus.IN_PROGRESS,
                SupportTicket.TicketStatus.PENDING_USER
            );

            long totalTickets = supportTicketRepository.findByUserOrderByCreatedAtDesc(user).size();
            long openTickets = supportTicketRepository.countOpenTicketsByUser(user, openStatuses);
            long unreadMessages = chatMessageRepository.countUnreadMessagesForUser(user);

            response.put("success", true);
            response.put("totalTickets", totalTickets);
            response.put("openTickets", openTickets);
            response.put("unreadMessages", unreadMessages);
            response.put("averageResponseTime", "4.2 hours");
            response.put("supportQuality", 4.7);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching support stats: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getTicketById(Long ticketId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            
            if (ticketOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Ticket not found");
                return response;
            }

            SupportTicket ticket = ticketOpt.get();
            
            if (!ticket.getUser().getId().equals(user.getId()) && 
                !hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized access");
                return response;
            }

            List<ChatMessage> messages = chatMessageRepository.findByTicketOrderByCreatedAtAsc(ticket);
            List<Map<String, Object>> messageList = messages.stream()
                    .map(this::convertChatMessageToResponse)
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("ticket", convertTicketToResponse(ticket));
            response.put("messages", messageList);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching ticket: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> updateTicketStatus(Long ticketId, Map<String, String> statusData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            if (!hasRole(user, "SUPPORT_AGENT") && !hasRole(user, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Ticket not found");
                return response;
            }

            SupportTicket ticket = ticketOpt.get();
            String newStatus = statusData.get("status");
            
            try {
                SupportTicket.TicketStatus status = SupportTicket.TicketStatus.valueOf(newStatus.toUpperCase());
                ticket.setStatus(status);
                if (status == SupportTicket.TicketStatus.RESOLVED) {
                    ticket.setResolvedAt(LocalDateTime.now());
                }
                supportTicketRepository.save(ticket);
                response.put("success", true);
                response.put("message", "Ticket status updated successfully");
                response.put("ticket", convertTicketToResponse(ticket));
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("message", "Invalid status value");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating ticket status: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> assignTicketToAgent(Long ticketId, Map<String, Object> assignmentData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            if (!hasRole(user, "ADMIN") && !hasRole(user, "SUPPORT_MANAGER")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin or manager access required");
                return response;
            }

            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Ticket not found");
                return response;
            }

            SupportTicket ticket = ticketOpt.get();
            String agentUsername = (String) assignmentData.get("agentUsername");
            
            Optional<User> agentOpt = userRepository.findByUsername(agentUsername);
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            if (!hasRole(agent, "SUPPORT_AGENT")) {
                response.put("success", false);
                response.put("message", "User is not a support agent");
                return response;
            }

            ticket.setAssignedTo(agentUsername);
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
            supportTicketRepository.save(ticket);
            
            response.put("success", true);
            response.put("message", "Ticket assigned successfully");
            response.put("ticket", convertTicketToResponse(ticket));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error assigning ticket: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getActiveChats(UserDetails agentDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> agentOpt = userRepository.findByUsername(agentDetails.getUsername());
            if (agentOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Agent not found");
                return response;
            }

            User agent = agentOpt.get();
            
            if (!hasRole(agent, "SUPPORT_AGENT") && !hasRole(agent, "ADMIN")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Not a support agent");
                return response;
            }

            System.out.println("Fetching active chats for agent: " + agent.getUsername());

            List<Map<String, Object>> activeChats = new ArrayList<>();
            Set<Long> processedUserIds = new HashSet<>();

            List<SupportTicket> needingResponse = supportTicketRepository.findTicketsNeedingFirstResponse();
            for (SupportTicket ticket : needingResponse) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    activeChats.add(createChatInfo(ticket, "NEEDS_FIRST_RESPONSE"));
                    processedUserIds.add(ticket.getUser().getId());
                }
            }

            List<SupportTicket> inProgressTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS);
            for (SupportTicket ticket : inProgressTickets) {
                if (!processedUserIds.contains(ticket.getUser().getId())) {
                    activeChats.add(createChatInfo(ticket, "IN_PROGRESS"));
                    processedUserIds.add(ticket.getUser().getId());
                }
            }

            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            List<ChatMessage> recentMessages = chatMessageRepository.findRecentUserMessages(yesterday);

            Map<Long, ChatMessage> latestMessagePerUser = new HashMap<>();
            for (ChatMessage message : recentMessages) {
                Long userId = message.getUser().getId();
                if (!processedUserIds.contains(userId) && message.getSenderType() == ChatMessage.SenderType.USER) {
                    if (!latestMessagePerUser.containsKey(userId) || 
                        message.getCreatedAt().isAfter(latestMessagePerUser.get(userId).getCreatedAt())) {
                        latestMessagePerUser.put(userId, message);
                    }
                }
            }

            for (ChatMessage latestMessage : latestMessagePerUser.values()) {
                activeChats.add(createChatInfoFromMessage(latestMessage, "CHAT_ONLY"));
            }

            activeChats.sort((a, b) -> {
                String statusA = (String) a.get("conversationType");
                String statusB = (String) b.get("conversationType");
                if ("NEEDS_FIRST_RESPONSE".equals(statusA) && !"NEEDS_FIRST_RESPONSE".equals(statusB)) return -1;
                if ("NEEDS_FIRST_RESPONSE".equals(statusB) && !"NEEDS_FIRST_RESPONSE".equals(statusA)) return 1;
                String timeA = (String) a.get("lastActivity");
                String timeB = (String) b.get("lastActivity");
                return timeB.compareTo(timeA);
            });

            System.out.println("Total active chats found: " + activeChats.size());

            response.put("success", true);
            response.put("activeChats", activeChats);
            response.put("totalCount", activeChats.size());

        } catch (Exception e) {
            System.err.println("Error fetching active chats: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error fetching active chats: " + e.getMessage());
        }

        return response;
    }

    private Map<String, Object> createChatInfo(SupportTicket ticket, String conversationType) {
        Map<String, Object> chatInfo = new HashMap<>();
        User user = ticket.getUser();
        chatInfo.put("userId", user.getId());
        chatInfo.put("userName", user.getFirstName() + " " + user.getLastName());
        chatInfo.put("userEmail", user.getEmail());
        chatInfo.put("ticketId", ticket.getId());
        chatInfo.put("ticketNumber", ticket.getTicketNumber());
        chatInfo.put("subject", ticket.getSubject());
        chatInfo.put("category", ticket.getCategory());
        chatInfo.put("priority", ticket.getPriority().name());
        chatInfo.put("status", ticket.getStatus().name());
        chatInfo.put("conversationType", conversationType);
        chatInfo.put("createdAt", ticket.getCreatedAt().toString());
        chatInfo.put("lastActivity", ticket.getUpdatedAt().toString());
        chatInfo.put("assignedTo", ticket.getAssignedTo());

        try {
            List<ChatMessage> messages = chatMessageRepository.findByTicketOrderByCreatedAtDesc(ticket, 1);
            if (!messages.isEmpty()) {
                ChatMessage lastMessage = messages.get(0);
                chatInfo.put("lastMessage", lastMessage.getMessage());
                chatInfo.put("lastMessageTime", lastMessage.getCreatedAt().toString());
                chatInfo.put("lastMessageSender", lastMessage.getSenderType().name());
            }
        } catch (Exception e) {
            System.err.println("Error getting last message for ticket " + ticket.getId());
        }

        return chatInfo;
    }

    private Map<String, Object> createChatInfoFromMessage(ChatMessage message, String conversationType) {
        Map<String, Object> chatInfo = new HashMap<>();
        User user = message.getUser();
        chatInfo.put("userId", user.getId());
        chatInfo.put("userName", user.getFirstName() + " " + user.getLastName());
        chatInfo.put("userEmail", user.getEmail());
        chatInfo.put("ticketId", null);
        chatInfo.put("ticketNumber", null);
        chatInfo.put("subject", "Chat Conversation");
        chatInfo.put("category", "General");
        chatInfo.put("priority", "NORMAL");
        chatInfo.put("status", "CHAT_ACTIVE");
        chatInfo.put("conversationType", conversationType);
        chatInfo.put("createdAt", message.getCreatedAt().toString());
        chatInfo.put("lastActivity", message.getCreatedAt().toString());
        chatInfo.put("assignedTo", null);
        chatInfo.put("lastMessage", message.getMessage());
        chatInfo.put("lastMessageTime", message.getCreatedAt().toString());
        chatInfo.put("lastMessageSender", message.getSenderType().name());
        return chatInfo;
    }

    public Map<String, Object> getSupportDashboard(UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> adminOpt = userRepository.findByUsername(adminDetails.getUsername());
            if (adminOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Admin not found");
                return response;
            }

            User admin = adminOpt.get();
            
            if (!hasRole(admin, "ADMIN") && !hasRole(admin, "SUPPORT_MANAGER")) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }

            long totalTickets = supportTicketRepository.count();
            long openTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size();
            long inProgressTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS).size();
            long resolvedTickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size();
            long needingResponse = supportTicketRepository.findTicketsNeedingFirstResponse().size();

            response.put("success", true);
            response.put("totalTickets", totalTickets);
            response.put("openTickets", openTickets);
            response.put("inProgressTickets", inProgressTickets);
            response.put("resolvedTickets", resolvedTickets);
            response.put("needingResponse", needingResponse);
            response.put("averageResponseTime", "4.2 hours");
            response.put("customerSatisfaction", 4.7);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching dashboard: " + e.getMessage());
        }

        return response;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    private long countUserTicketsToday(User user) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return supportTicketRepository.findByUserAndDateRange(user, startOfDay, endOfDay).size();
    }

    private SupportTicket.Priority determinePriority(String subject) {
        String lowerSubject = subject.toLowerCase();
        if (lowerSubject.contains("urgent") || lowerSubject.contains("emergency") ||
            lowerSubject.contains("critical") || lowerSubject.contains("can't access")) {
            return SupportTicket.Priority.HIGH;
        }
        return SupportTicket.Priority.NORMAL;
    }

    private boolean hasRole(User user, String roleName) {
        if (user.getRole() == null) return false;
        switch (roleName) {
            case "SUPPORT_AGENT": return user.getRole() == User.Role.DOCTOR || user.getRole() == User.Role.ADMIN;
            case "ADMIN": return user.getRole() == User.Role.ADMIN;
            case "SUPPORT_MANAGER": return user.getRole() == User.Role.ADMIN;
            default: return false;
        }
    }

    private String generateBotResponse(String userMessage) {
        if (userMessage.contains("booking") || userMessage.contains("appointment")) {
            return "I can help you with test booking issues. For immediate assistance with appointments, you can:\n\n1. Check the 'My Appointments' section\n2. Call our booking hotline: +234-XXX-XXXX\n3. Visit our FAQ for common booking questions\n\nWhat specific booking issue are you experiencing?";
        }
        if (userMessage.contains("result") || userMessage.contains("report")) {
            return "For test results and reports:\n\n1. Results are typically available 24-48 hours after your test\n2. You'll receive an SMS notification when ready\n3. Check the 'Results' tab in the app\n4. Contact the lab directly if results are delayed\n\nIs there a specific result you're looking for?";
        }
        if (userMessage.contains("payment") || userMessage.contains("bill")) {
            return "For payment and billing questions:\n\n1. View payment history in 'Account' section\n2. Download receipts from your appointment details\n3. Contact billing: billing@qualitest.com\n4. Payment issues: +234-XXX-XXXX\n\nWhat payment issue can I help you with?";
        }
        if (userMessage.contains("login") || userMessage.contains("password")) {
            return "For login issues:\n\n1. Use 'Forgot Password' on login screen\n2. Check your email for reset instructions\n3. Ensure you're using the correct email address\n4. Clear app cache and try again\n\nStill having trouble logging in?";
        }
        return "Thank you for contacting Qualitest Medical Support. I've noted your inquiry and our medical support team will assist you shortly. For immediate assistance, you can:\n\n• Check our FAQ section\n• Call our support line: +234-XXX-XXXX\n• Email: support@qualitest.com\n\nIs there anything specific I can help you with right now?";
    }

    private Map<String, Object> convertChatMessageToResponse(ChatMessage message) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", message.getId());
        response.put("message", message.getMessage());
        response.put("senderType", message.getSenderType().name());
        response.put("senderName", message.getSenderName());
        response.put("timestamp", message.getCreatedAt().toString());
        response.put("isRead", message.isRead());
        return response;
    }

    private Map<String, Object> convertTicketToResponse(SupportTicket ticket) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", ticket.getId());
        response.put("ticketNumber", ticket.getTicketNumber());
        response.put("subject", ticket.getSubject());
        response.put("category", ticket.getCategory());
        response.put("status", ticket.getStatus().getDisplayName());
        response.put("priority", ticket.getPriority().getDisplayName());
        response.put("createdAt", ticket.getCreatedAt().toString());
        response.put("updatedAt", ticket.getUpdatedAt().toString());
        response.put("isOpen", ticket.isOpen());
        response.put("assignedTo", ticket.getAssignedTo());
        response.put("resolvedAt", ticket.getResolvedAt() != null ? ticket.getResolvedAt().toString() : null);
        return response;
    }
}