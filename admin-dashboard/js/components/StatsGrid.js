const StatsGrid = ({ stats }) => {
    const statsData = [
        { 
            title: 'Total Patients', 
            value: stats.totalPatients || 0, 
            change: stats.patientsGrowth || '+0%',
            icon: 'patients'
        },
        { 
            title: "Today's Appointments", 
            value: stats.todayAppointments || 0, 
            change: stats.appointmentsToday || '0 scheduled',
            icon: 'appointments'
        },
        { 
            title: 'Pending Tests', 
            value: stats.pendingTests || 0, 
            change: stats.urgentTests ? `Urgent: ${stats.urgentTests}` : 'No urgent tests',
            icon: 'tests'
        },
        { 
            title: 'Completed Reports', 
            value: stats.completedReports || 0, 
            change: stats.reportsGrowth || '+0%',
            icon: 'reports'
        }
    ];

    return (
        <div className="stats-grid">
            {statsData.map((stat, index) => (
                <div key={index} className="stat-card">
                    <div className="stat-header">
                        <span className="stat-label">{stat.title}</span>
                        <div className={`stat-icon ${stat.icon}`}>
                            <i className={`fas fa-${
                                stat.icon === 'patients' ? 'users' :
                                stat.icon === 'appointments' ? 'calendar-check' :
                                stat.icon === 'tests' ? 'vial' : 'chart-bar'
                            }`}></i>
                        </div>
                    </div>
                    <div className="stat-value">{stat.value}</div>
                    <div className="stat-label">{stat.change}</div>
                </div>
            ))}
        </div>
    );
};