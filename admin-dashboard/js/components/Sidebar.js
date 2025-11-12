const Sidebar = ({ currentView, setCurrentView, onLogout }) => {
    const menuItems = [
        { id: 'dashboard', label: 'Dashboard', icon: 'fas fa-chart-line' },
        { id: 'patients', label: 'Patients', icon: 'fas fa-users' },
        { id: 'appointments', label: 'Appointments', icon: 'fas fa-calendar-alt' },
        { id: 'reports', label: 'Test Results', icon: 'fas fa-file-medical-alt' },
        { id: 'notifications', label: 'Notifications', icon: 'fas fa-bell' },  // ✅ ADD THIS LINE
    ];

    return (
        <div className="sidebar">
            <div className="sidebar-header">
                <div className="logo">
                    <i className="fas fa-heartbeat"></i>
                    Qualitest Medical
                </div>
                <p style={{fontSize: '12px', color: '#6b7280', marginTop: '5px'}}>Staff Portal</p>
            </div>
            <div className="nav-menu">
                {menuItems.map(item => (
                    <div
                        key={item.id}
                        className={`nav-item ${currentView === item.id ? 'active' : ''}`}
                        onClick={() => setCurrentView(item.id)}
                    >
                        <i className={item.icon}></i>
                        {item.label}
                    </div>
                ))}
                <div className="nav-item" onClick={onLogout} style={{marginTop: '20px', color: '#dc2626'}}>
                    <i className="fas fa-sign-out-alt"></i>
                    Logout
                </div>
            </div>
        </div>
    );
};