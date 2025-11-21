const CONFIG = {
    // FIXED: Added /api prefix to match backend endpoints
    API_BASE_URL: 'https://medical-app-backend-production-19dc.up.railway.app',
    ADMIN_API_URL: 'https://medical-app-backend-production-19dc.up.railway.app',
    // WebSocket URL - SockJS handles the upgrade
    WS_URL: 'https://medical-app-backend-production-19dc.up.railway.app/ws',
    TEST_TYPES: [
        'Blood Sugar Test',
        'Blood Pressure Check',
        'Cholesterol Panel',
        'Complete Blood Count',
        'Chest X-Ray',
        'ECG',
        'Urine Analysis',
        'Liver Function Test',
        'Kidney Function Test',
        'Thyroid Test'
    ],
    TIME_SLOTS: [
        '09:00', '09:30', '10:00', '10:30', '11:00', '11:30',
        '14:00', '14:30', '15:00', '15:30', '16:00', '16:30'
    ],
    PRIORITIES: ['normal', 'urgent', 'emergency'],
    STATUSES: ['NORMAL', 'ABNORMAL', 'PENDING'],
    APPOINTMENT_STATUSES: ['scheduled', 'pending', 'completed', 'cancelled']
};