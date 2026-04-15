
const AdminWebSocket = (() => {

    // ── Private state ─────────────────────────────────────────────────────────
    const SHOWN_EVENTS_KEY   = 'admin_shown_support_events';
    const CLEARED_IDS_KEY    = 'admin_cleared_chat_sessions';
    const MAX_DEDUP_ENTRIES  = 150;

    let stompClient        = null;
    let connected          = false;
    let subscribed         = false;   // ← NEW: prevents double-subscribe
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

    // ── NEW: Remove a userId from the cleared set ────────────────────────────
    // Called when a new message or ticket arrives from a previously-cleared
    // patient, indicating they started a fresh session.
    function removeClearedId(userId) {
        const uid = Number(userId);
        const arr = ssGet(CLEARED_IDS_KEY, []).filter(id => Number(id) !== uid);
        ssSet(CLEARED_IDS_KEY, arr);
        console.log('[WS] 🔓 Un-cleared userId:', uid, '(new activity detected)');
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
                    tag:                'qualitest-support',
                });
            } catch {}
        }
    }

    // ── Handle /topic/admin/new-message ───────────────────────────────────────
    function handleAdminNewMessage(data) {
        const event  = (data.event || '').toUpperCase();
        const userId = Number(data.userId);

        // ── SESSION_ENDED ────────────────────────────────────────────────────
        if (event === 'SESSION_ENDED') {
            console.log('[WS] 🔴 SESSION_ENDED for userId:', userId);

            addClearedId(userId);
            clearShownEventsForUser(userId);

            window.dispatchEvent(new CustomEvent('patientSessionEnded', {
                detail: { userId, timestamp: data.timestamp },
            }));

            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        // ── NEW_PATIENT_MESSAGE ──────────────────────────────────────────────
        if (event === 'NEW_PATIENT_MESSAGE') {
            // FIX v2.1: If this patient was previously cleared (session ended),
            // un-clear them — a new message means a new session has started.
            if (getClearedIds().has(userId)) {
                removeClearedId(userId);
                clearShownEventsForUser(userId);
                console.log('[WS] 💬 Previously-cleared userId', userId,
                            'sent a new message — un-cleared and will show in sidebar');
            }

            console.log('[WS] 💬 New patient message — userId:', userId,
                        '| sender:', data.senderName || data.userName,
                        '| msg:', (data.message || '').substring(0, 60));

            if (typeof _onNewPatientMessage === 'function') {
                _onNewPatientMessage(data);
            }

            window.dispatchEvent(new CustomEvent('newPatientChatMessage', { detail: data }));

            showBrowserNotification(
                `💬 ${data.userName || data.senderName || 'Patient'}`,
                data.message || 'New support message'
            );

            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        window.dispatchEvent(new CustomEvent('adminChatEvent', { detail: data }));
    }

    // ── Handle /topic/admin/support (ticket events) ───────────────────────────
    function handleSupportEvent(data) {
        const event  = (data.event || '').toUpperCase();
        const userId = Number(data.userId || 0);

        console.log('[WS] 🎫 Support event:', event,
                    '| ticketId:', data.ticketId,
                    '| userId:', userId);

        // Terminal events
        if (['SESSION_ENDED', 'TICKET_RESOLVED', 'TICKET_CLOSED'].includes(event)) {
            clearShownEventsForUser(userId);
            if (event === 'SESSION_ENDED') addClearedId(userId);
            window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));
            window.dispatchEvent(new CustomEvent('requestConversationRefresh'));
            return;
        }

        // Dedup
        const dedupeKey = event + ':' + (data.ticketId || userId || 'unknown');

        if (hasShownEvent(dedupeKey)) {
            console.log('[WS] ⏭️ Duplicate support event suppressed:', dedupeKey);
            return;
        }
        addShownEvent(dedupeKey);

        window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

        if (event === 'NEW_TICKET' || event === 'LIVE_AGENT_NEEDED') {
            // FIX v2.1: Un-clear the userId — a new ticket means a fresh session
            if (getClearedIds().has(userId)) {
                removeClearedId(userId);
                clearShownEventsForUser(userId);
                console.log('[WS] 🎫 Un-cleared userId', userId, 'due to new ticket/agent request');
            }

            showBrowserNotification(
                '🚨 New Support Ticket',
                `${data.userName || 'A patient'} opened a new support ticket`
            );

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
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    }

    // ── Subscribe to all admin topics ─────────────────────────────────────────
    // FIX v2.1: Extracted into a named function with a `subscribed` guard so
    // subscriptions only happen once per connection, preventing the
    // InvalidStateError that occurred when the callback fired before the
    // transport was fully ready.
    function subscribeAll() {
        if (subscribed || !stompClient || !stompClient.connected) return;
        subscribed = true;

        stompClient.subscribe('/topic/admin/appointments', (msg) => {
            try { handleNotification(JSON.parse(msg.body)); } catch {}
        });

        stompClient.subscribe('/topic/admin/patients', (msg) => {
            try { handleNotification(JSON.parse(msg.body)); } catch {}
        });

        stompClient.subscribe('/topic/admin/new-message', (msg) => {
            try { handleAdminNewMessage(JSON.parse(msg.body)); } catch (e) {
                console.error('[WS] Parse error on new-message:', e);
            }
        });

        stompClient.subscribe('/topic/admin/support', (msg) => {
            try { handleSupportEvent(JSON.parse(msg.body)); } catch (e) {
                console.error('[WS] Parse error on support:', e);
            }
        });

        console.log('[WS] ✅ Subscribed to all admin topics');
        window.dispatchEvent(new CustomEvent('websocketConnected'));
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
            stompClient.debug = () => {};

            // FIX v2.1: Reset subscribed flag before each connection attempt
            subscribed = false;

            stompClient.connect(
                { Authorization: `Bearer ${token}` },

                // ── Connected ─────────────────────────────────────────────
                () => {
                    console.log('[WS] ✅ Connected');
                    connected         = true;
                    reconnectAttempts = 0;
                    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }

                    // FIX v2.1: Small delay to ensure SockJS transport is fully
                    // ready before subscribing — prevents InvalidStateError
                    setTimeout(() => {
                        try {
                            subscribeAll();
                        } catch (e) {
                            console.error('[WS] Subscribe error (will retry on reconnect):', e);
                        }
                    }, 100);
                },

                // ── Error / disconnect ────────────────────────────────────
                (error) => {
                    console.warn('[WS] ❌ Disconnected:', error);
                    connected  = false;
                    subscribed = false;
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
            subscribed  = false;
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
        connected         = false;
        subscribed        = false;
        stompClient       = null;
        reconnectAttempts = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────────
    return {
        connect,
        disconnect,
        isConnected:    () => connected,
        getClearedIds,
        addClearedId,
        removeClearedId,   // ← NEW: exposed so ChatSupportModal can also un-clear

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

document.addEventListener('visibilitychange', () => {
    if (!document.hidden && localStorage.getItem('authToken') && !AdminWebSocket.isConnected()) {
        console.log('[WS] Tab visible — reconnecting...');
        AdminWebSocket.connect();
    }
});
