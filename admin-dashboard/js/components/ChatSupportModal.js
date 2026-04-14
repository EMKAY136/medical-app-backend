const { useState, useEffect, useRef } = React;

const ChatSupportModal = ({ onClose, isAdmin = false, currentUser, selectedPatient = null }) => {
  const [conversations, setConversations]         = useState([]);
  const [activeConversation, setActiveConversation] = useState(null);
  const [messages, setMessages]                   = useState([]);
  const [newMessage, setNewMessage]               = useState('');
  const [loading, setLoading]                     = useState(false);
  const [searchQuery, setSearchQuery]             = useState('');
  const [humanRequestCount, setHumanRequestCount] = useState(0);
  const [supportStatus, setSupportStatus]         = useState(null);
  const [endingSession, setEndingSession]         = useState(false);
  const [sessionEndError, setSessionEndError]     = useState(null);

  const messagesEndRef  = useRef(null);
  const messageInputRef = useRef(null);
  const activeConvRef   = useRef(null);
  const sendingRef      = useRef(false);

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

  // ── Admin: load sidebar conversation list ─────────────────────────────────
  const loadConversations = async (silent = false) => {
    if (!isAdmin) return;
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success && data.chats) {
        const mapped = data.chats.map(chat => {
          const isHumanRequest =
            (chat.subject || '').toLowerCase().includes('human support') ||
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
          .filter(msg => msg.senderType !== 'SYSTEM')
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
  // ✅ End Session & Clear
  //
  // Backend endpoint:  POST /api/support/admin/end-session/{userId}
  // Defined in:        SupportController.adminEndSession()
  // Service method:    SupportService.adminEndChatSession()
  //
  // If the endpoint returns 404 it means either:
  //   (a) The Spring Security config is blocking the route — add it to the
  //       permit list in SecurityConfig.java alongside the other /api/support/** paths
  //   (b) The compiled JAR on Railway hasn't been redeployed since you added the method
  //
  // This frontend always clears its own state regardless of the backend result
  // so the admin can continue working even while the backend is being fixed.
  // ─────────────────────────────────────────────────────────────────────────
  const endSessionAndClear = async (userId, patientName) => {
    if (!window.confirm(
      `End chat session with ${patientName || 'this patient'}?\n\n` +
      `All messages will be cleared on both sides. This cannot be undone.`
    )) return;

    setEndingSession(true);
    setSessionEndError(null);

    const endpoint = `${CONFIG.ADMIN_API_URL}/api/support/admin/end-session/${userId}`;

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: getHeaders(),
      });

      if (res.ok) {
        // ✅ Backend confirmed deletion
        const data = await res.json().catch(() => ({ success: true }));
        console.log('✅ End session success:', data);
        _clearSessionLocally(userId);
        window.showNotificationAlert && window.showNotificationAlert('Session ended — chat cleared for both sides ✅');

      } else if (res.status === 404) {
        // ─────────────────────────────────────────────────────────────────
        // 404 = endpoint registered in controller but route isn't matched.
        // Most likely cause: Spring Security is blocking /api/support/admin/**
        // Fix in SecurityConfig.java:
        //   .requestMatchers("/api/support/admin/**").hasRole("ADMIN")
        // OR temporarily:
        //   .requestMatchers("/api/support/admin/end-session/**").permitAll()
        //
        // We still clear the frontend so the admin isn't stuck.
        // ─────────────────────────────────────────────────────────────────
        console.warn(
          '⚠️ POST /api/support/admin/end-session returned 404.\n' +
          'The endpoint exists in SupportController but is not reachable.\n' +
          'Check: (1) SecurityConfig permits this route, (2) Railway has the latest build.'
        );
        _clearSessionLocally(userId);
        window.showNotificationAlert && window.showNotificationAlert(
          'Chat cleared on your view ✅ (Backend returned 404 — redeploy or check SecurityConfig)'
        );

      } else if (res.status === 401 || res.status === 403) {
        const msg = `Auth error ${res.status} — admin token may have expired. Please log out and back in.`;
        setSessionEndError(msg);
        console.error('❌ End session auth error:', res.status);

      } else {
        const errData = await res.json().catch(() => ({}));
        const msg = errData.message || `Server error ${res.status}`;
        setSessionEndError(msg);
        console.error('❌ End session server error:', res.status, errData);
        // Still clear locally so admin isn't blocked
        _clearSessionLocally(userId);
      }

    } catch (networkErr) {
      // Network failure (CORS, timeout, etc.) — still clear locally
      console.error('❌ End session network error:', networkErr.message);
      _clearSessionLocally(userId);
      window.showNotificationAlert && window.showNotificationAlert(
        'Chat cleared locally ✅ (Network error — backend may not have cleared patient side)'
      );
    } finally {
      setEndingSession(false);
    }
  };

  // ── Clears the active conversation from admin's local state ──────────────
  const _clearSessionLocally = (userId) => {
    setMessages([]);
    setConversations(prev => prev.filter(c => c.userId !== userId));
    setActiveConversation(null);
    activeConvRef.current = null;
    setSessionEndError(null);
  };

  // ─────────────────────────────────────────────────────────────────────────
  // Send message — optimistic UI, no spinner hang
  // ─────────────────────────────────────────────────────────────────────────
  const sendMessage = async () => {
    if (!newMessage.trim() || sendingRef.current) return;
    const text = newMessage.trim();
    setNewMessage('');
    sendingRef.current = true;

    const tempId = `temp_${Date.now()}`;
    setMessages(prev => [...prev, {
      id: tempId,
      senderId: currentUser?.id,
      senderName: isAdmin
        ? (currentUser?.name || 'Medical Support')
        : `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
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
          body: JSON.stringify({
            userId: conv.userId,
            message: text,
            ticketId: conv.ticketId || null,
            senderType: 'SUPPORT_AGENT',
          }),
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
        setMessages(prev => prev.map(m =>
          m.id === tempId ? { ...m, status: 'delivered', id: data.messageId || m.id } : m
        ));
        if (!isAdmin && data.botResponse) {
          setTimeout(() => {
            setMessages(prev => [...prev, {
              id: `bot_${Date.now()}`, senderId: 'bot',
              senderName: 'Medical Support Bot', senderType: 'BOT',
              message: data.botResponse, timestamp: new Date(),
            }]);
          }, 400);
        }
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
                ● {supportStatus.isOnline
                  ? `Online · ${supportStatus.estimatedResponseTime || 'Fast replies'}`
                  : 'Offline'}
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
                <input
                  type="text"
                  placeholder="Search patients..."
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  style={{
                    width: '100%', padding: '8px 12px', border: '1px solid #d1d5db',
                    borderRadius: '6px', fontSize: '13px', boxSizing: 'border-box',
                  }}
                />
              </div>

              <div style={{ flex: 1, overflowY: 'auto' }}>
                {loading && conversations.length === 0 ? (
                  <div style={{ padding: '20px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                    Loading conversations...
                  </div>
                ) : filteredConversations.length === 0 ? (
                  <div style={{ padding: '20px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                    No conversations yet.<br />
                    <small>Patients appear here when they message support.</small>
                  </div>
                ) : filteredConversations.map(conv => (
                  <div
                    key={conv.id}
                    onClick={() => {
                      setActiveConversation(conv);
                      activeConvRef.current = conv;
                      setSessionEndError(null);
                      loadMessagesForUser(conv.userId, false);
                    }}
                    style={{
                      padding: '11px 14px', cursor: 'pointer',
                      borderBottom: '1px solid #e5e7eb',
                      backgroundColor: activeConversation?.id === conv.id
                        ? '#dbeafe'
                        : conv.isHumanRequest ? '#fef9c3' : 'transparent',
                    }}
                    onMouseEnter={e => {
                      if (activeConversation?.id !== conv.id)
                        e.currentTarget.style.backgroundColor = '#f3f4f6';
                    }}
                    onMouseLeave={e => {
                      if (activeConversation?.id !== conv.id)
                        e.currentTarget.style.backgroundColor = conv.isHumanRequest ? '#fef9c3' : 'transparent';
                    }}
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
                            fontSize: '9px', fontWeight: '800', padding: '1px 6px', borderRadius: '4px',
                            marginBottom: '3px', border: '1px solid #fbbf24',
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
                        width: '8px', height: '8px', borderRadius: '50%',
                        marginLeft: '8px', marginTop: '4px', flexShrink: 0,
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

                  {isAdmin && (
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <button
                        onClick={() => loadMessagesForUser(activeConversation.userId, false)}
                        style={{
                          padding: '5px 10px', fontSize: '11px', background: '#f3f4f6',
                          border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer',
                        }}
                        title="Refresh messages"
                      >
                        🔄 Refresh
                      </button>

                      {/* ✅ End & Clear button — calls POST /api/support/admin/end-session/{userId} */}
                      <button
                        onClick={() => endSessionAndClear(activeConversation.userId, activeConversation.patientName)}
                        disabled={endingSession}
                        style={{
                          padding: '5px 12px', fontSize: '11px', fontWeight: '600',
                          backgroundColor: endingSession ? '#9ca3af' : '#ef4444',
                          color: 'white', border: 'none', borderRadius: '6px',
                          cursor: endingSession ? 'not-allowed' : 'pointer',
                          display: 'flex', alignItems: 'center', gap: '5px',
                          opacity: endingSession ? 0.7 : 1,
                          transition: 'background-color 0.15s',
                        }}
                        title="End session and clear all messages for this patient"
                      >
                        {endingSession ? (
                          <>
                            <span style={{ display: 'inline-block', width: '10px', height: '10px', border: '2px solid white', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
                            Ending...
                          </>
                        ) : (
                          '🗑 End & Clear'
                        )}
                      </button>
                    </div>
                  )}
                </div>

                {/* ✅ Session end error banner — shown inline under the sub-header */}
                {sessionEndError && (
                  <div style={{
                    padding: '8px 16px', background: '#fef2f2', borderBottom: '1px solid #fecaca',
                    fontSize: '12px', color: '#dc2626', display: 'flex', alignItems: 'center',
                    justifyContent: 'space-between', flexShrink: 0,
                  }}>
                    <span>⚠️ {sessionEndError}</span>
                    <button onClick={() => setSessionEndError(null)}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#dc2626', fontSize: '14px', padding: '0 4px' }}>
                      ✕
                    </button>
                  </div>
                )}

                {/* Messages */}
                <div style={{ flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' }}>
                  {messages.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' }}>
                      {loading
                        ? 'Loading messages...'
                        : isAdmin
                          ? 'No messages from this patient yet.'
                          : 'No messages yet. Start the conversation!'}
                    </div>
                  ) : messages.map(msg => {
                    const mine    = isMyMessage(msg);
                    const bgColor = getBubbleBg(msg);
                    const isBot   = (msg.senderType || '').toLowerCase() === 'bot';
                    return (
                      <div key={msg.id} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: '10px' }}>
                        <div style={{
                          maxWidth: '75%', padding: '10px 14px', borderRadius: '16px',
                          backgroundColor: bgColor,
                          color: (mine || isBot) ? 'white' : '#1f2937',
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
                          <div style={{
                            fontSize: '10px', opacity: 0.6, marginTop: '4px',
                            display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '3px',
                          }}>
                            {formatTime(msg.timestamp)}
                            {mine && (
                              <span>
                                {msg.status === 'sending' ? '🕐' : msg.status === 'read' ? '✓✓' : '✓'}
                              </span>
                            )}
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
                        minHeight: '42px', maxHeight: '110px', fontFamily: 'inherit',
                        outline: 'none', boxSizing: 'border-box',
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
                      Enter to send · Shift+Enter for new line · Use "End &amp; Clear" when session is done
                    </div>
                  )}
                </div>
              </>
            ) : (
              /* No conversation selected */
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', color: '#9ca3af' }}>
                <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.3 }}>💬</div>
                {isAdmin ? (
                  <div style={{ textAlign: 'center', maxWidth: '280px' }}>
                    <p style={{ fontWeight: '600', color: '#374151', marginBottom: '6px' }}>Select a conversation</p>
                    <small style={{ color: '#9ca3af', lineHeight: '1.5' }}>
                      Patients who send messages appear in the sidebar.<br />
                      Use "End &amp; Clear" after each session to wipe history.
                    </small>
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

      {/* Spinner keyframe for end-session loading state */}
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};

// ── Support Chat Button ───────────────────────────────────────────────────────
const SupportChatButton = ({ isAdmin, currentUser, patients = [] }) => {
  const [showChat, setShowChat]           = useState(false);
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

window.ChatSupportModal  = ChatSupportModal;
window.SupportChatButton = SupportChatButton;