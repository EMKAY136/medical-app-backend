const PatientsView = ({ patients, searchQuery, setSearchQuery, setSelectedPatient, setShowModal, onUpdateAppointment }) => {
    return (
        <div className="content-card">
            <div className="card-header">
                <h2 className="card-title">All Patients</h2>
                <div className="patient-count">
                    Total: {patients.length} {patients.length === 1 ? 'Patient' : 'Patients'}
                </div>
            </div>
            <div className="card-content">
                <div className="patient-search">
                    <input
                        type="text"
                        placeholder="Search patients by name, email, or phone..."
                        className="form-input"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        style={{marginBottom: '20px'}}
                    />
                </div>
                <div className="patient-list">
                    {patients.length > 0 ? (
                        patients.map(patient => (
                            <PatientItem 
                                key={patient.id}
                                patient={patient}
                                setSelectedPatient={setSelectedPatient}
                                setShowModal={setShowModal}
                                onUpdateAppointment={onUpdateAppointment}
                            />
                        ))
                    ) : (
                        <div className="empty-state">
                            <i className="fas fa-users" style={{fontSize: '48px', color: '#d1d5db', marginBottom: '16px'}}></i>
                            <p>No patients found</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

const PatientItem = ({ patient, setSelectedPatient, setShowModal, onUpdateAppointment }) => {
    const handleViewDetails = () => {
        setSelectedPatient(patient);
        setShowModal('patient-details');
    };

    // Format date helper
    const formatDate = (dateString) => {
        if (!dateString) return 'N/A';
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { 
            year: 'numeric', 
            month: 'short', 
            day: 'numeric' 
        });
    };

    // Format date with time
    const formatDateTime = (dateString) => {
        if (!dateString) return 'N/A';
        const date = new Date(dateString);
        return date.toLocaleString('en-US', { 
            year: 'numeric', 
            month: 'short', 
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    // Calculate age from date of birth
    const calculateAge = (dob) => {
        if (!dob) return null;
        const birthDate = new Date(dob);
        const today = new Date();
        let age = today.getFullYear() - birthDate.getFullYear();
        const monthDiff = today.getMonth() - birthDate.getMonth();
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
            age--;
        }
        return age;
    };

    const age = calculateAge(patient.dateOfBirth);

    return (
        <div className="patient-item">
            <div className="patient-avatar">
                {patient.firstName && patient.lastName ? 
                    `${patient.firstName[0]}${patient.lastName[0]}` : 
                    patient.email[0].toUpperCase()
                }
            </div>
            <div className="patient-info">
                <div className="patient-name">
                    {patient.firstName && patient.lastName ? 
                        `${patient.firstName} ${patient.lastName}` : 
                        patient.email
                    }
                    {patient.gender && (
                        <span className="patient-badge" style={{
                            marginLeft: '8px',
                            fontSize: '11px',
                            padding: '2px 8px',
                            borderRadius: '12px',
                            backgroundColor: patient.gender === 'MALE' ? '#dbeafe' : '#fce7f3',
                            color: patient.gender === 'MALE' ? '#1e40af' : '#be185d'
                        }}>
                            {patient.gender === 'MALE' ? '♂' : patient.gender === 'FEMALE' ? '♀' : '⚥'} {patient.gender}
                        </span>
                    )}
                </div>
                
                <div className="patient-details-grid" style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '8px',
                    marginTop: '8px'
                }}>
                    <div className="patient-details">
                        <i className="fas fa-envelope" style={{marginRight: '6px', color: '#6b7280'}}></i>
                        {patient.email}
                    </div>
                    
                    <div className="patient-details">
                        <i className="fas fa-phone" style={{marginRight: '6px', color: '#6b7280'}}></i>
                        {patient.phone || 'No phone number'}
                    </div>

                    {age && (
                        <div className="patient-details">
                            <i className="fas fa-birthday-cake" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {age} years old
                        </div>
                    )}

                    {patient.bloodGroup && (
                        <div className="patient-details">
                            <i className="fas fa-tint" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Blood: {patient.bloodGroup}
                        </div>
                    )}

                    {patient.address && (
                        <div className="patient-details">
                            <i className="fas fa-map-marker-alt" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {patient.address}
                        </div>
                    )}

                    {patient.occupation && (
                        <div className="patient-details">
                            <i className="fas fa-briefcase" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {patient.occupation}
                        </div>
                    )}

                    {patient.emergencyContact && (
                        <div className="patient-details">
                            <i className="fas fa-user-shield" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Emergency: {patient.emergencyContact}
                        </div>
                    )}

                    {patient.medicalHistory && (
                        <div className="patient-details" style={{gridColumn: '1 / -1'}}>
                            <i className="fas fa-notes-medical" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            <strong>Medical History:</strong> {patient.medicalHistory}
                        </div>
                    )}

                    {patient.allergies && (
                        <div className="patient-details" style={{gridColumn: '1 / -1'}}>
                            <i className="fas fa-exclamation-triangle" style={{marginRight: '6px', color: '#f59e0b'}}></i>
                            <strong style={{color: '#f59e0b'}}>Allergies:</strong> {patient.allergies}
                        </div>
                    )}
                </div>

                <div style={{
                    display: 'flex',
                    gap: '12px',
                    marginTop: '8px',
                    fontSize: '12px',
                    color: '#6b7280'
                }}>
                    {patient.createdAt && (
                        <div>
                            <i className="fas fa-user-plus" style={{marginRight: '4px'}}></i>
                            Registered: {formatDate(patient.createdAt)}
                        </div>
                    )}
                    {patient.lastLogin && (
                        <div>
                            <i className="fas fa-clock" style={{marginRight: '4px'}}></i>
                            Last login: {formatDateTime(patient.lastLogin)}
                        </div>
                    )}
                    {patient.emailVerified && (
                        <div style={{color: '#10b981'}}>
                            <i className="fas fa-check-circle" style={{marginRight: '4px'}}></i>
                            Email Verified
                        </div>
                    )}
                </div>
            </div>
            <div className="patient-actions">
                <button 
                    className="btn btn-secondary btn-sm"
                    onClick={handleViewDetails}
                    title="View patient details"
                >
                    <i className="fas fa-eye"></i>
                </button>
            </div>
        </div>
    );
};