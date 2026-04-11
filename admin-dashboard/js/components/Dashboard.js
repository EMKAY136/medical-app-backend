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
    const [showNotificationForm, setShowNotificationForm]     = useState(false);
    const [showAutoNotificationForm, setShowAutoNotificationForm] = useState(false);
    const [notificationTab, setNotificationTab]   = useState('sent');

    // Support Chat
    const [showSupportChat, setShowSupportChat]     = useState(false);
    const [supportChatPatient, setSupportChatPatient] = useState(null);

    const [formData, setFormData] = useState({ recipientId: '', title: '', message: '', type: 'appointment', sendToAll: false });
    const [autoFormData, setAutoFormData] = useState({ trigger: 'appointment_scheduled', title: '', message: '', type: 'appointment', enabled: true, delayMinutes: 0 });

    const [stats, setStats] = useState({
        totalPatients: 0, todayAppointments: 0, pendingTests: 0,
        completedReports: 0, totalNotifications: 0, activeAutoRules: 0,
        pendingPayments: 0, missedAppointments: 0,
    });

    // ── Init ─────────────────────────────────────────────────────────────────
    useEffect(() => {
        const token = localStorage.getItem('authToken');
        if (token) { setIsAuthenticated(true); loadDashboardData(); }
    }, []);

    useEffect(() => {
        if (patients.length > 0 || appointments.length > 0 || testResults.length > 0) loadStats();
    }, [patients, appointments, testResults, notifications, autoNotifications]);

    // Expose globals for WebSocket callbacks
    useEffect(() => {
        if (isAuthenticated && window.AdminWebSocketClient) {
            const wsClient = new window.AdminWebSocketClient();
            wsClient.connect();
            window.loadAppointments     = loadAppointments;
            window.loadPatients         = loadPatients;
            window.loadTestResults      = loadTestResults;
            window.loadStats            = loadStats;
            window.showNotificationAlert = showNotificationAlert;
            return () => {
                wsClient.disconnect();
                ['loadAppointments','loadPatients','loadTestResults','loadStats','showNotificationAlert']
                    .forEach(k => delete window[k]);
            };
        }
    }, [isAuthenticated]);

    // ── Helpers ──────────────────────────────────────────────────────────────
    const showNotificationAlert = (message, type = 'success') => {
        setNotification({ message, type });
        setTimeout(() => setNotification(null), 4000);
    };

    const parseDate = (v) => {
        if (!v) return null;
        if (Array.isArray(v)) { const [yr, mo, dy, hr = 0, mn = 0] = v; return new Date(yr, mo - 1, dy, hr, mn); }
        const d = new Date(v); return isNaN(d.getTime()) ? null : d;
    };

    // ── Data loaders ─────────────────────────────────────────────────────────
    const loadDashboardData = async () => {
        setLoading(true);
        try {
            await loadPatients();
            await Promise.all([loadAppointments(), loadTestResults(), loadNotifications(), loadAutoNotifications()]);
        } catch (err) {
            showNotificationAlert('Error loading dashboard data', 'error');
        } finally {
            setLoading(false);
        }
    };

    const loadPatients = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/patients?page=0&size=100`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setPatients(data.patients && Array.isArray(data.patients) ? data.patients : []);
        } catch (err) {
            console.error('Error loading patients:', err);
            showNotificationAlert('Error loading patients', 'error');
        }
    };

    const loadAppointments = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/appointments?page=0&size=200`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setAppointments(data.appointments && Array.isArray(data.appointments) ? data.appointments : []);
        } catch (err) {
            console.error('Error loading appointments:', err);
            showNotificationAlert('Error loading appointments', 'error');
        }
    };

    const loadTestResults = async () => {
        try {
            const data = await ApiService.getTestResults(0, 100);
            setTestResults(data.results || []);
        } catch (err) {
            console.error('Error loading test results:', err);
        }
    };

    const loadNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            if (res.ok) { const data = await res.json(); setNotifications(data.notifications || []); }
        } catch (err) { console.error('Error loading notifications:', err); }
    };

    const loadAutoNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            if (res.ok) { const data = await res.json(); setAutoNotifications(data.autoNotifications || []); }
        } catch (err) { console.error('Error loading auto-notifications:', err); }
    };

    const loadStats = () => {
        try {
            const today = new Date().toDateString();
            const now   = new Date();

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

            // NEW stats
            const pendingPayments = appointments.filter(apt =>
                (apt.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION'
            ).length;

           // CORRECT — only count what's explicitly marked MISSED in the DB
const missedAppointments = appointments.filter(apt =>
    (apt.status || '').toUpperCase() === 'MISSED'
).length;

            setStats({
                totalPatients: patients.length,
                todayAppointments: todayApts,
                pendingTests,
                completedReports,
                totalNotifications: notifications.length,
                activeAutoRules: autoNotifications.filter(n => n.enabled).length,
                pendingPayments,
                missedAppointments,
            });
        } catch (err) { console.error('Error calculating stats:', err); }
    };

    // ── Actions ──────────────────────────────────────────────────────────────
    const handleLoginSuccess  = () => { setIsAuthenticated(true); loadDashboardData(); };
    const handleLogout        = () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('user_info');
        setIsAuthenticated(false);
        setPatients([]); setAppointments([]); setTestResults([]);
        setNotifications([]); setAutoNotifications([]);
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
        } catch (err) {
            showNotificationAlert('Network error while updating appointment', 'error');
        }
    };

    const handleAddResult = async (resultData) => {
        try {
            const hasFiles = resultData.attachments && resultData.attachments.length > 0;
            let response;
            if (hasFiles) {
                const fd = new FormData();
                fd.append('patientId', resultData.patientId);
                fd.append('testType', resultData.testType);
                fd.append('result', resultData.result || '');
                fd.append('notes', resultData.notes || '');
                fd.append('category', resultData.category || 'General');
                fd.append('status', resultData.status || 'COMPLETED');
                fd.append('doctorName', resultData.doctorName || 'Admin');
                fd.append('testDate', resultData.testDate || new Date().toISOString());
                if (resultData.appointmentId) fd.append('appointmentId', resultData.appointmentId);
                fd.append('markCompleted', resultData.markAppointmentCompleted || false);
                const att = resultData.attachments[0];
                const b64 = att.data.split(',')[1];
                const bytes = atob(b64);
                const arr = new Uint8Array(bytes.length);
                for (let i = 0; i < bytes.length; i++) arr[i] = bytes.charCodeAt(i);
                fd.append('file', new File([new Blob([arr], { type: att.type })], att.name, { type: att.type }));
                const token = localStorage.getItem('authToken');
                const fetchRes = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/upload-result-with-file`, {
                    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd
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
        } catch (err) {
            showNotificationAlert('Error: ' + err.message, 'error');
        }
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
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                showNotificationAlert('Notification sent!');
                setFormData({ recipientId: '', title: '', message: '', type: 'appointment', sendToAll: false });
                setShowNotificationForm(false);
                loadNotifications();
            } else { showNotificationAlert('Failed to send notification', 'error'); }
        } catch (err) { showNotificationAlert('Error sending notification', 'error'); }
        finally { setLoading(false); }
    };

    const handleCreateAutoNotification = async () => {
        if (!autoFormData.title.trim() || !autoFormData.message.trim()) { showNotificationAlert('Fill in all fields', 'error'); return; }
        setLoading(true);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body: JSON.stringify(autoFormData),
            });
            if (res.ok) {
                showNotificationAlert('Auto-notification created!');
                setAutoFormData({ trigger: 'appointment_scheduled', title: '', message: '', type: 'appointment', enabled: true, delayMinutes: 0 });
                setShowAutoNotificationForm(false);
                loadAutoNotifications();
            } else { showNotificationAlert('Failed to create auto-notification', 'error'); }
        } catch (err) { showNotificationAlert('Error creating auto-notification', 'error'); }
        finally { setLoading(false); }
    };

    const handleDeleteNotification = async (id) => {
        if (!window.confirm('Delete this notification?')) return;
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } });
            if (res.ok) { setNotifications(notifications.filter(n => n.id !== id)); showNotificationAlert('Notification deleted'); }
        } catch (err) { console.error(err); }
    };

    const handleToggleAutoNotification = async (id, current) => {
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${id}/toggle`, {
                method: 'PUT',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: !current }),
            });
            if (res.ok) loadAutoNotifications();
        } catch (err) { console.error(err); }
    };

    const handleDeleteAutoNotification = async (id) => {
        if (!window.confirm('Delete this auto-notification?')) return;
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } });
            if (res.ok) { setAutoNotifications(autoNotifications.filter(n => n.id !== id)); showNotificationAlert('Auto-notification deleted'); }
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

    const getTypeIcon = (t) => ({ appointment: '📅', results: '📊', alert: '⚠️', reminder: '🔔', payment: '💰' }[t] || '📬');

    const getCurrentUser = () => { try { return JSON.parse(localStorage.getItem('user_info') || '{}'); } catch { return {}; } };

    // ── Guards ───────────────────────────────────────────────────────────────
    if (!isAuthenticated) return React.createElement(Login, { onLoginSuccess: handleLoginSuccess });
    if (loading && patients.length === 0) return (
        <div className="loading"><div className="spinner"></div><p>Loading dashboard...</p></div>
    );

    // ── Render ───────────────────────────────────────────────────────────────
    return (
        <div className="dashboard" style={{ display: 'flex', minHeight: '100vh' }}>
            {React.createElement(Sidebar, { currentView, setCurrentView, onLogout: handleLogout })}

            <div className="main-content" style={{ flex: 1, overflowY: 'auto' }}>
                {React.createElement(Header)}

                {notification && (
                    <div className={`alert alert-${notification.type}`}>
                        <i className={`fas ${notification.type === 'success' ? 'fa-check-circle' : 'fa-exclamation-triangle'}`}></i>
                        {notification.message}
                    </div>
                )}

                {/* ── DASHBOARD ── */}
                {currentView === 'dashboard' && (
                    <div>
                        {/* Extended stats grid */}
                        {React.createElement(StatsGrid, { stats })}

                        {/* Pending payments banner */}
                        {stats.pendingPayments > 0 && (
                            <div style={{
                                margin: '0 20px 20px',
                                padding: '14px 18px',
                                background: '#fef3c7',
                                border: '2px solid #fbbf24',
                                borderRadius: '10px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between'
                            }}>
                                <div style={{ fontWeight: '700', color: '#92400e', fontSize: '15px' }}>
                                    ⏳ {stats.pendingPayments} payment{stats.pendingPayments > 1 ? 's' : ''} awaiting confirmation
                                </div>
                                <button
                                    onClick={() => setCurrentView('appointments')}
                                    style={{ padding: '8px 18px', background: '#d97706', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '14px' }}
                                >
                                    Review →
                                </button>
                            </div>
                        )}

                        {/* Missed appointments banner */}
                        {stats.missedAppointments > 0 && (
                            <div style={{
                                margin: '0 20px 20px',
                                padding: '14px 18px',
                                background: '#ffedd5',
                                border: '2px solid #ea580c',
                                borderRadius: '10px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between'
                            }}>
                                <div style={{ fontWeight: '700', color: '#9a3412', fontSize: '15px' }}>
                                    ⚠️ {stats.missedAppointments} missed appointment{stats.missedAppointments > 1 ? 's' : ''}
                                </div>
                                <button
                                    onClick={() => setCurrentView('appointments')}
                                    style={{ padding: '8px 18px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '14px' }}
                                >
                                    View →
                                </button>
                            </div>
                        )}

                        <div className="content-grid">
                            {React.createElement(MainPanel, {
                                patients: filteredPatients, searchQuery, setSearchQuery,
                                setSelectedPatient, setShowModal, onUpdateAppointment: handleUpdateAppointment
                            })}
                            {React.createElement(SidePanel, {
                                appointments, setShowModal, onRefresh: loadAppointments
                            })}
                        </div>
                    </div>
                )}

                {/* ── PATIENTS ── */}
                {currentView === 'patients' && React.createElement(PatientsView, {
                    patients: filteredPatients, searchQuery, setSearchQuery,
                    setSelectedPatient, setShowModal, onUpdateAppointment: handleUpdateAppointment
                })}

                {/* ── APPOINTMENTS ── */}
                {currentView === 'appointments' && React.createElement(AppointmentsView, {
                    appointments, setShowModal, onRefresh: loadAppointments
                })}

                {/* ── REPORTS ── */}
                {currentView === 'reports' && React.createElement(ReportsView, { testResults, setShowModal })}

                {/* ── NOTIFICATIONS ── */}
                {currentView === 'notifications' && (
                    <div style={{ padding: '20px', maxWidth: '1200px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                            <h2 style={{ fontSize: '28px', fontWeight: 'bold' }}>Notification Manager</h2>
                        </div>

                        <div style={{ borderBottom: '1px solid #ccc', marginBottom: '20px' }}>
                            {['sent', 'auto'].map(tab => (
                                <button key={tab} onClick={() => setNotificationTab(tab)} style={{ padding: '10px 20px', borderBottom: notificationTab === tab ? '3px solid #007bff' : 'none', background: 'none', border: 'none', cursor: 'pointer', fontWeight: notificationTab === tab ? 'bold' : 'normal' }}>
                                    {tab === 'sent' ? 'Send Notifications' : 'Auto Notifications'}
                                </button>
                            ))}
                        </div>

                        {notificationTab === 'sent' && (
                            <div>
                                <button onClick={() => setShowNotificationForm(!showNotificationForm)} style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginBottom: '20px' }}>
                                    Send New Notification
                                </button>

                                {showNotificationForm && (
                                    <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '5px', marginBottom: '20px' }}>
                                        <label style={{ display: 'block', marginBottom: '10px' }}>
                                            <input type="checkbox" checked={formData.sendToAll} onChange={e => setFormData({ ...formData, sendToAll: e.target.checked })} />
                                            {' '}Send to All Patients
                                        </label>
                                        {!formData.sendToAll && (
                                            <div style={{ marginBottom: '10px' }}>
                                                <label style={{ display: 'block', marginBottom: '5px' }}>Select Patient ({patients.length} available)</label>
                                                <select value={formData.recipientId} onChange={e => setFormData({ ...formData, recipientId: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                    <option value="">Choose a patient...</option>
                                                    {patients.map(p => (
                                                        <option key={p.id} value={p.id}>{p.firstName} {p.lastName} ({p.email})</option>
                                                    ))}
                                                </select>
                                            </div>
                                        )}
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={formData.type} onChange={e => setFormData({ ...formData, type: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                                <option value="payment">Payment</option>
                                            </select>
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={formData.title} onChange={e => setFormData({ ...formData, title: e.target.value })} placeholder="Title" maxLength="100" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={formData.message} onChange={e => setFormData({ ...formData, message: e.target.value })} placeholder="Message" maxLength="500" rows="4" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
                                        </div>
                                        <button onClick={handleSendNotification} disabled={loading} style={{ padding: '10px 20px', background: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginRight: '10px' }}>
                                            {loading ? 'Sending...' : 'Send'}
                                        </button>
                                        <button onClick={() => setShowNotificationForm(false)} style={{ padding: '10px 20px', background: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                            Cancel
                                        </button>
                                    </div>
                                )}

                                <div style={{ marginTop: '20px' }}>
                                    <h3>Sent Notifications ({notifications.length})</h3>
                                    {notifications.length === 0 ? <p>No notifications sent</p> : notifications.map(notif => (
                                        <div key={notif.id} style={{ background: '#fff', border: '1px solid #ddd', padding: '15px', marginBottom: '10px', borderRadius: '5px' }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                <div>
                                                    <h4 style={{ margin: '0 0 5px' }}>{notif.title}</h4>
                                                    <p style={{ margin: '0 0 5px', color: '#666' }}>{notif.message}</p>
                                                    <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>
                                                        To: {notif.recipientName || 'All Patients'} | {new Date(notif.createdAt).toLocaleString()}
                                                    </p>
                                                </div>
                                                <button onClick={() => handleDeleteNotification(notif.id)} style={{ padding: '5px 10px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>Delete</button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {notificationTab === 'auto' && (
                            <div>
                                <button onClick={() => setShowAutoNotificationForm(!showAutoNotificationForm)} style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginBottom: '20px' }}>
                                    Create Auto Notification
                                </button>

                                {showAutoNotificationForm && (
                                    <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '5px', marginBottom: '20px' }}>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Trigger Event</label>
                                            <select value={autoFormData.trigger} onChange={e => setAutoFormData({ ...autoFormData, trigger: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
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
                                            <input type="number" value={autoFormData.delayMinutes} onChange={e => setAutoFormData({ ...autoFormData, delayMinutes: parseInt(e.target.value) || 0 })} min="0" max="1440" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={autoFormData.type} onChange={e => setAutoFormData({ ...autoFormData, type: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                                <option value="payment">Payment</option>
                                            </select>
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={autoFormData.title} onChange={e => setAutoFormData({ ...autoFormData, title: e.target.value })} placeholder="Title" maxLength="100" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>
                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={autoFormData.message} onChange={e => setAutoFormData({ ...autoFormData, message: e.target.value })} placeholder="Message" maxLength="500" rows="4" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
                                        </div>
                                        <button onClick={handleCreateAutoNotification} disabled={loading} style={{ padding: '10px 20px', background: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginRight: '10px' }}>
                                            {loading ? 'Creating...' : 'Create'}
                                        </button>
                                        <button onClick={() => setShowAutoNotificationForm(false)} style={{ padding: '10px 20px', background: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>Cancel</button>
                                    </div>
                                )}

                                <div style={{ marginTop: '20px' }}>
                                    <h3>Auto Notifications</h3>
                                    {autoNotifications.length === 0 ? <p>No auto notifications configured</p> : autoNotifications.map(an => (
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
                                                    <button onClick={() => handleToggleAutoNotification(an.id, an.enabled)} style={{ padding: '5px 10px', background: an.enabled ? '#ffc107' : '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                                        {an.enabled ? 'Disable' : 'Enable'}
                                                    </button>
                                                    <button onClick={() => handleDeleteAutoNotification(an.id)} style={{ padding: '5px 10px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>Delete</button>
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
                onAdd: handleAddResult,
                showNotification: showNotificationAlert,
            })}

            {showModal === 'patient-details' && selectedPatient && React.createElement(PatientDetailsModal, {
                patient: selectedPatient,
                testResults: testResults.filter(r => r.patientId === selectedPatient.id),
                appointments: appointments.filter(a => a.patientId === selectedPatient.id),
                onClose: () => { setShowModal(null); setSelectedPatient(null); },
                showNotification: showNotificationAlert,
                loadTestResults,
            })}

            {showSupportChat && window.ChatSupportModal && React.createElement(window.ChatSupportModal, {
                onClose: () => { setShowSupportChat(false); setSupportChatPatient(null); },
                isAdmin: true,
                currentUser: getCurrentUser(),
                selectedPatient: supportChatPatient,
            })}

            {/* Floating support button */}
            {!showSupportChat && (
                <button
                    onClick={() => { setSupportChatPatient(null); setShowSupportChat(true); }}
                    title="Open Patient Support Chat"
                    style={{ position: 'fixed', bottom: '24px', right: '24px', width: '56px', height: '56px', borderRadius: '50%', backgroundColor: '#3b82f6', color: 'white', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '20px', boxShadow: '0 4px 14px rgba(59,130,246,0.5)', zIndex: 999 }}
                    onMouseEnter={e => { e.currentTarget.style.transform = 'scale(1.1)'; }}
                    onMouseLeave={e => { e.currentTarget.style.transform = 'scale(1)'; }}
                >
                    <i className="fas fa-headset"></i>
                </button>
            )}
        </div>
    );
};