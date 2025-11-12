const { useState, useMemo } = React;

const AppointmentsView = ({ appointments, setShowModal }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterDate, setFilterDate] = useState('');

    // Helper to parse dates from multiple formats
    const parseDate = (dateValue) => {
        if (!dateValue) return null;
        
        try {
            // Handle array format [2025, 11, 7, hour, minute]
            if (Array.isArray(dateValue)) {
                const [year, month, day, hour = 0, minute = 0] = dateValue;
                return new Date(year, month - 1, day, hour, minute);
            }
            
            // Handle string/number format
            const date = new Date(dateValue);
            return isNaN(date.getTime()) ? null : date;
        } catch (e) {
            console.error('Date parsing error:', e);
            return null;
        }
    };

    // Filter and sort appointments
    const filteredAndSortedAppointments = useMemo(() => {
        // Filter appointments
        const filtered = appointments.filter(apt => {
            // Status filter
            const statusMatch = filterStatus === 'all' || 
                               apt.status?.toLowerCase() === filterStatus.toLowerCase();
            
            // Date filter
            let dateMatch = true;
            if (filterDate) {
                const aptDate = parseDate(
                    apt.appointmentDate || 
                    apt.scheduledDate || 
                    apt.date || 
                    apt.createdAt
                );
                
                if (aptDate) {
                    const filterDateObj = new Date(filterDate);
                    dateMatch = aptDate.toDateString() === filterDateObj.toDateString();
                } else {
                    dateMatch = false;
                }
            }
            
            return statusMatch && dateMatch;
        });

        // Sort: Oldest first (ascending order)
        const sorted = [...filtered].sort((a, b) => {
            const dateA = parseDate(
                a.appointmentDate || 
                a.scheduledDate || 
                a.date || 
                a.createdAt
            );
            const dateB = parseDate(
                b.appointmentDate || 
                b.scheduledDate || 
                b.date || 
                b.createdAt
            );
            
            if (!dateA) return 1;  // Push nulls to end
            if (!dateB) return -1; // Push nulls to end
            
            // Ascending order: oldest first
            return dateA.getTime() - dateB.getTime();
        });

        return sorted;
    }, [appointments, filterStatus, filterDate]);

    // Format date for display
    const formatDate = (dateValue) => {
        const date = parseDate(dateValue);
        if (!date) return 'Date not scheduled';
        
        try {
            return `${date.toLocaleDateString()} at ${date.toLocaleTimeString([], {
                hour: '2-digit', 
                minute: '2-digit'
            })}`;
        } catch (error) {
            return 'Invalid date';
        }
    };

    return (
        <div className="content-card">
            <div className="card-header">
                <h2 className="card-title">
                    Appointments ({filteredAndSortedAppointments.length})
                </h2>
            </div>
            <div className="card-content">
                {/* Filters */}
                <div style={{
                    display: 'flex', 
                    gap: '12px', 
                    marginBottom: '20px',
                    flexWrap: 'wrap',
                    alignItems: 'flex-end'
                }}>
                    <div>
                        <label style={{
                            display: 'block',
                            fontSize: '12px',
                            color: '#6b7280',
                            marginBottom: '4px',
                            fontWeight: '500'
                        }}>
                            Status
                        </label>
                        <select 
                            className="form-input"
                            value={filterStatus}
                            onChange={(e) => setFilterStatus(e.target.value)}
                            style={{width: '180px'}}
                        >
                            <option value="all">All Status</option>
                            <option value="scheduled">Scheduled</option>
                            <option value="completed">Completed</option>
                            <option value="cancelled">Cancelled</option>
                            <option value="confirmed">Confirmed</option>
                        </select>
                    </div>
                    
                    <div>
                        <label style={{
                            display: 'block',
                            fontSize: '12px',
                            color: '#6b7280',
                            marginBottom: '4px',
                            fontWeight: '500'
                        }}>
                            Date
                        </label>
                        <input
                            type="date"
                            className="form-input"
                            value={filterDate}
                            onChange={(e) => setFilterDate(e.target.value)}
                            style={{width: '180px'}}
                        />
                    </div>

                    {(filterStatus !== 'all' || filterDate) && (
                        <button
                            onClick={() => {
                                setFilterStatus('all');
                                setFilterDate('');
                            }}
                            style={{
                                padding: '8px 16px',
                                background: '#6b7280',
                                color: 'white',
                                border: 'none',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                fontSize: '14px',
                                fontWeight: '500'
                            }}
                        >
                            Clear Filters
                        </button>
                    )}
                </div>

                {/* Sort indicator */}
                <div style={{
                    padding: '8px 12px',
                    background: '#f3f4f6',
                    borderRadius: '6px',
                    marginBottom: '16px',
                    fontSize: '13px',
                    color: '#6b7280'
                }}>
                    📅 Sorted by date: Oldest first
                </div>

                {/* Appointments List */}
                <div className="patient-list">
                    {filteredAndSortedAppointments.length === 0 ? (
                        <div style={{
                            textAlign: 'center',
                            padding: '48px 20px',
                            color: '#9ca3af'
                        }}>
                            <div style={{fontSize: '48px', marginBottom: '12px'}}>📅</div>
                            <div style={{fontSize: '16px', fontWeight: '500', marginBottom: '4px', color: '#374151'}}>
                                No appointments found
                            </div>
                            <div style={{fontSize: '14px'}}>
                                {filterDate || filterStatus !== 'all' 
                                    ? 'Try adjusting your filters'
                                    : 'No appointments scheduled yet'
                                }
                            </div>
                        </div>
                    ) : (
                        filteredAndSortedAppointments.map((appointment, index) => (
                            <div key={appointment.id} className="appointment-item">
                                {/* Position indicator */}
                                <div style={{
                                    position: 'absolute',
                                    top: '8px',
                                    left: '8px',
                                    background: '#e5e7eb',
                                    color: '#6b7280',
                                    fontSize: '11px',
                                    padding: '2px 6px',
                                    borderRadius: '4px',
                                    fontWeight: '600'
                                }}>
                                    #{index + 1}
                                </div>

                                <div className="appointment-avatar">
                                    {appointment.patientName ? 
                                        appointment.patientName.split(' ').map(n => n[0]).join('') :
                                        'U'
                                    }
                                </div>
                                <div className="appointment-info">
                                    <div className="appointment-patient">
                                        {appointment.patientName || 'Unknown Patient'}
                                    </div>
                                    <div className="appointment-details">
                                        {appointment.reason || appointment.testType || 'Appointment'}
                                    </div>
                                    <div className="appointment-details">
                                        {formatDate(
                                            appointment.appointmentDate || 
                                            appointment.scheduledDate || 
                                            appointment.date || 
                                            appointment.createdAt
                                        )}
                                    </div>
                                    {appointment.notes && (
                                        <div style={{
                                            fontSize: '12px', 
                                            color: '#6b7280', 
                                            marginTop: '4px',
                                            fontStyle: 'italic'
                                        }}>
                                            💬 {appointment.notes}
                                        </div>
                                    )}
                                </div>
                                <div className={`patient-status status-${appointment.status?.toLowerCase() || 'unknown'}`}>
                                    {appointment.status || 'Unknown'}
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};