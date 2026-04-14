// ChatSupportModal.js  — Admin side
// Displays all patient conversations, lets agents reply, end sessions, get notified of live requests.
// Polling every 4 s keeps sidebar + active chat fresh without WebSocket dependency.

const { useState, useEffect, useRef, useCallback } = React;

const ChatSupportModal = ({ onClose, isAdmin = false, currentUser, selectedPatient = null }) => {
  // ── State ──────────────────────────────────────────────────────────────────
  const [conversations,    setConversations]    = useState([]);
  const [activeConversation, setActiveConversation] = useState(null);
  const [messages,         setMessages]         = useState([]);
  const [newMessage,       setNewMessage]        = useState('');
  const [loading,          setLoading]          = useState(false);
  const [searchQuery,      setSearchQuery]      = useState('');
  const [supportStatus,    setSupportStatus]    = useState(null);
  const [endingSession,    setEndingSession]    = useState(false);
  const [sessionEndError,  setSessionEndError]  = useState(null);
  const [humanRequestCount, setHumanRequestCount] = useState(0);
  const [newRequestAlert,  setNewRequestAlert]  = useState(null); // { name, ticketNumber }
  const [prevHumanIds,     setPrevHumanIds]     = useState(new Set());

  // ── Refs ───────────────────────────────────────────────────────────────────
  const messagesEndRef   = useRef(null);
  const messageInputRef  = useRef(null);
  const activeConvRef    = useRef(null);
  const sendingRef       = useRef(false);
  const alertTimerRef    = useRef(null);

  // Keep ref in sync with state so intervals have current value
  useEffect(() => { activeConvRef.current = activeConversation; }, [activeConversation]);

  // ── Scroll to bottom whenever messages change ──────────────────────────────
  const scrollToBottom = () =>
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 60);
  useEffect(scrollToBottom, [messages]);

  // ── Auth header ────────────────────────────────────────────────────────────
  const getHeaders = () => ({
    Authorization: `Bearer ${localStorage.getItem('authToken')}`,
    'Content-Type': 'application/json',
  });

  // ── On mount ───────────────────────────────────────────────────────────────
  useEffect(() => {
    loadSupportStatus();
    if (isAdmin) {
      loadConversations(false);
    } else {
      loadPatientConversation();
    }
  }, [isAdmin]);

  // Open a pre-selected patient immediately
  useEffect(() => {
    if (selectedPatient && isAdmin) openPatientConversation(selectedPatient);
  }, [selectedPatient]);

  // ── Polling — every 4 s ────────────────────────────────────────────────────
  useEffect(() => {
    const interval = setInterval(() => {
      if (isAdmin) {
        loadConversations(true);
        const conv = activeConvRef.current;
        if (conv?.userId) loadMessagesForUser(conv.userId, true);
      } else {
        const conv = activeConvRef.current;
        if (conv) loadPatientHistory(true);
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [isAdmin]);

  // ── Support status ─────────────────────────────────────────────────────────
  const loadSupportStatus = async () => {
    try {
      const res = await fetch(`${CONFIG.API_BASE_URL}/api/support/status`, { headers: getHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      if (data.success) setSupportStatus(data);
    } catch {}
  };

  // ── Admin: load conversation sidebar ──────────────────────────────────────
  const loadConversations = async (silent = false) => {
    if (!isAdmin) return;
    try {
      if (!silent) setLoading(true);

      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`, {
        headers: getHeaders(),
      });

      if (res.status === 401 || res.status === 403) {
        console.error('❌ Support chat: auth error', res.status, '— check SecurityConfig allows ADMIN/DOCTOR on /api/support/admin/**');
        if (!silent) setLoading(false);
        return;
      }
      if (!res.ok) {
        console.error('❌ Support chat: server error', res.status);
        if (!silent) setLoading(false);
        return;
      }

      const data = await res.json();
      if (!data.success || !data.chats) return;

      const mapped = data.chats.map(chat => {
        const isHumanRequest =
          (chat.subject  || '').toLowerCase().includes('human support') ||
          (chat.category || '').toLowerCase().includes('human support');
        return {
          id:              chat.ticketId ? `ticket_${chat.ticketId}` : `user_${chat.userId}`,
          userId:          chat.userId,
          patientName:     chat.userName,
          patientEmail:    chat.userEmail,
          lastMessage:     chat.lastMessage || chat.subject || 'Support conversation',
          lastMessageTime: new Date(chat.lastActivity || chat.createdAt),
          isHumanRequest,
          status:          chat.status || 'active',
          priority:        chat.priority,
          ticketNumber:    chat.ticketNumber,
          ticketId:        chat.ticketId,
        };
      });

      // Sort: human requests first, then most recent
      mapped.sort((a, b) => {
        if (a.isHumanRequest && !b.isHumanRequest) return -1;
        if (!a.isHumanRequest && b.isHumanRequest) return 1;
        return new Date(b.lastMessageTime) - new Date(a.lastMessageTime);
      });

      const humanOnes = mapped.filter(c => c.isHumanRequest);
      setHumanRequestCount(humanOnes.length);

      // Detect NEW human requests since last poll and show alert
      const newHumanIds = new Set(humanOnes.map(c => c.userId));
      setPrevHumanIds(prev => {
        const brandNew = humanOnes.filter(c => !prev.has(c.userId));
        if (brandNew.length > 0) {
          showNewRequestAlert(brandNew[0]);
        }
        return newHumanIds;
      });

      setConversations(mapped);
    } catch (e) {
      console.error('loadConversations error:', e);
    } finally {
      if (!silent) setLoading(false);
    }
  };

  // ── Flash alert when a new live-agent request arrives ─────────────────────
  const showNewRequestAlert = (conv) => {
    if (alertTimerRef.current) clearTimeout(alertTimerRef.current);
    setNewRequestAlert({ name: conv.patientName, ticketNumber: conv.ticketNumber });
    // Browser notification
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('🚨 Live Agent Requested!', {
        body: `${conv.patientName} needs a live support agent`,
        icon: '/favicon.ico',
      });
    } else if ('Notification' in window && Notification.permission !== 'denied') {
      Notification.requestPermission();
    }
    alertTimerRef.current = setTimeout(() => setNewRequestAlert(null), 8000);
  };

  // ── Open a patient conversation ────────────────────────────────────────────
  const openPatientConversation = (patient) => {
    const conv = {
      id:           `user_${patient.id}`,
      userId:       patient.id,
      patientName:  `${patient.firstName || ''} ${patient.lastName || ''}`.trim(),
      patientEmail: patient.email,
      status:       'active',
    };
    setActiveConversation(conv);
    activeConvRef.current = conv;
    setSessionEndError(null);
    loadMessagesForUser(patient.id, false);
  };

  const selectConversation = (conv) => {
    setActiveConversation(conv);
    activeConvRef.current = conv;
    setSessionEndError(null);
    setMessages([]);
    loadMessagesForUser(conv.userId, false);
  };

  // ── Admin: fetch messages for a patient ────────────────────────────────────
  const loadMessagesForUser = async (userId, silent = false) => {
    if (!userId) return;
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/chat/${userId}`, {
        headers: getHeaders(),
      });
      if (!res.ok) return;
      const data = await res.json();
      if (!data.success) return;

      const formatted = (data.messages || [])
        .filter(msg => msg.senderType !== 'SYSTEM')
        .map(msg => ({
          id:         msg.id,
          senderId:   isAgentSenderType(msg.senderType) ? 'admin' : userId,
          senderName: msg.senderName,
          senderType: msg.senderType,
          message:    msg.message,
          timestamp:  new Date(msg.timestamp || msg.createdAt),
          status:     msg.isRead ? 'read' : 'delivered',
        }));
      setMessages(formatted);

      // Patch active conversation with fresh user details
      if (data.user) {
        setActiveConversation(prev => prev ? ({
          ...prev,
          patientName:  data.user.name || `${data.user.firstName || ''} ${data.user.lastName || ''}`.trim(),
          patientEmail: data.user.email,
          patientPhone: data.user.phoneNumber || data.user.phone || '',
        }) : prev);
      }
    } catch (e) {
      console.error('loadMessagesForUser error:', e);
    } finally {
      if (!silent) setLoading(false);
    }
  };

  // ── Patient: init own conversation ─────────────────────────────────────────
  const loadPatientConversation = () => {
    const conv = {
      id:           'patient_chat',
      userId:       currentUser?.id,
      patientName:  `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim(),
      patientEmail: currentUser?.email,
      status:       'active',
    };
    setActiveConversation(conv);
    activeConvRef.current = conv;
    loadPatientHistory(false);
  };

  const loadPatientHistory = async (silent = false) => {
    try {
      if (!silent) setLoading(true);
      const res = await fetch(`${CONFIG.API_BASE_URL}/api/support/chat/history`, {
        headers: getHeaders(),
      });
      if (!res.ok) return;
      const data = await res.json();
      if (!data.success) return;
      setMessages((data.messages || [])
        .filter(m => m.senderType !== 'SYSTEM')
        .map(m => ({
          id:         m.id,
          senderId:   isAgentSenderType(m.senderType) ? 'admin' : currentUser?.id,
          senderName: m.senderName,
          senderType: m.senderType,
          message:    m.message,
          timestamp:  new Date(m.timestamp || m.createdAt),
        })));
    } catch (e) {
      console.error('loadPatientHistory error:', e);
    } finally {
      if (!silent) setLoading(false);
    }
  };

  const isAgentSenderType = (t) => {
    const s = (t || '').toLowerCase();
    return s === 'support_agent' || s === 'admin';
  };

  // ── End session (admin) ────────────────────────────────────────────────────
  const endSessionAndClear = async (userId, patientName) => {
    if (!window.confirm(
      `End chat session with ${patientName || 'this patient'}?\n\nAll messages will be cleared on both sides. This cannot be undone.`
    )) return;

    setEndingSession(true);
    setSessionEndError(null);

    try {
      const res = await fetch(
        `${CONFIG.ADMIN_API_URL}/api/support/admin/end-session/${userId}`,
        { method: 'POST', headers: getHeaders() }
      );

      if (res.ok) {
        console.log('✅ Session ended successfully');
        clearSessionLocally(userId);
        window.showNotificationAlert?.('Session ended — chat cleared for both sides ✅');
      } else if (res.status === 403) {
        // Auth problem — likely SecurityConfig not permitting POST to /admin/**
        const msg = 'Server returned 403. Check SecurityConfig.java allows ADMIN/DOCTOR on POST /api/support/admin/**.';
        setSessionEndError(msg);
        console.error('❌', msg);
        // Still clear locally so admin is not blocked
        clearSessionLocally(userId);
      } else if (res.status === 404) {
        console.warn('⚠️ end-session returned 404 — route may not be deployed yet');
        clearSessionLocally(userId);
        window.showNotificationAlert?.('Cleared locally ✅ (backend 404 — redeploy needed)');
      } else {
        const errData = await res.json().catch(() => ({}));
        setSessionEndError(errData.message || `Server error ${res.status}`);
        clearSessionLocally(userId);
      }
    } catch (networkErr) {
      console.error('❌ end-session network error:', networkErr.message);
      clearSessionLocally(userId);
      window.showNotificationAlert?.('Cleared locally ✅ (network error — patient side may not have cleared)');
    } finally {
      setEndingSession(false);
    }
  };

  const clearSessionLocally = (userId) => {
    setMessages([]);
    setConversations(prev => prev.filter(c => c.userId !== userId));
    setActiveConversation(null);
    activeConvRef.current = null;
    setSessionEndError(null);
    setPrevHumanIds(prev => { const n = new Set(prev); n.delete(userId); return n; });
  };

  // ── Send message ───────────────────────────────────────────────────────────
  const sendMessage = async () => {
    if (!newMessage.trim() || sendingRef.current) return;
    const text = newMessage.trim();
    setNewMessage('');
    sendingRef.current = true;

    const tempId = `temp_${Date.now()}`;
    const senderName = isAdmin
      ? (currentUser?.name || currentUser?.firstName || 'Medical Support')
      : `${currentUser?.firstName || ''} ${currentUser?.lastName || ''}`.trim();

    setMessages(prev => [...prev, {
      id:         tempId,
      senderId:   currentUser?.id,
      senderName,
      senderType: isAdmin ? 'SUPPORT_AGENT' : 'USER',
      message:    text,
      timestamp:  new Date(),
      status:     'sending',
    }]);

    try {
      let res;
      if (isAdmin) {
        const conv = activeConvRef.current;
        if (!conv?.userId) throw new Error('No patient selected');
        res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/reply`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify({
            userId:   conv.userId,
            message:  text,
            ticketId: conv.ticketId || null,
          }),
        });
      } else {
        res = await fetch(`${CONFIG.API_BASE_URL}/api/support/chat/message`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify({ message: text }),
        });
      }

      if (!res.ok) throw new Error(`Server error ${res.status}`);
      const data = await res.json();

      if (data.success) {
        // Replace temp message with confirmed one
        setMessages(prev => prev.map(m =>
          m.id === tempId ? { ...m, status: 'delivered', id: data.messageId || m.id } : m
        ));
        // If patient gets a bot reply, show it
        if (!isAdmin && data.botResponse) {
          setTimeout(() => setMessages(prev => [...prev, {
            id:         `bot_${Date.now()}`,
            senderId:   'bot',
            senderName: 'Medical Support Bot',
            senderType: 'BOT',
            message:    data.botResponse,
            timestamp:  new Date(),
          }]), 400);
        }
        // Refresh after small delay
        const conv = activeConvRef.current;
        setTimeout(() => {
          if (isAdmin && conv?.userId) loadMessagesForUser(conv.userId, true);
          else if (!isAdmin) loadPatientHistory(true);
        }, 500);
      } else {
        throw new Error(data.message || 'Send failed');
      }
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

  // ── Helpers ────────────────────────────────────────────────────────────────
  const formatTime = (t) => {
    const now = new Date(), d = new Date(t);
    const h = (now - d) / 3600000;
    if (h < 1)  return 'Just now';
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
    (c.patientName  || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
    (c.patientEmail || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
    (c.lastMessage  || '').toLowerCase().includes(searchQuery.toLowerCase())
  );

  // ── RENDER ─────────────────────────────────────────────────────────────────
  return (
    <div className="modal" style={{ zIndex: 1000 }}>
      <div className="modal-content" style={{
        maxWidth:      isAdmin ? '1100px' : '680px',
        height:        '85vh',
        display:       'flex',
        flexDirection: 'column',
        overflow:      'hidden',
        padding:       0,
        borderRadius:  '12px',
      }}>

        {/* ── New human-agent alert banner ── */}
        {newRequestAlert && (
          <div style={{
            position:       'absolute',
            top:            '12px',
            left:           '50%',
            transform:      'translateX(-50%)',
            zIndex:         2000,
            background:     '#ef4444',
            color:          'white',
            padding:        '10px 20px',
            borderRadius:   '8px',
            boxShadow:      '0 4px 20px rgba(0,0,0,0.3)',
            display:        'flex',
            alignItems:     'center',
            gap:            '10px',
            fontSize:       '13px',
            fontWeight:     '700',
            animation:      'slideDown 0.3s ease',
          }}>
            <span>🚨</span>
            <span>{newRequestAlert.name} requested a LIVE AGENT</span>
            <button
              onClick={() => setNewRequestAlert(null)}
              style={{ background: 'none', border: 'none', color: 'white', cursor: 'pointer', fontSize: '16px', padding: '0 4px' }}
            >✕</button>
          </div>
        )}

        {/* ── Header ── */}
        <div style={{
          display:         'flex',
          justifyContent:  'space-between',
          alignItems:      'center',
          padding:         '14px 20px',
          backgroundColor: 'white',
          borderBottom:    '1px solid #e5e7eb',
          flexShrink:      0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <h2 style={{ margin: 0, fontSize: '18px', fontWeight: '700' }}>
              {isAdmin ? '💬 Patient Support Chat' : '🏥 Medical Support'}
            </h2>
            {isAdmin && humanRequestCount > 0 && (
              <span style={{
                backgroundColor: '#ef4444',
                color:           'white',
                borderRadius:    '12px',
                padding:         '2px 10px',
                fontSize:        '12px',
                fontWeight:      '700',
                animation:       'pulse 1.2s infinite',
              }}>
                🚨 {humanRequestCount} LIVE request{humanRequestCount > 1 ? 's' : ''}
              </span>
            )}
            {!isAdmin && supportStatus && (
              <span style={{ fontSize: '12px', color: supportStatus.isOnline ? '#10b981' : '#f59e0b' }}>
                ● {supportStatus.isOnline
                  ? `Online · ${supportStatus.estimatedResponseTime || 'Fast replies'}`
                  : 'Offline — we\'ll reply soon'}
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

          {/* ── Admin sidebar ── */}
          {isAdmin && (
            <div style={{
              width:           '290px',
              flexShrink:      0,
              borderRight:     '1px solid #e5e7eb',
              display:         'flex',
              flexDirection:   'column',
              backgroundColor: '#f9fafb',
            }}>
              {/* Search */}
              <div style={{ padding: '10px 12px', borderBottom: '1px solid #e5e7eb' }}>
                <input
                  type="text"
                  placeholder="🔍 Search patients..."
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  style={{
                    width:        '100%',
                    padding:      '7px 12px',
                    border:       '1px solid #d1d5db',
                    borderRadius: '6px',
                    fontSize:     '13px',
                    boxSizing:    'border-box',
                    outline:      'none',
                  }}
                />
              </div>

              {/* Conversation list */}
              <div style={{ flex: 1, overflowY: 'auto' }}>
                {loading && conversations.length === 0 ? (
                  <div style={{ padding: '24px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                    Loading conversations...
                  </div>
                ) : filteredConversations.length === 0 ? (
                  <div style={{ padding: '24px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' }}>
                    No conversations yet.<br />
                    <small style={{ color: '#c4c4c4' }}>Patients appear here when they send a message.</small>
                  </div>
                ) : filteredConversations.map(conv => (
                  <div
                    key={conv.id}
                    onClick={() => selectConversation(conv)}
                    style={{
                      padding:         '11px 14px',
                      cursor:          'pointer',
                      borderBottom:    '1px solid #e5e7eb',
                      backgroundColor: activeConversation?.id === conv.id
                        ? '#dbeafe'
                        : conv.isHumanRequest ? '#fff7ed' : 'transparent',
                      transition:      'background 0.15s',
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
                            display:         'inline-block',
                            backgroundColor: '#ef4444',
                            color:           'white',
                            fontSize:        '9px',
                            fontWeight:      '800',
                            padding:         '2px 7px',
                            borderRadius:    '4px',
                            marginBottom:    '3px',
                            animation:       'pulse 1.2s infinite',
                          }}>
                            🚨 LIVE AGENT NEEDED
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
                        width:           '9px',
                        height:          '9px',
                        borderRadius:    '50%',
                        marginLeft:      '8px',
                        marginTop:       '4px',
                        flexShrink:      0,
                        backgroundColor: conv.isHumanRequest ? '#ef4444' : '#10b981',
                        boxShadow:       conv.isHumanRequest ? '0 0 6px #ef444480' : 'none',
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
                  padding:         '10px 16px',
                  borderBottom:    '1px solid #e5e7eb',
                  backgroundColor: isAdmin ? '#f0f9ff' : 'white',
                  flexShrink:      0,
                  display:         'flex',
                  alignItems:      'center',
                  justifyContent:  'space-between',
                }}>
                  <div>
                    <div style={{ fontWeight: '600', fontSize: '14px' }}>
                      {isAdmin ? activeConversation.patientName : 'Medical Support Team'}
                    </div>
                    <div style={{ fontSize: '11px', color: '#6b7280' }}>
                      {isAdmin
                        ? [activeConversation.patientEmail, activeConversation.patientPhone].filter(Boolean).join(' · ')
                        : 'Qualitest Medical · info@qualitestmedical.com'}
                    </div>
                  </div>

                  {isAdmin && (
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <button
                        onClick={() => loadMessagesForUser(activeConversation.userId, false)}
                        style={{
                          padding:      '5px 10px',
                          fontSize:     '11px',
                          background:   '#f3f4f6',
                          border:       '1px solid #d1d5db',
                          borderRadius: '6px',
                          cursor:       'pointer',
                          fontWeight:   '600',
                        }}
                        title="Refresh messages"
                      >
                        🔄 Refresh
                      </button>
                      <button
                        onClick={() => endSessionAndClear(activeConversation.userId, activeConversation.patientName)}
                        disabled={endingSession}
                        style={{
                          padding:         '5px 12px',
                          fontSize:        '11px',
                          fontWeight:      '700',
                          backgroundColor: endingSession ? '#9ca3af' : '#ef4444',
                          color:           'white',
                          border:          'none',
                          borderRadius:    '6px',
                          cursor:          endingSession ? 'not-allowed' : 'pointer',
                          display:         'flex',
                          alignItems:      'center',
                          gap:             '5px',
                          opacity:         endingSession ? 0.7 : 1,
                          transition:      'background 0.15s',
                        }}
                        title="End session and clear all messages for this patient"
                      >
                        {endingSession ? (
                          <>
                            <span style={{
                              display:         'inline-block',
                              width:           '10px',
                              height:          '10px',
                              border:          '2px solid white',
                              borderTopColor:  'transparent',
                              borderRadius:    '50%',
                              animation:       'spin 0.7s linear infinite',
                            }} />
                            Ending...
                          </>
                        ) : '🗑 End & Clear'}
                      </button>
                    </div>
                  )}
                </div>

                {/* Session error banner */}
                {sessionEndError && (
                  <div style={{
                    padding:      '8px 16px',
                    background:   '#fef2f2',
                    borderBottom: '1px solid #fecaca',
                    fontSize:     '12px',
                    color:        '#dc2626',
                    display:      'flex',
                    alignItems:   'center',
                    justifyContent: 'space-between',
                    flexShrink:   0,
                  }}>
                    <span>⚠️ {sessionEndError}</span>
                    <button
                      onClick={() => setSessionEndError(null)}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#dc2626', fontSize: '14px', padding: '0 4px' }}
                    >✕</button>
                  </div>
                )}

                {/* Messages */}
                <div style={{ flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' }}>
                  {loading && messages.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' }}>
                      Loading messages...
                    </div>
                  ) : messages.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' }}>
                      {isAdmin ? 'No messages from this patient yet.' : 'No messages yet. Start the conversation!'}
                    </div>
                  ) : messages.map(msg => {
                    const mine    = isMyMessage(msg);
                    const bgColor = getBubbleBg(msg);
                    const isBot   = (msg.senderType || '').toLowerCase() === 'bot';
                    return (
                      <div key={msg.id} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: '10px' }}>
                        <div style={{
                          maxWidth:            '75%',
                          padding:             '10px 14px',
                          borderRadius:        '16px',
                          backgroundColor:     bgColor,
                          color:               (mine || isBot) ? 'white' : '#1f2937',
                          boxShadow:           '0 1px 2px rgba(0,0,0,0.08)',
                          borderBottomRightRadius: mine ? '4px' : '16px',
                          borderBottomLeftRadius:  mine ? '16px' : '4px',
                        }}>
                          {!mine && msg.senderName && (
                            <div style={{ fontSize: '10px', fontWeight: '700', marginBottom: '3px', opacity: 0.7 }}>
                              {msg.senderName}
                              {isBot && ' 🤖'}
                              {isAgentSenderType(msg.senderType) && ' 🟢'}
                            </div>
                          )}
                          <div style={{ fontSize: '13px', lineHeight: '1.5', whiteSpace: 'pre-wrap' }}>
                            {msg.message}
                          </div>
                          <div style={{
                            fontSize:       '10px',
                            opacity:        0.6,
                            marginTop:      '4px',
                            display:        'flex',
                            justifyContent: 'flex-end',
                            alignItems:     'center',
                            gap:            '3px',
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
                      placeholder={
                        isAdmin
                          ? `Reply to ${activeConversation.patientName}... (Enter to send, Shift+Enter for new line)`
                          : 'Type your message...'
                      }
                      rows={1}
                      style={{
                        flex:        1,
                        padding:     '10px 14px',
                        border:      '1px solid #d1d5db',
                        borderRadius:'20px',
                        resize:      'none',
                        fontSize:    '13px',
                        minHeight:   '42px',
                        maxHeight:   '110px',
                        fontFamily:  'inherit',
                        outline:     'none',
                        boxSizing:   'border-box',
                      }}
                    />
                    <button
                      onClick={sendMessage}
                      disabled={!newMessage.trim()}
                      style={{
                        padding:         '0 18px',
                        height:          '42px',
                        backgroundColor: newMessage.trim() ? '#3b82f6' : '#d1d5db',
                        color:           'white',
                        border:          'none',
                        borderRadius:    '20px',
                        cursor:          newMessage.trim() ? 'pointer' : 'not-allowed',
                        display:         'flex',
                        alignItems:      'center',
                        justifyContent:  'center',
                        gap:             '6px',
                        fontSize:        '13px',
                        fontWeight:      '600',
                        whiteSpace:      'nowrap',
                        transition:      'background 0.15s',
                      }}
                    >
                      Send <i className="fas fa-paper-plane" style={{ fontSize: '11px' }} />
                    </button>
                  </div>
                  {isAdmin && (
                    <div style={{ fontSize: '10px', color: '#9ca3af', marginTop: '4px', paddingLeft: '4px' }}>
                      Enter to send · Shift+Enter for new line · Click "End &amp; Clear" when session is done
                    </div>
                  )}
                </div>
              </>
            ) : (
              /* No conversation selected */
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', color: '#9ca3af' }}>
                <div style={{ fontSize: '52px', marginBottom: '16px', opacity: 0.25 }}>💬</div>
                {isAdmin ? (
                  <div style={{ textAlign: 'center', maxWidth: '300px' }}>
                    <p style={{ fontWeight: '600', color: '#374151', marginBottom: '6px' }}>Select a conversation</p>
                    <small style={{ color: '#9ca3af', lineHeight: '1.6' }}>
                      Patients appear in the sidebar when they send messages or request a live agent.<br />
                      Rows with 🚨 need immediate attention.
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

      {/* Keyframes */}
      <style>{`
        @keyframes spin      { from { transform: rotate(0deg);   } to { transform: rotate(360deg); } }
        @keyframes pulse     { 0%,100% { opacity:1; } 50% { opacity:0.55; } }
        @keyframes slideDown { from { transform: translateX(-50%) translateY(-20px); opacity:0; } to { transform: translateX(-50%) translateY(0); opacity:1; } }
      `}</style>
    </div>
  );
};

// ── Support Chat Button ────────────────────────────────────────────────────────
const SupportChatButton = ({ isAdmin, currentUser, patients = [] }) => {
  const [showChat,         setShowChat]         = useState(false);
  const [selectedPatient,  setSelectedPatient]  = useState(null);

  return (
    <>
      {isAdmin ? (
        <button className="btn btn-primary" onClick={() => setShowChat(true)} style={{ marginRight: '8px' }}>
          <i className="fas fa-headset" style={{ marginRight: '8px' }} />
          Support Center
        </button>
      ) : (
        <button
          className="btn btn-primary"
          onClick={() => setShowChat(true)}
          style={{ position: 'fixed', bottom: '20px', right: '20px', borderRadius: '50px', padding: '12px 20px' }}
        >
          <i className="fas fa-life-ring" style={{ marginRight: '8px' }} />
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