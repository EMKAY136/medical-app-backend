// MedicalAdminDashboard — v4
// ───────────────────────────────────────────────────────────────────────────
// KEY FIXES vs previous:
//  1. chatUnreadCount is ONLY set by:
//       a. loadChatUnreadCount() which queries the DB directly, OR
//       b. 'supportConversationsLoaded' CustomEvent from ChatSupportModal
//          (emitted after every loadConversations() call with the real count)
//     It is NEVER incremented by WS events. This kills phantom badge counts.
//  2. 'requestConversationRefresh' listener calls loadChatUnreadCount() so
//     the badge updates within ~1 s of any WS support event.
//  3. Clicking the chat button NO LONGER resets chatUnreadCount to 0 eagerly —
//     it lets the modal's first loadConversations() emit the real count.
//  4. loadRefundRequests() called only once per fastInterval tick (was called
//     twice — duplicate API call removed).
//  5. window.showNotificationAlert exposed for ChatSupportModal to call.
//  6. loadRefundRequests() now only keeps genuinely pending refunds (excludes
//     APPROVED, COMPLETED, REFUNDED, REJECTED, DECLINED statuses).
//  7. loadStats() refundRequests count has a secondary safety filter.

const { useState, useEffect } = React;

const MedicalAdminDashboard = () => {
    const [isAuthenticated, setIsAuthenticated]   = useState(false);
    const [currentView, setCurrentView]           = useState('dashboard');
    const [patients, setPatients]                 = useState([]);
    const [appointments, setAppointments]         = useState([]);
    const [testResults, setTestResults]           = useState([]);
    const [notifications, setNotifications]       = useState([]);
    const [autoNotifications, setAutoNotifications] = useState([]);
    const [loading, setLoading]                   = useState(false);
    const [showModal, setShowModal]               = useState(null);
    const [selectedPatient, setSelectedPatient]   = useState(null);
    const [searchQuery, setSearchQuery]           = useState('');
    const [notification, setNotification]         = useState(null);
    const [showNotificationForm, setShowNotificationForm]         = useState(false);
    const [showAutoNotificationForm, setShowAutoNotificationForm] = useState(false);
    const [notificationTab, setNotificationTab]   = useState('sent');
    const [validatingToken, setValidatingToken]   = useState(true);

    // Support Chat
    const [showSupportChat, setShowSupportChat]       = useState(false);
    const [supportChatPatient, setSupportChatPatient] = useState(null);
    // chatUnreadCount = authoritative "human requests needing attention" count from DB
    const [chatUnreadCount, setChatUnreadCount]       = useState(0);

    const [formData, setFormData] = useState({
        recipientId: '', title: '', message: '', type: 'appointment', sendToAll: false,
    });
    const [autoFormData, setAutoFormData] = useState({
        trigger: 'appointment_scheduled', title: '', message: '',
        type: 'appointment', enabled: true, delayMinutes: 0,
    });

    const [refundRequests, setRefundRequests] = useState([]);

    const [stats, setStats] = useState({
        totalPatients: 0, todayAppointments: 0, pendingTests: 0,
        completedReports: 0, totalNotifications: 0, activeAutoRules: 0,
        pendingPayments: 0, missedAppointments: 0, refundRequests: 0,
    });

    // ── Helpers ───────────────────────────────────────────────────────────────
    const showNotificationAlert = (message, type = 'success') => {
        setNotification({ message, type });
        setTimeout(() => setNotification(null), 4000);
    };

    const parseDate = (v) => {
        if (!v) return null;
        if (Array.isArray(v)) {
            const [yr, mo, dy, hr = 0, mn = 0] = v;
            return new Date(yr, mo - 1, dy, hr, mn);
        }
        const s = String(v).trim();
        if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(s) && !s.endsWith('Z') && !/[+-]\d{2}:?\d{2}$/.test(s)) {
            const [datePart, timePart] = s.split('T');
            const [yr, mo, dy] = datePart.split('-').map(Number);
            const [hr, mn] = timePart.split(':').map(Number);
            return new Date(yr, mo - 1, dy, hr, mn);
        }
        const d = new Date(v);
        return isNaN(d.getTime()) ? null : d;
    };

    // ── Token validation ──────────────────────────────────────────────────────
    const validateTokenAndLoad = async (token) => {
        try {
            const res = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/patients?page=0&size=1`,
                { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
            );
            if (res.ok) {
                setIsAuthenticated(true);
                loadDashboardData();
            } else {
                localStorage.removeItem('authToken');
                localStorage.removeItem('user_info');
                setIsAuthenticated(false);
            }
        } catch {
            localStorage.removeItem('authToken');
            setIsAuthenticated(false);
        } finally {
            setValidatingToken(false);
        }
    };

    useEffect(() => {
        const token = localStorage.getItem('authToken');
        if (token) validateTokenAndLoad(token);
        else       setValidatingToken(false);
    }, []);

    // ── Badge sync listeners ──────────────────────────────────────────────────
    // These are the ONLY two ways chatUnreadCount is ever set:
    //   1. 'supportConversationsLoaded' — authoritative count from ChatSupportModal
    //   2. loadChatUnreadCount()        — direct DB query
    useEffect(() => {
        if (!isAuthenticated) return;

        // ChatSupportModal emits this after every loadConversations()
        const onConversationsLoaded = (e) => {
            const count = e.detail?.humanRequestCount ?? 0;
            setChatUnreadCount(count);
        };

        // admin-websocket-v3.js emits this instead of updateNotificationCount()
        // → triggers a DB refresh so the badge reflects reality within ~1 s
        const onRefreshRequest = () => {
            loadChatUnreadCount();
        };

        window.addEventListener('supportConversationsLoaded', onConversationsLoaded);
        window.addEventListener('requestConversationRefresh', onRefreshRequest);

        return () => {
            window.removeEventListener('supportConversationsLoaded', onConversationsLoaded);
            window.removeEventListener('requestConversationRefresh', onRefreshRequest);
        };
    }, [isAuthenticated]);

    // ── Auto-refresh intervals ────────────────────────────────────────────────
    useEffect(() => {
        if (!isAuthenticated) return;

        const fastInterval = setInterval(() => {
            if (!localStorage.getItem('authToken')) return;
            loadAppointments();
            loadNotifications();
            loadChatUnreadCount();   // authoritative badge update every 10 s
            loadRefundRequests();    // called once per tick (was duplicated)
        }, 10000);

        const slowInterval = setInterval(() => {
            if (!localStorage.getItem('authToken')) return;
            loadPatients();
            loadTestResults();
        }, 60000);

        return () => {
            clearInterval(fastInterval);
            clearInterval(slowInterval);
        };
    }, [isAuthenticated]);

    // ── WebSocket ─────────────────────────────────────────────────────────────
    useEffect(() => {
        if (!isAuthenticated) return;

        // Expose helpers for ChatSupportModal and admin-websocket
        window.loadAppointments      = loadAppointments;
        window.loadPatients          = loadPatients;
        window.loadTestResults       = loadTestResults;
        window.loadStats             = loadStats;
        window.showNotificationAlert = showNotificationAlert;

        if (window.AdminWebSocketClient) {
            const wsClient = new window.AdminWebSocketClient();
            wsClient.connect();
            return () => {
                wsClient.disconnect();
                ['loadAppointments','loadPatients','loadTestResults','loadStats','showNotificationAlert']
                    .forEach(k => delete window[k]);
            };
        } else if (window.AdminWebSocket && !window.AdminWebSocket.isConnected()) {
            window.AdminWebSocket.connect();
        }

        return () => {
            ['loadAppointments','loadPatients','loadTestResults','loadStats','showNotificationAlert']
                .forEach(k => delete window[k]);
        };
    }, [isAuthenticated]);

    useEffect(() => {
        if (patients.length > 0 || appointments.length > 0 || testResults.length > 0) loadStats();
    }, [patients, appointments, testResults, notifications, autoNotifications, refundRequests]);

    // ── Data loaders ──────────────────────────────────────────────────────────

    // ── FIX: loadRefundRequests now only keeps genuinely pending refunds ───────
    const loadRefundRequests = async () => {
        const PROCESSED_STATUSES = ['APPROVED', 'COMPLETED', 'REFUNDED', 'REJECTED', 'DECLINED'];

        const isPending = (r) => {
            const status = (r.refundStatus || r.status || '').toUpperCase();
            return !PROCESSED_STATUSES.includes(status) &&
                (status === 'REQUESTED' || status === 'PENDING' || status === 'PENDING_REVIEW');
        };

        const isPendingAppointment = (a) => {
            const refundStatus = (a.refundStatus || '').toUpperCase();
            const payStatus    = (a.paymentStatus || '').toUpperCase();
            return (
                a.refundRequested === true &&
                !PROCESSED_STATUSES.includes(refundStatus) &&
                (
                    refundStatus === 'REQUESTED' ||
                    refundStatus === 'PENDING'   ||
                    payStatus    === 'REFUND_REQUESTED'
                )
            );
        };

        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/refund-requests`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                const data = await res.json();
                const list = data.refundRequests || data.data || data.requests || (Array.isArray(data) ? data : []);
                setRefundRequests(list.filter(isPending));
            } else {
                setRefundRequests(appointments.filter(isPendingAppointment));
            }
        } catch {
            setRefundRequests(appointments.filter(isPendingAppointment));
        }
    };

    const loadDashboardData = async () => {
        setLoading(true);
        try {
            await loadPatients();
            await Promise.all([
                loadAppointments(), loadTestResults(),
                loadNotifications(), loadAutoNotifications(),
                loadChatUnreadCount(), loadRefundRequests(),
            ]);
        } catch {
            showNotificationAlert('Error loading dashboard data', 'error');
        } finally {
            setLoading(false);
        }
    };

    const loadPatients = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/patients?page=0&size=100`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.status === 401) { handleLogout(); return; }
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setPatients(data.patients && Array.isArray(data.patients) ? data.patients : []);
        } catch (err) { console.error('Error loading patients:', err); }
    };

    const loadAppointments = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/appointments?page=0&size=200`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.status === 401) { handleLogout(); return; }
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setAppointments(data.appointments && Array.isArray(data.appointments) ? data.appointments : []);
        } catch (err) { console.error('Error loading appointments:', err); }
    };

    const loadTestResults = async () => {
        try {
            const data = await ApiService.getTestResults(0, 100);
            setTestResults(data.results || []);
        } catch (err) { console.error('Error loading test results:', err); }
    };

    const loadNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) { const data = await res.json(); setNotifications(data.notifications || []); }
        } catch (err) { console.error('Error loading notifications:', err); }
    };

    const loadAutoNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) { const data = await res.json(); setAutoNotifications(data.autoNotifications || []); }
        } catch (err) { console.error('Error loading auto-notifications:', err); }
    };

    // ── loadChatUnreadCount — THE single source of truth for the badge ────────
    // Queries the DB directly. Badge = number of chats where a human agent
    // was explicitly requested and admin hasn't cleared yet.
    const loadChatUnreadCount = async () => {
    try {
        const token = localStorage.getItem('authToken');
        const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/support/admin/all-chats`, {
            headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        });
        if (!res.ok) return;
        const data = await res.json();
        if (!data.success || !data.chats) return;

        // DEBUG: Log what the API actually returns so you can see the real field values
        console.log('[Badge] Raw chats from API:', data.chats.map(c => ({
            userId: c.userId,
            status: c.status,
            conversationType: c.conversationType,
            subject: c.subject,
            category: c.category,
            priority: c.priority,
            humanRequested: c.humanRequested,
            requiresHuman: c.requiresHuman,
            agentRequested: c.agentRequested,
        })));

        let clearedIds = new Set();
        try {
            const raw = sessionStorage.getItem('admin_cleared_chat_sessions');
            clearedIds = raw ? new Set(JSON.parse(raw).map(Number)) : new Set();
        } catch {}

        const count = data.chats.filter(c => {
            if (clearedIds.has(Number(c.userId))) return false;
            const subj = (c.subject  || '').toLowerCase();
            const cat  = (c.category || '').toLowerCase();
            const type = (c.conversationType || '').toLowerCase();
            const status = (c.status || '').toLowerCase();

            return (
                // Existing checks
                c.status === 'NEEDS_RESPONSE'               ||
                c.status === 'NEEDS_FIRST_RESPONSE'         ||
                c.conversationType === 'NEEDS_FIRST_RESPONSE' ||
                subj.includes('human support')              ||
                subj.includes('human agent')                ||
                cat.includes('human support')               ||
                cat.includes('human agent')                 ||
                c.priority === 'HIGH'                       ||
                // NEW: broader checks for human agent request fields
                c.humanRequested === true                   ||
                c.requiresHuman === true                    ||
                c.agentRequested === true                   ||
                c.humanAgentRequested === true              ||
                subj.includes('human')                      ||
                cat.includes('human')                       ||
                type.includes('human')                      ||
                status.includes('human')                    ||
                status === 'open'                           ||
                status === 'pending'
            );
        }).length;

        console.log('[Badge] Filtered count:', count, '| Cleared IDs:', [...clearedIds]);
        setChatUnreadCount(count);
    } catch (err) {
        console.error('[Badge] Error:', err);
    }
};

    const loadStats = () => {
        try {
            const today = new Date().toDateString();

            const todayApts = appointments.filter(apt => {
                const d = parseDate(apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt);
                return d && d.toDateString() === today;
            }).length;

            const pendingTests = appointments.filter(apt =>
                ['scheduled', 'pending'].includes((apt.status || '').toLowerCase())
            ).length;

            const completedReports = testResults.filter(r =>
                ['completed', 'normal'].includes((r.status || '').toLowerCase())
            ).length;

            const pendingPayments = appointments.filter(apt =>
                (apt.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION'
            ).length;

            const missedAppointments = appointments.filter(apt =>
                (apt.status || '').toUpperCase() === 'MISSED' &&
                (apt.paymentStatus || '').toUpperCase() === 'PAID'
            ).length;

            // ── FIX: secondary safety filter — only count genuinely pending refunds ──
            const PROCESSED_STATUSES = ['APPROVED', 'COMPLETED', 'REFUNDED', 'REJECTED', 'DECLINED'];
            const pendingRefunds = refundRequests.filter(r => {
                const status = (r.refundStatus || r.status || '').toUpperCase();
                return !PROCESSED_STATUSES.includes(status);
            }).length;

            setStats({
                totalPatients:      patients.length,
                todayAppointments:  todayApts,
                pendingTests,
                completedReports,
                totalNotifications: notifications.length,
                activeAutoRules:    autoNotifications.filter(n => n.enabled).length,
                pendingPayments,
                missedAppointments,
                refundRequests:     pendingRefunds,
            });
        } catch (err) { console.error('Error calculating stats:', err); }
    };

    // ── Actions ───────────────────────────────────────────────────────────────
    const handleLoginSuccess = () => { setIsAuthenticated(true); loadDashboardData(); };

    const handleLogout = () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('user_info');
        setIsAuthenticated(false);
        setPatients([]); setAppointments([]); setTestResults([]);
        setNotifications([]); setAutoNotifications([]);
        setChatUnreadCount(0);
        window.dispatchEvent(new CustomEvent('userLoggedOut'));
    };

    const handleUpdateAppointment = async (appointmentId, newStatus) => {
        try {
            const res = await ApiService.updateAppointmentStatus(appointmentId, newStatus);
            if (res.ok) {
                showNotificationAlert(`Appointment ${newStatus.toLowerCase()} successfully!`);
                await loadAppointments();
            } else {
                const err = await res.json();
                showNotificationAlert(err.message || 'Error updating appointment', 'error');
            }
        } catch { showNotificationAlert('Network error while updating appointment', 'error'); }
    };

    const handleAddResult = async (resultData) => {
        try {
            const hasFiles = resultData.attachments && resultData.attachments.length > 0;
            let response;
            if (hasFiles) {
                const fd = new FormData();
                fd.append('patientId',    resultData.patientId);
                fd.append('testType',     resultData.testType);
                fd.append('result',       resultData.result || '');
                fd.append('notes',        resultData.notes  || '');
                fd.append('category',     resultData.category || 'General');
                fd.append('status',       resultData.status   || 'COMPLETED');
                fd.append('doctorName',   resultData.doctorName || 'Admin');
                fd.append('testDate',     resultData.testDate   || new Date().toISOString());
                if (resultData.appointmentId) fd.append('appointmentId', resultData.appointmentId);
                fd.append('markCompleted', resultData.markAppointmentCompleted || false);
                const att   = resultData.attachments[0];
                const b64   = att.data.split(',')[1];
                const bytes = atob(b64);
                const arr   = new Uint8Array(bytes.length);
                for (let i = 0; i < bytes.length; i++) arr[i] = bytes.charCodeAt(i);
                fd.append('file', new File([new Blob([arr], { type: att.type })], att.name, { type: att.type }));
                const token    = localStorage.getItem('authToken');
                const fetchRes = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/upload-result-with-file`, {
                    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd,
                });
                if (!fetchRes.ok) throw new Error(`Server returned ${fetchRes.status}`);
                response = await fetchRes.json();
            } else {
                response = await ApiService.addTestResult(resultData);
            }
            if (response.success) {
                showNotificationAlert('Test result added successfully!');
                setShowModal(null);
                await loadTestResults();
            } else {
                showNotificationAlert(response.message || 'Failed to add result', 'error');
            }
        } catch (err) { showNotificationAlert('Error: ' + err.message, 'error'); }
    };

    const handleSendNotification = async () => {
        if (!formData.title.trim() || !formData.message.trim()) { showNotificationAlert('Fill in all fields', 'error'); return; }
        if (!formData.sendToAll && !formData.recipientId) { showNotificationAlert('Select a patient or "Send to All"', 'error'); return; }
        setLoading(true);
        try {
            const token    = localStorage.getItem('authToken');
            const endpoint = formData.sendToAll
                ? `${CONFIG.ADMIN_API_URL}/api/admin/notifications/send-all`
                : `${CONFIG.ADMIN_API_URL}/api/admin/notifications/send`;
            const payload  = formData.sendToAll
                ? { title: formData.title, message: formData.message, type: formData.type }
                : { recipientId: parseInt(formData.recipientId), title: formData.title, message: formData.message, type: formData.type };
            const res = await fetch(endpoint, {
                method:  'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body:    JSON.stringify(payload),
            });
            if (res.ok) {
                showNotificationAlert('Notification sent!');
                setFormData({ recipientId: '', title: '', message: '', type: 'appointment', sendToAll: false });
                setShowNotificationForm(false);
                loadNotifications();
            } else {
                showNotificationAlert('Failed to send notification', 'error');
            }
        } catch { showNotificationAlert('Error sending notification', 'error'); }
        finally  { setLoading(false); }
    };

    const handleCreateAutoNotification = async () => {
        if (!autoFormData.title.trim() || !autoFormData.message.trim()) { showNotificationAlert('Fill in all fields', 'error'); return; }
        setLoading(true);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                method:  'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body:    JSON.stringify(autoFormData),
            });
            if (res.ok) {
                showNotificationAlert('Auto-notification created!');
                setAutoFormData({ trigger: 'appointment_scheduled', title: '', message: '', type: 'appointment', enabled: true, delayMinutes: 0 });
                setShowAutoNotificationForm(false);
                loadAutoNotifications();
            } else {
                showNotificationAlert('Failed to create auto-notification', 'error');
            }
        } catch { showNotificationAlert('Error creating auto-notification', 'error'); }
        finally  { setLoading(false); }
    };

    const handleDeleteNotification = async (id) => {
        if (!window.confirm('Delete this notification?')) return;
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications/${id}`, {
                method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
            });
            if (res.ok) {
                setNotifications(notifications.filter(n => n.id !== id));
                showNotificationAlert('Notification deleted');
            }
        } catch (err) { console.error(err); }
    };

    const handleToggleAutoNotification = async (id, current) => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${id}/toggle`, {
                method:  'PUT',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body:    JSON.stringify({ enabled: !current }),
            });
            if (res.ok) loadAutoNotifications();
        } catch (err) { console.error(err); }
    };

    const handleDeleteAutoNotification = async (id) => {
        if (!window.confirm('Delete this auto-notification?')) return;
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${id}`, {
                method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
            });
            if (res.ok) {
                setAutoNotifications(autoNotifications.filter(n => n.id !== id));
                showNotificationAlert('Auto-notification deleted');
            }
        } catch (err) { console.error(err); }
    };

    const filteredPatients = patients.filter(p => {
        const name  = `${p.firstName || ''} ${p.lastName || ''}`.toLowerCase();
        const email = (p.email || '').toLowerCase();
        const q     = searchQuery.toLowerCase();
        return name.includes(q) || email.includes(q);
    });

    const getTriggerLabel = (t) => ({
        appointment_scheduled: 'Appointment Scheduled',
        results_ready:         'Results Ready',
        appointment_reminder:  'Appointment Reminder',
        test_booked:           'Test Booked',
        payment_approved:      'Payment Approved',
        appointment_missed:    'Appointment Missed',
    }[t] || t);

    const getTypeIcon = (t) => ({
        appointment: '📅', results: '📊', alert: '⚠️', reminder: '🔔', payment: '💰',
    }[t] || '📬');

    const getCurrentUser = () => {
        try { return JSON.parse(localStorage.getItem('user_info') || '{}'); } catch { return {}; }
    };

    // ── Guards ────────────────────────────────────────────────────────────────
    if (validatingToken) {
        return <div className="loading"><div className="spinner"></div><p>Verifying session...</p></div>;
    }
    if (!isAuthenticated) {
        return React.createElement(Login, { onLoginSuccess: handleLoginSuccess });
    }
    if (loading && patients.length === 0) {
        return <div className="loading"><div className="spinner"></div><p>Loading dashboard...</p></div>;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    return (
        <div className="dashboard" style={{ display: 'flex', minHeight: '100vh' }}>
            <style>{`
                @keyframes pulse {
                    0%   { box-shadow: 0 0 0 0   rgba(239,68,68,0.6), 0 4px 14px rgba(239,68,68,0.4); }
                    70%  { box-shadow: 0 0 0 12px rgba(239,68,68,0),   0 4px 14px rgba(239,68,68,0.4); }
                    100% { box-shadow: 0 0 0 0   rgba(239,68,68,0),   0 4px 14px rgba(239,68,68,0.4); }
                }
            `}</style>

            {React.createElement(Sidebar, { currentView, setCurrentView, onLogout: handleLogout })}

            <div className="main-content" style={{ flex: 1, overflowY: 'auto' }}>
                {React.createElement(Header)}

                {notification && (
                    <div className={`alert alert-${notification.type}`}>
                        <i className={`fas ${notification.type === 'success' ? 'fa-check-circle' : 'fa-exclamation-triangle'}`}></i>
                        {' '}{notification.message}
                    </div>
                )}

                {/* ── DASHBOARD ── */}
                {currentView === 'dashboard' && (
                    <div>
                        {React.createElement(StatsGrid, { stats })}

                        {stats.pendingPayments > 0 && (
                            <div style={{ margin: '0 20px 20px', padding: '14px 18px', background: '#fef3c7', border: '2px solid #fbbf24', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <div style={{ fontWeight: '700', color: '#92400e', fontSize: '15px' }}>
                                    ⏳ {stats.pendingPayments} payment{stats.pendingPayments > 1 ? 's' : ''} awaiting confirmation
                                </div>
                                <button onClick={() => setCurrentView('appointments')}
                                    style={{ padding: '8px 18px', background: '#d97706', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '14px' }}>
                                    Review →
                                </button>
                            </div>
                        )}

                        {stats.refundRequests > 0 && (
                            <div style={{ margin: '0 20px 20px', padding: '14px 18px', background: '#f3e8ff', border: '2px solid #a78bfa', borderRadius: '10px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                                <div style={{ fontSize: '22px' }}>🔄</div>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: '700', color: '#5b21b6', fontSize: '15px' }}>
                                        {stats.refundRequests} refund request{stats.refundRequests > 1 ? 's' : ''} pending review
                                    </div>
                                    <div style={{ fontSize: '12px', color: '#7c3aed', marginTop: '2px' }}>
                                        Go to Appointments → Refund Requests tab to review
                                    </div>
                                </div>
                            </div>
                        )}

                        <div className="content-grid">
                            {React.createElement(MainPanel, {
                                patients: filteredPatients, searchQuery, setSearchQuery,
                                setSelectedPatient, setShowModal,
                                onUpdateAppointment: handleUpdateAppointment,
                            })}
                            {React.createElement(SidePanel, {
                                appointments, setShowModal, onRefresh: loadAppointments,
                            })}
                        </div>
                    </div>
                )}

                {currentView === 'patients' && React.createElement(PatientsView, {
                    patients: filteredPatients, searchQuery, setSearchQuery,
                    setSelectedPatient, setShowModal,
                    onUpdateAppointment: handleUpdateAppointment,
                })}

                {currentView === 'appointments' && React.createElement(AppointmentsView, {
                    appointments, setShowModal,
                    onRefresh: () => { loadAppointments(); loadRefundRequests(); },
                })}

                {currentView === 'reports' && React.createElement(ReportsView, { testResults, setShowModal })}

                {currentView === 'notifications' && (
                    <div style={{ padding: '20px', maxWidth: '1200px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                            <h2 style={{ fontSize: '28px', fontWeight: 'bold' }}>Notification Manager</h2>
                        </div>

                        <div style={{ borderBottom: '1px solid #ccc', marginBottom: '20px' }}>
                            {['sent', 'auto'].map(tab => (
                                <button key={tab} onClick={() => setNotificationTab(tab)}
                                    style={{ padding: '10px 20px', borderBottom: notificationTab === tab ? '3px solid #007bff' : 'none', background: 'none', border: 'none', cursor: 'pointer', fontWeight: notificationTab === tab ? 'bold' : 'normal' }}>
                                    {tab === 'sent' ? 'Send Notifications' : 'Auto Notifications'}
                                </button>
                            ))}
                        </div>

                        {notificationTab === 'sent' && (
                            <div>
                                <button onClick={() => setShowNotificationForm(!showNotificationForm)}
                                    style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginBottom: '20px' }}>
                                    Send New Notification
                                </button>
                                {showNotificationForm && (
                                    <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '5px', marginBottom: '20px' }}>
                                        <label style={{ display: 'block', marginBottom: '10px' }}>
                                            <input type="checkbox" checked={formData.sendToAll}
                                                onChange={e => setFormData({ ...formData, sendToAll: e.target.checked })} />
                                            {' '}Send to All Patients
                                        </label>
                                        {!formData.sendToAll && (
                                            <div style={{ marginBottom: '10px' }}>
                                                <label style={{ display: 'block', marginBottom: '5px' }}>Select Patient ({patients.length} available)</label>
                                                <select value={formData.recipientId}
                                                    onChange={e => setFormData({ ...formData, recipientId: e.target.value })}
                                                    style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                    <option value="">Choose a patient...</option>
                                                    {patients.map(p => <option key={p.id} value={p.id}>{p.firstName} {p.lastName} ({p.email})</option>)}
                                                </select>
                                            </div>
                                        )}
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={formData.type}
                                                onChange={e => setFormData({ ...formData, type: e.target.value })}
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                                <option value="payment">Payment</option>
                                            </select>
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={formData.title}
                                                onChange={e => setFormData({ ...formData, title: e.target.value })}
                                                placeholder="Title" maxLength="100"
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={formData.message}
                                                onChange={e => setFormData({ ...formData, message: e.target.value })}
                                                placeholder="Message" maxLength="500" rows="4"
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
                                        </div>
                                        <button onClick={handleSendNotification} disabled={loading}
                                            style={{ padding: '10px 20px', background: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginRight: '10px' }}>
                                            {loading ? 'Sending...' : 'Send'}
                                        </button>
                                        <button onClick={() => setShowNotificationForm(false)}
                                            style={{ padding: '10px 20px', background: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                            Cancel
                                        </button>
                                    </div>
                                )}
                                <div style={{ marginTop: '20px' }}>
                                    <h3>Sent Notifications ({notifications.length})</h3>
                                    {notifications.length === 0
                                        ? <p>No notifications sent</p>
                                        : notifications.map(notif => (
                                            <div key={notif.id} style={{ background: '#fff', border: '1px solid #ddd', padding: '15px', marginBottom: '10px', borderRadius: '5px' }}>
                                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                    <div>
                                                        <h4 style={{ margin: '0 0 5px' }}>{notif.title}</h4>
                                                        <p style={{ margin: '0 0 5px', color: '#666' }}>{notif.message}</p>
                                                        <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>
                                                            To: {notif.recipientName || 'All Patients'} | {new Date(notif.createdAt).toLocaleString()}
                                                        </p>
                                                    </div>
                                                    <button onClick={() => handleDeleteNotification(notif.id)}
                                                        style={{ padding: '5px 10px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                                        Delete
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                </div>
                            </div>
                        )}

                        {notificationTab === 'auto' && (
                            <div>
                                <button onClick={() => setShowAutoNotificationForm(!showAutoNotificationForm)}
                                    style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginBottom: '20px' }}>
                                    Create Auto Notification
                                </button>
                                {showAutoNotificationForm && (
                                    <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '5px', marginBottom: '20px' }}>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Trigger Event</label>
                                            <select value={autoFormData.trigger}
                                                onChange={e => setAutoFormData({ ...autoFormData, trigger: e.target.value })}
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment_scheduled">Appointment Scheduled</option>
                                                <option value="results_ready">Results Ready</option>
                                                <option value="appointment_reminder">Appointment Reminder</option>
                                                <option value="test_booked">Test Booked</option>
                                                <option value="payment_approved">Payment Approved</option>
                                                <option value="appointment_missed">Appointment Missed</option>
                                            </select>
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Delay (minutes)</label>
                                            <input type="number" value={autoFormData.delayMinutes}
                                                onChange={e => setAutoFormData({ ...autoFormData, delayMinutes: parseInt(e.target.value) || 0 })}
                                                min="0" max="1440"
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={autoFormData.type}
                                                onChange={e => setAutoFormData({ ...autoFormData, type: e.target.value })}
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                                <option value="payment">Payment</option>
                                            </select>
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={autoFormData.title}
                                                onChange={e => setAutoFormData({ ...autoFormData, title: e.target.value })}
                                                placeholder="Title" maxLength="100"
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={autoFormData.message}
                                                onChange={e => setAutoFormData({ ...autoFormData, message: e.target.value })}
                                                placeholder="Message" maxLength="500" rows="4"
                                                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
                                        </div>
                                        <button onClick={handleCreateAutoNotification} disabled={loading}
                                            style={{ padding: '10px 20px', background: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginRight: '10px' }}>
                                            {loading ? 'Creating...' : 'Create'}
                                        </button>
                                        <button onClick={() => setShowAutoNotificationForm(false)}
                                            style={{ padding: '10px 20px', background: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                            Cancel
                                        </button>
                                    </div>
                                )}
                                <div style={{ marginTop: '20px' }}>
                                    <h3>Auto Notifications</h3>
                                    {autoNotifications.length === 0
                                        ? <p>No auto notifications configured</p>
                                        : autoNotifications.map(an => (
                                            <div key={an.id} style={{ background: '#fff', border: '1px solid #ddd', padding: '15px', marginBottom: '10px', borderRadius: '5px' }}>
                                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                    <div style={{ flex: 1 }}>
                                                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '5px' }}>
                                                            <h4 style={{ margin: 0, marginRight: '10px' }}>{an.title}</h4>
                                                            <span style={{ padding: '2px 8px', borderRadius: '12px', fontSize: '12px', background: an.enabled ? '#d4edda' : '#f8d7da', color: an.enabled ? '#155724' : '#721c24' }}>
                                                                {an.enabled ? 'Active' : 'Disabled'}
                                                            </span>
                                                        </div>
                                                        <p style={{ margin: '5px 0', color: '#666' }}>{an.message}</p>
                                                        <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>
                                                            Trigger: {getTriggerLabel(an.trigger)} | Delay: {an.delayMinutes} min | {getTypeIcon(an.type)} {an.type}
                                                        </p>
                                                    </div>
                                                    <div style={{ display: 'flex', gap: '5px' }}>
                                                        <button onClick={() => handleToggleAutoNotification(an.id, an.enabled)}
                                                            style={{ padding: '5px 10px', background: an.enabled ? '#ffc107' : '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                                            {an.enabled ? 'Disable' : 'Enable'}
                                                        </button>
                                                        <button onClick={() => handleDeleteAutoNotification(an.id)}
                                                            style={{ padding: '5px 10px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                                            Delete
                                                        </button>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* ══ MODALS ══ */}
            {showModal === 'add-result' && React.createElement(AddResultModal, {
                patients, appointments,
                onClose: () => setShowModal(null),
                onAdd:   handleAddResult,
                showNotification: showNotificationAlert,
            })}

            {showModal === 'patient-details' && selectedPatient && React.createElement(PatientDetailsModal, {
                patient:      selectedPatient,
                testResults:  testResults.filter(r => r.patientId === selectedPatient.id),
                appointments: appointments.filter(a => a.patientId === selectedPatient.id),
                onClose:      () => { setShowModal(null); setSelectedPatient(null); },
                showNotification: showNotificationAlert,
                loadTestResults,
            })}

            {showSupportChat && window.ChatSupportModal && React.createElement(window.ChatSupportModal, {
                onClose: () => {
                    setShowSupportChat(false);
                    setSupportChatPatient(null);
                    // Do NOT reset chatUnreadCount here — let loadChatUnreadCount
                    // set it correctly on the next tick (10 s interval)
                    loadChatUnreadCount();
                },
                isAdmin:         true,
                currentUser:     getCurrentUser(),
                selectedPatient: supportChatPatient,
            })}

            {/* ── Floating support button ── */}
            {!showSupportChat && (
                <button
                    onClick={() => {
                        setSupportChatPatient(null);
                        setShowSupportChat(true);
                        // Do NOT set chatUnreadCount = 0 here.
                        // The modal's first loadConversations() will emit the real count.
                    }}
                    title="Open Patient Support Chat"
                    style={{
                        position:        'fixed', bottom: '24px', right: '24px',
                        width:           '56px',  height: '56px',
                        borderRadius:    '50%',
                        backgroundColor: chatUnreadCount > 0 ? '#ef4444' : '#3b82f6',
                        color:           'white', border: 'none',
                        cursor:          'pointer', display: 'flex',
                        alignItems:      'center', justifyContent: 'center',
                        fontSize:        '20px',
                        boxShadow:       chatUnreadCount > 0
                            ? '0 0 0 4px rgba(239,68,68,0.3), 0 4px 14px rgba(239,68,68,0.5)'
                            : '0 4px 14px rgba(59,130,246,0.5)',
                        zIndex:          999,
                        transition:      'transform 0.15s, background-color 0.3s, box-shadow 0.3s',
                        animation:       chatUnreadCount > 0 ? 'pulse 1.5s infinite' : 'none',
                    }}
                    onMouseEnter={e => { e.currentTarget.style.transform = 'scale(1.1)'; }}
                    onMouseLeave={e => { e.currentTarget.style.transform = 'scale(1)';   }}
                >
                    <i className={chatUnreadCount > 0 ? 'fas fa-exclamation' : 'fas fa-headset'}></i>

                    {chatUnreadCount > 0 && (
                        <span style={{
                            position:        'absolute', top: '-6px', right: '-6px',
                            minWidth:        '22px',     height: '22px',
                            borderRadius:    '11px',
                            backgroundColor: '#fbbf24', color: '#1f2937',
                            fontSize:        '11px',    fontWeight: '900',
                            display:         'flex',    alignItems: 'center', justifyContent: 'center',
                            padding:         '0 4px',   border: '2px solid white',
                            zIndex:          1000,
                        }}>
                            {chatUnreadCount > 9 ? '9+' : chatUnreadCount}
                        </span>
                    )}

                    {chatUnreadCount > 0 && (
                        <span style={{
                            position:        'absolute', right: '64px',
                            backgroundColor: '#1f2937',  color: 'white',
                            fontSize:        '12px',     fontWeight: '600',
                            padding:         '5px 10px', borderRadius: '6px',
                            whiteSpace:      'nowrap',   pointerEvents: 'none',
                        }}>
                            {chatUnreadCount} patient{chatUnreadCount > 1 ? 's need' : ' needs'} help
                        </span>
                    )}
                </button>
            )}
        </div>
    );
};

window.MedicalAdminDashboard = MedicalAdminDashboard;