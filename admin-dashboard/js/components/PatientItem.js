const PatientItem = ({ patient, setSelectedPatient, setShowModal, onUpdateAppointment }) => {
    console.log('Patient data:', patient); // Keep this for debugging

    const handleViewDetails = () => {
        setSelectedPatient(patient);
        setShowModal('patient-details');
    };

    // Helper function to get value with multiple possible property names
    const getValue = (obj, ...keys) => {
        for (let key of keys) {
            if (obj && obj[key] !== null && obj[key] !== undefined) {
                return obj[key];
            }
        }
        return null;
    };

    const handlePatientUpdate = async (payload) => {
        const res = await axios.put(`/api/patients/${selectedPatient.id}`, payload);
        if (res.data?.success) {
            const updatedPatient = res.data.user;
            setSelectedPatient(updatedPatient);
            setPatients(prev => prev.map(p => p.id === updatedPatient.id ? updatedPatient : p));
        }
    };

    // Format date helper function
    const formatDate = (dateString) => {
        if (!dateString) return null;
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-US', { 
                year: 'numeric', 
                month: 'short', 
                day: 'numeric' 
            });
        } catch (e) {
            return null;
        }
    };

    // Format date with time
    const formatDateTime = (dateString) => {
        if (!dateString) return null;
        try {
            const date = new Date(dateString);
            return date.toLocaleString('en-US', { 
                year: 'numeric', 
                month: 'short', 
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return null;
        }
    };

    // Calculate age from date of birth
    const calculateAge = (dob) => {
        if (!dob) return null;
        try {
            const birthDate = new Date(dob);
            const today = new Date();
            let age = today.getFullYear() - birthDate.getFullYear();
            const monthDiff = today.getMonth() - birthDate.getMonth();
            if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
                age--;
            }
            return age > 0 ? age : null;
        } catch (e) {
            return null;
        }
    };

    // Extract values with multiple possible property names
    const firstName = getValue(patient, 'firstName', 'first_name', 'firstname');
    const lastName = getValue(patient, 'lastName', 'last_name', 'lastname');
    const email = getValue(patient, 'email', 'emailAddress', 'email_address');
    const phone = getValue(patient, 'phone', 'phoneNumber', 'phone_number', 'mobile');
    const gender = getValue(patient, 'gender', 'sex');
    const dateOfBirth = getValue(patient, 'dateOfBirth', 'date_of_birth', 'dob', 'birthDate');
    const bloodGroup = getValue(patient, 'bloodGroup', 'blood_group', 'bloodType', 'blood_type');
    const address = getValue(patient, 'address', 'fullAddress', 'full_address');
    const occupation = getValue(patient, 'occupation', 'job', 'profession');
    const emergencyContact = getValue(patient, 'emergencyContact', 'emergency_contact', 'emergencyContactNumber');
    const medicalHistory = getValue(patient, 'medicalHistory', 'medical_history', 'history');
    const allergies = getValue(patient, 'allergies', 'allergy');
    const createdAt = getValue(patient, 'createdAt', 'created_at', 'registeredDate', 'registered_date');
    const lastLogin = getValue(patient, 'lastLogin', 'last_login', 'lastLoginDate');
    const emailVerified = getValue(patient, 'emailVerified', 'email_verified', 'isEmailVerified');
    const height = getValue(patient, 'height');
    const weight = getValue(patient, 'weight');
    const genotype = getValue(patient, 'genotype');

    const age = calculateAge(dateOfBirth);
    const fullName = firstName && lastName ? `${firstName} ${lastName}` : null;

    return (
        <div className="patient-item">
            <div className="patient-avatar">
                {fullName ? 
                    `${firstName[0]}${lastName[0]}` : 
                    email ? email[0].toUpperCase() : '?'
                }
            </div>
            <div className="patient-info">
                <div className="patient-name">
                    {fullName || email || 'Unknown Patient'}
                    {gender && (
                        <span className="patient-badge" style={{
                            marginLeft: '8px',
                            fontSize: '11px',
                            padding: '2px 8px',
                            borderRadius: '12px',
                            backgroundColor: gender.toUpperCase() === 'MALE' ? '#dbeafe' : '#fce7f3',
                            color: gender.toUpperCase() === 'MALE' ? '#1e40af' : '#be185d'
                        }}>
                            {gender.toUpperCase() === 'MALE' ? '♂' : gender.toUpperCase() === 'FEMALE' ? '♀' : '⚥'} {gender}
                        </span>
                    )}
                </div>
                
                <div className="patient-details-grid" style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '8px',
                    marginTop: '8px'
                }}>
                    {email && (
                        <div className="patient-details">
                            <i className="fas fa-envelope" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {email}
                        </div>
                    )}
                    
                    {phone && (
                        <div className="patient-details">
                            <i className="fas fa-phone" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {phone}
                        </div>
                    )}

                    {age && (
                        <div className="patient-details">
                            <i className="fas fa-birthday-cake" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {age} years old
                        </div>
                    )}

                    {bloodGroup && (
                        <div className="patient-details">
                            <i className="fas fa-tint" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Blood: {bloodGroup}
                        </div>
                    )}

                    {genotype && (
                        <div className="patient-details">
                            <i className="fas fa-dna" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Genotype: {genotype}
                        </div>
                    )}

                    {height && (
                        <div className="patient-details">
                            <i className="fas fa-ruler-vertical" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Height: {height}
                        </div>
                    )}

                    {weight && (
                        <div className="patient-details">
                            <i className="fas fa-weight" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Weight: {weight}
                        </div>
                    )}

                    {address && (
                        <div className="patient-details">
                            <i className="fas fa-map-marker-alt" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {address}
                        </div>
                    )}

                    {occupation && (
                        <div className="patient-details">
                            <i className="fas fa-briefcase" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            {occupation}
                        </div>
                    )}

                    {emergencyContact && (
                        <div className="patient-details">
                            <i className="fas fa-user-shield" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            Emergency: {emergencyContact}
                        </div>
                    )}

                    {medicalHistory && (
                        <div className="patient-details" style={{gridColumn: '1 / -1'}}>
                            <i className="fas fa-notes-medical" style={{marginRight: '6px', color: '#6b7280'}}></i>
                            <strong>Medical History:</strong> {medicalHistory}
                        </div>
                    )}

                    {allergies && (
                        <div className="patient-details" style={{gridColumn: '1 / -1'}}>
                            <i className="fas fa-exclamation-triangle" style={{marginRight: '6px', color: '#f59e0b'}}></i>
                            <strong style={{color: '#f59e0b'}}>Allergies:</strong> {allergies}
                        </div>
                    )}
                </div>

                <div style={{
                    display: 'flex',
                    gap: '12px',
                    marginTop: '8px',
                    fontSize: '12px',
                    color: '#6b7280',
                    flexWrap: 'wrap'
                }}>
                    {createdAt && formatDate(createdAt) && (
                        <div>
                            <i className="fas fa-user-plus" style={{marginRight: '4px'}}></i>
                            Registered: {formatDate(createdAt)}
                        </div>
                    )}
                    {lastLogin && formatDateTime(lastLogin) && (
                        <div>
                            <i className="fas fa-clock" style={{marginRight: '4px'}}></i>
                            Last login: {formatDateTime(lastLogin)}
                        </div>
                    )}
                    {emailVerified && (
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