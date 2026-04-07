// admin-websocket.js
// WebSocket connection for real-time admin notifications + live support chat

const AdminWebSocket = {
    stompClient: null,
    connected: false,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,

    // Callbacks registered by the chat UI
    onNewPatientMessage: null,  // called when a patient sends a message
    onNewAgentMessage: null,    // called when an agent reply is confirmed

    connect: function () {
        const token = localStorage.getItem('authToken');
        if (!token) {
            console.log('No auth token, skipping WebSocket connection');
            return;
        }

        // SockJS requires HTTP/HTTPS URLs, not WS/WSS
        let wsUrl = CONFIG.WS_URL || CONFIG.API_BASE_URL;

        wsUrl = wsUrl.replace(/^wss:\/\//i, 'https://');
        wsUrl = wsUrl.replace(/^ws:\/\//i, 'http://');

        if (!wsUrl.startsWith('http://') && !wsUrl.startsWith('https://')) {
            wsUrl = 'https://' + wsUrl;
        }

        if (!wsUrl.endsWith('/ws')) {
            wsUrl = wsUrl.replace(/\/$/, '') + '/ws';
        }

        // Append token as query param so the backend handshake interceptor can validate it
        wsUrl = `${wsUrl}?token=${token}`;

        console.log('Connecting to WebSocket:', wsUrl);

        try {
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);

            // Suppress STOMP debug noise in production
            this.stompClient.debug = () => {};

            const connectHeaders = {
                'Authorization': `Bearer ${token}`
            };

            this.stompClient.connect(
                connectHeaders,

                // ── On connect ──────────────────────────────────────────────
                (frame) => {
                    console.log('✅ WebSocket Connected');
                    this.connected = true;
                    this.reconnectAttempts = 0;

                    // ── Existing admin topics ────────────────────────────────
                    this.stompClient.subscribe('/topic/admin/appointments', (message) => {
                        console.log('📬 Appointment notification');
                        try { this.handleNotification(JSON.parse(message.body)); }
                        catch (e) { console.error('Parse error:', e); }
                    });

                    this.stompClient.subscribe('/topic/admin/patients', (message) => {
                        console.log('📬 Patient notification');
                        try { this.handleNotification(JSON.parse(message.body)); }
                        catch (e) { console.error('Parse error:', e); }
                    });

                    // ── NEW: Subscribe to patient chat messages ───────────────
                    // Backend publishes here whenever a patient sends a message
                    this.stompClient.subscribe('/topic/admin/new-message', (message) => {
                        console.log('💬 New patient message received via WebSocket');
                        try {
                            const data = JSON.parse(message.body);
                            this.handleNewPatientMessage(data);
                        } catch (e) {
                            console.error('Error parsing patient message:', e);
                        }
                    });

                    // ── NEW: Subscribe to support ticket events ──────────────
                    this.stompClient.subscribe('/topic/admin/support', (message) => {
                        console.log('🎫 Support event received');
                        try {
                            const data = JSON.parse(message.body);
                            this.handleSupportEvent(data);
                        } catch (e) {
                            console.error('Error parsing support event:', e);
                        }
                    });

                    console.log('✅ Subscribed to all admin topics');
                },

                // ── On error / disconnect ────────────────────────────────────
                (error) => {
                    console.error('❌ WebSocket error:', error);
                    this.connected = false;

                    if (this.reconnectAttempts < this.maxReconnectAttempts) {
                        this.reconnectAttempts++;
                        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
                        console.log(`Retrying in ${delay / 1000}s (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
                        setTimeout(() => this.connect(), delay);
                    } else {
                        console.error('Max reconnect attempts reached. Please refresh the page.');
                    }
                }
            );
        } catch (error) {
            console.error('❌ Error setting up WebSocket:', error);
        }
    },

    disconnect: function () {
        if (this.stompClient !== null && this.connected) {
            this.stompClient.disconnect(() => {
                console.log('WebSocket disconnected');
                this.connected = false;
            });
        }
    },

    // ── Handle incoming patient message ─────────────────────────────────────
    handleNewPatientMessage: function (data) {
        console.log('📨 Patient message from userId:', data.userId, '|', data.message);

        // 1. If the chat UI has registered a callback, call it
        //    (ChatSupportModal registers this so it can refresh the active chat)
        if (typeof this.onNewPatientMessage === 'function') {
            this.onNewPatientMessage(data);
        }

        // 2. Dispatch a DOM event so any part of the page can react
        window.dispatchEvent(new CustomEvent('newPatientChatMessage', { detail: data }));

        // 3. Show a browser notification so the admin is alerted even if the
        //    support panel is not open
        this.showBrowserNotification(
            `New message from ${data.userName || 'Patient'}`,
            data.message || 'New support message received'
        );

        // 4. Refresh notification badge / conversation list
        this.updateNotificationCount();
    },

    // ── Handle support ticket / agent events ────────────────────────────────
    handleSupportEvent: function (data) {
        console.log('🎫 Support event:', data.event);

        window.dispatchEvent(new CustomEvent('supportEvent', { detail: data }));

        if (data.event === 'NEW_TICKET') {
            this.showBrowserNotification(
                'New Support Ticket',
                `${data.userName || 'A patient'} opened a new ticket`
            );
            this.updateNotificationCount();
        }
    },

    // ── Generic notification handler (existing topics) ───────────────────────
    handleNotification: function (notification) {
        console.log('Processing notification:', notification);

        this.showBrowserNotification(
            notification.title || 'New Notification',
            notification.message || 'You have a new notification'
        );

        window.dispatchEvent(new CustomEvent('adminNotification', { detail: notification }));
        this.updateNotificationCount();
    },

    // ── Browser push notification helper ────────────────────────────────────
    showBrowserNotification: function (title, body) {
        if (Notification.permission === 'default') {
            Notification.requestPermission();
        }

        if (Notification.permission === 'granted') {
            new Notification(title, {
                body,
                icon: '/favicon.ico',
                badge: '/favicon.ico',
                requireInteraction: false
            });
        }
    },

    updateNotificationCount: function () {
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    },

    isConnected: function () {
        return this.connected;
    },

    // ── Register chat UI callbacks ───────────────────────────────────────────
    // Call this from ChatSupportModal so live messages update the open chat
    registerChatCallbacks: function ({ onNewPatientMessage, onNewAgentMessage } = {}) {
        if (onNewPatientMessage) this.onNewPatientMessage = onNewPatientMessage;
        if (onNewAgentMessage)   this.onNewAgentMessage   = onNewAgentMessage;
    },

    unregisterChatCallbacks: function () {
        this.onNewPatientMessage = null;
        this.onNewAgentMessage   = null;
    }
};

window.AdminWebSocket = AdminWebSocket;

// ── Auto-connect on page load ────────────────────────────────────────────────
window.addEventListener('load', () => {
    const token = localStorage.getItem('authToken');
    if (token) {
        console.log('Auto-connecting WebSocket...');
        setTimeout(() => AdminWebSocket.connect(), 1000);
    }
});

// ── Re-connect after login ───────────────────────────────────────────────────
window.addEventListener('userAuthenticated', () => {
    console.log('User authenticated, connecting WebSocket...');
    if (!AdminWebSocket.isConnected()) AdminWebSocket.connect();
});

// ── Disconnect on logout ─────────────────────────────────────────────────────
window.addEventListener('userLoggedOut', () => {
    console.log('User logged out, disconnecting WebSocket...');
    AdminWebSocket.disconnect();
});

// ── Reconnect when tab becomes visible again ─────────────────────────────────
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && localStorage.getItem('authToken') && !AdminWebSocket.isConnected()) {
        console.log('Page visible, reconnecting WebSocket...');
        AdminWebSocket.connect();
    }
});