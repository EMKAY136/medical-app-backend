const StatsGrid = ({ stats }) => {
    const statsData = [
        {
            title: 'Total Patients',
            value: stats.totalPatients || 0,
            change: stats.patientsGrowth || '+0%',
            icon: 'patients',
        },
        {
            title: "Today's Appointments",
            value: stats.todayAppointments || 0,
            change: `${stats.todayAppointments || 0} scheduled`,
            icon: 'appointments',
        },
        {
            title: 'Pending Tests',
            value: stats.pendingTests || 0,
            change: stats.urgentTests ? `Urgent: ${stats.urgentTests}` : 'No urgent tests',
            icon: 'tests',
        },
        {
            title: 'Completed Reports',
            value: stats.completedReports || 0,
            change: stats.reportsGrowth || '+0%',
            icon: 'reports',
        },
        // ── NEW ──
        {
            title: 'Pending Payments',
            value: stats.pendingPayments || 0,
            change: stats.pendingPayments > 0 ? 'Awaiting confirmation' : 'All clear',
            icon: 'payment',
            alert: (stats.pendingPayments || 0) > 0,
        },
        {
            title: 'Missed Appointments',
            value: stats.missedAppointments || 0,
            change: stats.missedAppointments > 0 ? 'Needs follow-up' : 'None',
            icon: 'missed',
            alert: (stats.missedAppointments || 0) > 0,
        },
    ];

    const iconClass = (icon) => {
        const map = {
            patients:    'fa-users',
            appointments:'fa-calendar-check',
            tests:       'fa-vial',
            reports:     'fa-chart-bar',
            payment:     'fa-credit-card',
            missed:      'fa-calendar-times',
        };
        return map[icon] || 'fa-chart-line';
    };

    const iconColor = (icon, alert) => {
        if (alert) {
            return { bg: icon === 'payment' ? '#fef3c7' : '#ffedd5', color: icon === 'payment' ? '#d97706' : '#ea580c' };
        }
        const map = {
            patients:    { bg: '#dbeafe', color: '#1e40af' },
            appointments:{ bg: '#d1fae5', color: '#065f46' },
            tests:       { bg: '#ede9fe', color: '#5b21b6' },
            reports:     { bg: '#fce7f3', color: '#9d174d' },
            payment:     { bg: '#ecfdf5', color: '#065f46' },
            missed:      { bg: '#f3f4f6', color: '#6b7280' },
        };
        return map[icon] || { bg: '#f3f4f6', color: '#6b7280' };
    };

    return (
        <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))' }}>
            {statsData.map((stat, i) => {
                const ic = iconColor(stat.icon, stat.alert);
                return (
                    <div key={i} className="stat-card" style={stat.alert ? { border: `2px solid ${ic.color}30` } : {}}>
                        <div className="stat-header">
                            <span className="stat-label">{stat.title}</span>
                            <div
                                className={`stat-icon ${stat.icon}`}
                                style={{ background: ic.bg, color: ic.color }}
                            >
                                <i className={`fas ${iconClass(stat.icon)}`}></i>
                            </div>
                        </div>
                        <div className="stat-value" style={stat.alert && stat.value > 0 ? { color: ic.color } : {}}>
                            {stat.value}
                        </div>
                        <div className="stat-label" style={stat.alert && stat.value > 0 ? { color: ic.color, fontWeight: '600' } : {}}>
                            {stat.alert && stat.value > 0 ? `⚠ ${stat.change}` : stat.change}
                        </div>
                    </div>
                );
            })}
        </div>
    );
};