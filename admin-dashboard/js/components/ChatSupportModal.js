// ChatSupportModal.js — Admin & Patient support chat
//
// Fixes vs previous version:
//  1. Listens for 'supportSessionEnded' DOM event (fired by admin-websocket-v2.js)
//     so cleared sessions disappear from the sidebar in real-time without a poll cycle.
//  2. clearedUserIds stored in sessionStorage (survives same-tab reload).
//  3. loadConversations always filters against persisted cleared set.
//  4. WebSocket new-message handler skips cleared users.
//  5. senderName display no longer shows "undefined" — falls back to userName.

const { useState, useEffect, useRef } = React;

// ── Persistent cleared-user storage ──────────────────────────────────────────
const ClearedSessions = {
    _KEY: 'admin_cleared_chat_sessions',

    load: function () {
        try { return new Set(JSON.parse(sessionStorage.getItem(this._KEY) || '[]')); }
        catch (e) { return new Set(); }
    },

    save: function (set) {
        try { sessionStorage.setItem(this._KEY, JSON.stringify([...set])); } catch (e) {}
    },

    add: function (userId) {
        const s = this.load();
        s.add(Number(userId));
        this.save(s);
        return s;
    },

    has: function (userId) { return this.load().has(Number(userId)); },

    clear: function () { sessionStorage.removeItem(this._KEY); },
};

// ─────────────────────────────────────────────────────────────────────────────
const ChatSupportModal = ({ onClose, isAdmin = false, currentUser, selectedPatient = null }) => {

    // ── State ─────────────────────────────────────────────────────────────────
    const [conversations,      setConversations]      = useState([]);
    const [activeConversation, setActiveConversation] = useState(null);
    const [messages,           setMessages]           = useState([]);
    const [newMessage,         setNewMessage]         = useState('');
    const [loading,            setLoading]            = useState(false);
    const [searchQuery,        setSearchQuery]        = useState('');
    const [supportStatus,      setSupportStatus]      = useState(null);
    const [endingSession,      setEndingSession]      = useState(false);
    const [sessionEndError,    setSessionEndError]    = useState(null);
    const [humanRequestCount,  setHumanRequestCount]  = useState(0);
    const [newRequestAlert,    setNewRequestAlert]    = useState(null);

    // ── Refs ──────────────────────────────────────────────────────────────────
    const messagesEndRef  = useRef(null);
    const messageInputRef = useRef(null);
    const activeConvRef   = useRef(null);
    const sendingRef      = useRef(false);
    const alertTimerRef   = useRef(null);
    const prevHumanIdsRef = useRef(new Set());

    // In-memory mirror of sessionStorage — kept in sync by ClearedSessions.add()
    const clearedUserIdsRef = useRef(ClearedSessions.load());

    useEffect(() => { activeConvRef.current = activeConversation; }, [activeConversation]);

    useEffect(() => {
        setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 60);
    }, [messages]);

    const getHeaders = () => ({
        Authorization: 'Bearer ' + localStorage.getItem('authToken'),
        'Content-Type': 'application/json',
    });

    // ── Mount ─────────────────────────────────────────────────────────────────
    useEffect(() => {
        loadSupportStatus();
        if (isAdmin) loadConversations(false);
        else         loadPatientConversation();
    }, [isAdmin]);

    useEffect(() => {
        if (selectedPatient && isAdmin) openPatientConversation(selectedPatient);
    }, [selectedPatient]);

    // ── WebSocket: new patient message ────────────────────────────────────────
    useEffect(() => {
        if (!isAdmin) return;

        const handler = function (e) {
            const data = e.detail || {};
            const uid  = Number(data.userId);

            if (clearedUserIdsRef.current.has(uid)) {
                console.log('[Chat] Skipping WS event for cleared userId:', uid);
                return;
            }

            loadConversations(true);

            const conv = activeConvRef.current;
            if (conv && Number(conv.userId) === uid) loadMessagesForUser(uid, true);
        };

        window.addEventListener('newPatientChatMessage', handler);
        return () => window.removeEventListener('newPatientChatMessage', handler);
    }, [isAdmin]);

    // ── WebSocket: session ended (fired by admin-websocket-v2 → _handleSessionEnded) ──
    // FIX: This listener immediately removes the patient from the sidebar and
    // clears the active conversation if it's the same patient — no poll lag.
    useEffect(() => {
        if (!isAdmin) return;

        const handler = function (e) {
            const data = e.detail || {};
            const uid  = Number(data.userId);
            if (!uid) return;

            console.log('[Chat] SESSION_ENDED received for userId:', uid);

            // Persist cleared state
            const newSet = ClearedSessions.add(uid);
            clearedUserIdsRef.current = newSet;
            prevHumanIdsRef.current.delete(uid);

            // Wipe UI immediately — no need to wait for next poll
            _applyLocalClear(uid);
        };

        window.addEventListener('supportSessionEnded', handler);
        return () => window.removeEventListener('supportSessionEnded', handler);
    }, [isAdmin]);

    // ── Polling every 4 s ─────────────────────────────────────────────────────
    useEffect(() => {
        const interval = setInterval(function () {
            if (isAdmin) {
                loadConversations(true);
                const conv = activeConvRef.current;
                if (conv && conv.userId) loadMessagesForUser(conv.userId, true);
            } else {
                if (activeConvRef.current) loadPatientHistory(true);
            }
        }, 4000);
        return () => clearInterval(interval);
    }, [isAdmin]);

    // ── Support status ────────────────────────────────────────────────────────
    const loadSupportStatus = async function () {
        try {
            const res = await fetch(CONFIG.API_BASE_URL + '/api/support/status', { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            if (data.success) setSupportStatus(data);
        } catch (e) {}
    };

    // ── Admin: load conversation list ─────────────────────────────────────────
    const loadConversations = async function (silent) {
        if (!isAdmin) return;
        try {
            if (!silent) setLoading(true);
            const res = await fetch(CONFIG.ADMIN_API_URL + '/api/support/admin/all-chats', { headers: getHeaders() });
            if (!res.ok) { console.error('[Chat] loadConversations HTTP error', res.status); return; }
            const data = await res.json();
            if (!data.success || !data.chats) return;

            // Re-read from storage so reloads always pick up persisted cleared set
            const cleared = ClearedSessions.load();
            clearedUserIdsRef.current = cleared;

            const mapped = data.chats
                .map(function (chat) {
                    const isHumanRequest =
                        (chat.subject  || '').toLowerCase().includes('human support') ||
                        (chat.category || '').toLowerCase().includes('human support');
                    return {
                        id:              chat.ticketId ? ('ticket_' + chat.ticketId) : ('user_' + chat.userId),
                        userId:          Number(chat.userId),
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
                })
                .filter(function (c) { return !cleared.has(c.userId); });

            mapped.sort(function (a, b) {
                if (a.isHumanRequest && !b.isHumanRequest) return -1;
                if (!a.isHumanRequest && b.isHumanRequest)  return  1;
                return new Date(b.lastMessageTime) - new Date(a.lastMessageTime);
            });

            const humanOnes = mapped.filter(c => c.isHumanRequest);
            setHumanRequestCount(humanOnes.length);

            const brandNew = humanOnes.filter(c => !prevHumanIdsRef.current.has(c.userId));
            if (brandNew.length > 0) showNewRequestAlert(brandNew[0].patientName);
            prevHumanIdsRef.current = new Set(humanOnes.map(c => c.userId));

            setConversations(mapped);
        } catch (e) {
            console.error('[Chat] loadConversations error:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    const showNewRequestAlert = function (patientName) {
        if (alertTimerRef.current) clearTimeout(alertTimerRef.current);
        setNewRequestAlert({ name: patientName });
        if (window.Notification && Notification.permission === 'granted') {
            new Notification('🚨 Live Agent Requested!', { body: patientName + ' needs a live support agent', icon: '/favicon.ico' });
        } else if (window.Notification && Notification.permission !== 'denied') {
            Notification.requestPermission();
        }
        alertTimerRef.current = setTimeout(() => setNewRequestAlert(null), 8000);
    };

    const openPatientConversation = function (patient) {
        const conv = {
            id:           'user_' + patient.id,
            userId:       Number(patient.id),
            patientName:  ((patient.firstName || '') + ' ' + (patient.lastName || '')).trim(),
            patientEmail: patient.email,
            status:       'active',
        };
        setActiveConversation(conv);
        activeConvRef.current = conv;
        setSessionEndError(null);
        loadMessagesForUser(patient.id, false);
    };

    const selectConversation = function (conv) {
        setActiveConversation(conv);
        activeConvRef.current = conv;
        setSessionEndError(null);
        setMessages([]);
        loadMessagesForUser(conv.userId, false);
    };

    // ── Admin: fetch messages for a patient ───────────────────────────────────
    const loadMessagesForUser = async function (userId, silent) {
        if (!userId) return;
        if (clearedUserIdsRef.current.has(Number(userId))) return;
        try {
            if (!silent) setLoading(true);
            const res = await fetch(CONFIG.ADMIN_API_URL + '/api/support/admin/chat/' + userId, { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            if (!data.success) return;

            const formatted = (data.messages || [])
                .filter(m => m.senderType !== 'SYSTEM')
                .map(m => ({
                    id:         m.id,
                    senderId:   isAgentSenderType(m.senderType) ? 'admin' : userId,
                    // FIX: was showing "undefined" — now falls back to userName from userInfo
                    senderName: m.senderName || (data.user && data.user.name) || '',
                    senderType: m.senderType,
                    message:    m.message,
                    timestamp:  new Date(m.timestamp || m.createdAt),
                    status:     m.isRead ? 'read' : 'delivered',
                }));
            setMessages(formatted);

            if (data.user) {
                setActiveConversation(prev => prev ? ({
                    ...prev,
                    patientName:  data.user.name || ((data.user.firstName || '') + ' ' + (data.user.lastName || '')).trim(),
                    patientEmail: data.user.email,
                    patientPhone: data.user.phoneNumber || data.user.phone || '',
                }) : prev);
            }
        } catch (e) {
            console.error('[Chat] loadMessagesForUser error:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    // ── Patient: init conversation ────────────────────────────────────────────
    const loadPatientConversation = function () {
        const conv = {
            id:           'patient_chat',
            userId:       currentUser && currentUser.id,
            patientName:  currentUser ? ((currentUser.firstName || '') + ' ' + (currentUser.lastName || '')).trim() : '',
            patientEmail: currentUser && currentUser.email,
            status:       'active',
        };
        setActiveConversation(conv);
        activeConvRef.current = conv;
        loadPatientHistory(false);
    };

    const loadPatientHistory = async function (silent) {
        try {
            if (!silent) setLoading(true);
            const res = await fetch(CONFIG.API_BASE_URL + '/api/support/chat/history', { headers: getHeaders() });
            if (!res.ok) return;
            const data = await res.json();
            if (!data.success) return;
            setMessages(
                (data.messages || [])
                    .filter(m => m.senderType !== 'SYSTEM')
                    .map(m => ({
                        id:         m.id,
                        senderId:   isAgentSenderType(m.senderType) ? 'admin' : (currentUser && currentUser.id),
                        senderName: m.senderName || '',
                        senderType: m.senderType,
                        message:    m.message,
                        timestamp:  new Date(m.timestamp || m.createdAt),
                    }))
            );
        } catch (e) {
            console.error('[Chat] loadPatientHistory error:', e);
        } finally {
            if (!silent) setLoading(false);
        }
    };

    const isAgentSenderType = t => { const s = (t || '').toLowerCase(); return s === 'support_agent' || s === 'admin'; };

    // ── End session (admin) ───────────────────────────────────────────────────
    // Order of operations:
    //  1. Persist userId to sessionStorage IMMEDIATELY
    //  2. Wipe local UI
    //  3. Call backend (best-effort)
    const endSessionAndClear = async function (userId, patientName) {
        if (!window.confirm(
            'End chat session with ' + (patientName || 'this patient') + '?\n\n' +
            'All messages will be permanently deleted on both sides. This cannot be undone.'
        )) return;

        setEndingSession(true);
        setSessionEndError(null);

        const uid = Number(userId);

        // 1. Persist + mirror immediately
        const newSet = ClearedSessions.add(uid);
        clearedUserIdsRef.current = newSet;
        prevHumanIdsRef.current.delete(uid);

        // 2. Wipe UI
        _applyLocalClear(uid);

        // 3. Backend call
        try {
            const res = await fetch(
                CONFIG.ADMIN_API_URL + '/api/support/admin/end-session/' + uid,
                { method: 'POST', headers: getHeaders() }
            );

            if (res.ok) {
                const d = await res.json().catch(() => ({}));
                const deleted  = d.deletedMessages ?? '?';
                const resolved = d.resolvedTickets  ?? '?';
                window.showNotificationAlert && window.showNotificationAlert(
                    `Session ended ✅ — ${deleted} messages deleted, ${resolved} ticket(s) resolved`
                );
                console.log(`[Chat] ✅ Backend cleared: ${deleted} messages, ${resolved} tickets for user ${uid}`);
            } else {
                const statusMsg = {
                    404: 'Backend endpoint missing — redeploy to also wipe patient messages',
                    403: '403 — add POST /api/support/admin/end-session/** to SecurityConfig',
                }[res.status] || ('Server error ' + res.status);
                console.warn('[Chat] end-session backend:', statusMsg);
                window.showNotificationAlert && window.showNotificationAlert('Cleared from view ✅ (' + statusMsg + ')');
            }
        } catch (netErr) {
            console.error('[Chat] end-session network error:', netErr.message);
            window.showNotificationAlert && window.showNotificationAlert(
                'Cleared from view ✅ (network error — reload to confirm backend deletion)'
            );
        } finally {
            setEndingSession(false);
        }
    };

    const _applyLocalClear = function (userId) {
        setMessages([]);
        setConversations(prev => prev.filter(c => Number(c.userId) !== Number(userId)));
        setActiveConversation(null);
        activeConvRef.current = null;
        setSessionEndError(null);
    };

    // ── Send message ──────────────────────────────────────────────────────────
    const sendMessage = async function () {
        if (!newMessage.trim() || sendingRef.current) return;
        const text = newMessage.trim();
        setNewMessage('');
        sendingRef.current = true;

        const tempId     = 'temp_' + Date.now();
        const senderName = isAdmin
            ? (currentUser && (currentUser.name || currentUser.firstName)) || 'Medical Support'
            : currentUser ? ((currentUser.firstName || '') + ' ' + (currentUser.lastName || '')).trim() : '';

        setMessages(prev => [...prev, {
            id: tempId, senderId: currentUser && currentUser.id,
            senderName, senderType: isAdmin ? 'SUPPORT_AGENT' : 'USER',
            message: text, timestamp: new Date(), status: 'sending',
        }]);

        try {
            let res;
            if (isAdmin) {
                const conv = activeConvRef.current;
                if (!conv || !conv.userId) throw new Error('No patient selected');
                res = await fetch(CONFIG.ADMIN_API_URL + '/api/support/admin/reply', {
                    method:  'POST',
                    headers: getHeaders(),
                    body:    JSON.stringify({ userId: conv.userId, message: text, ticketId: conv.ticketId || null }),
                });
            } else {
                res = await fetch(CONFIG.API_BASE_URL + '/api/support/chat/message', {
                    method:  'POST',
                    headers: getHeaders(),
                    body:    JSON.stringify({ message: text }),
                });
            }

            if (!res.ok) throw new Error('Server error ' + res.status);
            const data = await res.json();

            if (data.success) {
                setMessages(prev => prev.map(m =>
                    m.id === tempId ? { ...m, status: 'delivered', id: data.messageId || m.id } : m
                ));
                if (!isAdmin && data.botResponse) {
                    setTimeout(() => {
                        setMessages(prev => [...prev, {
                            id: 'bot_' + Date.now(), senderId: 'bot',
                            senderName: 'Medical Support Bot', senderType: 'BOT',
                            message: data.botResponse, timestamp: new Date(),
                        }]);
                    }, 400);
                }
                const activeConv = activeConvRef.current;
                setTimeout(() => {
                    if (isAdmin && activeConv && activeConv.userId) loadMessagesForUser(activeConv.userId, true);
                    else if (!isAdmin) loadPatientHistory(true);
                }, 500);
            } else {
                throw new Error(data.message || 'Send failed');
            }
        } catch (e) {
            console.error('[Chat] sendMessage error:', e);
            setMessages(prev => prev.filter(m => m.id !== tempId));
            alert('Failed to send: ' + e.message);
        } finally {
            sendingRef.current = false;
            setTimeout(() => messageInputRef.current && messageInputRef.current.focus(), 50);
        }
    };

    const handleKeyPress = e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } };

    // ── Helpers ───────────────────────────────────────────────────────────────
    const formatTime = t => {
        const now = new Date(), d = new Date(t);
        const h = (now - d) / 3600000;
        if (h < 1)  return 'Just now';
        if (h < 24) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
    };

    const isMyMessage = msg => {
        const t = (msg.senderType || '').toLowerCase();
        return isAdmin ? (t === 'support_agent' || t === 'admin') : t === 'user';
    };

    const getBubbleBg = msg => {
        if (isMyMessage(msg)) return '#3b82f6';
        if ((msg.senderType || '').toLowerCase() === 'bot') return '#10b981';
        return 'white';
    };

    const filteredConversations = conversations.filter(c => {
        const q = searchQuery.toLowerCase();
        return (c.patientName  || '').toLowerCase().includes(q) ||
               (c.patientEmail || '').toLowerCase().includes(q) ||
               (c.lastMessage  || '').toLowerCase().includes(q);
    });

    // ── RENDER ────────────────────────────────────────────────────────────────
    return React.createElement('div', { className: 'modal', style: { zIndex: 1000 } },
        React.createElement('div', {
            className: 'modal-content',
            style: {
                maxWidth: isAdmin ? '1100px' : '680px', height: '85vh',
                display: 'flex', flexDirection: 'column',
                overflow: 'hidden', padding: 0, borderRadius: '12px', position: 'relative',
            },
        },

            // ── New agent request alert ───────────────────────────────────────
            newRequestAlert && React.createElement('div', {
                style: {
                    position: 'absolute', top: '12px', left: '50%', transform: 'translateX(-50%)',
                    zIndex: 2000, background: '#ef4444', color: 'white',
                    padding: '10px 20px', borderRadius: '8px', boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
                    display: 'flex', alignItems: 'center', gap: '10px',
                    fontSize: '13px', fontWeight: '700', whiteSpace: 'nowrap',
                },
            },
                React.createElement('span', null, '🚨'),
                React.createElement('span', null, newRequestAlert.name + ' requested a LIVE AGENT'),
                React.createElement('button', {
                    onClick: () => setNewRequestAlert(null),
                    style: { background: 'none', border: 'none', color: 'white', cursor: 'pointer', fontSize: '16px' },
                }, '✕')
            ),

            // ── Header ────────────────────────────────────────────────────────
            React.createElement('div', {
                style: {
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    padding: '14px 20px', backgroundColor: 'white',
                    borderBottom: '1px solid #e5e7eb', flexShrink: 0,
                },
            },
                React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: '12px' } },
                    React.createElement('h2', { style: { margin: 0, fontSize: '18px', fontWeight: '700' } },
                        isAdmin ? '💬 Patient Support Chat' : '🏥 Medical Support'
                    ),
                    isAdmin && humanRequestCount > 0 && React.createElement('span', {
                        style: {
                            backgroundColor: '#ef4444', color: 'white', borderRadius: '12px',
                            padding: '2px 10px', fontSize: '12px', fontWeight: '700',
                            animation: 'pulse 1.5s infinite',
                        },
                    }, '🚨 ' + humanRequestCount + ' LIVE request' + (humanRequestCount > 1 ? 's' : '')),
                    !isAdmin && supportStatus && React.createElement('span', {
                        style: { fontSize: '12px', color: supportStatus.isOnline ? '#10b981' : '#f59e0b' },
                    }, '● ' + (supportStatus.isOnline
                        ? 'Online · ' + (supportStatus.estimatedResponseTime || 'Fast replies')
                        : "Offline — we'll reply soon"))
                ),
                React.createElement('button', {
                    onClick: onClose,
                    style: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '20px', color: '#6b7280', padding: '4px 8px' },
                }, '✕')
            ),

            // ── Body ──────────────────────────────────────────────────────────
            React.createElement('div', { style: { display: 'flex', flex: 1, overflow: 'hidden' } },

                // Admin sidebar
                isAdmin && React.createElement('div', {
                    style: {
                        width: '290px', flexShrink: 0, borderRight: '1px solid #e5e7eb',
                        display: 'flex', flexDirection: 'column', backgroundColor: '#f9fafb',
                    },
                },
                    React.createElement('div', { style: { padding: '10px 12px', borderBottom: '1px solid #e5e7eb' } },
                        React.createElement('input', {
                            type: 'text', placeholder: '🔍 Search patients...',
                            value: searchQuery,
                            onChange: e => setSearchQuery(e.target.value),
                            style: {
                                width: '100%', padding: '7px 12px', border: '1px solid #d1d5db',
                                borderRadius: '6px', fontSize: '13px', boxSizing: 'border-box', outline: 'none',
                            },
                        })
                    ),
                    React.createElement('div', { style: { flex: 1, overflowY: 'auto' } },
                        loading && conversations.length === 0
                            ? React.createElement('div', { style: { padding: '24px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' } }, 'Loading conversations...')
                            : filteredConversations.length === 0
                                ? React.createElement('div', { style: { padding: '24px', textAlign: 'center', color: '#9ca3af', fontSize: '13px' } },
                                    'No active conversations.',
                                    React.createElement('br'),
                                    React.createElement('small', { style: { color: '#c4c4c4' } }, 'Patients appear here when they send a message.')
                                )
                                : filteredConversations.map(conv => {
                                    const isActive = activeConversation && activeConversation.id === conv.id;
                                    return React.createElement('div', {
                                        key: conv.id,
                                        onClick: () => selectConversation(conv),
                                        style: {
                                            padding: '11px 14px', cursor: 'pointer',
                                            borderBottom: '1px solid #e5e7eb',
                                            backgroundColor: isActive ? '#dbeafe' : conv.isHumanRequest ? '#fff7ed' : 'transparent',
                                            transition: 'background-color 0.1s',
                                        },
                                    },
                                        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' } },
                                            React.createElement('div', { style: { flex: 1, minWidth: 0 } },
                                                React.createElement('div', { style: { fontWeight: conv.isHumanRequest ? '700' : '500', fontSize: '13px', marginBottom: '2px' } }, conv.patientName),
                                                conv.ticketNumber && React.createElement('div', { style: { fontSize: '10px', color: '#3b82f6', fontWeight: '700', marginBottom: '2px' } }, '#' + conv.ticketNumber),
                                                conv.isHumanRequest && React.createElement('div', {
                                                    style: { display: 'inline-block', backgroundColor: '#ef4444', color: 'white', fontSize: '9px', fontWeight: '800', padding: '2px 7px', borderRadius: '4px', marginBottom: '3px' },
                                                }, '🚨 LIVE AGENT NEEDED'),
                                                React.createElement('div', { style: { fontSize: '11px', color: '#6b7280', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, conv.lastMessage),
                                                React.createElement('div', { style: { fontSize: '10px', color: '#9ca3af', marginTop: '2px' } }, formatTime(conv.lastMessageTime))
                                            ),
                                            React.createElement('div', {
                                                style: {
                                                    width: '9px', height: '9px', borderRadius: '50%',
                                                    marginLeft: '8px', marginTop: '4px', flexShrink: 0,
                                                    backgroundColor: conv.isHumanRequest ? '#ef4444' : '#10b981',
                                                },
                                            })
                                        )
                                    );
                                })
                    )
                ),

                // ── Chat area ─────────────────────────────────────────────────
                React.createElement('div', { style: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 } },
                    activeConversation ? React.createElement(React.Fragment, null,

                        // Sub-header
                        React.createElement('div', {
                            style: {
                                padding: '10px 16px', borderBottom: '1px solid #e5e7eb',
                                backgroundColor: isAdmin ? '#f0f9ff' : 'white',
                                flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                            },
                        },
                            React.createElement('div', null,
                                React.createElement('div', { style: { fontWeight: '600', fontSize: '14px' } },
                                    isAdmin ? activeConversation.patientName : 'Medical Support Team'
                                ),
                                React.createElement('div', { style: { fontSize: '11px', color: '#6b7280' } },
                                    isAdmin
                                        ? [activeConversation.patientEmail, activeConversation.patientPhone].filter(Boolean).join(' · ')
                                        : 'Qualitest Medical · info@qualitestmedical.com'
                                )
                            ),
                            isAdmin && React.createElement('div', { style: { display: 'flex', gap: '8px', alignItems: 'center' } },
                                React.createElement('button', {
                                    onClick: () => loadMessagesForUser(activeConversation.userId, false),
                                    style: {
                                        padding: '5px 10px', fontSize: '11px', background: '#f3f4f6',
                                        border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer', fontWeight: '600',
                                    },
                                }, '🔄 Refresh'),
                                React.createElement('button', {
                                    onClick:  () => endSessionAndClear(activeConversation.userId, activeConversation.patientName),
                                    disabled: endingSession,
                                    style: {
                                        padding: '5px 12px', fontSize: '11px', fontWeight: '700',
                                        backgroundColor: endingSession ? '#9ca3af' : '#ef4444',
                                        color: 'white', border: 'none', borderRadius: '6px',
                                        cursor: endingSession ? 'not-allowed' : 'pointer',
                                        opacity: endingSession ? 0.7 : 1,
                                        display: 'flex', alignItems: 'center', gap: '5px',
                                    },
                                },
                                    endingSession
                                        ? React.createElement(React.Fragment, null,
                                            React.createElement('span', {
                                                style: {
                                                    display: 'inline-block', width: '10px', height: '10px',
                                                    border: '2px solid white', borderTopColor: 'transparent',
                                                    borderRadius: '50%', animation: 'spin 0.7s linear infinite',
                                                },
                                            }),
                                            ' Ending...'
                                        )
                                        : '🗑 End & Clear'
                                )
                            )
                        ),

                        // Error banner
                        sessionEndError && React.createElement('div', {
                            style: {
                                padding: '8px 16px', background: '#fef2f2', borderBottom: '1px solid #fecaca',
                                fontSize: '12px', color: '#dc2626',
                                display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0,
                            },
                        },
                            React.createElement('span', null, '⚠️ ' + sessionEndError),
                            React.createElement('button', {
                                onClick: () => setSessionEndError(null),
                                style: { background: 'none', border: 'none', cursor: 'pointer', color: '#dc2626', fontSize: '14px', padding: '0 4px' },
                            }, '✕')
                        ),

                        // Messages
                        React.createElement('div', {
                            style: { flex: 1, overflowY: 'auto', padding: '16px', backgroundColor: '#f9fafb' },
                        },
                            loading && messages.length === 0
                                ? React.createElement('div', { style: { textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' } }, 'Loading messages...')
                                : messages.length === 0
                                    ? React.createElement('div', { style: { textAlign: 'center', color: '#9ca3af', marginTop: '40px', fontSize: '14px' } },
                                        isAdmin ? 'No messages from this patient yet.' : 'No messages yet. Start the conversation!'
                                    )
                                    : messages.map(msg => {
                                        const mine    = isMyMessage(msg);
                                        const bgColor = getBubbleBg(msg);
                                        const isBot   = (msg.senderType || '').toLowerCase() === 'bot';
                                        return React.createElement('div', {
                                            key: msg.id,
                                            style: { display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: '10px' },
                                        },
                                            React.createElement('div', {
                                                style: {
                                                    maxWidth: '75%', padding: '10px 14px', borderRadius: '16px',
                                                    backgroundColor: bgColor,
                                                    color: (mine || isBot) ? 'white' : '#1f2937',
                                                    boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
                                                    borderBottomRightRadius: mine ? '4px' : '16px',
                                                    borderBottomLeftRadius:  mine ? '16px' : '4px',
                                                },
                                            },
                                                !mine && msg.senderName && React.createElement('div', {
                                                    style: { fontSize: '10px', fontWeight: '700', marginBottom: '3px', opacity: 0.7 },
                                                }, msg.senderName + (isBot ? ' 🤖' : isAgentSenderType(msg.senderType) ? ' 🟢' : '')),
                                                React.createElement('div', { style: { fontSize: '13px', lineHeight: '1.5', whiteSpace: 'pre-wrap' } }, msg.message),
                                                React.createElement('div', {
                                                    style: {
                                                        fontSize: '10px', opacity: 0.6, marginTop: '4px',
                                                        display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '3px',
                                                    },
                                                },
                                                    formatTime(msg.timestamp),
                                                    mine && React.createElement('span', null,
                                                        msg.status === 'sending' ? '🕐' : msg.status === 'read' ? '✓✓' : '✓'
                                                    )
                                                )
                                            )
                                        );
                                    }),
                            React.createElement('div', { ref: messagesEndRef })
                        ),

                        // Input
                        React.createElement('div', {
                            style: { padding: '12px 16px', borderTop: '1px solid #e5e7eb', backgroundColor: 'white', flexShrink: 0 },
                        },
                            React.createElement('div', { style: { display: 'flex', gap: '8px', alignItems: 'flex-end' } },
                                React.createElement('textarea', {
                                    ref:        messageInputRef,
                                    value:      newMessage,
                                    onChange:   e => setNewMessage(e.target.value),
                                    onKeyPress: handleKeyPress,
                                    placeholder: isAdmin
                                        ? 'Reply to ' + activeConversation.patientName + '... (Enter to send)'
                                        : 'Type your message...',
                                    rows: 1,
                                    style: {
                                        flex: 1, padding: '10px 14px', border: '1px solid #d1d5db',
                                        borderRadius: '20px', resize: 'none', fontSize: '13px',
                                        minHeight: '42px', maxHeight: '110px', fontFamily: 'inherit',
                                        outline: 'none', boxSizing: 'border-box',
                                    },
                                }),
                                React.createElement('button', {
                                    onClick:  sendMessage,
                                    disabled: !newMessage.trim(),
                                    style: {
                                        padding: '0 18px', height: '42px',
                                        backgroundColor: newMessage.trim() ? '#3b82f6' : '#d1d5db',
                                        color: 'white', border: 'none', borderRadius: '20px',
                                        cursor: newMessage.trim() ? 'pointer' : 'not-allowed',
                                        display: 'flex', alignItems: 'center', gap: '6px',
                                        fontSize: '13px', fontWeight: '600', whiteSpace: 'nowrap',
                                    },
                                },
                                    'Send ',
                                    React.createElement('i', { className: 'fas fa-paper-plane', style: { fontSize: '11px' } })
                                )
                            ),
                            isAdmin && React.createElement('div', {
                                style: { fontSize: '10px', color: '#9ca3af', marginTop: '4px', paddingLeft: '4px' },
                            }, 'Enter to send · Shift+Enter for new line · "End & Clear" permanently deletes the session')
                        )

                    ) : (
                        React.createElement('div', {
                            style: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', color: '#9ca3af' },
                        },
                            React.createElement('div', { style: { fontSize: '52px', marginBottom: '16px', opacity: 0.25 } }, '💬'),
                            isAdmin
                                ? React.createElement('div', { style: { textAlign: 'center', maxWidth: '300px' } },
                                    React.createElement('p', { style: { fontWeight: '600', color: '#374151', marginBottom: '6px' } }, 'Select a conversation'),
                                    React.createElement('small', { style: { color: '#9ca3af', lineHeight: '1.6' } },
                                        'Patients appear in the sidebar when they send a message. Rows with 🚨 need immediate attention.')
                                )
                                : React.createElement('div', { style: { textAlign: 'center' } },
                                    React.createElement('p', { style: { color: '#374151' } }, 'Connecting to medical support...'),
                                    React.createElement('small', null, 'Our team is here to help')
                                )
                        )
                    )
                )
            ),

            // Keyframe CSS
            React.createElement('style', null, `
                @keyframes spin  { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
            `)
        )
    );
};

// ── Support Chat Button ───────────────────────────────────────────────────────
const SupportChatButton = function ({ isAdmin, currentUser, patients }) {
    const [showChat,        setShowChat]        = useState(false);
    const [selectedPatient, setSelectedPatient] = useState(null);

    return React.createElement(React.Fragment, null,
        isAdmin
            ? React.createElement('button', {
                className: 'btn btn-primary',
                onClick: () => setShowChat(true),
                style: { marginRight: '8px' },
            },
                React.createElement('i', { className: 'fas fa-headset', style: { marginRight: '8px' } }),
                'Support Center'
            )
            : React.createElement('button', {
                className: 'btn btn-primary',
                onClick: () => setShowChat(true),
                style: { position: 'fixed', bottom: '20px', right: '20px', borderRadius: '50px', padding: '12px 20px' },
            },
                React.createElement('i', { className: 'fas fa-life-ring', style: { marginRight: '8px' } }),
                'Get Help'
            ),
        showChat && React.createElement(ChatSupportModal, {
            onClose:         () => { setShowChat(false); setSelectedPatient(null); },
            isAdmin,
            currentUser,
            selectedPatient,
        })
    );
};

window.ChatSupportModal  = ChatSupportModal;
window.SupportChatButton = SupportChatButton;