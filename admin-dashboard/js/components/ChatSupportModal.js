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
    const messagesEndRef = useRef(null);
    const messageInputRef = useRef(null);
    const activeConversationRef = useRef(null);
    const lastMessageCountRef = useRef(0);

    const getHeaders = () => ({
        'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
        'Content-Type': 'application/json',
    });

    // Keep ref in sync with state
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

    // ── Polling — single stable interval ─────────────────────────────────────
    useEffect(() => {
        const interval = setInterval(() => {
            const conv = activeConversationRef.current;
            if (isAdmin) {
                loadActiveChats(true);
                if (conv?.userId) loadAdminChatForUser(conv.userId, true);
            } else {
                if (conv) loadPatientChatHistory(true);
            }
        }, 4000);
        return () => clearInterval(interval);
    }, [isAdmin]);

    // ── Support status ────────────────────────────────────────────────────────
    const loadSupportStatus = async () => {
        try {
            // Use patient API for status (publicly available)
            const response = await fetch(`${CONFIG.API_BASE_URL}/api/support/status`, {
                headers: getHeaders(),
            });
            if (!response.ok) return;
            const data = await response.json();
            if (data.success) setSupportStatus(data);
        } catch (e) {
            console.error('Error loading support status:', e);
        }
    };

    // ── Admin: load sidebar conversation list ─────────────────────────────────
    // Reads from ADMIN server — lists all patients who sent messages
    const loadActiveChats = async (silent = false) => {
        if (!isAdmin) return;
        try {
            if (!silent) setLoading(true);
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('loadActiveChats failed:', response.status);
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
                    unreadCount: chat.status === 'NEEDS_RESPONSE' ? 1 : 0,
                    status: chat.status || 'active',
                    priority: chat.priority,
                    ticketNumber: chat.ticketNumber,
                    ticketId: chat.ticketId,
                }));
                setConversations(mapped);
                setUnreadCount(mapped.filter(c => c.unreadCount > 0).length);
            }
        } catch (e) {
            console.error('Error loading active chats:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Admin: open a specific patient's chat ─────────────────────────────────
    const startConversationWithPatient = (patient) => {
        const conv = {
            id: `user_${patient.id}`,
            userId: patient.id,
            patientName: `${patient.firstName} ${patient.lastName}`,
            patientEmail: patient.email,
            lastMessage: '',
            lastMessageTime: new Date(),
            unreadCount: 0,
            status: 'active',
        };
        setActiveConversation(conv);
        activeConversationRef.current = conv;
        loadAdminChatForUser(patient.id, false);
    };

    // ── Admin: fetch messages for a user ──────────────────────────────────────
    // KEY FIX: reads from ADMIN server which proxies/stores patient messages
    const loadAdminChatForUser = async (userId, silent = false) => {
        if (!userId) return;
        try {
            if (!silent) setLoading(true);
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/support/admin/chat/${userId}`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('loadAdminChatForUser failed:', response.status, 'userId:', userId);
                return;
            }
            const data = await response.json();
            if (data.success) {
                const formatted = (data.messages || []).map(msg => ({
                    id: msg.id,
                    senderId: isAgentMessage(msg.senderType) ? 'admin' : userId,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp || msg.createdAt),
                    status: msg.isRead ? 'read' : 'delivered',
                }));
                // Only update if message count changed (avoids flicker)
                if (formatted.length !== lastMessageCountRef.current) {
                    lastMessageCountRef.current = formatted.length;
                    setMessages(formatted);
                }
                // Update patient details if provided
                if (data.user) {
                    setActiveConversation(prev => prev ? ({
                        ...prev,
                        patientName: data.user.name ||
                            `${data.user.firstName || ''} ${data.user.lastName || ''}`.trim(),
                        patientEmail: data.user.email,
                        patientPhone: data.user.phone,
                    }) : prev);
                }
            }
        } catch (e) {
            console.error('Error in loadAdminChatForUser:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Patient: initialise own conversation ──────────────────────────────────
    const loadPatientConversation = async () => {
        try {
            const conv = {
                id: 'patient_chat',
                userId: currentUser?.id,
                patientName: `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
                patientEmail: currentUser?.email,
                adminName: 'Medical Support Team',
                status: 'active',
            };
            setActiveConversation(conv);
            activeConversationRef.current = conv;
            loadPatientChatHistory(false);
        } catch (e) {
            console.error('Error loading patient conversation:', e);
        }
    };

    // ── Patient: fetch own messages from patient server ───────────────────────
    const loadPatientChatHistory = async (silent = false) => {
        try {
            if (!silent) setLoading(true);
            const response = await fetch(
                `${CONFIG.API_BASE_URL}/api/support/chat/history`,
                { headers: getHeaders() }
            );
            if (!response.ok) {
                console.error('loadPatientChatHistory failed:', response.status);
                return;
            }
            const data = await response.json();
            if (data.success) {
                const formatted = (data.messages || []).map(msg => ({
                    id: msg.id,
                    senderId: isAgentMessage(msg.senderType) ? 'admin' : currentUser?.id,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp || msg.createdAt),
                    status: msg.isRead ? 'read' : 'delivered',
                }));
                if (formatted.length !== lastMessageCountRef.current) {
                    lastMessageCountRef.current = formatted.length;
                    setMessages(formatted);
                }
            }
        } catch (e) {
            console.error('Error loading patient chat history:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Helper: identify agent/admin message types ────────────────────────────
    const isAgentMessage = (senderType) => {
        const t = (senderType || '').toLowerCase();
        return t === 'support_agent' || t === 'admin' || t === 'support_agent';
    };

    // ── Send message ──────────────────────────────────────────────────────────
    const sendMessage = async () => {
        if (!newMessage.trim() || sending) return;

        const messageText = newMessage.trim();
        setNewMessage('');
        setSending(true);

        // Optimistic UI
        const tempMessage = {
            id: `temp_${Date.now()}`,
            senderId: currentUser?.id,
            senderName: isAdmin
                ? (currentUser?.name || 'Medical Support')
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
                // ── Admin reply: POST to ADMIN server ────────────────────────
                const conv = activeConversationRef.current;
                if (!conv?.userId) throw new Error('No patient selected');

                response = await fetch(
                    `${CONFIG.ADMIN_API_URL}/api/support/admin/reply`,
                    {
                        method: 'POST',
                        headers: getHeaders(),
                        body: JSON.stringify({
                            userId: conv.userId,
                            message: messageText,
                            ticketId: conv.ticketId || null,
                            senderType: 'SUPPORT_AGENT',
                        }),
                    }
                );
            } else {
                // ── Patient message: POST to patient server ───────────────────
                response = await fetch(
                    `${CONFIG.API_BASE_URL}/api/support/chat/message`,
                    {
                        method: 'POST',
                        headers: getHeaders(),
                        body: JSON.stringify({
                            message: messageText,
                            senderType: 'USER',
                        }),
                    }
                );
            }

            if (response.status === 401) throw new Error('Session expired. Please login again.');
            if (!response.ok) {
                const err = await response.json().catch(() => ({ message: response.statusText }));
                throw new Error(err.message || `Server error ${response.status}`);
            }

            const data = await response.json();

            if (data.success) {
                // Confirm temp message
                setMessages(prev => prev.map(msg =>
                    msg.id === tempMessage.id
                        ? { ...msg, status: 'delivered', id: data.messageId || msg.id }
                        : msg
                ));

                // Bot response for patient (only in non-human-agent mode)
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
                    }, 800);
                }

                // Immediately re-fetch to confirm delivery
                const conv = activeConversationRef.current;
                setTimeout(() => {
                    if (isAdmin && conv?.userId) {
                        loadAdminChatForUser(conv.userId, true);
                    } else if (!isAdmin) {
                        loadPatientChatHistory(true);
                    }
                }, 500);

            } else {
                throw new Error(data.message || 'Failed to send message');
            }

        } catch (e) {
            console.error('sendMessage error:', e);
            // Remove failed temp message
            setMessages(prev => prev.filter(msg => msg.id !== tempMessage.id));
            alert('Failed to send: ' + e.message);
        } finally {
            setSending(false);
            messageInputRef.current?.focus();
        }
    };

    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    // ── Helpers ───────────────────────────────────────────────────────────────
    const formatMessageTime = (timestamp) => {
        const now = new Date();
        const t = new Date(timestamp);
        const diffH = (now - t) / (1000 * 60 * 60);
        if (diffH < 1) return 'Just now';
        if (diffH < 24) return t.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        return t.toLocaleDateString([], { month: 'short', day: 'numeric' });
    };

    const isMyMessage = (message) => {
        const t = (message.senderType || '').toLowerCase();
        if (isAdmin) return t === 'support_agent' || t === 'admin';
        return t === 'user';
    };

    const getBubbleColor = (message) => {
        const t = (message.senderType || '').toLowerCase();
        if (t === 'bot') return '#10b981';
        return '#3b82f6';
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
                maxWidth: isAdmin ? '1100px' : '680px',
                height: '85vh',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                padding: 0,
            }}>

                {/* ── Header ── */}
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    padding: '14px 20px',
                    backgroundColor: 'white',
                    borderBottom: '1px solid #e5e7eb',
                    flexShrink: 0,
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <h2 style={{ margin: 0, fontSize: '18px', fontWeight: '700' }}>
                            {isAdmin ? '💬 Patient Support Chat' : '🏥 Medical Support'}
                        </h2>
                        {isAdmin && unreadCount > 0 && (
                            <span style={{
                                backgroundColor: '#ef4444', color: 'white',
                                borderRadius: '12px', padding: '2px 10px', fontSize: '12px', fontWeight: '700',
                            }}>
                                {unreadCount} need response
                            </span>
                        )}
                        {!isAdmin && supportStatus && (
                            <span style={{ fontSize: '12px', color: supportStatus.isOnline ? '#10b981' : '#f59e0b' }}>
                                ● {supportStatus.isOnline ? `Online · ${supportStatus.estimatedResponseTime || 'Fast replies'}` : 'Offline'}
                            </span>
                        )}
                    </div>
                    <button
                        onClick={onClose}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '20px', color: '#6b7280', padding: '4px 8px' }}
                    >
                        ✕
                    </button>
                </div>

                {/* ── Body ── */}
                <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

                    {/* ── Admin sidebar: conversation list ── */}
                    {isAdmin && (
                        <div style={{
                            width: '280px', flexShrink: 0,
                            borderRight: '1px solid #e5e7eb',
                            display: 'flex', flexDirection: 'column',
                            backgroundColor: '#f9fafb',
                        }}>
                            <div style={{ padding: '12px' }}>
                                <input
                                    type="text"
                                    placeholder="Search patients..."
                                    value={searchQuery}
                                    onChange={e => setSearchQuery(e.target.value)}
                                    style={{
                                        width: '100%', padding: '8px 12px',
                                        border: '1px solid #d1d5db', borderRadius: '6px',
                                        fontSize: '13px', boxSizing: 'border-box',
                                    }}
                                />
                            </div>

                            <div style={{ flex: 1, overflowY: 'auto' }}>
                                {loading && conversations.length === 0 ? (
                                    <div style={{ padding: '20px', textAlign: 'center' }}>
                                        <div className="spinner"></div>
                                    </div>
                                ) : filteredConversations.length === 0 ? (
                                    <div style={{ padding: '20px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                                        No active conversations
                                    </div>
                                ) : filteredConversations.map(conv => (
                                    <div
                                        key={conv.id}
                                        onClick={() => {
                                            lastMessageCountRef.current = 0;
                                            setActiveConversation(conv);
                                            activeConversationRef.current = conv;
                                            loadAdminChatForUser(conv.userId, false);
                                        }}
                                        style={{
                                            padding: '11px 14px', cursor: 'pointer',
                                            borderBottom: '1px solid #e5e7eb',
                                            backgroundColor: activeConversation?.id === conv.id ? '#dbeafe' : 'transparent',
                                        }}
                                        onMouseEnter={e => { if (activeConversation?.id !== conv.id) e.currentTarget.style.backgroundColor = '#f3f4f6'; }}
                                        onMouseLeave={e => { if (activeConversation?.id !== conv.id) e.currentTarget.style.backgroundColor = 'transparent'; }}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ fontWeight: conv.unreadCount > 0 ? '700' : '600', fontSize: '13px', marginBottom: '2px' }}>
                                                    {conv.patientName}
                                                </div>
                                                {conv.ticketNumber && (
                                                    <div style={{ fontSize: '10px', color: '#3b82f6', fontWeight: '700', marginBottom: '2px' }}>
                                                        #{conv.ticketNumber}
                                                    </div>
                                                )}
                                                <div style={{ fontSize: '11px', color: '#6b7280', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                    {conv.lastMessage || 'New conversation'}
                                                </div>
                                                <div style={{ fontSize: '10px', color: '#9ca3af', marginTop: '2px' }}>
                                                    {formatMessageTime(conv.lastMessageTime)}
                                                </div>
                                            </div>
                                            <div style={{ marginLeft: '6px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '3px' }}>
                                                {conv.unreadCount > 0 && (
                                                    <div style={{
                                                        backgroundColor: '#ef4444', color: 'white',
                                                        borderRadius: '10px', width: '16px', height: '16px',
                                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                        fontSize: '9px', fontWeight: '700',
                                                    }}>!</div>
                                                )}
                                                <div style={{
                                                    width: '7px', height: '7px', borderRadius: '50%',
                                                    backgroundColor:
                                                        conv.status === 'active' ? '#10b981' :
                                                        conv.status === 'NEEDS_RESPONSE' ? '#ef4444' :
                                                        conv.status === 'resolved' ? '#6b7280' : '#3b82f6',
                                                }} />
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* ── Chat area ── */}
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 }}>
                        {activeConversation ? (
                            <>
                                {/* Chat sub-header */}
                                <div style={{
                                    padding: '10px 16px', borderBottom: '1px solid #e5e7eb',
                                    backgroundColor: 'white', flexShrink: 0,
                                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                }}>
                                    <div>
                                        <div style={{ fontWeight: '600', fontSize: '14px' }}>
                                            {isAdmin ? activeConversation.patientName : 'Medical Support Team'}
                                        </div>
                                        <div style={{ fontSize: '11px', color: '#6b7280' }}>
                                            {isAdmin
                                                ? `${activeConversation.patientEmail || ''}${activeConversation.patientPhone ? ' · ' + activeConversation.patientPhone : ''}`
                                                : 'Qualitest Medical · info@qualitestmedical.com'}
                                        </div>
                                    </div>
                                    {isAdmin && (
                                        <button
                                            onClick={() => {
                                                lastMessageCountRef.current = 0;
                                                loadAdminChatForUser(activeConversation.userId, false);
                                            }}
                                            title="Refresh messages"
                                            style={{ padding: '5px 10px', fontSize: '12px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer' }}
                                        >
                                            🔄 Refresh
                                        </button>
                                    )}
                                </div>

                                {/* Messages area */}
                                <div style={{ flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' }}>
                                    {messages.length === 0 && (
                                        <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '13px' }}>
                                            {isAdmin ? 'No messages yet from this patient.' : 'No messages yet. Start the conversation!'}
                                        </div>
                                    )}
                                    {messages.map(message => {
                                        const mine = isMyMessage(message);
                                        return (
                                            <div key={message.id} style={{
                                                display: 'flex',
                                                justifyContent: mine ? 'flex-end' : 'flex-start',
                                                marginBottom: '10px',
                                            }}>
                                                <div style={{
                                                    maxWidth: '75%', padding: '10px 14px',
                                                    borderRadius: '16px',
                                                    backgroundColor: mine ? getBubbleColor(message) : 'white',
                                                    color: mine ? 'white' : '#1f2937',
                                                    boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
                                                    borderBottomRightRadius: mine ? '4px' : '16px',
                                                    borderBottomLeftRadius: mine ? '16px' : '4px',
                                                }}>
                                                    {!mine && message.senderName && (
                                                        <div style={{ fontSize: '10px', fontWeight: '700', marginBottom: '3px', opacity: 0.7 }}>
                                                            {message.senderName}
                                                        </div>
                                                    )}
                                                    <div style={{ fontSize: '13px', lineHeight: '1.45', whiteSpace: 'pre-wrap' }}>
                                                        {message.message}
                                                    </div>
                                                    <div style={{ fontSize: '10px', opacity: 0.6, marginTop: '4px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '3px' }}>
                                                        {formatMessageTime(message.timestamp)}
                                                        {mine && (
                                                            <span>{
                                                                message.status === 'sending' ? '🕐' :
                                                                message.status === 'read'     ? '✓✓' : '✓'
                                                            }</span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                    <div ref={messagesEndRef} />
                                </div>

                                {/* Input bar */}
                                <div style={{ padding: '12px 16px', borderTop: '1px solid #e5e7eb', backgroundColor: 'white', flexShrink: 0 }}>
                                    <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-end' }}>
                                        <textarea
                                            ref={messageInputRef}
                                            value={newMessage}
                                            onChange={e => setNewMessage(e.target.value)}
                                            onKeyPress={handleKeyPress}
                                            placeholder={isAdmin
                                                ? 'Type your reply to the patient... (Enter to send)'
                                                : 'Type your message... (Enter to send)'}
                                            rows={1}
                                            style={{
                                                flex: 1, padding: '10px 14px',
                                                border: '1px solid #d1d5db',
                                                borderRadius: '20px', resize: 'none',
                                                fontSize: '13px', minHeight: '42px', maxHeight: '110px',
                                                fontFamily: 'inherit', outline: 'none',
                                                boxSizing: 'border-box',
                                            }}
                                        />
                                        <button
                                            onClick={sendMessage}
                                            disabled={!newMessage.trim() || sending}
                                            style={{
                                                padding: '0 16px',
                                                height: '42px',
                                                backgroundColor: newMessage.trim() && !sending ? '#3b82f6' : '#d1d5db',
                                                color: 'white', border: 'none', borderRadius: '20px',
                                                cursor: newMessage.trim() && !sending ? 'pointer' : 'not-allowed',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                gap: '6px', fontSize: '13px', fontWeight: '600', whiteSpace: 'nowrap',
                                                transition: 'background 0.15s',
                                            }}
                                        >
                                            {sending ? (
                                                <div className="spinner" style={{ width: '14px', height: '14px' }}></div>
                                            ) : (
                                                <>Send <i className="fas fa-paper-plane" style={{ fontSize: '12px' }}></i></>
                                            )}
                                        </button>
                                    </div>
                                </div>
                            </>
                        ) : (
                            /* No conversation selected */
                            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', color: '#9ca3af' }}>
                                <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.3 }}>💬</div>
                                {isAdmin ? (
                                    <div style={{ textAlign: 'center' }}>
                                        <p style={{ fontWeight: '600', color: '#374151', marginBottom: '4px' }}>Select a patient to start chatting</p>
                                        <small>Patients who sent messages appear in the sidebar</small>
                                    </div>
                                ) : (
                                    <div style={{ textAlign: 'center' }}>
                                        <p style={{ color: '#374151' }}>Connecting to medical support...</p>
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
                <button className="btn btn-primary" onClick={() => setShowChat(true)} style={{ marginRight: '8px' }}>
                    <i className="fas fa-headset" style={{ marginRight: '8px' }}></i>
                    Support Center
                </button>
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