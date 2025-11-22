const MedicalAdminDashboard = () => {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [currentView, setCurrentView] = useState('dashboard');
    const [patients, setPatients] = useState([]);
    const [appointments, setAppointments] = useState([]);
    const [testResults, setTestResults] = useState([]);
    const [notifications, setNotifications] = useState([]);
    const [autoNotifications, setAutoNotifications] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showModal, setShowModal] = useState(null);
    const [selectedPatient, setSelectedPatient] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [notification, setNotification] = useState(null);
    const [showNotificationForm, setShowNotificationForm] = useState(false);
    const [showAutoNotificationForm, setShowAutoNotificationForm] = useState(false);
    const [notificationTab, setNotificationTab] = useState('sent');
    
    const [formData, setFormData] = useState({
        recipientId: '',
        title: '',
        message: '',
        type: 'appointment',
        sendToAll: false,
    });

    const [autoFormData, setAutoFormData] = useState({
        trigger: 'appointment_scheduled',
        title: '',
        message: '',
        type: 'appointment',
        enabled: true,
        delayMinutes: 0,
    });

    const [stats, setStats] = useState({
        totalPatients: 0,
        todayAppointments: 0,
        pendingTests: 0,
        completedReports: 0,
        totalNotifications: 0,
        activeAutoRules: 0
    });

    useEffect(() => {
        console.log('Dashboard mounting...');
        const token = localStorage.getItem('authToken');
        if (token) {
            setIsAuthenticated(true);
            loadDashboardData();
        }
    }, []);

    useEffect(() => {
        if (patients.length > 0 || appointments.length > 0 || testResults.length > 0 || notifications.length > 0) {
            loadStats();
        }
    }, [patients, appointments, testResults, notifications, autoNotifications]);

    const showNotificationAlert = (message, type = 'success') => {
        setNotification({ message, type });
        setTimeout(() => setNotification(null), 4000);
    };

    useEffect(() => {
    if (isAuthenticated && window.AdminWebSocketClient) {
        const wsClient = new window.AdminWebSocketClient();
        wsClient.connect();
        
        // Expose functions for WebSocket callbacks
        window.loadAppointments = loadAppointments;
        window.loadPatients = loadPatients;
        window.loadTestResults = loadTestResults;
        window.loadStats = loadStats;
        window.showNotificationAlert = showNotificationAlert;
        
        return () => {
            wsClient.disconnect();
            delete window.loadAppointments;
            delete window.loadPatients;
            delete window.loadTestResults;
            delete window.loadStats;
            delete window.showNotificationAlert;
        };
    }
}, [isAuthenticated]);

    // Add this to your Admin Dashboard JavaScript file

    const handleLoginSuccess = () => {
        console.log('Login successful');
        setIsAuthenticated(true);
        loadDashboardData();
    };

    const handleLogout = () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('user_info');
        setIsAuthenticated(false);
        setPatients([]);
        setAppointments([]);
        setTestResults([]);
        setNotifications([]);
        setAutoNotifications([]);
    };

    const loadDashboardData = async () => {
    console.log('Loading dashboard data...');
    setLoading(true);
    try {
        // Load patients first, then everything else
        await loadPatients();  // ✅ Wait for this first
        
        await Promise.all([
            loadAppointments(),
            loadTestResults(),
            loadNotifications(),
            loadAutoNotifications()
        ]);
    } catch (error) {
        console.error('Error loading dashboard data:', error);
        showNotificationAlert('Error loading dashboard data', 'error');
    } finally {
        setLoading(false);
    }
};


    const loadStats = async () => {
        try {
            const today = new Date().toDateString();
            
            const todayAppointments = appointments.filter(apt => 
                new Date(apt.appointmentDate || apt.scheduledDate).toDateString() === today
            ).length;

            const pendingTests = appointments.filter(apt => 
                apt.status && (apt.status.toLowerCase() === 'scheduled' || apt.status.toLowerCase() === 'pending')
            ).length;

            const completedReports = testResults.filter(result => 
                result.status && (result.status.toLowerCase() === 'completed' || result.status.toLowerCase() === 'normal')
            ).length;

            setStats({
                totalPatients: patients.length,
                todayAppointments,
                pendingTests,
                completedReports,
                totalNotifications: notifications.length,
                activeAutoRules: autoNotifications.filter(n => n.enabled).length
            });

            console.log('Stats updated:', {
                totalPatients: patients.length,
                todayAppointments,
                pendingTests,
                completedReports,
                totalNotifications: notifications.length,
                activeAutoRules: autoNotifications.filter(n => n.enabled).length
            });
        } catch (error) {
            console.error('Error calculating stats:', error);
        }
    };

const loadPatients = async () => {
    try {
        console.log('🔍 Loading patients...');
        const token = localStorage.getItem('authToken');
        
        // Make the request directly instead of using ApiService
        const response = await fetch(
            `${CONFIG.ADMIN_API_URL}/api/admin/patients?page=0&size=50`,
            {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            }
        );

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        console.log('✅ Patients loaded:', data);
        console.log('Patient count:', data.patients?.length || 0);
        
        if (data.patients && Array.isArray(data.patients)) {
            setPatients(data.patients);
            console.log('Patients set to state:', data.patients);
        } else {
            console.warn('No patients in response');
            setPatients([]);
        }
    } catch (error) {
        console.error('❌ Error loading patients:', error);
        showNotificationAlert('Error loading patients', 'error');
    }
};

    const loadAppointments = async () => {
        try {
            console.log('🔍 Loading appointments...');
            const token = localStorage.getItem('authToken');

            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/appointments?page=0&size=50`,
                {
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json',
                    },
                }
            );

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('✅ Appointments loaded:', data);
            console.log('Appointment count:', data.appointments?.length || 0);

            if (data.appointments && Array.isArray(data.appointments)) {
                setAppointments(data.appointments);
                console.log('Appointments set to state:', data.appointments);
            } else {
                console.warn('No appointments in response');
                setAppointments([]);
            }
        } catch (error) {
            console.error('❌ Error loading appointments:', error);
            showNotificationAlert('Error loading appointments', 'error');
        }
    };

    const loadTestResults = async () => {
        try {
            console.log('Loading test results...');
            const data = await ApiService.getTestResults(0, 50);
            setTestResults(data.results || []);
        } catch (error) {
            console.error('Error loading test results:', error);
            showNotificationAlert('Error loading test results', 'error');
        }
    };

    const loadNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });
            if (response.ok) {
                const data = await response.json();
                setNotifications(data.notifications || []);
            }
        } catch (error) {
            console.error('Error loading notifications:', error);
        }
    };

    const loadAutoNotifications = async () => {
        try {
            const token = localStorage.getItem('authToken');
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });
            if (response.ok) {
                const data = await response.json();
                setAutoNotifications(data.autoNotifications || []);
            }
        } catch (error) {
            console.error('Error loading auto notifications:', error);
        }
    };

    const handleSendNotification = async () => {
        if (!formData.title.trim() || !formData.message.trim()) {
            showNotificationAlert('Please fill in all fields', 'error');
            return;
        }

        if (!formData.sendToAll && !formData.recipientId) {
            showNotificationAlert('Please select a patient or choose "Send to All"', 'error');
            return;
        }

        setLoading(true);

        try {
            const token = localStorage.getItem('authToken');
            const endpoint = formData.sendToAll 
                ? `${CONFIG.ADMIN_API_URL}/api/admin/notifications/send-all`
                : `${CONFIG.ADMIN_API_URL}/api/admin/notifications/send`;

            const payload = formData.sendToAll
                ? { title: formData.title, message: formData.message, type: formData.type }
                : { recipientId: parseInt(formData.recipientId), title: formData.title, message: formData.message, type: formData.type };

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(payload),
            });

            if (response.ok) {
                showNotificationAlert('Notification sent successfully!');
                setFormData({ recipientId: '', title: '', message: '', type: 'appointment', sendToAll: false });
                setShowNotificationForm(false);
                loadNotifications();
            } else {
                showNotificationAlert('Failed to send notification', 'error');
            }
        } catch (err) {
            console.error('Error sending notification:', err);
            showNotificationAlert('Error sending notification', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleCreateAutoNotification = async () => {
        if (!autoFormData.title.trim() || !autoFormData.message.trim()) {
            showNotificationAlert('Please fill in all fields', 'error');
            return;
        }

        setLoading(true);

        try {
            const token = localStorage.getItem('authToken');
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(autoFormData),
            });

            if (response.ok) {
                showNotificationAlert('Auto-notification created successfully!');
                setAutoFormData({ trigger: 'appointment_scheduled', title: '', message: '', type: 'appointment', enabled: true, delayMinutes: 0 });
                setShowAutoNotificationForm(false);
                loadAutoNotifications();
            } else {
                showNotificationAlert('Failed to create auto-notification', 'error');
            }
        } catch (err) {
            console.error('Error creating auto-notification:', err);
            showNotificationAlert('Error creating auto-notification', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteNotification = async (notificationId) => {
        if (window.confirm('Are you sure?')) {
            try {
                const token = localStorage.getItem('authToken');
                const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications/${notificationId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` },
                });

                if (response.ok) {
                    setNotifications(notifications.filter(n => n.id !== notificationId));
                    showNotificationAlert('Notification deleted');
                }
            } catch (err) {
                console.error('Error deleting notification:', err);
            }
        }
    };

    const handleToggleAutoNotification = async (autoNotifId, currentStatus) => {
        try {
            const token = localStorage.getItem('authToken');
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${autoNotifId}/toggle`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: !currentStatus }),
            });

            if (response.ok) {
                loadAutoNotifications();
            }
        } catch (err) {
            console.error('Error toggling auto-notification:', err);
        }
    };

    const handleDeleteAutoNotification = async (autoNotifId) => {
        if (window.confirm('Are you sure?')) {
            try {
                const token = localStorage.getItem('authToken');
                const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications/${autoNotifId}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` },
                });

                if (response.ok) {
                    setAutoNotifications(autoNotifications.filter(n => n.id !== autoNotifId));
                    showNotificationAlert('Auto-notification deleted');
                }
            } catch (err) {
                console.error('Error deleting auto-notification:', err);
            }
        }
    };

    const handleUpdateAppointment = async (appointmentId, newStatus) => {
        try {
            const response = await ApiService.updateAppointmentStatus(appointmentId, newStatus);
            
            if (response.ok) {
                showNotificationAlert(`Appointment ${newStatus.toLowerCase()} successfully!`);
                await loadAppointments();
            } else {
                const errorData = await response.json();
                showNotificationAlert(errorData.message || 'Error updating appointment', 'error');
            }
        } catch (error) {
            console.error('Error updating appointment:', error);
            showNotificationAlert('Network error while updating appointment', 'error');
        }
    };

    

    const handleAddResult = async (resultData) => {
        try {
            console.log('=== SUBMITTING TEST RESULT ===');
            console.log('Result data:', resultData);
            
            const hasFiles = resultData.attachments && resultData.attachments.length > 0;
            
            let response;
            if (hasFiles) {
                const formData = new FormData();
                formData.append('patientId', resultData.patientId);
                formData.append('testType', resultData.testType);
                formData.append('result', resultData.result || '');
                formData.append('notes', resultData.notes || '');
                formData.append('category', resultData.category || 'General');
                formData.append('status', resultData.status || 'COMPLETED');
                formData.append('doctorName', resultData.doctorName || 'Admin');
                formData.append('testDate', resultData.testDate || new Date().toISOString());
                
                if (resultData.appointmentId) {
                    formData.append('appointmentId', resultData.appointmentId);
                }
                formData.append('markCompleted', resultData.markAppointmentCompleted || false);
                
                const attachment = resultData.attachments[0];
                const base64Data = attachment.data.split(',')[1];
                const byteCharacters = atob(base64Data);
                const byteNumbers = new Array(byteCharacters.length);
                for (let i = 0; i < byteCharacters.length; i++) {
                    byteNumbers[i] = byteCharacters.charCodeAt(i);
                }
                const byteArray = new Uint8Array(byteNumbers);
                const blob = new Blob([byteArray], { type: attachment.type });
                const file = new File([blob], attachment.name, { type: attachment.type });
                
                formData.append('file', file);
                
                const token = localStorage.getItem('authToken');
                const fetchResponse = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/results/admin/upload-with-file`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${token}`
                    },
                    body: formData
                });
                
                if (!fetchResponse.ok) {
                    throw new Error(`Server returned ${fetchResponse.status}`);
                }
                
                response = await fetchResponse.json();
            } else {
                response = await ApiService.addTestResult(resultData);
            }
            
            console.log('API response:', response);
            
            if (response.success) {
                showNotificationAlert('Test result added successfully!');
                setShowModal(null);
                await loadTestResults();
            } else {
                showNotificationAlert(response.message || 'Failed to add result', 'error');
            }
        } catch (error) {
            console.error('=== ERROR ADDING TEST RESULT ===');
            console.error('Error:', error);
            showNotificationAlert('Error: ' + error.message, 'error');
        }
    };

    const filteredPatients = patients.filter(patient => {
        const fullName = `${patient.firstName || ''} ${patient.lastName || ''}`.toLowerCase();
        const email = (patient.email || '').toLowerCase();
        const query = searchQuery.toLowerCase();
        
        return fullName.includes(query) || email.includes(query);
    });

    const getTriggerLabel = (trigger) => {
        const labels = {
            appointment_scheduled: 'Appointment Scheduled',
            results_ready: 'Results Ready',
            appointment_reminder: 'Appointment Reminder',
            test_booked: 'Test Booked',
        };
        return labels[trigger] || trigger;
    };

    const getTypeIcon = (type) => {
        const icons = { appointment: '📅', results: '📊', alert: '⚠️', reminder: '🔔' };
        return icons[type] || '📬';
    };

    console.log('Rendering dashboard, isAuthenticated:', isAuthenticated);

    if (!isAuthenticated) {
        return React.createElement(Login, { onLoginSuccess: handleLoginSuccess });
    }

    if (loading && patients.length === 0) {
        return (
            <div className="loading">
                <div className="spinner"></div>
                <p>Loading dashboard data...</p>
            </div>
        );
    }

    return (
        <div className="dashboard" style={{ display: 'flex', minHeight: '100vh' }}>
            {React.createElement(Sidebar, { 
                currentView, 
                setCurrentView, 
                onLogout: handleLogout 
            })}
            <div className="main-content" style={{ flex: 1, overflowY: 'auto' }}>
                {React.createElement(Header)}
                
                {notification && (
                    <div className={`alert alert-${notification.type}`}>
                        <i className={`fas ${notification.type === 'success' ? 'fa-check-circle' : 'fa-exclamation-triangle'}`}></i>
                        {notification.message}
                    </div>
                )}
                
                {currentView === 'dashboard' && (
                    <div>
                        {React.createElement(StatsGrid, { stats })}
                        <div className="content-grid">
                            {React.createElement(MainPanel, {
                                patients: filteredPatients,
                                searchQuery,
                                setSearchQuery,
                                setSelectedPatient,
                                setShowModal,
                                onUpdateAppointment: handleUpdateAppointment
                            })}
                            {React.createElement(SidePanel, {
                                appointments,
                                setShowModal
                            })}
                        </div>
                    </div>
                )}
                
                {currentView === 'patients' && React.createElement(PatientsView, {
                    patients: filteredPatients,
                    searchQuery,
                    setSearchQuery,
                    setSelectedPatient,
                    setShowModal,
                    onUpdateAppointment: handleUpdateAppointment
                })}
                
                {currentView === 'appointments' && React.createElement(AppointmentsView, {
                    appointments,
                    setShowModal
                })}
                
                {currentView === 'reports' && React.createElement(ReportsView, {
                    testResults,
                    setShowModal
                })}

                {currentView === 'notifications' && (
                    <div style={{ padding: '20px', maxWidth: '1200px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                            <h2 style={{ fontSize: '28px', fontWeight: 'bold' }}>Notification Manager</h2>
                        </div>

                        <div style={{ borderBottom: '1px solid #ccc', marginBottom: '20px' }}>
                            <button onClick={() => setNotificationTab('sent')} style={{ padding: '10px 20px', borderBottom: notificationTab === 'sent' ? '3px solid #007bff' : 'none', background: 'none', border: 'none', cursor: 'pointer', fontWeight: notificationTab === 'sent' ? 'bold' : 'normal' }}>
                                Send Notifications
                            </button>
                            <button onClick={() => setNotificationTab('auto')} style={{ padding: '10px 20px', borderBottom: notificationTab === 'auto' ? '3px solid #007bff' : 'none', background: 'none', border: 'none', cursor: 'pointer', fontWeight: notificationTab === 'auto' ? 'bold' : 'normal' }}>
                                Auto Notifications
                            </button>
                        </div>

                        {notificationTab === 'sent' && (
                            <div>
                                <button onClick={() => setShowNotificationForm(!showNotificationForm)} style={{ padding: '10px 20px', background: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginBottom: '20px' }}>
                                    Send New Notification
                                </button>

                                {showNotificationForm && (
                                    <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '5px', marginBottom: '20px' }}>
                                        <label style={{ display: 'block', marginBottom: '10px' }}>
                                            <input type="checkbox" checked={formData.sendToAll} onChange={(e) => setFormData({ ...formData, sendToAll: e.target.checked })} />
                                            Send to All Patients
                                        </label>

                                        {!formData.sendToAll && (
    <div style={{ marginBottom: '10px' }}>
        <label style={{ display: 'block', marginBottom: '5px' }}>
            Select Patient ({patients.length} available)
        </label>
        {patients.length === 0 ? (
            <div style={{ padding: '10px', background: '#fff3cd', border: '1px solid #ffc107', borderRadius: '5px', color: '#856404' }}>
                No patients found. Make sure patients are loaded first.
            </div>
        ) : (
            <select 
                value={formData.recipientId} 
                onChange={(e) => {
                    console.log('Selected patient ID:', e.target.value);
                    setFormData({ ...formData, recipientId: e.target.value });
                }} 
                style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}
            >
                <option value="">Choose a patient...</option>
                {patients.map(patient => (
                    <option key={patient.id} value={patient.id}>
                        {patient.firstName} {patient.lastName} ({patient.email})
                    </option>
                ))}
            </select>
        )}
    </div>
)}


                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                            </select>
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={formData.title} onChange={(e) => setFormData({ ...formData, title: e.target.value })} placeholder="Title" maxLength="100" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={formData.message} onChange={(e) => setFormData({ ...formData, message: e.target.value })} placeholder="Message" maxLength="500" rows="4" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
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
                                    <h3>Sent Notifications</h3>
                                    {notifications.length === 0 ? (
                                        <p>No notifications sent</p>
                                    ) : (
                                        <div>
                                            {notifications.map(notif => (
                                                <div key={notif.id} style={{ background: '#fff', border: '1px solid #ddd', padding: '15px', marginBottom: '10px', borderRadius: '5px' }}>
                                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                        <div>
                                                            <h4 style={{ margin: '0 0 5px 0' }}>{notif.title}</h4>
                                                            <p style={{ margin: '0 0 5px 0', color: '#666' }}>{notif.message}</p>
                                                            <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>To: {notif.recipientName || 'All Patients'} | {new Date(notif.createdAt).toLocaleString()}</p>
                                                        </div>
                                                        <button onClick={() => handleDeleteNotification(notif.id)} style={{ padding: '5px 10px', background: '#dc3545', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                                            Delete
                                                        </button>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
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
                                            <select value={autoFormData.trigger} onChange={(e) => setAutoFormData({ ...autoFormData, trigger: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment_scheduled">Appointment Scheduled</option>
                                                <option value="results_ready">Results Ready</option>
                                                <option value="appointment_reminder">Appointment Reminder</option>
                                                <option value="test_booked">Test Booked</option>
                                            </select>
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Delay (minutes)</label>
                                            <input type="number" value={autoFormData.delayMinutes} onChange={(e) => setAutoFormData({ ...autoFormData, delayMinutes: parseInt(e.target.value) || 0 })} min="0" max="1440" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Type</label>
                                            <select value={autoFormData.type} onChange={(e) => setAutoFormData({ ...autoFormData, type: e.target.value })} style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }}>
                                                <option value="appointment">Appointment</option>
                                                <option value="results">Results</option>
                                                <option value="alert">Alert</option>
                                                <option value="reminder">Reminder</option>
                                            </select>
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Title</label>
                                            <input type="text" value={autoFormData.title} onChange={(e) => setAutoFormData({ ...autoFormData, title: e.target.value })} placeholder="Title" maxLength="100" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc' }} />
                                        </div>

                                        <div style={{ marginBottom: '10px' }}>
                                            <label style={{ display: 'block', marginBottom: '5px' }}>Message</label>
                                            <textarea value={autoFormData.message} onChange={(e) => setAutoFormData({ ...autoFormData, message: e.target.value })} placeholder="Message" maxLength="500" rows="4" style={{ width: '100%', padding: '8px', borderRadius: '5px', border: '1px solid #ccc', fontFamily: 'Arial' }} />
                                        </div>

                                        <button onClick={handleCreateAutoNotification} disabled={loading} style={{ padding: '10px 20px', background: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', marginRight: '10px' }}>
                                            {loading ? 'Creating...' : 'Create'}
                                        </button>
                                        <button onClick={() => setShowAutoNotificationForm(false)} style={{ padding: '10px 20px', background: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer' }}>
                                            Cancel
                                        </button>
                                    </div>
                                )}

                                <div style={{ marginTop: '20px' }}>
                                    <h3>Auto Notifications</h3>
                                    {autoNotifications.length === 0 ? (
                                        <p>No auto notifications configured</p>
                                    ) : (
                                        <div>
                                            {autoNotifications.map(autoNotif => (
                                                <div key={autoNotif.id} style={{ background: '#fff', border: '1px solid #ddd', padding: '15px', marginBottom: '10px', borderRadius: '5px' }}>
                                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                        <div style={{ flex: 1 }}>
                                                            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '5px' }}>
                                                                <h4 style={{ margin: 0, marginRight: '10px' }}>{autoNotif.title}</h4>
                                                                <span style={{ 
                                                                    padding: '2px 8px', 
                                                                    borderRadius: '12px', 
                                                                    fontSize: '12px', 
                                                                    background: autoNotif.enabled ? '#d4edda' : '#f8d7da',
                                                                    color: autoNotif.enabled ? '#155724' : '#721c24'
                                                                }}>
                                                                    {autoNotif.enabled ? 'Active' : 'Disabled'}
                                                                </span>
                                                            </div>
                                                            <p style={{ margin: '5px 0', color: '#666' }}>{autoNotif.message}</p>
                                                            <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>
                                                                Trigger: {getTriggerLabel(autoNotif.trigger)} | 
                                                                Delay: {autoNotif.delayMinutes} min | 
                                                                Type: {getTypeIcon(autoNotif.type)} {autoNotif.type}
                                                            </p>
                                                        </div>
                                                        <div style={{ display: 'flex', gap: '5px' }}>
                                                            <button 
                                                                onClick={() => handleToggleAutoNotification(autoNotif.id, autoNotif.enabled)} 
                                                                style={{ 
                                                                    padding: '5px 10px', 
                                                                    background: autoNotif.enabled ? '#ffc107' : '#28a745',
                                                                    color: 'white', 
                                                                    border: 'none', 
                                                                    borderRadius: '5px', 
                                                                    cursor: 'pointer' 
                                                                }}
                                                            >
                                                                {autoNotif.enabled ? 'Disable' : 'Enable'}
                                                            </button>
                                                            <button 
                                                                onClick={() => handleDeleteAutoNotification(autoNotif.id)} 
                                                                style={{ 
                                                                    padding: '5px 10px', 
                                                                    background: '#dc3545', 
                                                                    color: 'white', 
                                                                    border: 'none', 
                                                                    borderRadius: '5px', 
                                                                    cursor: 'pointer' 
                                                                }}
                                                            >
                                                                Delete
                                                            </button>
                                                        </div>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>


            {showModal === 'add-result' && React.createElement(AddResultModal, {
                patients,
                appointments,
                onClose: () => setShowModal(null),
                onAdd: handleAddResult,
                showNotification: showNotificationAlert
            })}

            {showModal === 'patient-details' && selectedPatient && React.createElement(PatientDetailsModal, {
                patient: selectedPatient,
                testResults: testResults.filter(result => result.patientId === selectedPatient.id),
                appointments: appointments.filter(apt => apt.patientId === selectedPatient.id),
                onClose: () => {
                    setShowModal(null);
                    setSelectedPatient(null);
                },
                showNotification: showNotificationAlert,
                loadTestResults
            })}
        </div>
    );
};
    
    
