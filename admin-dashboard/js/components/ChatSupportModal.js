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

    // Auto-scroll to bottom when new messages arrive
    const scrollToBottom = () => {
        setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
        }, 100);
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    // Load conversations and support status on mount
    useEffect(() => {
        loadSupportStatus();
        
        if (isAdmin) {
            loadActiveChats();
        } else {
            loadPatientConversation();
        }
        
        if (selectedPatient && isAdmin) {
            startConversationWithPatient(selectedPatient);
        }
    }, [selectedPatient, isAdmin]);

    // Poll for new messages every 5 seconds
    useEffect(() => {
        if (activeConversation) {
            const interval = setInterval(() => {
                if (isAdmin) {
                    loadChatByUserId(activeConversation.userId);
                } else {
                    loadChatHistory();
                }
            }, 5000);
            return () => clearInterval(interval);
        }
    }, [activeConversation]);

    const loadSupportStatus = async () => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/support/status`, {
                headers: { 'Authorization': `Bearer ${localStorage.getItem('authToken')}` }
            });
            const data = await response.json();
            
            if (data.success) {
                setSupportStatus(data);
            }
        } catch (error) {
            console.error('Error loading support status:', error);
        }
    };

    const loadActiveChats = async () => {
    if (!isAdmin) return;
    
    try {
        setLoading(true);
        
        // Replace this mock data with your actual API call
        const response = await fetch('http://localhost:8080/api/support/admin/active-chats', {
            headers: { 
                'Authorization': `Bearer ${localStorage.getItem('authToken')}` 
            }
        });
        
        if (!response.ok) throw new Error('Failed to load chats');
        
        const data = await response.json();
        
        if (data.success) {
            setConversations(data.activeChats.map(chat => ({
                id: chat.ticketId || chat.userId,
                userId: chat.userId,
                patientName: chat.userName,
                patientEmail: chat.userEmail,
                lastMessage: chat.subject || 'New conversation',
                lastMessageTime: new Date(chat.createdAt),
                unreadCount: chat.status === 'NEEDS_RESPONSE' ? 1 : 0,
                status: chat.status || 'active',
                priority: chat.priority,
                ticketNumber: chat.ticketNumber
            })));
            
            const totalUnread = data.activeChats.filter(chat => 
                chat.status === 'NEEDS_RESPONSE'
            ).length;
            setUnreadCount(totalUnread);
        }
    } catch (error) {
        console.error('Error loading conversations:', error);
        // Fallback to empty list instead of mock data
        setConversations([]);
    } finally {
        setLoading(false);
    }
};

    const startConversationWithPatient = (patient) => {
        const conversation = {
            id: `user_${patient.id}`,
            userId: patient.id,
            patientName: `${patient.firstName} ${patient.lastName}`,
            patientEmail: patient.email,
            lastMessage: '',
            lastMessageTime: new Date(),
            unreadCount: 0,
            status: 'new'
        };
        
        setActiveConversation(conversation);
        loadChatByUserId(patient.id);
    };

    const loadPatientConversation = async () => {
        try {
            const conversation = {
                id: 'patient_chat',
                userId: currentUser.id,
                patientName: `${currentUser.firstName} ${currentUser.lastName}`,
                patientEmail: currentUser.email,
                adminName: 'Medical Support Team',
                status: 'active'
            };
            setActiveConversation(conversation);
            loadChatHistory();
        } catch (error) {
            console.error('Error loading patient conversation:', error);
        }
    };

    const loadChatHistory = async () => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/support/chat/history`, {
                headers: { 'Authorization': `Bearer ${localStorage.getItem('authToken')}` }
            });
            const data = await response.json();
            
            if (data.success) {
                const formattedMessages = data.messages.map(msg => ({
                    id: msg.id,
                    senderId: msg.senderType === 'admin' || msg.senderType === 'support_agent' ? 'admin' : currentUser.id,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp),
                    status: msg.isRead ? 'read' : 'delivered'
                }));
                setMessages(formattedMessages);
            }
        } catch (error) {
            console.error('Error loading chat history:', error);
        }
    };

    const loadChatByUserId = async (userId) => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/support/admin/chat/${userId}`, {
                headers: { 'Authorization': `Bearer ${localStorage.getItem('authToken')}` }
            });
            const data = await response.json();
            
            if (data.success) {
                const formattedMessages = data.messages.map(msg => ({
                    id: msg.id,
                    senderId: msg.senderType === 'admin' || msg.senderType === 'support_agent' ? currentUser.id : userId,
                    senderName: msg.senderName,
                    senderType: msg.senderType,
                    message: msg.message,
                    timestamp: new Date(msg.timestamp),
                    status: msg.isRead ? 'read' : 'delivered'
                }));
                setMessages(formattedMessages);
                
                // Update active conversation with user info
                if (data.user) {
                    setActiveConversation(prev => ({
                        ...prev,
                        patientName: data.user.name,
                        patientEmail: data.user.email,
                        patientPhone: data.user.phone
                    }));
                }
            }
        } catch (error) {
            console.error('Error loading user chat:', error);
        }
    };

    const sendMessage = async () => {
        if (!newMessage.trim() || sending) return;

        setSending(true);
        const messageText = newMessage.trim();
        setNewMessage('');

        try {
            // Optimistic update
            const tempMessage = {
                id: `temp_${Date.now()}`,
                senderId: currentUser.id,
                senderName: isAdmin ? 'Medical Support' : `${currentUser.firstName} ${currentUser.lastName}`,
                senderType: isAdmin ? 'support_agent' : 'user',
                message: messageText,
                timestamp: new Date(),
                status: 'sending'
            };

            setMessages(prev => [...prev, tempMessage]);

            let response;
            if (isAdmin) {
                // Send agent reply
                response = await fetch(`${CONFIG.API_BASE_URL}/support/admin/reply`, {
                    method: 'POST',
                    headers: { 
                        'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        userId: activeConversation.userId,
                        message: messageText,
                        ticketId: activeConversation.ticketId || null
                    })
                });
            } else {
                // Send patient message
                response = await fetch(`${CONFIG.API_BASE_URL}/support/chat/message`, {
                    method: 'POST',
                    headers: { 
                        'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ message: messageText })
                });
            }

            const data = await response.json();

            if (data.success) {
                // Update message status
                setMessages(prev => prev.map(msg => 
                    msg.id === tempMessage.id 
                        ? {...msg, status: 'delivered', id: data.messageId}
                        : msg
                ));

                // Add bot response if available (for patient messages)
                if (!isAdmin && data.botResponse) {
                    setTimeout(() => {
                        const botMessage = {
                            id: `bot_${Date.now()}`,
                            senderId: 'bot',
                            senderName: 'Medical Support Bot',
                            senderType: 'bot',
                            message: data.botResponse,
                            timestamp: new Date(),
                            status: 'delivered'
                        };
                        setMessages(prev => [...prev, botMessage]);
                    }, 1000);
                }

                // Update conversation last message
                if (activeConversation) {
                    setConversations(prev => prev.map(conv =>
                        conv.id === activeConversation.id
                            ? {...conv, lastMessage: messageText, lastMessageTime: new Date()}
                            : conv
                    ));
                }
            } else {
                throw new Error(data.message || 'Failed to send message');
            }

        } catch (error) {
            console.error('Error sending message:', error);
            // Remove failed message
            setMessages(prev => prev.filter(msg => msg.id !== tempMessage.id));
            alert('Failed to send message. Please try again.');
        } finally {
            setSending(false);
        }
    };

    const createSupportTicket = async (ticketData) => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/support/ticket`, {
                method: 'POST',
                headers: { 
                    'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(ticketData)
            });
            const data = await response.json();
            
            if (data.success) {
                setTicketNumber(data.ticketNumber);
                return data;
            } else {
                throw new Error(data.message);
            }
        } catch (error) {
            console.error('Error creating ticket:', error);
            throw error;
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

        if (diffInHours < 1) {
            return 'Just now';
        } else if (diffInHours < 24) {
            return messageTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } else {
            return messageTime.toLocaleDateString([], { month: 'short', day: 'numeric' });
        }
    };

    const getSenderTypeColor = (senderType) => {
        switch (senderType) {
            case 'support_agent':
            case 'admin':
                return '#3b82f6'; // Blue
            case 'bot':
                return '#10b981'; // Green
            case 'system':
                return '#6b7280'; // Gray
            default:
                return '#3b82f6'; // Default blue
        }
    };

    const filteredConversations = conversations.filter(conv => 
        conv.patientName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        conv.patientEmail.toLowerCase().includes(searchQuery.toLowerCase()) ||
        conv.lastMessage.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <div className="modal" style={{ zIndex: 1000 }}>
            <div className="modal-content" style={{ 
                maxWidth: isAdmin ? '1200px' : '700px', 
                height: '85vh', 
                display: 'flex', 
                flexDirection: isAdmin ? 'row' : 'column',
                overflow: 'hidden'
            }}>
                {/* Header */}
                <div className="card-header" style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'center',
                    position: isAdmin ? 'absolute' : 'relative',
                    top: 0,
                    left: 0,
                    right: 0,
                    zIndex: 10,
                    backgroundColor: 'white',
                    borderBottom: '1px solid #e5e7eb'
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
                                        Online - Response time: {supportStatus.estimatedResponseTime}
                                    </span>
                                ) : (
                                    <span style={{ color: '#f59e0b' }}>
                                        <i className="fas fa-circle" style={{ fontSize: '8px', marginRight: '4px' }}></i>
                                        Offline - {supportStatus.supportHours}
                                    </span>
                                )}
                            </div>
                        )}
                        {isAdmin && unreadCount > 0 && (
                            <span style={{
                                backgroundColor: '#ef4444',
                                color: 'white',
                                borderRadius: '12px',
                                padding: '2px 8px',
                                fontSize: '12px',
                                marginLeft: '8px'
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
                    display: 'flex', 
                    flex: 1, 
                    marginTop: isAdmin ? '70px' : '0',
                    overflow: 'hidden'
                }}>
                    {/* Conversations Sidebar (Admin only) */}
                    {isAdmin && (
                        <div style={{
                            width: '350px',
                            borderRight: '1px solid #e5e7eb',
                            display: 'flex',
                            flexDirection: 'column',
                            backgroundColor: '#f9fafb'
                        }}>
                            {/* Search */}
                            <div style={{ padding: '16px' }}>
                                <input
                                    type="text"
                                    placeholder="Search patients..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    style={{
                                        width: '100%',
                                        padding: '8px 12px',
                                        border: '1px solid #d1d5db',
                                        borderRadius: '6px',
                                        fontSize: '14px'
                                    }}
                                />
                            </div>

                            {/* Conversation List */}
                            <div style={{ flex: 1, overflowY: 'auto' }}>
                                {loading ? (
                                    <div style={{ padding: '20px', textAlign: 'center' }}>
                                        <div className="spinner"></div>
                                    </div>
                                ) : filteredConversations.length === 0 ? (
                                    <div style={{ padding: '20px', textAlign: 'center', color: '#6b7280' }}>
                                        No active conversations
                                    </div>
                                ) : (
                                    filteredConversations.map(conversation => (
                                        <div
                                            key={conversation.id}
                                            onClick={() => {
                                                setActiveConversation(conversation);
                                                loadChatByUserId(conversation.userId);
                                            }}
                                            style={{
                                                padding: '12px 16px',
                                                cursor: 'pointer',
                                                borderBottom: '1px solid #e5e7eb',
                                                backgroundColor: activeConversation?.id === conversation.id ? '#dbeafe' : 'transparent',
                                                ':hover': { backgroundColor: '#f3f4f6' }
                                            }}
                                            onMouseEnter={(e) => {
                                                if (activeConversation?.id !== conversation.id) {
                                                    e.target.style.backgroundColor = '#f3f4f6';
                                                }
                                            }}
                                            onMouseLeave={(e) => {
                                                if (activeConversation?.id !== conversation.id) {
                                                    e.target.style.backgroundColor = 'transparent';
                                                }
                                            }}
                                        >
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                <div style={{ flex: 1 }}>
                                                    <div style={{ 
                                                        fontWeight: conversation.unreadCount > 0 ? '600' : '500',
                                                        fontSize: '14px',
                                                        marginBottom: '4px'
                                                    }}>
                                                        {conversation.patientName}
                                                    </div>
                                                    {conversation.ticketNumber && (
                                                        <div style={{ 
                                                            fontSize: '11px', 
                                                            color: '#3b82f6',
                                                            marginBottom: '2px',
                                                            fontWeight: '500'
                                                        }}>
                                                            #{conversation.ticketNumber}
                                                        </div>
                                                    )}
                                                    <div style={{ 
                                                        fontSize: '12px', 
                                                        color: '#6b7280',
                                                        marginBottom: '4px',
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis',
                                                        whiteSpace: 'nowrap'
                                                    }}>
                                                        {conversation.lastMessage || 'New conversation'}
                                                    </div>
                                                    <div style={{ fontSize: '11px', color: '#9ca3af' }}>
                                                        {formatMessageTime(conversation.lastMessageTime)}
                                                    </div>
                                                </div>
                                                <div style={{ marginLeft: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                                                    {conversation.priority && conversation.priority !== 'NORMAL' && (
                                                        <div style={{
                                                            fontSize: '10px',
                                                            color: conversation.priority === 'HIGH' ? '#ef4444' : '#f59e0b',
                                                            marginBottom: '4px',
                                                            fontWeight: '600'
                                                        }}>
                                                            {conversation.priority}
                                                        </div>
                                                    )}
                                                    {conversation.unreadCount > 0 && (
                                                        <div style={{
                                                            backgroundColor: '#ef4444',
                                                            color: 'white',
                                                            borderRadius: '10px',
                                                            width: '18px',
                                                            height: '18px',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            justifyContent: 'center',
                                                            fontSize: '10px',
                                                            marginBottom: '4px'
                                                        }}>
                                                            !
                                                        </div>
                                                    )}
                                                    <div style={{
                                                        width: '8px',
                                                        height: '8px',
                                                        borderRadius: '50%',
                                                        backgroundColor: 
                                                            conversation.status === 'active' ? '#10b981' :
                                                            conversation.status === 'NEEDS_RESPONSE' ? '#ef4444' :
                                                            conversation.status === 'waiting' ? '#f59e0b' :
                                                            conversation.status === 'resolved' ? '#6b7280' : '#3b82f6'
                                                    }}></div>
                                                </div>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    )}

                    {/* Chat Area */}
                    <div style={{
                        flex: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        overflow: 'hidden'
                    }}>
                        {activeConversation ? (
                            <>
                                {/* Chat Header */}
                                <div style={{
                                    padding: '12px 16px',
                                    borderBottom: '1px solid #e5e7eb',
                                    backgroundColor: 'white',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between'
                                }}>
                                    <div>
                                        <div style={{ fontWeight: '500', fontSize: '16px' }}>
                                            {isAdmin ? activeConversation.patientName : 'Medical Support Team'}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#6b7280' }}>
                                            {isAdmin ? (
                                                <>
                                                    {activeConversation.patientEmail}
                                                    {activeConversation.patientPhone && ` • ${activeConversation.patientPhone}`}
                                                </>
                                            ) : (
                                                'Qualitest Medical Support'
                                            )}
                                        </div>
                                        {ticketNumber && (
                                            <div style={{ fontSize: '11px', color: '#3b82f6', fontWeight: '500' }}>
                                                Ticket #{ticketNumber}
                                            </div>
                                        )}
                                    </div>
                                    <div style={{
                                        width: '8px',
                                        height: '8px',
                                        borderRadius: '50%',
                                        backgroundColor: '#10b981'
                                    }}></div>
                                </div>

                                {/* Messages */}
                                <div style={{
                                    flex: 1,
                                    overflowY: 'auto',
                                    padding: '16px',
                                    backgroundColor: '#f9fafb'
                                }}>
                                    {messages.map(message => {
                                        const isMyMessage = isAdmin ? 
                                            (message.senderType === 'support_agent' || message.senderType === 'admin') :
                                            message.senderType === 'user';
                                        
                                        return (
                                            <div
                                                key={message.id}
                                                style={{
                                                    display: 'flex',
                                                    justifyContent: isMyMessage ? 'flex-end' : 'flex-start',
                                                    marginBottom: '12px'
                                                }}
                                            >
                                                <div style={{
                                                    maxWidth: '75%',
                                                    padding: '12px 16px',
                                                    borderRadius: '18px',
                                                    backgroundColor: isMyMessage ? getSenderTypeColor(message.senderType) : 'white',
                                                    color: isMyMessage ? 'white' : '#1f2937',
                                                    boxShadow: '0 1px 2px rgba(0, 0, 0, 0.1)',
                                                    position: 'relative'
                                                }}>
                                                    {!isMyMessage && message.senderName && (
                                                        <div style={{
                                                            fontSize: '11px',
                                                            fontWeight: '500',
                                                            marginBottom: '4px',
                                                            opacity: 0.8
                                                        }}>
                                                            {message.senderName}
                                                        </div>
                                                    )}
                                                    <div style={{ marginBottom: '4px', whiteSpace: 'pre-wrap' }}>
                                                        {message.message}
                                                    </div>
                                                    <div style={{
                                                        fontSize: '11px',
                                                        opacity: 0.7,
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'flex-end'
                                                    }}>
                                                        {formatMessageTime(message.timestamp)}
                                                        {isMyMessage && (
                                                            <i className={`fas ${
                                                                message.status === 'sending' ? 'fa-clock' :
                                                                message.status === 'delivered' ? 'fa-check' : 'fa-check-double'
                                                            }`} style={{ marginLeft: '4px', fontSize: '10px' }}></i>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                    <div ref={messagesEndRef} />
                                </div>

                                {/* Message Input */}
                                <div style={{
                                    padding: '16px',
                                    borderTop: '1px solid #e5e7eb',
                                    backgroundColor: 'white'
                                }}>
                                    <div style={{ display: 'flex', gap: '8px' }}>
                                        <textarea
                                            ref={messageInputRef}
                                            value={newMessage}
                                            onChange={(e) => setNewMessage(e.target.value)}
                                            onKeyPress={handleKeyPress}
                                            placeholder={isAdmin ? "Type your reply..." : "Type your message..."}
                                            rows={1}
                                            style={{
                                                flex: 1,
                                                padding: '12px',
                                                border: '1px solid #d1d5db',
                                                borderRadius: '24px',
                                                resize: 'none',
                                                fontSize: '14px',
                                                minHeight: '48px',
                                                maxHeight: '120px'
                                            }}
                                        />
                                        <button
                                            onClick={sendMessage}
                                            disabled={!newMessage.trim() || sending}
                                            style={{
                                                padding: '12px 16px',
                                                backgroundColor: newMessage.trim() ? '#3b82f6' : '#d1d5db',
                                                color: 'white',
                                                border: 'none',
                                                borderRadius: '24px',
                                                cursor: newMessage.trim() ? 'pointer' : 'not-allowed',
                                                minWidth: '48px'
                                            }}
                                        >
                                            {sending ? 
                                                <div className="spinner" style={{width: '16px', height: '16px'}}></div> :
                                                <i className="fas fa-paper-plane"></i>
                                            }
                                        </button>
                                    </div>
                                </div>
                            </>
                        ) : (
                            <div style={{
                                flex: 1,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                flexDirection: 'column',
                                color: '#6b7280'
                            }}>
                                <i className="fas fa-comments" style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.5 }}></i>
                                {isAdmin ? (
                                    <div style={{ textAlign: 'center' }}>
                                        <p>Select a patient conversation to start chatting</p>
                                        <small>Active conversations needing response will appear here</small>
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

// Quick Support Button Component
const SupportChatButton = ({ isAdmin, currentUser, patients = [], onPatientSelect }) => {
    const [showChat, setShowChat] = useState(false);
    const [selectedPatient, setSelectedPatient] = useState(null);

    const openChatForPatient = (patient) => {
        setSelectedPatient(patient);
        setShowChat(true);
    };

    return (
        <>
            {isAdmin ? (
                <div style={{ position: 'relative' }}>
                    <button
                        className="btn btn-primary"
                        onClick={() => setShowChat(true)}
                        style={{ marginRight: '8px' }}
                    >
                        <i className="fas fa-headset" style={{ marginRight: '8px' }}></i>
                        Support Center
                    </button>
                    {/* Quick patient chat buttons */}
                    <div style={{ marginTop: '8px' }}>
                        {patients.slice(0, 3).map(patient => (
                            <button
                                key={patient.id}
                                className="btn btn-sm btn-secondary"
                                onClick={() => openChatForPatient(patient)}
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
                    onClose={() => {
                        setShowChat(false);
                        setSelectedPatient(null);
                    }}
                    isAdmin={isAdmin}
                    currentUser={currentUser}
                    selectedPatient={selectedPatient}
                />
            )}
        </>
    );
};

// Export both components
window.ChatSupportModal = ChatSupportModal;
window.SupportChatButton = SupportChatButton;