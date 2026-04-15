// admin-websocket-v2.js
// Real-time WebSocket for admin notifications + live support chat.
//
// Fixes vs v1:
//  1. NEW_TICKET dedup key uses ticketId (not userId) — prevents suppressing
//     genuine second tickets from the same patient.
//  2. handleNewPatientMessage logs senderName properly (was "| undefined").
//  3. SESSION_ENDED fired by admin is dispatched as a DOM event so
//     ChatSupportModal can clear the sidebar immediately.
//  4. Reconnection back-off is capped and resets the dedup set so stale
//     suppression doesn't persist forever after a network blip.

const AdminWebSocket = {
    stompClient:        null,
    connected:          false,
    reconnectAttempts:  0,
    maxReconnectAttempts: 5,

    // Tracks "event:id" keys already shown this connection.
    // Cleared on full reconnect so stale suppressions don't outlive the session.
    _shownSupportEvents: new Set(),

    // Callbacks registered by ChatSupportModal
    onNewPatientMessage: null,
    onNewAgentMessage:   null,

    // ── Connect ──────────────────────────────────────────────────────────────
    connect: function () {
        const token = localStorage.getItem('authToken');
        if (!token) { console.log('[WS] No auth token — skipping connect'); return; }

        let wsUrl = (CONFIG.WS_URL || CONFIG.API_BASE_URL)
            .replace(/^wss:\/\//i, 'https://')
            .replace(/^ws:\/\//i,  'http://');

        if (!/^https?:\/\//i.test(wsUrl)) wsUrl = 'https://' + wsUrl;
        if (!wsUrl.endsWith('/ws'))        wsUrl = wsUrl.replace(/\/$/, '') + '/ws';
        wsUrl += '?token=' + token;

        console.log('[WS] Connecting to', wsUrl);

        try {
            const socket      = new SockJS(wsUrl);
            this.stompClient  = Stomp.over(socket);
            this.stompClient.debug = () => {};   // suppress STOMP noise

            this.stompClient.connect(
                { Authorization: 'Bearer ' + token },

                // ── onConnect ────────────────────────────────────────────────
                (frame) => {
                    console.log('[WS] ✅ Connected');
                    this.connected          = true;
                    this.reconnectAttempts  = 0;
                    // Clear stale dedup keys — fresh connection = fresh slate
                    this._shownSupportEvents.clear();

                    // Existing admin topics
                    this.stompClient.subscribe('/topic/admin/appointments', (msg) => {
                        try { this.handleNotification(JSON.parse(msg.body)); } catch (e) { console.error('[WS] appointments parse error', e); }
                    });
                    this.stompClient.subscribe('/topic/admin/patients', (msg) => {
                        try { this.handleNotification(JSON.parse(msg.body)); } catch (e) { console.error('[WS] patients parse error', e); }
                    });

                    // Patient chat messages
                    this.stompClient.subscribe('/topic/admin/new-message', (msg) => {
                        try {
                            const data = JSON.parse(msg.body);
                            // SESSION_ENDED published here by both patient and admin end-session
                            if (data.event === 'SESSION_ENDED') {
                                this._handleSessionEnded(data);
                                return;
                            }
                            this.handleNewPatientMessage(data);
                        } catch (e) { console.error('[WS] new-message parse error', e); }
                    });

                    // Support ticket events (NEW_TICKET, LIVE_AGENT_NEEDED, etc.)
                    this.stompClient.subscribe('/topic/admin/support', (msg) => {
                        try { this.handleSupportEvent(JSON.parse(msg.body)); }
                        catch (e) { console.error('[WS] support event parse error', e); }
                    });

                    console.log('[WS] ✅ Subscribed to all admin topics');
                },

                // ── onError ──────────────────────────────────────────────────
                (error) => {
                    console.error('[WS] ❌ Error:', error);
                    this.connected = false;
                    this._scheduleReconnect();
                }
            );
        } catch (e) {
            console.error('[WS] ❌ Setup error:', e);
            this._scheduleReconnect();
        }
    },

    // ── Disconnect ────────────────────────────────────────────────────────────
    disconnect: function () {
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect(() => {
                console.log('[WS] Disconnected');
                this.connected = false;
            });
        }
    },

    // ── Reconnect logic ───────────────────────────────────────────────────────
    _scheduleReconnect: function () {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('[WS] Max reconnect attempts reached. Refresh the page.');
            return;
        }
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
        console.log('[WS] Retrying in ' + (delay / 1000) + 's (' + this.reconnectAttempts + '/' + this.maxReconnectAttempts + ')...');
        setTimeout(() => this.connect(), delay);
    },

    // ── Handle incoming patient message ───────────────────────────────────────
    handleNewPatientMessage: function (data) {
        // FIX: senderName was "undefined" because the old service didn't include it.
        // Now the service always sends senderName = fullName(user). We still fall
        // back gracefully to userName for older backend deploys.
        const displayName = data.senderName || data.userName || 'Patient';
        console.log('[WS] Patient message from userId:', data.userId, '|', displayName, '|', (data.message || '').substring(0, 60));

        if (typeof this.onNewPatientMessage === 'function') {
            this.onNewPatientMessage(data);
        }

        window.dispatchEvent(new CustomEvent('newPatientChatMessage', { detail: data }));

        this.showBrowserNotification(
            'New message from ' + displayName,
            data.message || 'New support message received'
        );

        this.updateNotificationCount();
    },

    // ── Handle support ticket / agent events ──────────────────────────────────
    handleSupportEvent: function (data) {
        console.log('[WS] Support event:', data.event, '| ticketId:', data.ticketId, '| userId:', data.userId);

        const terminalEvents = ['SESSION_ENDED', 'TICKET_RESOLVED', 'TICKET_CLOSED'];
        if (terminalEvents.includes(data.event)) {
            // Clear dedup entries for this user/ticket so a new request shows correctly
            const prefix = 'NEW_TICKET:'     + (data.ticketId || data.userId || '');
            const prefix2 = 'LIVE_AGENT_NEEDED:' + (data.ticketId || data.userId || '');
            this._shownSupportEvents.delete(prefix);
            this._shownSupportEvents.delete(prefix2);
            window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));
            return;
        }

        // FIX: dedup key now uses ticketId when available so two different tickets
        // from the same patient are NOT suppressed (old bug: both used userId).
        const dedupeKey = data.event + ':' + (data.ticketId || data.userId || 'unknown');

        if (this._shownSupportEvents.has(dedupeKey)) {
            console.log('[WS] Duplicate support event suppressed:', dedupeKey);
            return;
        }
        this._shownSupportEvents.add(dedupeKey);

        window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

        if (data.event === 'NEW_TICKET' || data.event === 'LIVE_AGENT_NEEDED') {
            this.showBrowserNotification(
                'New Support Ticket',
                (data.userName || 'A patient') + ' opened a new ticket'
            );
            this.updateNotificationCount();
        }
    },

    // ── Handle SESSION_ENDED ──────────────────────────────────────────────────
    // Dispatched via both /topic/admin/new-message (backend fires this on both
    // patient-end and admin-end) so the sidebar row disappears in real time.
    _handleSessionEnded: function (data) {
        console.log('[WS] SESSION_ENDED for userId:', data.userId);

        // Let ChatSupportModal know (it also listens to the DOM event)
        window.dispatchEvent(new CustomEvent('supportSessionEnded', { detail: data }));

        // Also fire as a generic supportEvent so any other listener can react
        window.dispatchEvent(new CustomEvent('supportEvent', { detail: { ...data, event: 'SESSION_ENDED' } }));

        // Clear dedup state for this user so future requests show correctly
        const uid = data.userId || '';
        ['NEW_TICKET', 'LIVE_AGENT_NEEDED'].forEach(evt => {
            this._shownSupportEvents.delete(evt + ':' + uid);
        });
    },

    // ── Generic notification handler (appointments, patients topics) ──────────
    handleNotification: function (notification) {
        this.showBrowserNotification(
            notification.title   || 'New Notification',
            notification.message || 'You have a new notification'
        );
        window.dispatchEvent(new CustomEvent('adminNotification', { detail: notification }));
        this.updateNotificationCount();
    },

    // ── Browser notification helper ───────────────────────────────────────────
    showBrowserNotification: function (title, body) {
        if (Notification.permission === 'default') Notification.requestPermission();
        if (Notification.permission === 'granted') {
            new Notification(title, { body, icon: '/favicon.ico', badge: '/favicon.ico', requireInteraction: false });
        }
    },

    updateNotificationCount: function () {
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    },

    isConnected: function () { return this.connected; },

    // ── Register / unregister chat UI callbacks ───────────────────────────────
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

// ── Auto-connect on page load ─────────────────────────────────────────────────
window.addEventListener('load', () => {
    if (localStorage.getItem('authToken')) {
        console.log('[WS] Auto-connecting...');
        setTimeout(() => AdminWebSocket.connect(), 1000);
    }
});

window.addEventListener('userAuthenticated', () => {
    if (!AdminWebSocket.isConnected()) AdminWebSocket.connect();
});

window.addEventListener('userLoggedOut', () => {
    AdminWebSocket.disconnect();
});

document.addEventListener('visibilitychange', () => {
    if (!document.hidden && localStorage.getItem('authToken') && !AdminWebSocket.isConnected()) {
        console.log('[WS] Tab visible — reconnecting...');
        AdminWebSocket.connect();
    }
});