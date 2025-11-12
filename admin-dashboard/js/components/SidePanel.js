const { useState, useEffect } = React;

const SidePanel = ({ appointments, setShowModal }) => {
    const today = new Date();
    const todayString = today.toDateString();
    
    const todayAppointments = appointments.filter(apt => {
        const appointmentDate = new Date(apt.appointmentDate);
        return appointmentDate.toDateString() === todayString;
    });

    return (
        <div className="side-panel">
            <div className="content-card">
                <div className="card-header">
                    <h3 className="card-title">Today's Appointments ({todayAppointments.length})</h3>
                </div>
                <div className="card-content">
                    {todayAppointments.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">📅</div>
                            <p>No appointments today</p>
                            <small style={{color: '#6b7280'}}>
                                {today.toLocaleDateString('en-US', { 
                                    weekday: 'long', 
                                    year: 'numeric', 
                                    month: 'long', 
                                    day: 'numeric' 
                                })}
                            </small>
                        </div>
                    ) : (
                        todayAppointments.map(appointment => (
                            <div key={appointment.id} className="appointment-item">
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
                                        {appointment.reason || appointment.testType}
                                    </div>
                                    <div className="appointment-details">
                                        {new Date(appointment.appointmentDate).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                                    </div>
                                </div>
                                <div className={`patient-status status-${appointment.status.toLowerCase()}`}>
                                    {appointment.status}
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
            
            <div className="content-card">
                <div className="card-header">
                    <h3 className="card-title">Quick Actions</h3>
                </div>
                <div className="card-content">
                    <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px'}}>
                                            <button 
                            className="btn btn-primary"
                            onClick={() => setShowModal('add-result')}
                            style={{padding: '16px 12px', fontSize: '14px', background: 'linear-gradient(135deg, #48bb78, #38a169)'}}
                        >
                            <i className="fas fa-file-medical"></i>
                            Add Result
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};