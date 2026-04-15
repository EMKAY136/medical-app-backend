// admin-websocket-v3.js — Production-grade WebSocket manager
// ─────────────────────────────────────────────────────────────
// KEY FIXES vs previous version:
//  1. Badge is NEVER incremented by WS events — only synced from loadConversations()
//     result via 'supportConversationsLoaded' CustomEvent. Eliminates phantom counts
//     on reconnect and stale-ticket ghost badges.
//  2. Dedup set persisted in sessionStorage — reconnects can't replay events.
//  3. SESSION_ENDED clears the user's dedup entry so future real requests show up.
//  4. _getClearedIds() shared logic extracted to avoid sessionStorage read repetition.
//  5. All support WS events dispatch CustomEvents only — badge update is the
//     dashboard's responsibility after consulting the DB, not the WS client's.

const AdminWebSocket = (() => {

    // ── Private state ─────────────────────────────────────────────────────────
    const SHOWN_EVENTS_KEY   = 'admin_shown_support_events';
    const CLEARED_IDS_KEY    = 'admin_cleared_chat_sessions';
    const MAX_DEDUP_ENTRIES  = 150;

    let stompClient        = null;
    let connected          = false;
    let reconnectAttempts  = 0;
    let reconnectTimer     = null;
    const MAX_RECONNECTS   = 8;

    let _onNewPatientMessage = null;
    let _onNewAgentMessage   = null;

    // ── sessionStorage helpers ────────────────────────────────────────────────
    function ssGet(key, fallback) {
        try {
            const raw = sessionStorage.getItem(key);
            return raw ? JSON.parse(raw) : fallback;
        } catch { return fallback; }
    }

    function ssSet(key, value) {
        try { sessionStorage.setItem(key, JSON.stringify(value)); } catch {}
    }

    // ── Cleared-user IDs ──────────────────────────────────────────────────────
    function getClearedIds() {
        return new Set((ssGet(CLEARED_IDS_KEY, [])).map(Number));
    }

    function addClearedId(userId) {
        const arr = ssGet(CLEARED_IDS_KEY, []);
        if (!arr.includes(Number(userId))) arr.push(Number(userId));
        ssSet(CLEARED_IDS_KEY, arr);
    }

    // ── Dedup helpers (sessionStorage-backed) ─────────────────────────────────
    function getShownEvents() {
        return new Set(ssGet(SHOWN_EVENTS_KEY, []));
    }

    function hasShownEvent(key) {
        return getShownEvents().has(key);
    }

    function addShownEvent(key) {
        const arr = ssGet(SHOWN_EVENTS_KEY, []);
        if (!arr.includes(key)) {
            arr.push(key);
            // Trim to avoid unbounded growth
            if (arr.length > MAX_DEDUP_ENTRIES) arr.splice(0, arr.length - MAX_DEDUP_ENTRIES);
            ssSet(SHOWN_EVENTS_KEY, arr);
        }
    }

    function clearShownEventsForUser(userId) {
        if (!userId) return;
        const arr = ssGet(SHOWN_EVENTS_KEY, []);
        const filtered = arr.filter(k => !k.endsWith(':' + userId));
        ssSet(SHOWN_EVENTS_KEY, filtered);
    }

    // ── Browser notification ──────────────────────────────────────────────────
    function showBrowserNotification(title, body) {
        if (Notification.permission === 'default') {
            Notification.requestPermission();
            return;
        }
        if (Notification.permission === 'granted') {
            try {
                new Notification(title, {
                    body,
                    icon:               '/favicon.ico',
                    badge:              '/favicon.ico',
                    requireInteraction: false,
                    tag:                'qualitest-support',   // collapses duplicate toasts
                });
            } catch {}
        }
    }

    // ── Handle /topic/admin/new-message ───────────────────────────────────────
    // Covers NEW_PATIENT_MESSAGE and SESSION_ENDED
    function handleAdminNewMessage(data) {
        const event  = (data.event || '').toUpperCase();
        const userId = Number(data.userId);

        // ── SESSION_ENDED ────────────────────────────────────────────────────
        if (event === 'SESSION_ENDED') {
            console.log('[WS] 🔴 SESSION_ENDED for userId:', userId);

            // Persist as cleared — prevents WS ghost messages after session ends
            addClearedId(userId);
            clearShownEventsForUser(userId);

            // Tell ChatSupportModal and dashboard to drop the row immediately
            window.dispatchEvent(new CustomEvent('patientSessionEnded', {
                detail: { userId, timestamp: data.timestamp },
            }));

            // Authoritative badge sync — fire loadConversations via dashboard
            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        // ── NEW_PATIENT_MESSAGE ──────────────────────────────────────────────
        if (event === 'NEW_PATIENT_MESSAGE') {
            // Skip cleared patients
            if (getClearedIds().has(userId)) {
                console.log('[WS] ⏭️ Skipping message for cleared userId:', userId);
                return;
            }

            console.log('[WS] 💬 New patient message — userId:', userId,
                        '| sender:', data.senderName || data.userName,
                        '| msg:', (data.message || '').substring(0, 60));

            // Fire user-facing callback (ChatSupportModal sidebar refresh)
            if (typeof _onNewPatientMessage === 'function') {
                _onNewPatientMessage(data);
            }

            window.dispatchEvent(new CustomEvent('newPatientChatMessage', { detail: data }));

            // Browser notification
            showBrowserNotification(
                `💬 ${data.userName || data.senderName || 'Patient'}`,
                data.message || 'New support message'
            );

            // Badge sync: ask dashboard to re-query DB — NOT increment a counter
            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        // Other events on this topic
        window.dispatchEvent(new CustomEvent('adminChatEvent', { detail: data }));
    }

    // ── Handle /topic/admin/support (ticket events) ───────────────────────────
    function handleSupportEvent(data) {
        const event  = (data.event || '').toUpperCase();
        const userId = Number(data.userId || 0);

        console.log('[WS] 🎫 Support event:', event,
                    '| ticketId:', data.ticketId,
                    '| userId:', userId);

        // Terminal events — clear dedup so future requests from same user show up
        if (['SESSION_ENDED', 'TICKET_RESOLVED', 'TICKET_CLOSED'].includes(event)) {
            clearShownEventsForUser(userId);
            if (event === 'SESSION_ENDED') addClearedId(userId);
            window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));
            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        // Dedup key uses ticketId when available (prevents false dedup across users)
        const dedupeKey = event + ':' + (data.ticketId || userId || 'unknown');

        if (hasShownEvent(dedupeKey)) {
            console.log('[WS] ⏭️ Duplicate support event suppressed:', dedupeKey);
            return;
        }
        addShownEvent(dedupeKey);

        window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

        if (event === 'NEW_TICKET' || event === 'LIVE_AGENT_NEEDED') {
            // Skip cleared users
            if (getClearedIds().has(userId)) return;

            showBrowserNotification(
                '🚨 New Support Ticket',
                `${data.userName || 'A patient'} opened a new support ticket`
            );

            // Badge sync: ask dashboard to re-query DB
            // NO incrementing of any counter here — badge comes from DB only
            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
        }
    }

    // ── Generic notification handler (appointments, patients) ─────────────────
    function handleNotification(notification) {
        showBrowserNotification(
            notification.title   || 'New Notification',
            notification.message || 'You have a new notification'
        );
        window.dispatchEvent(new CustomEvent('adminNotification', { detail: notification }));
        // These are non-support events — let dashboard decide what to refresh
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    }

    // ── Connect ───────────────────────────────────────────────────────────────
    function connect() {
        const token = localStorage.getItem('authToken');
        if (!token) {
            console.log('[WS] No auth token — skipping connection');
            return;
        }
        if (connected) {
            console.log('[WS] Already connected');
            return;
        }

        // Build URL
        let wsUrl = (CONFIG.WS_URL || CONFIG.API_BASE_URL || '')
            .replace(/^wss:\/\//i, 'https://')
            .replace(/^ws:\/\//i,  'http://');
        if (!wsUrl.startsWith('http://') && !wsUrl.startsWith('https://')) {
            wsUrl = 'https://' + wsUrl;
        }
        wsUrl = wsUrl.replace(/\/$/, '');
        if (!wsUrl.endsWith('/ws')) wsUrl += '/ws';
        wsUrl += '?token=' + token;

        console.log('[WS] Connecting to', wsUrl);

        try {
            const socket     = new SockJS(wsUrl);
            stompClient      = Stomp.over(socket);
            stompClient.debug = () => {};  // silence STOMP frame noise

            stompClient.connect(
                { Authorization: `Bearer ${token}` },

                // ── Connected ─────────────────────────────────────────────
                () => {
                    console.log('[WS] ✅ Connected');
                    connected         = true;
                    reconnectAttempts = 0;
                    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }

                    // Appointments
                    stompClient.subscribe('/topic/admin/appointments', (msg) => {
                        try { handleNotification(JSON.parse(msg.body)); } catch {}
                    });

                    // Patients
                    stompClient.subscribe('/topic/admin/patients', (msg) => {
                        try { handleNotification(JSON.parse(msg.body)); } catch {}
                    });

                    // Chat messages + SESSION_ENDED
                    stompClient.subscribe('/topic/admin/new-message', (msg) => {
                        try { handleAdminNewMessage(JSON.parse(msg.body)); } catch (e) {
                            console.error('[WS] Parse error on new-message:', e);
                        }
                    });

                    // Ticket events
                    stompClient.subscribe('/topic/admin/support', (msg) => {
                        try { handleSupportEvent(JSON.parse(msg.body)); } catch (e) {
                            console.error('[WS] Parse error on support:', e);
                        }
                    });

                    console.log('[WS] ✅ Subscribed to all admin topics');
                    window.dispatchEvent(new CustomEvent('websocketConnected'));
                },

                // ── Error / disconnect ────────────────────────────────────
                (error) => {
                    console.warn('[WS] ❌ Disconnected:', error);
                    connected = false;
                    scheduleReconnect();
                }
            );
        } catch (err) {
            console.error('[WS] ❌ Setup error:', err);
            scheduleReconnect();
        }
    }

    function scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECTS) {
            console.error('[WS] Max reconnects reached. Refresh the page to retry.');
            return;
        }
        reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
        console.log(`[WS] Retrying in ${delay / 1000}s (attempt ${reconnectAttempts}/${MAX_RECONNECTS})`);
        reconnectTimer = setTimeout(() => {
            stompClient = null;
            connect();
        }, delay);
    }

    function disconnect() {
        if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
        if (stompClient && connected) {
            try {
                stompClient.disconnect(() => {
                    console.log('[WS] Disconnected cleanly');
                });
            } catch {}
        }
        connected     = false;
        stompClient   = null;
        reconnectAttempts = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────────
    return {
        connect,
        disconnect,
        isConnected:    () => connected,
        getClearedIds,
        addClearedId,

        registerChatCallbacks({ onNewPatientMessage, onNewAgentMessage } = {}) {
            if (onNewPatientMessage) _onNewPatientMessage = onNewPatientMessage;
            if (onNewAgentMessage)   _onNewAgentMessage   = onNewAgentMessage;
        },
        unregisterChatCallbacks() {
            _onNewPatientMessage = null;
            _onNewAgentMessage   = null;
        },
    };
})();

window.AdminWebSocket = AdminWebSocket;

// ── Auto-connect ──────────────────────────────────────────────────────────────
window.addEventListener('load', () => {
    if (localStorage.getItem('authToken')) {
        setTimeout(() => AdminWebSocket.connect(), 800);
    }
});

window.addEventListener('userAuthenticated', () => {
    if (!AdminWebSocket.isConnected()) AdminWebSocket.connect();
});

window.addEventListener('userLoggedOut', () => AdminWebSocket.disconnect());

// Reconnect on tab visibility — but ONLY if truly disconnected
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && localStorage.getItem('authToken') && !AdminWebSocket.isConnected()) {
        console.log('[WS] Tab visible — reconnecting...');
        AdminWebSocket.connect();
    }
});