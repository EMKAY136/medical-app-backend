const { useState, useEffect, useRef } = React;

const ChatSupportModal = ({ onClose, isAdmin = false, currentUser, selectedPatient = null }) => {
  const [conversations, setConversations]   = useState([]);
  const [activeConversation, setActiveConversation] = useState(null);
  const [messages, setMessages]             = useState([]);
  const [newMessage, setNewMessage]         = useState('');
  const [loading, setLoading]               = useState(false);
  const [searchQuery, setSearchQuery]       = useState('');
  const [humanRequestCount, setHumanRequestCount] = useState(0);
  const [supportStatus, setSupportStatus]   = useState(null);

  const messagesEndRef       = useRef(null);
  const messageInputRef      = useRef(null);
  const activeConvRef        = useRef(null);
  const sendingRef           = useRef(false);

  const getHeaders = () => ({
    'Authorization': `Bearer ${localStorage.getItem('authToken')}`,
    'Content-Type': 'application/json',
  });

  useEffect(() => { activeConvRef.current = activeConversation; }, [activeConversation]);

  const scrollToBottom = () => {
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 50);
  };
  useEffect(scrollToBottom, [messages]);

  // ── On mount ──────────────────────────────────────────────────────────────
  useEffect(() => {
    loadSupportStatus();
    if (isAdmin) loadConversations();
    else loadPatientConversation();
  }, [isAdmin]);

  useEffect(() => {
    if (selectedPatient && isAdmin) openPatientConversation(selectedPatient);
  }, [selectedPatient]);

  // ── Polling ───────────────────────────────────────────────────────────────
  useEffect(() => {
    const interval = setInterval(() => {
      const conv = activeConvRef.current;
      if (isAdmin) {
        loadConversations(true);
        if (conv?.userId) loadMessagesForUser(conv.userId, true);
      } else {
        if (conv) loadPatientHistory(true);
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [isAdmin]);

  // ── Support status ────────────────────────────────────────────────────────
  const loadSupportStatus = async () => {
    try {
      const res = await fetch(`${CONFIG.API_BASE_URL}/api/support/status`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success) setSupportStatus(data);
    } catch {}
  };

  // ─────────────────────────────────────────────────────────────────────────
  // Admin: load sidebar conversation list
  // Only show conversations where patient has explicitly requested a human agent
  // (identified by having a support ticket with "Human Support" subject)
  // ─────────────────────────────────────────────────────────────────────────
  const loadConversations = async (silent = false) => {
    if (!isAdmin) return;
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success && data.chats) {
        const mapped = data.chats.map(chat => {
          // A conversation is "active/needs attention" only when the patient typed "human agent"
          const isHumanRequest = (chat.subject || '').toLowerCase().includes('human support') ||
            (chat.category || '').toLowerCase().includes('human support');
          return {
            id: chat.ticketId || `user_${chat.userId}`,
            userId: chat.userId,
            patientName: chat.userName,
            patientEmail: chat.userEmail,
            lastMessage: chat.lastMessage || chat.subject || 'Support conversation',
            lastMessageTime: new Date(chat.lastActivity || chat.createdAt),
            isHumanRequest,
            status: chat.status || 'active',
            priority: chat.priority,
            ticketNumber: chat.ticketNumber,
            ticketId: chat.ticketId,
          };
        });
        // Sort: human requests first, then by time
        mapped.sort((a, b) => {
          if (a.isHumanRequest && !b.isHumanRequest) return -1;
          if (!a.isHumanRequest && b.isHumanRequest) return 1;
          return new Date(b.lastMessageTime) - new Date(a.lastMessageTime);
        });
        setConversations(mapped);
        setHumanRequestCount(mapped.filter(c => c.isHumanRequest).length);
      }
    } catch (e) { console.error('loadConversations error:', e); }
    finally { if (!silent) setLoading(false); }
  };

  const openPatientConversation = (patient) => {
    const conv = {
      id: `user_${patient.id}`, userId: patient.id,
      patientName: `${patient.firstName} ${patient.lastName}`,
      patientEmail: patient.email, status: 'active',
    };
    setActiveConversation(conv);
    activeConvRef.current = conv;
    loadMessagesForUser(patient.id, false);
  };

  // ── Admin: fetch messages for a patient ──────────────────────────────────
  const loadMessagesForUser = async (userId, silent = false) => {
    if (!userId) return;
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/chat/${userId}`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success) {
        const formatted = (data.messages || [])
          .filter(msg => msg.senderType !== 'SYSTEM') // hide system messages
          .map(msg => ({
            id: msg.id,
            senderId: isAgentType(msg.senderType) ? 'admin' : userId,
            senderName: msg.senderName,
            senderType: msg.senderType,
            message: msg.message,
            timestamp: new Date(msg.timestamp || msg.createdAt),
            status: msg.isRead ? 'read' : 'delivered',
          }));
        setMessages(formatted);
        // Update patient details if provided
        if (data.user) {
          setActiveConversation(prev => prev ? ({
            ...prev,
            patientName: data.user.name || `${data.user.firstName || ''} ${data.user.lastName || ''}`.trim(),
            patientEmail: data.user.email,
            patientPhone: data.user.phone,
          }) : prev);
        }
      }
    } catch (e) { console.error('loadMessagesForUser error:', e); }
    finally { if (!silent) setLoading(false); }
  };

  // ── Patient: init conversation ────────────────────────────────────────────
  const loadPatientConversation = () => {
    const conv = {
      id: 'patient_chat', userId: currentUser?.id,
      patientName: `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
      patientEmail: currentUser?.email, status: 'active',
    };
    setActiveConversation(conv);
    activeConvRef.current = conv;
    loadPatientHistory(false);
  };

  // ── Patient: fetch own messages ───────────────────────────────────────────
  const loadPatientHistory = async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.API_BASE_URL}/api/support/chat/history`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success) {
        setMessages((data.messages || [])
          .filter(msg => msg.senderType !== 'SYSTEM')
          .map(msg => ({
            id: msg.id,
            senderId: isAgentType(msg.senderType) ? 'admin' : currentUser?.id,
            senderName: msg.senderName,
            senderType: msg.senderType,
            message: msg.message,
            timestamp: new Date(msg.timestamp || msg.createdAt),
          })));
      }
    } catch (e) { console.error('loadPatientHistory error:', e); }
    finally { if (!silent) setLoading(false); }
  };

  const isAgentType = (t) => {
    const s = (t || '').toLowerCase();
    return s === 'support_agent' || s === 'admin';
  };

  // ─────────────────────────────────────────────────────────────────────────
  // End Session & Clear — admin-initiated
  // Deletes ALL messages for this patient on the backend
  // ─────────────────────────────────────────────────────────────────────────
  const endSessionAndClear = async (userId) => {
    if (!confirm(`End this session and clear all chat history for this patient?\n\nThis cannot be undone. The patient's chat will also be cleared.`)) return;
    try {
      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/end-session/${userId}`, {
        method: 'POST', headers: getHeaders(),
      });
      if (res.ok) {
        setMessages([]);
        loadConversations(true);
        // Remove this conversation from the list
        setConversations(prev => prev.filter(c => c.userId !== userId));
        setActiveConversation(null);
        activeConvRef.current = null;
        window.showNotificationAlert && window.showNotificationAlert('Session ended. Chat cleared for both sides ✅');
      } else {
        alert('Failed to end session. Please try again.');
      }
    } catch (e) { alert('Error ending session: ' + e.message); }
  };

  // ─────────────────────────────────────────────────────────────────────────
  // Send message — fire-and-forget, no spinner hang
  // ─────────────────────────────────────────────────────────────────────────
  const sendMessage = async () => {
    if (!newMessage.trim() || sendingRef.current) return;
    const text = newMessage.trim();
    setNewMessage('');
    sendingRef.current = true;

    // Optimistic UI
    const tempId = `temp_${Date.now()}`;
    setMessages(prev => [...prev, {
      id: tempId,
      senderId: currentUser?.id,
      senderName: isAdmin ? (currentUser?.name || 'Medical Support') : `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
      senderType: isAdmin ? 'SUPPORT_AGENT' : 'USER',
      message: text,
      timestamp: new Date(),
      status: 'sending',
    }]);

    try {
      let res;
      if (isAdmin) {
        const conv = activeConvRef.current;
        if (!conv?.userId) throw new Error('No patient selected');
        res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/reply`, {
          method: 'POST', headers: getHeaders(),
          body: JSON.stringify({ userId: conv.userId, message: text, ticketId: conv.ticketId || null, senderType: 'SUPPORT_AGENT' }),
        });
      } else {
        res = await fetch(`${CONFIG.API_BASE_URL}/api/support/chat/message`, {
          method: 'POST', headers: getHeaders(),
          body: JSON.stringify({ message: text, senderType: 'USER' }),
        });
      }

      if (!res.ok) throw new Error(`Server error ${res.status}`);
      const data = await res.json();

      if (data.success) {
        // Confirm temp
        setMessages(prev => prev.map(m => m.id === tempId ? { ...m, status: 'delivered', id: data.messageId || m.id } : m));
        // Bot response (patient only)
        if (!isAdmin && data.botResponse) {
          setTimeout(() => {
            setMessages(prev => [...prev, {
              id: `bot_${Date.now()}`, senderId: 'bot', senderName: 'Medical Support Bot',
              senderType: 'BOT', message: data.botResponse, timestamp: new Date(),
            }]);
          }, 400);
        }
        // Re-fetch after 400ms to sync
        const conv = activeConvRef.current;
        setTimeout(() => {
          if (isAdmin && conv?.userId) loadMessagesForUser(conv.userId, true);
          else if (!isAdmin) loadPatientHistory(true);
        }, 400);
      } else throw new Error(data.message || 'Send failed');
    } catch (e) {
      console.error('sendMessage error:', e);
      setMessages(prev => prev.filter(m => m.id !== tempId));
      alert('Failed to send: ' + e.message);
    } finally {
      sendingRef.current = false;
      setTimeout(() => messageInputRef.current?.focus(), 50);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  };

  const formatTime = (t) => {
    const now = new Date(), d = new Date(t);
    const h = (now - d) / 3600000;
    if (h < 1) return 'Just now';
    if (h < 24) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  const isMyMessage = (msg) => {
    const t = (msg.senderType || '').toLowerCase();
    return isAdmin ? (t === 'support_agent' || t === 'admin') : t === 'user';
  };

  const getBubbleBg = (msg) => {
    if (isMyMessage(msg)) return '#3b82f6';
    const t = (msg.senderType || '').toLowerCase();
    if (t === 'bot') return '#10b981';
    return 'white';
  };

  const filteredConversations = conversations.filter(c =>
    c.patientName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.patientEmail?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.lastMessage?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className="modal" style={{ zIndex: 1000 }}>
      <div className="modal-content" style={{
        maxWidth: isAdmin ? '1100px' : '680px',
        height: '85vh',
        display: 'flex', flexDirection: 'column',
        overflow: 'hidden', padding: 0,
      }}>

        {/* ── Header ── */}
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '14px 20px', backgroundColor: 'white',
          borderBottom: '1px solid #e5e7eb', flexShrink: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <h2 style={{ margin: 0, fontSize: '18px', fontWeight: '700' }}>
              {isAdmin ? '💬 Patient Support Chat' : '🏥 Medical Support'}
            </h2>
            {/* Badge only for actual human agent requests */}
            {isAdmin && humanRequestCount > 0 && (
              <span style={{
                backgroundColor: '#ef4444', color: 'white', borderRadius: '12px',
                padding: '2px 10px', fontSize: '12px', fontWeight: '700',
              }}>
                {humanRequestCount} live request{humanRequestCount > 1 ? 's' : ''}
              </span>
            )}
            {!isAdmin && supportStatus && (
              <span style={{ fontSize: '12px', color: supportStatus.isOnline ? '#10b981' : '#f59e0b' }}>
                ● {supportStatus.isOnline ? `Online · ${supportStatus.estimatedResponseTime || 'Fast replies'}` : 'Offline'}
              </span>
            )}
          </div>
          <button onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '20px', color: '#6b7280', padding: '4px 8px' }}>
            ✕
          </button>
        </div>

        {/* ── Body ── */}
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

          {/* ── Admin sidebar ── */}
          {isAdmin && (
            <div style={{
              width: '280px', flexShrink: 0, borderRight: '1px solid #e5e7eb',
              display: 'flex', flexDirection: 'column', backgroundColor: '#f9fafb',
            }}>
              <div style={{ padding: '12px' }}>
                <input type="text" placeholder="Search patients..."
                  value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                  style={{ width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '13px', boxSizing: 'border-box' }}
                />
              </div>

              <div style={{ flex: 1, overflowY: 'auto' }}>
                {loading && conversations.length === 0 ? (
                  <div style={{ padding: '20px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>Loading conversations...</div>
                ) : filteredConversations.length === 0 ? (
                  <div style={{ padding: '20px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                    No conversations yet.<br />
                    <small>Patients appear here when they request a human agent.</small>
                  </div>
                ) : filteredConversations.map(conv => (
                  <div
                    key={conv.id}
                    onClick={() => {
                      setActiveConversation(conv);
                      activeConvRef.current = conv;
                      loadMessagesForUser(conv.userId, false);
                    }}
                    style={{
                      padding: '11px 14px', cursor: 'pointer',
                      borderBottom: '1px solid #e5e7eb',
                      backgroundColor: activeConversation?.id === conv.id ? '#dbeafe'
                        : conv.isHumanRequest ? '#fef9c3' : 'transparent',
                    }}
                    onMouseEnter={e => { if (activeConversation?.id !== conv.id) e.currentTarget.style.backgroundColor = '#f3f4f6'; }}
                    onMouseLeave={e => { if (activeConversation?.id !== conv.id) e.currentTarget.style.backgroundColor = conv.isHumanRequest ? '#fef9c3' : 'transparent'; }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: conv.isHumanRequest ? '700' : '500', fontSize: '13px', marginBottom: '2px' }}>
                          {conv.patientName}
                        </div>
                        {conv.ticketNumber && (
                          <div style={{ fontSize: '10px', color: '#3b82f6', fontWeight: '700', marginBottom: '2px' }}>
                            #{conv.ticketNumber}
                          </div>
                        )}
                        {conv.isHumanRequest && (
                          <div style={{
                            display: 'inline-block', backgroundColor: '#fef3c7', color: '#92400e',
                            fontSize: '9px', fontWeight: '800', padding: '1px 6px',
                            borderRadius: '4px', marginBottom: '3px', border: '1px solid #fbbf24',
                          }}>
                            🙋 LIVE AGENT REQUESTED
                          </div>
                        )}
                        <div style={{ fontSize: '11px', color: '#6b7280', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {conv.lastMessage}
                        </div>
                        <div style={{ fontSize: '10px', color: '#9ca3af', marginTop: '2px' }}>
                          {formatTime(conv.lastMessageTime)}
                        </div>
                      </div>
                      <div style={{
                        width: '8px', height: '8px', borderRadius: '50%', marginLeft: '8px', marginTop: '4px', flexShrink: 0,
                        backgroundColor: conv.isHumanRequest ? '#ef4444' : '#10b981',
                      }} />
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
                  backgroundColor: isAdmin ? '#f0f9ff' : 'white', flexShrink: 0,
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
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    {isAdmin && (
                      <>
                        <button
                          onClick={() => { loadMessagesForUser(activeConversation.userId, false); }}
                          style={{ padding: '5px 10px', fontSize: '11px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer' }}
                          title="Refresh messages"
                        >
                          🔄 Refresh
                        </button>
                        <button
                          onClick={() => endSessionAndClear(activeConversation.userId)}
                          style={{ padding: '5px 12px', fontSize: '11px', fontWeight: '600', backgroundColor: '#ef4444', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer' }}
                          title="End session and clear all messages"
                        >
                          🗑 End & Clear
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {/* Messages */}
                <div style={{ flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' }}>
                  {messages.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' }}>
                      {isAdmin ? 'No messages from this patient yet.' : 'No messages yet. Start the conversation!'}
                    </div>
                  ) : messages.map(msg => {
                    const mine = isMyMessage(msg);
                    const bgColor = getBubbleBg(msg);
                    return (
                      <div key={msg.id} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: '10px' }}>
                        <div style={{
                          maxWidth: '75%', padding: '10px 14px', borderRadius: '16px',
                          backgroundColor: bgColor,
                          color: (mine || (msg.senderType || '').toLowerCase() === 'bot') ? 'white' : '#1f2937',
                          boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
                          borderBottomRightRadius: mine ? '4px' : '16px',
                          borderBottomLeftRadius:  mine ? '16px' : '4px',
                        }}>
                          {!mine && msg.senderName && (
                            <div style={{ fontSize: '10px', fontWeight: '700', marginBottom: '3px', opacity: 0.7 }}>
                              {msg.senderName}
                            </div>
                          )}
                          <div style={{ fontSize: '13px', lineHeight: '1.45', whiteSpace: 'pre-wrap' }}>
                            {msg.message}
                          </div>
                          <div style={{ fontSize: '10px', opacity: 0.6, marginTop: '4px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '3px' }}>
                            {formatTime(msg.timestamp)}
                            {mine && <span>{msg.status === 'sending' ? '🕐' : msg.status === 'read' ? '✓✓' : '✓'}</span>}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                  <div ref={messagesEndRef} />
                </div>

                {/* Input */}
                <div style={{ padding: '12px 16px', borderTop: '1px solid #e5e7eb', backgroundColor: 'white', flexShrink: 0 }}>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-end' }}>
                    <textarea
                      ref={messageInputRef}
                      value={newMessage}
                      onChange={e => setNewMessage(e.target.value)}
                      onKeyPress={handleKeyPress}
                      placeholder={isAdmin ? 'Type your reply to the patient... (Enter to send)' : 'Type your message...'}
                      rows={1}
                      style={{
                        flex: 1, padding: '10px 14px', border: '1px solid #d1d5db',
                        borderRadius: '20px', resize: 'none', fontSize: '13px',
                        minHeight: '42px', maxHeight: '110px', fontFamily: 'inherit', outline: 'none',
                        boxSizing: 'border-box',
                      }}
                    />
                    <button
                      onClick={sendMessage}
                      disabled={!newMessage.trim()}
                      style={{
                        padding: '0 16px', height: '42px',
                        backgroundColor: newMessage.trim() ? '#3b82f6' : '#d1d5db',
                        color: 'white', border: 'none', borderRadius: '20px',
                        cursor: newMessage.trim() ? 'pointer' : 'not-allowed',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        gap: '6px', fontSize: '13px', fontWeight: '600', whiteSpace: 'nowrap',
                      }}
                    >
                      Send <i className="fas fa-paper-plane" style={{ fontSize: '11px' }}></i>
                    </button>
                  </div>
                  {isAdmin && (
                    <div style={{ fontSize: '10px', color: '#9ca3af', marginTop: '4px', paddingLeft: '4px' }}>
                      Enter to send · Shift+Enter for new line · Use "End &amp; Clear" when done
                    </div>
                  )}
                </div>
              </>
            ) : (
              /* No conversation selected */
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', color: '#9ca3af' }}>
                <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.3 }}>💬</div>
                {isAdmin ? (
                  <div style={{ textAlign: 'center' }}>
                    <p style={{ fontWeight: '600', color: '#374151', marginBottom: '4px' }}>Select a conversation</p>
                    <small>Patients who typed "human agent" appear in the sidebar.<br />
                    Use "End &amp; Clear" after each session to wipe the history.</small>
                  </div>
                ) : (
                  <div style={{ textAlign: 'center' }}>
                    <p style={{ color: '#374151' }}>Connecting to medical support...</p>
                    <small>Our team is here to help</small>
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
        <button className="btn btn-primary" onClick={() => setShowChat(true)}
          style={{ position: 'fixed', bottom: '20px', right: '20px', borderRadius: '50px', padding: '12px 20px' }}>
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

window.ChatSupportModal  = ChatSupportModal;
window.SupportChatButton = SupportChatButton;