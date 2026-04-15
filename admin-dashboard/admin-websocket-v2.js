// admin-websocket-v3.js
// Changes vs v2:
//  1. SESSION_ENDED now dispatches a 'patientSessionEnded' CustomEvent carrying userId,
//     so ChatSupportModal can remove the row from the sidebar in real time.
//  2. NEW_PATIENT_MESSAGE skips users whose IDs are in the admin's cleared set
//     (reads from sessionStorage so it survives page reload within the same tab).
//  3. Deduplication key for support events uses ticketId when available.

const AdminWebSocket = {
    stompClient: null,
    connected: false,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,

    _shownSupportEvents: new Set(),

    onNewPatientMessage: null,
    onNewAgentMessage:   null,

    // ── Helper: read cleared IDs from sessionStorage ─────────────────────────
    _getClearedIds: function () {
        try {
            const raw = sessionStorage.getItem('admin_cleared_chat_sessions');
            return raw ? new Set(JSON.parse(raw).map(Number)) : new Set();
        } catch (e) {
            return new Set();
        }
    },

    connect: function () {
        const token = localStorage.getItem('authToken');
        if (!token) {
            console.log('No auth token, skipping WebSocket connection');
            return;
        }

        let wsUrl = CONFIG.WS_URL || CONFIG.API_BASE_URL;
        wsUrl = wsUrl.replace(/^wss:\/\//i, 'https://');
        wsUrl = wsUrl.replace(/^ws:\/\//i,  'http://');
        if (!wsUrl.startsWith('http://') && !wsUrl.startsWith('https://')) {
            wsUrl = 'https://' + wsUrl;
        }
        if (!wsUrl.endsWith('/ws')) {
            wsUrl = wsUrl.replace(/\/$/, '') + '/ws';
        }
        wsUrl = `${wsUrl}?token=${token}`;

        console.log('[WS] Connecting to', wsUrl);

        try {
            const socket      = new SockJS(wsUrl);
            this.stompClient  = Stomp.over(socket);
            this.stompClient.debug = () => {};

            this.stompClient.connect(
                { 'Authorization': `Bearer ${token}` },

                // ── On connect ─────────────────────────────────────────────
                (frame) => {
                    console.log('[WS] ✅ Connected');
                    this.connected          = true;
                    this.reconnectAttempts  = 0;

                    // Appointment / patient topics (existing)
                    this.stompClient.subscribe('/topic/admin/appointments', (msg) => {
                        try { this.handleNotification(JSON.parse(msg.body)); }
                        catch (e) { console.error('Parse error:', e); }
                    });

                    this.stompClient.subscribe('/topic/admin/patients', (msg) => {
                        try { this.handleNotification(JSON.parse(msg.body)); }
                        catch (e) { console.error('Parse error:', e); }
                    });

                    // ── Patient chat messages ──────────────────────────────
                    this.stompClient.subscribe('/topic/admin/new-message', (msg) => {
                        try {
                            const data = JSON.parse(msg.body);
                            this.handleAdminNewMessage(data);
                        } catch (e) {
                            console.error('Error parsing admin new-message:', e);
                        }
                    });

                    // ── Support ticket events ──────────────────────────────
                    this.stompClient.subscribe('/topic/admin/support', (msg) => {
                        try {
                            const data = JSON.parse(msg.body);
                            this.handleSupportEvent(data);
                        } catch (e) {
                            console.error('Error parsing support event:', e);
                        }
                    });

                    console.log('[WS] ✅ Subscribed to all admin topics');
                },

                // ── On error ───────────────────────────────────────────────
                (error) => {
                    console.error('[WS] ❌ Error:', error);
                    this.connected = false;

                    if (this.reconnectAttempts < this.maxReconnectAttempts) {
                        this.reconnectAttempts++;
                        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
                        console.log(`[WS] Retrying in ${delay / 1000}s (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
                        setTimeout(() => this.connect(), delay);
                    } else {
                        console.error('[WS] Max reconnects reached. Please refresh the page.');
                    }
                }
            );
        } catch (error) {
            console.error('[WS] ❌ Setup error:', error);
        }
    },

    disconnect: function () {
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect(() => {
                console.log('[WS] Disconnected');
                this.connected = false;
            });
        }
    },

    // ── Handle /topic/admin/new-message ──────────────────────────────────────
    // Covers both NEW_PATIENT_MESSAGE and SESSION_ENDED events.
    handleAdminNewMessage: function (data) {
        const event  = data.event || '';
        const userId = Number(data.userId);

        // ── SESSION_ENDED ────────────────────────────────────────────────────
        // Backend fired this after adminEndChatSession() deleted messages.
        // Dispatch 'patientSessionEnded' so the sidebar removes the row instantly
        // even before the next 4-second poll fires.
        if (event === 'SESSION_ENDED') {
            console.log('[WS] 🔴 SESSION_ENDED for userId:', userId);

            // Tell ChatSupportModal to drop the row
            window.dispatchEvent(new CustomEvent('patientSessionEnded', {
                detail: { userId, timestamp: data.timestamp },
            }));

            // Also fire the generic supportEvent so other listeners can react
            window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

            // Clear from dedup set so a future genuine request shows up correctly
            this._shownSupportEvents.forEach(key => {
                if (key.endsWith(':' + userId)) this._shownSupportEvents.delete(key);
            });
            return;
        }

        // ── NEW_PATIENT_MESSAGE ──────────────────────────────────────────────
        if (event === 'NEW_PATIENT_MESSAGE') {
            // Skip if this patient was already cleared by the admin
            if (this._getClearedIds().has(userId)) {
                console.log('[WS] ⏭️ Skipping new message for cleared userId:', userId);
                return;
            }

            console.log('[WS] 💬 New patient message from userId:', userId, '|', data.message);

            if (typeof this.onNewPatientMessage === 'function') {
                this.onNewPatientMessage(data);
            }

            window.dispatchEvent(new CustomEvent('newPatientChatMessage', { detail: data }));

            this.showBrowserNotification(
                `New message from ${data.userName || data.senderName || 'Patient'}`,
                data.message || 'New support message received'
            );

            this.updateNotificationCount();
            return;
        }

        // Other events on this topic
        window.dispatchEvent(new CustomEvent('adminChatEvent', { detail: data }));
    },

    // ── Handle /topic/admin/support (ticket events) ──────────────────────────
    handleSupportEvent: function (data) {
        console.log('[WS] 🎫 Support event:', data.event);

        // Use ticketId for dedup key when available (prevents false dedup across users)
        const dedupeKey = data.event + ':' + (data.ticketId || data.userId || 'unknown');

        const terminalEvents = ['SESSION_ENDED', 'TICKET_RESOLVED', 'TICKET_CLOSED'];
        if (terminalEvents.includes(data.event)) {
            // Clear stale dedup entries for this user so future requests show up
            this._shownSupportEvents.forEach(key => {
                if (key.endsWith(':' + (data.userId || ''))) {
                    this._shownSupportEvents.delete(key);
                }
            });
            window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));
            return;
        }

        if (this._shownSupportEvents.has(dedupeKey)) {
            console.log('[WS] ⏭️ Duplicate support event suppressed:', dedupeKey);
            return;
        }
        this._shownSupportEvents.add(dedupeKey);

        window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

        if (data.event === 'NEW_TICKET' || data.event === 'LIVE_AGENT_NEEDED') {
            this.showBrowserNotification(
                'New Support Ticket',
                `${data.userName || 'A patient'} opened a new ticket`
            );
            this.updateNotificationCount();
        }
    },

    // ── Generic notification handler ─────────────────────────────────────────
    handleNotification: function (notification) {
        this.showBrowserNotification(
            notification.title   || 'New Notification',
            notification.message || 'You have a new notification'
        );
        window.dispatchEvent(new CustomEvent('adminNotification', { detail: notification }));
        this.updateNotificationCount();
    },

    showBrowserNotification: function (title, body) {
        if (Notification.permission === 'default') Notification.requestPermission();
        if (Notification.permission === 'granted') {
            new Notification(title, {
                body,
                icon:              '/favicon.ico',
                badge:             '/favicon.ico',
                requireInteraction: false,
            });
        }
    },

    updateNotificationCount: function () {
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    },

    isConnected: function () { return this.connected; },

    registerChatCallbacks: function ({ onNewPatientMessage, onNewAgentMessage } = {}) {
        if (onNewPatientMessage) this.onNewPatientMessage = onNewPatientMessage;
        if (onNewAgentMessage)   this.onNewAgentMessage   = onNewAgentMessage;
    },

    unregisterChatCallbacks: function () {
        this.onNewPatientMessage = null;
        this.onNewAgentMessage   = null;
    },
};

window.AdminWebSocket = AdminWebSocket;

// ── Auto-connect on load ──────────────────────────────────────────────────────
window.addEventListener('load', () => {
    if (localStorage.getItem('authToken')) {
        setTimeout(() => AdminWebSocket.connect(), 1000);
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