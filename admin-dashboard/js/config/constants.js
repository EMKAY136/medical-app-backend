const USE_LOCAL = true; // 👈 flip to false for production

const BASE_URL = USE_LOCAL
    ? 'http://172.16.60.226:5000'
    : 'https://meticulous-clarity-production.up.railway.app';

const CONFIG = {
    API_BASE_URL: BASE_URL,
    ADMIN_API_URL: BASE_URL,
    WS_URL: `${BASE_URL}/ws`,

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