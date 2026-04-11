const { useState, useEffect, useRef } = React;

const ChatSupportModal = ({ onClose, isAdmin = false, currentUser, selectedPatient = null }) => {
    const [conversations, setConversations] = useState([]);
    const [activeConversation, setActiveConversation] = useState(null);
    const [messages, setMessages] = useState([]);
    const [newMessage, setNewMessage] = useState('');
    const [loading, setLoading] = useState(false);
    const [sending, setSending] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [unreadCount, setUnreadCount] = useState(0);
    const [supportStatus, setSupportStatus] = useState(null);
    const [ticketNumber, setTicketNumber] = useState(null);
    const messagesEndRef = useRef(null);
    const messageInputRef = useRef(null);
    const activeConversationRef = useRef(null);

    const getHeaders = () => ({
        'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
        'Content-Type': 'application/json',
    });

    useEffect(() => {
        activeConversationRef.current = activeConversation;
    }, [activeConversation]);

    const scrollToBottom = () => {
        setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
    };

    useEffect(() => { scrollToBottom(); }, [messages]);

    // ── On mount ──────────────────────────────────────────────────────────────
    useEffect(() => {
        loadSupportStatus();
        if (isAdmin) {
            loadActiveChats();
        } else {
            loadPatientConversation();
        }
    }, [isAdmin]);

    useEffect(() => {
        if (selectedPatient && isAdmin) {
            startConversationWithPatient(selectedPatient);
        }
    }, [selectedPatient]);

    // ── Single stable polling interval ────────────────────────────────────────
    useEffect(() => {
        const interval = setInterval(() => {
            const conv = activeConversationRef.current;

            if (isAdmin) {
                // Always refresh sidebar list
                loadActiveChats(true);
                // Refresh active chat if one is open
                if (conv?.userId) {
                    loadChatByUserId(conv.userId, true);
                }
            } else {
                // Patient side: poll own chat history from patient server
                if (conv) loadPatientChatHistory(true);
            }
        }, 5000);
        return () => clearInterval(interval);
    }, [isAdmin]);

    // ── Support status ────────────────────────────────────────────────────────
    const loadSupportStatus = async () => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/api/support/status`, {
                headers: getHeaders()
            });
            if (!response.ok) return;
            const data = await response.json();
            if (data.success) setSupportStatus(data);
        } catch (error) {
            console.error('Error loading support status:', error);
        }
    };

    // ── Admin: load all active chats from ADMIN server ────────────────────────
    const loadActiveChats = async (silent = false) => {
        if (!isAdmin) return;
        try {
            if (!silent) setLoading(true);
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('Failed to load active chats:', response.status);
                return;
            }
            const data = await response.json();
            if (data.success && data.chats) {
                const mapped = data.chats.map(chat => ({
                    id: chat.ticketId || `user_${chat.userId}`,
                    userId: chat.userId,
                    patientName: chat.userName,
                    patientEmail: chat.userEmail,
                    lastMessage: chat.lastMessage || chat.subject || 'New conversation',
                    lastMessageTime: new Date(chat.lastActivity || chat.createdAt),
                    unreadCount: chat.status === 'NEEDS_RESPONSE' || chat.conversationType === 'NEEDS_FIRST_RESPONSE' ? 1 : 0,
                    status: chat.status || 'active',
                    priority: chat.priority,
                    ticketNumber: chat.ticketNumber,
                    ticketId: chat.ticketId,
                }));
                setConversations(mapped);
                setUnreadCount(mapped.filter(c => c.unreadCount > 0).length);
            }
        } catch (error) {
            console.error('Error loading conversations:', error);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Admin: open chat for a specific patient ───────────────────────────────
    const startConversationWithPatient = (patient) => {
        const conversation = {
            id: `user_${patient.id}`,
            userId: patient.id,
            patientName: `${patient.firstName} ${patient.lastName}`,
            patientEmail: patient.email,
            lastMessage: '',
            lastMessageTime: new Date(),
            unreadCount: 0,
            status: 'new',
        };
        setActiveConversation(conversation);
        activeConversationRef.current = conversation;
        loadChatByUserId(patient.id, false);
    };

    // ── Admin: fetch messages for a patient from ADMIN server ─────────────────
    const loadChatByUserId = async (userId, silent = false) => {
        if (!userId) return;
        try {
            if (!silent) setLoading(true);
            // ✅ ADMIN_API_URL — admin reads patient messages from admin server
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/support/admin/chat/${userId}`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('Failed to load chat for user', userId, response.status);
                return;
            }
            const data = await response.json();
            if (data.success) {
                const formatted = (data.messages || []).map(msg => ({
                    id: msg.id,
                    senderId: (msg.senderType === 'SUPPORT_AGENT' || msg.senderType === 'admin' || msg.senderType === 'support_agent')
                        ? currentUser?.id
                        : userId,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp || msg.createdAt),
                    status: msg.isRead ? 'read' : 'delivered',
                }));
                setMessages(formatted);

                if (data.user) {
                    setActiveConversation(prev => ({
                        ...prev,
                        patientName: data.user.name || `${data.user.firstName || ''} ${data.user.lastName || ''}`.trim(),
                        patientEmail: data.user.email,
                        patientPhone: data.user.phone,
                    }));
                }
            }
        } catch (error) {
            console.error('Error loading chat by userId:', error);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Patient: set up own conversation ──────────────────────────────────────
    const loadPatientConversation = async () => {
        try {
            const conversation = {
                id: 'patient_chat',
                userId: currentUser?.id,
                patientName: `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
                patientEmail: currentUser?.email,
                adminName: 'Medical Support Team',
                status: 'active',
            };
            setActiveConversation(conversation);
            activeConversationRef.current = conversation;
            loadPatientChatHistory(false);
        } catch (error) {
            console.error('Error loading patient conversation:', error);
        }
    };

    // ── Patient: fetch own chat history from PATIENT server ───────────────────
    const loadPatientChatHistory = async (silent = false) => {
        try {
            if (!silent) setLoading(true);
            // ✅ API_BASE_URL — patient reads their own chat from patient server
            const response = await fetch(
                `${CONFIG.API_BASE_URL}/api/support/chat/history`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('Failed to load patient chat history:', response.status);
                return;
            }
            const data = await response.json();
            if (data.success) {
                const formatted = (data.messages || []).map(msg => ({
                    id: msg.id,
                    senderId: (msg.senderType === 'SUPPORT_AGENT' || msg.senderType === 'admin' || msg.senderType === 'support_agent')
                        ? 'admin'
                        : currentUser?.id,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp || msg.createdAt),
                    status: msg.isRead ? 'read' : 'delivered',
                }));
                setMessages(formatted);
            }
        } catch (error) {
            console.error('Error loading patient chat history:', error);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Send message ──────────────────────────────────────────────────────────
    const sendMessage = async () => {
        if (!newMessage.trim() || sending) return;

        setSending(true);
        const messageText = newMessage.trim();
        setNewMessage('');

        const tempMessage = {
            id: `temp_${Date.now()}`,
            senderId: currentUser?.id,
            senderName: isAdmin
                ? 'Medical Support'
                : `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
            senderType: isAdmin ? 'SUPPORT_AGENT' : 'USER',
            message: messageText,
            timestamp: new Date(),
            status: 'sending',
        };
        setMessages(prev => [...prev, tempMessage]);

        try {
            let response;

            if (isAdmin) {
                const conv = activeConversationRef.current;
                if (!conv?.userId) throw new Error('No active patient selected');

                // ✅ Admin sends reply via ADMIN server
                response = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/reply`, {
                    method: 'POST',
                    headers: getHeaders(),
                    body: JSON.stringify({
                        userId: conv.userId,
                        message: messageText,
                        ticketId: conv.ticketId || null,
                    }),
                });
            } else {
                // ✅ Patient sends message via PATIENT server
                response = await fetch(`${CONFIG.API_BASE_URL}/api/support/chat/message`, {
                    method: 'POST',
                    headers: getHeaders(),
                    body: JSON.stringify({ message: messageText }),
                });
            }

            if (response.status === 401) throw new Error('Session expired. Please login again.');
            if (!response.ok) {
                const err = await response.json().catch(() => ({ message: response.statusText }));
                throw new Error(err.message || 'Failed to send message');
            }

            const data = await response.json();

            if (data.success) {
                // Confirm temp message as delivered
                setMessages(prev => prev.map(msg =>
                    msg.id === tempMessage.id
                        ? { ...msg, status: 'delivered', id: data.messageId || msg.id }
                        : msg
                ));

                // Bot response for patient side
                if (!isAdmin && data.botResponse) {
                    setTimeout(() => {
                        setMessages(prev => [...prev, {
                            id: `bot_${Date.now()}`,
                            senderId: 'bot',
                            senderName: 'Medical Support Bot',
                            senderType: 'bot',
                            message: data.botResponse,
                            timestamp: new Date(),
                            status: 'delivered',
                        }]);
                    }, 1000);
                }

                // Update sidebar last message
                const conv = activeConversationRef.current;
                if (conv) {
                    setConversations(prev => prev.map(c =>
                        c.id === conv.id
                            ? { ...c, lastMessage: messageText, lastMessageTime: new Date() }
                            : c
                    ));
                }

                // Immediately re-fetch to confirm message landed
                if (isAdmin && conv?.userId) {
                    loadChatByUserId(conv.userId, true);
                } else if (!isAdmin) {
                    loadPatientChatHistory(true);
                }

            } else {
                throw new Error(data.message || 'Failed to send message');
            }

        } catch (error) {
            console.error('Error sending message:', error);
            setMessages(prev => prev.filter(msg => msg.id !== tempMessage.id));
            alert('Failed to send message: ' + error.message);
        } finally {
            setSending(false);
        }
    };

    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    const formatMessageTime = (timestamp) => {
        const now = new Date();
        const messageTime = new Date(timestamp);
        const diffInHours = (now - messageTime) / (1000 * 60 * 60);
        if (diffInHours < 1) return 'Just now';
        if (diffInHours < 24) return messageTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        return messageTime.toLocaleDateString([], { month: 'short', day: 'numeric' });
    };

    const getSenderTypeColor = (senderType) => {
        const t = (senderType || '').toLowerCase();
        if (t === 'support_agent' || t === 'admin') return '#3b82f6';
        if (t === 'bot') return '#10b981';
        if (t === 'system') return '#6b7280';
        return '#3b82f6';
    };

    const isMyMessage = (message) => {
        const t = (message.senderType || '').toLowerCase();
        if (isAdmin) return t === 'support_agent' || t === 'admin';
        return t === 'user';
    };

    const filteredConversations = conversations.filter(conv =>
        conv.patientName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        conv.patientEmail?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        conv.lastMessage?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    // ── RENDER ────────────────────────────────────────────────────────────────
    return (
        <div className="modal" style={{ zIndex: 1000 }}>
            <div className="modal-content" style={{
                maxWidth: isAdmin ? '1200px' : '700px',
                height: '85vh',
                display: 'flex',
                flexDirection: isAdmin ? 'row' : 'column',
                overflow: 'hidden',
            }}>
                {/* Header */}
                <div className="card-header" style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    position: isAdmin ? 'absolute' : 'relative',
                    top: 0, left: 0, right: 0,
                    zIndex: 10,
                    backgroundColor: 'white',
                    borderBottom: '1px solid #e5e7eb',
                }}>
                    <div>
                        <h2 className="card-title">
                            {isAdmin ? 'Patient Support Chat' : 'Medical Support'}
                        </h2>
                        {supportStatus && !isAdmin && (
                            <div style={{ fontSize: '12px', color: '#6b7280' }}>
                                {supportStatus.isOnline ? (
                                    <span style={{ color: '#10b981' }}>
                                        <i className="fas fa-circle" style={{ fontSize: '8px', marginRight: '4px' }}></i>
                                        Online — Response time: {supportStatus.estimatedResponseTime}
                                    </span>
                                ) : (
                                    <span style={{ color: '#f59e0b' }}>
                                        <i className="fas fa-circle" style={{ fontSize: '8px', marginRight: '4px' }}></i>
                                        Offline — {supportStatus.supportHours}
                                    </span>
                                )}
                            </div>
                        )}
                        {isAdmin && unreadCount > 0 && (
                            <span style={{
                                backgroundColor: '#ef4444', color: 'white',
                                borderRadius: '12px', padding: '2px 8px',
                                fontSize: '12px', marginLeft: '8px',
                            }}>
                                {unreadCount} need response
                            </span>
                        )}
                    </div>
                    <button className="btn btn-secondary" onClick={onClose}>
                        <i className="fas fa-times"></i>
                    </button>
                </div>

                <div style={{
                    display: 'flex', flex: 1,
                    marginTop: isAdmin ? '70px' : '0',
                    overflow: 'hidden',
                }}>
                    {/* ── Sidebar: Admin conversation list ── */}
                    {isAdmin && (
                        <div style={{
                            width: '320px',
                            borderRight: '1px solid #e5e7eb',
                            display: 'flex',
                            flexDirection: 'column',
                            backgroundColor: '#f9fafb',
                        }}>
                            <div style={{ padding: '16px' }}>
                                <input
                                    type="text"
                                    placeholder="Search patients..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    style={{
                                        width: '100%', padding: '8px 12px',
                                        border: '1px solid #d1d5db',
                                        borderRadius: '6px', fontSize: '14px',
                                    }}
                                />
                            </div>

                            <div style={{ flex: 1, overflowY: 'auto' }}>
                                {loading ? (
                                    <div style={{ padding: '20px', textAlign: 'center' }}>
                                        <div className="spinner"></div>
                                    </div>
                                ) : filteredConversations.length === 0 ? (
                                    <div style={{ padding: '20px', textAlign: 'center', color: '#6b7280', fontSize: '13px' }}>
                                        No active conversations
                                    </div>
                                ) : (
                                    filteredConversations.map(conversation => (
                                        <div
                                            key={conversation.id}
                                            onClick={() => {
                                                setActiveConversation(conversation);
                                                activeConversationRef.current = conversation;
                                                loadChatByUserId(conversation.userId, false);
                                            }}
                                            style={{
                                                padding: '12px 16px',
                                                cursor: 'pointer',
                                                borderBottom: '1px solid #e5e7eb',
                                                backgroundColor: activeConversation?.id === conversation.id
                                                    ? '#dbeafe' : 'transparent',
                                                transition: 'background 0.15s',
                                            }}
                                            onMouseEnter={(e) => {
                                                if (activeConversation?.id !== conversation.id)
                                                    e.currentTarget.style.backgroundColor = '#f3f4f6';
                                            }}
                                            onMouseLeave={(e) => {
                                                if (activeConversation?.id !== conversation.id)
                                                    e.currentTarget.style.backgroundColor = 'transparent';
                                            }}
                                        >
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <div style={{ fontWeight: conversation.unreadCount > 0 ? '700' : '500', fontSize: '14px', marginBottom: '3px' }}>
                                                        {conversation.patientName}
                                                    </div>
                                                    {conversation.ticketNumber && (
                                                        <div style={{ fontSize: '11px', color: '#3b82f6', marginBottom: '2px', fontWeight: '600' }}>
                                                            #{conversation.ticketNumber}
                                                        </div>
                                                    )}
                                                    <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '3px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                        {conversation.lastMessage || 'New conversation'}
                                                    </div>
                                                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>
                                                        {formatMessageTime(conversation.lastMessageTime)}
                                                    </div>
                                                </div>
                                                <div style={{ marginLeft: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                                                    {conversation.priority && conversation.priority !== 'NORMAL' && (
                                                        <div style={{ fontSize: '10px', color: conversation.priority === 'HIGH' ? '#ef4444' : '#f59e0b', fontWeight: '700' }}>
                                                            {conversation.priority}
                                                        </div>
                                                    )}
                                                    {conversation.unreadCount > 0 && (
                                                        <div style={{
                                                            backgroundColor: '#ef4444', color: 'white',
                                                            borderRadius: '10px', width: '18px', height: '18px',
                                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                            fontSize: '10px', fontWeight: '700',
                                                        }}>!</div>
                                                    )}
                                                    <div style={{
                                                        width: '8px', height: '8px', borderRadius: '50%',
                                                        backgroundColor:
                                                            conversation.status === 'active' ? '#10b981' :
                                                            conversation.status === 'NEEDS_RESPONSE' ? '#ef4444' :
                                                            conversation.status === 'waiting' ? '#f59e0b' :
                                                            conversation.status === 'resolved' ? '#6b7280' : '#3b82f6',
                                                    }}></div>
                                                </div>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    )}

                    {/* ── Chat area ── */}
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                        {activeConversation ? (
                            <>
                                {/* Chat header */}
                                <div style={{
                                    padding: '12px 16px',
                                    borderBottom: '1px solid #e5e7eb',
                                    backgroundColor: 'white',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                }}>
                                    <div>
                                        <div style={{ fontWeight: '600', fontSize: '15px' }}>
                                            {isAdmin ? activeConversation.patientName : 'Medical Support Team'}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#6b7280' }}>
                                            {isAdmin ? (
                                                <>
                                                    {activeConversation.patientEmail}
                                                    {activeConversation.patientPhone && ` • ${activeConversation.patientPhone}`}
                                                </>
                                            ) : 'Qualitest Medical Support'}
                                        </div>
                                        {ticketNumber && (
                                            <div style={{ fontSize: '11px', color: '#3b82f6', fontWeight: '600' }}>
                                                Ticket #{ticketNumber}
                                            </div>
                                        )}
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                        {isAdmin && (
                                            <button
                                                onClick={() => loadChatByUserId(activeConversation.userId, false)}
                                                style={{ padding: '6px 10px', fontSize: '12px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer' }}
                                                title="Refresh messages"
                                            >
                                                <i className="fas fa-sync-alt"></i>
                                            </button>
                                        )}
                                        <div style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#10b981' }}></div>
                                    </div>
                                </div>

                                {/* Messages */}
                                <div style={{ flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' }}>
                                    {messages.length === 0 && (
                                        <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' }}>
                                            {isAdmin ? 'No messages yet from this patient.' : 'No messages yet. Start the conversation!'}
                                        </div>
                                    )}
                                    {messages.map(message => {
                                        const mine = isMyMessage(message);
                                        return (
                                            <div key={message.id} style={{
                                                display: 'flex',
                                                justifyContent: mine ? 'flex-end' : 'flex-start',
                                                marginBottom: '12px',
                                            }}>
                                                <div style={{
                                                    maxWidth: '75%',
                                                    padding: '12px 16px',
                                                    borderRadius: '18px',
                                                    backgroundColor: mine ? getSenderTypeColor(message.senderType) : 'white',
                                                    color: mine ? 'white' : '#1f2937',
                                                    boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
                                                }}>
                                                    {!mine && message.senderName && (
                                                        <div style={{ fontSize: '11px', fontWeight: '600', marginBottom: '4px', opacity: 0.75 }}>
                                                            {message.senderName}
                                                        </div>
                                                    )}
                                                    <div style={{ marginBottom: '4px', whiteSpace: 'pre-wrap', lineHeight: '1.4' }}>
                                                        {message.message}
                                                    </div>
                                                    <div style={{ fontSize: '11px', opacity: 0.65, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '4px' }}>
                                                        {formatMessageTime(message.timestamp)}
                                                        {mine && (
                                                            <i className={`fas ${
                                                                message.status === 'sending' ? 'fa-clock' :
                                                                message.status === 'read' ? 'fa-check-double' :
                                                                'fa-check'
                                                            }`} style={{ fontSize: '10px' }}></i>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                    <div ref={messagesEndRef} />
                                </div>

                                {/* Input */}
                                <div style={{ padding: '16px', borderTop: '1px solid #e5e7eb', backgroundColor: 'white' }}>
                                    <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-end' }}>
                                        <textarea
                                            ref={messageInputRef}
                                            value={newMessage}
                                            onChange={(e) => setNewMessage(e.target.value)}
                                            onKeyPress={handleKeyPress}
                                            placeholder={isAdmin ? 'Type your reply to the patient...' : 'Type your message...'}
                                            rows={1}
                                            style={{
                                                flex: 1, padding: '12px',
                                                border: '1px solid #d1d5db',
                                                borderRadius: '24px', resize: 'none',
                                                fontSize: '14px', minHeight: '48px', maxHeight: '120px',
                                                fontFamily: 'inherit',
                                            }}
                                        />
                                        <button
                                            onClick={sendMessage}
                                            disabled={!newMessage.trim() || sending}
                                            style={{
                                                padding: '12px 16px',
                                                backgroundColor: newMessage.trim() && !sending ? '#3b82f6' : '#d1d5db',
                                                color: 'white', border: 'none',
                                                borderRadius: '24px',
                                                cursor: newMessage.trim() && !sending ? 'pointer' : 'not-allowed',
                                                minWidth: '48px', height: '48px',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            }}
                                        >
                                            {sending
                                                ? <div className="spinner" style={{ width: '16px', height: '16px' }}></div>
                                                : <i className="fas fa-paper-plane"></i>
                                            }
                                        </button>
                                    </div>
                                    {isAdmin && (
                                        <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '6px', paddingLeft: '12px' }}>
                                            Press Enter to send · Shift+Enter for new line
                                        </div>
                                    )}
                                </div>
                            </>
                        ) : (
                            <div style={{
                                flex: 1, display: 'flex', alignItems: 'center',
                                justifyContent: 'center', flexDirection: 'column', color: '#6b7280',
                            }}>
                                <i className="fas fa-comments" style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.4 }}></i>
                                {isAdmin ? (
                                    <div style={{ textAlign: 'center' }}>
                                        <p style={{ fontWeight: '500', marginBottom: '4px' }}>Select a patient to start chatting</p>
                                        <small>Conversations needing a response are highlighted</small>
                                    </div>
                                ) : (
                                    <div style={{ textAlign: 'center' }}>
                                        <p>Connecting to medical support...</p>
                                        <small>Our team is here to help you</small>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

// ── Support Chat Button ───────────────────────────────────────────────────────
const SupportChatButton = ({ isAdmin, currentUser, patients = [] }) => {
    const [showChat, setShowChat] = useState(false);
    const [selectedPatient, setSelectedPatient] = useState(null);

    return (
        <>
            {isAdmin ? (
                <div style={{ position: 'relative' }}>
                    <button className="btn btn-primary" onClick={() => setShowChat(true)} style={{ marginRight: '8px' }}>
                        <i className="fas fa-headset" style={{ marginRight: '8px' }}></i>
                        Support Center
                    </button>
                    <div style={{ marginTop: '8px' }}>
                        {patients.slice(0, 3).map(patient => (
                            <button
                                key={patient.id}
                                className="btn btn-sm btn-secondary"
                                onClick={() => { setSelectedPatient(patient); setShowChat(true); }}
                                style={{ marginRight: '4px', marginBottom: '4px' }}
                            >
                                <i className="fas fa-comment" style={{ marginRight: '4px' }}></i>
                                {patient.firstName}
                            </button>
                        ))}
                    </div>
                </div>
            ) : (
                <button
                    className="btn btn-primary"
                    onClick={() => setShowChat(true)}
                    style={{ position: 'fixed', bottom: '20px', right: '20px', borderRadius: '50px', padding: '12px 20px' }}
                >
                    <i className="fas fa-life-ring" style={{ marginRight: '8px' }}></i>
                    Get Help
                </button>
            )}

            {showChat && (
                <ChatSupportModal
                    onClose={() => { setShowChat(false); setSelectedPatient(null); }}
                    isAdmin={isAdmin}
                    currentUser={currentUser}
                    selectedPatient={selectedPatient}
                />
            )}
        </>
    );
};

window.ChatSupportModal = ChatSupportModal;
window.SupportChatButton = SupportChatButton;