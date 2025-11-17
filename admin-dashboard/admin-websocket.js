// admin-websocket.js
// WebSocket connection for real-time admin notifications

const AdminWebSocket = {
    stompClient: null,
    connected: false,
    
    connect: function() {
        const token = localStorage.getItem('authToken');
        if (!token) {
            console.log('No auth token, skipping WebSocket connection');
            return;
        }
        
        // Use Railway WebSocket URL
        const wsUrl = `${CONFIG.WS_URL}?token=${token}`;
        console.log('Connecting to WebSocket:', wsUrl);
        
        try {
            const socket = new SockJS(wsUrl);
            this.stompClient = Stomp.over(socket);
            
            // Disable debug logging
            this.stompClient.debug = null;
            
            const connectHeaders = {
                'Authorization': `Bearer ${token}`
            };
            
            this.stompClient.connect(
                connectHeaders,
                (frame) => {
                    console.log('✅ WebSocket Connected:', frame);
                    this.connected = true;
                    
                    // Subscribe to admin notifications
                    this.stompClient.subscribe('/topic/admin/appointments', (message) => {
                        console.log('📬 New appointment notification:', message.body);
                        const notification = JSON.parse(message.body);
                        this.handleNotification(notification);
                    });
                    
                    console.log('✅ Subscribed to admin notifications');
                },
                (error) => {
                    console.error('❌ WebSocket connection error:', error);
                    this.connected = false;
                    
                    // Retry connection after 5 seconds
                    setTimeout(() => {
                        console.log('Retrying WebSocket connection...');
                        this.connect();
                    }, 5000);
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
        
        // Show browser notification if permitted
        if (Notification.permission === 'granted') {
            new Notification('New Appointment', {
                body: notification.message || 'A new appointment has been created',
                icon: '/favicon.ico'
            });
        }
        
        // Dispatch custom event for UI to handle
        window.dispatchEvent(new CustomEvent('adminNotification', {
            detail: notification
        }));
    }
};

// Make it globally available
window.AdminWebSocket = AdminWebSocket;

// Auto-connect when page loads and user is authenticated
window.addEventListener('load', () => {
    if (localStorage.getItem('authToken')) {
        console.log('Auto-connecting WebSocket...');
        AdminWebSocket.connect();
    }
});