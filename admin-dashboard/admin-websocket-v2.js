// admin-websocket.js
// WebSocket connection for real-time admin notifications

const AdminWebSocket = {
    stompClient: null,
    connected: false,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,
    
    connect: function() {
        const token = localStorage.getItem('authToken');
        if (!token) {
            console.log('No auth token, skipping WebSocket connection');
            return;
        }
        
        // FIXED: Convert WS URL to HTTP/HTTPS for SockJS
        // SockJS requires HTTP/HTTPS URLs, not WS/WSS
        let wsUrl = CONFIG.WS_URL || CONFIG.API_URL;
        
        // Remove any ws:// or wss:// protocol and replace with http/https
        wsUrl = wsUrl.replace(/^wss:\/\//i, 'https://');
        wsUrl = wsUrl.replace(/^ws:\/\//i, 'http://');
        
        // Ensure it has http/https protocol
        if (!wsUrl.startsWith('http://') && !wsUrl.startsWith('https://')) {
            wsUrl = 'https://' + wsUrl;
        }
        
        // Add WebSocket endpoint path (adjust to match your backend)
        if (!wsUrl.endsWith('/ws')) {
            wsUrl = wsUrl.replace(/\/$/, '') + '/ws';
        }
        
        console.log('Connecting to WebSocket:', wsUrl);
        
        try {
            // SockJS will automatically handle WebSocket protocol upgrade from HTTP/HTTPS
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);
            
            // Disable debug logging in production
            this.stompClient.debug = (str) => {
                // Uncomment for debugging
                // console.log('STOMP:', str);
            };
            
            const connectHeaders = {
                'Authorization': `Bearer ${token}`
            };
            
            this.stompClient.connect(
                connectHeaders,
                (frame) => {
                    console.log('✅ WebSocket Connected');
                    this.connected = true;
                    this.reconnectAttempts = 0; // Reset reconnect counter on success
                    
                    // Subscribe to admin notifications
                    this.stompClient.subscribe('/topic/admin/appointments', (message) => {
                        console.log('📬 New appointment notification');
                        try {
                            const notification = JSON.parse(message.body);
                            this.handleNotification(notification);
                        } catch (error) {
                            console.error('Error parsing notification:', error);
                        }
                    });
                    
                    // Subscribe to other admin topics
                    this.stompClient.subscribe('/topic/admin/patients', (message) => {
                        console.log('📬 New patient notification');
                        try {
                            const notification = JSON.parse(message.body);
                            this.handleNotification(notification);
                        } catch (error) {
                            console.error('Error parsing notification:', error);
                        }
                    });
                    
                    console.log('✅ Subscribed to admin notifications');
                },
                (error) => {
                    console.error('❌ WebSocket connection error:', error);
                    this.connected = false;
                    
                    // Exponential backoff reconnection
                    if (this.reconnectAttempts < this.maxReconnectAttempts) {
                        this.reconnectAttempts++;
                        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
                        console.log(`Retrying WebSocket connection in ${delay/1000}s (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
                        
                        setTimeout(() => {
                            this.connect();
                        }, delay);
                    } else {
                        console.error('Max reconnection attempts reached. Please refresh the page.');
                    }
                }
            );
        } catch (error) {
            console.error('❌ Error setting up WebSocket:', error);
        }
    },
    
    disconnect: function() {
        if (this.stompClient !== null && this.connected) {
            this.stompClient.disconnect(() => {
                console.log('WebSocket disconnected');
                this.connected = false;
            });
        }
    },
    
    handleNotification: function(notification) {
        console.log('Processing notification:', notification);
        
        // Request notification permission if not already granted
        if (Notification.permission === 'default') {
            Notification.requestPermission();
        }
        
        // Show browser notification if permitted
        if (Notification.permission === 'granted') {
            new Notification(notification.title || 'New Notification', {
                body: notification.message || 'You have a new notification',
                icon: '/favicon.ico',
                badge: '/favicon.ico',
                tag: notification.id || 'admin-notification',
                requireInteraction: false
            });
        }
        
        // Dispatch custom event for UI to handle
        window.dispatchEvent(new CustomEvent('adminNotification', {
            detail: notification
        }));
        
        // Update notification badge/count if you have one
        this.updateNotificationCount();
    },
    
    updateNotificationCount: function() {
        // Trigger a refresh of notification count in the UI
        window.dispatchEvent(new CustomEvent('refreshNotifications'));
    },
    
    isConnected: function() {
        return this.connected;
    }
};

// Make it globally available
window.AdminWebSocket = AdminWebSocket;

// Auto-connect when page loads and user is authenticated
window.addEventListener('load', () => {
    const token = localStorage.getItem('authToken');
    if (token) {
        console.log('Auto-connecting WebSocket...');
        // Small delay to ensure all scripts are loaded
        setTimeout(() => {
            AdminWebSocket.connect();
        }, 1000);
    }
});

// Reconnect on authentication
window.addEventListener('userAuthenticated', () => {
    console.log('User authenticated, connecting WebSocket...');
    if (!AdminWebSocket.isConnected()) {
        AdminWebSocket.connect();
    }
});

// Disconnect on logout
window.addEventListener('userLoggedOut', () => {
    console.log('User logged out, disconnecting WebSocket...');
    AdminWebSocket.disconnect();
});

// Handle page visibility changes to reconnect if needed
document.addEventListener('visibilitychange', () => {
    if (!document.hidden && localStorage.getItem('authToken') && !AdminWebSocket.isConnected()) {
        console.log('Page became visible, reconnecting WebSocket...');
        AdminWebSocket.connect();
    }
});