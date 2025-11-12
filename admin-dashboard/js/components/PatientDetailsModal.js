const PatientDetailsModal = ({ 
  patient, 
  patientId, 
  testResults = [], 
  appointments = [], 
  onClose, 
  showNotification = () => {}, 
  loadTestResults = () => {} 
}) => {
  const [activeTab, setActiveTab] = useState('info');
  const [patientData, setPatientData] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    console.log('🔍 Modal opened with patientId:', patientId);
    console.log('🔍 Initial patient prop:', patient);

    if (patientId) {
      console.log('🔄 Fetching fresh patient data from backend...');
      fetchPatientDetails();
    }
  }, [patientId]);

  const fetchPatientDetails = async () => {
    setLoading(true);
    const url = `${CONFIG.API_BASE_URL}/api/users/profile/${patientId}`;
    
    try {
      const token = localStorage.getItem('authToken');
      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
      console.log('✅ API Response:', data);
      
      let userData = null;
      
      if (data.success && data.user) {
        userData = data.user;
      } else if (data.user) {
        userData = data.user;
      } else if (data.data) {
        userData = data.data;
      } else if (data.id || data.email) {
        userData = data;
      }
      
      console.log('📦 Final patient data to display:', userData);
      setPatientData(userData);
      
    } catch (err) {
      console.error('❌ Fetch error:', err);
      if (showNotification) {
        showNotification('error', 'Failed to load patient details');
      }
    } finally {
      setLoading(false);
    }
  };

  const getValue = (obj, ...keys) => {
    if (!obj) return null;
    
    for (let key of keys) {
      const value = obj[key];
      
      if (value !== null && value !== undefined && value !== '') {
        return value;
      }
      
      if (value === 0) {
        return value;
      }
    }
    
    return null;
  };

  const formatDateTime = (dateString) => {
    if (!dateString || (Array.isArray(dateString) && dateString.length === 0)) {
      return { date: 'Not provided', time: '' };
    }
    
    try {
      let date;
      
      if (Array.isArray(dateString)) {
        const [year, month, day, hour = 0, minute = 0] = dateString;
        date = new Date(year, month - 1, day, hour, minute);
      } else {
        date = new Date(dateString);
      }
      
      if (isNaN(date.getTime())) {
        return { date: 'Invalid date', time: '' };
      }
      
      return {
        date: date.toLocaleDateString('en-US', { 
          year: 'numeric', 
          month: 'short', 
          day: 'numeric' 
        }),
        time: date.toLocaleTimeString('en-US', { 
          hour: '2-digit', 
          minute: '2-digit',
          hour12: true
        })
      };
    } catch (e) {
      return { date: 'Date error', time: '' };
    }
  };

  const getStatusColor = (status) => {
    if (!status) return '#6b7280';
    const s = status.toLowerCase();
    if (s === 'completed' || s === 'confirmed') return '#10b981';
    if (s === 'pending') return '#f59e0b';
    if (s === 'cancelled') return '#ef4444';
    return '#6b7280';
  };

  const calculateAge = (dob) => {
    if (!dob) return null;
    try {
      const birthDate = Array.isArray(dob) 
        ? new Date(dob[0], dob[1] - 1, dob[2])
        : new Date(dob);
      
      if (isNaN(birthDate.getTime())) return null;
      
      const today = new Date();
      let age = today.getFullYear() - birthDate.getFullYear();
      const m = today.getMonth() - birthDate.getMonth();
      if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
        age--;
      }
      return age > 0 ? age : null;
    } catch {
      return null;
    }
  };

  // Sort appointments alphabetically by test name/reason
  const sortedAppointments = [...(appointments || [])].sort((a, b) => {
    const nameA = (a.reason || a.testType || 'Unnamed').toLowerCase();
    const nameB = (b.reason || b.testType || 'Unnamed').toLowerCase();
    return nameA.localeCompare(nameB);
  });

  // Sort test results alphabetically by test name/type
  const sortedTestResults = [...(testResults || [])].sort((a, b) => {
    const nameA = (a.testType || a.testName || 'Unnamed').toLowerCase();
    const nameB = (b.testType || b.testName || 'Unnamed').toLowerCase();
    return nameA.localeCompare(nameB);
  });

  const p = patientData || patient || {};
  
  const firstName = getValue(p, 'firstName', 'first_name', 'firstname');
  const lastName = getValue(p, 'lastName', 'last_name', 'lastname');
  const email = getValue(p, 'email', 'emailAddress', 'email_address');
  const phone = getValue(p, 'phone', 'phoneNumber', 'phone_number', 'mobile');
  const address = getValue(p, 'address', 'fullAddress', 'full_address');
  const dob = getValue(p, 'dob', 'dateOfBirth', 'date_of_birth', 'birthDate');
  const gender = getValue(p, 'gender', 'sex');
  const height = getValue(p, 'height');
  const weight = getValue(p, 'weight');
  const bloodGroup = getValue(p, 'bloodGroup', 'blood_group', 'bloodType', 'blood_type');
  const genotype = getValue(p, 'genotype');
  const createdAt = getValue(p, 'createdAt', 'created_at', 'registeredDate', 'registered_date', 'joinedDate');
  const userId = getValue(p, 'id', 'userId', 'user_id', 'patientId', 'patient_id');
  
  const age = calculateAge(dob);
  const fullName = firstName && lastName ? `${firstName} ${lastName}` : null;

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(0,0,0,0.6)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1000,
      padding: '20px'
    }}>
      <div style={{
        background: 'white',
        borderRadius: '16px',
        maxWidth: '900px',
        width: '100%',
        maxHeight: '90vh',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04)'
      }}>
        {/* Header */}
        <div style={{
          padding: '24px',
          borderBottom: '1px solid #e5e7eb',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
        }}>
          <h2 style={{margin: 0, fontSize: '24px', fontWeight: '700', color: 'white'}}>
            Patient Details
          </h2>
          <button 
            onClick={onClose}
            style={{
              background: 'rgba(255,255,255,0.2)',
              border: 'none',
              borderRadius: '8px',
              padding: '8px 12px',
              cursor: 'pointer',
              fontSize: '24px',
              color: 'white',
              fontWeight: 'bold',
              transition: 'background 0.2s'
            }}
            onMouseEnter={(e) => e.target.style.background = 'rgba(255,255,255,0.3)'}
            onMouseLeave={(e) => e.target.style.background = 'rgba(255,255,255,0.2)'}
          >
            ×
          </button>
        </div>
        
        {/* Patient Summary */}
        <div style={{
          display: 'flex', 
          alignItems: 'center', 
          padding: '24px', 
          background: '#f8fafc', 
          borderBottom: '1px solid #e5e7eb'
        }}>
          <div style={{
            width: '90px', 
            height: '90px',
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '36px',
            fontWeight: 'bold',
            marginRight: '20px',
            boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)'
          }}>
            {fullName ? 
              `${firstName[0]}${lastName[0]}` : 
              (email ? email[0].toUpperCase() : '?')
            }
          </div>
          <div style={{flex: 1}}>
            <h3 style={{margin: '0 0 8px 0', fontSize: '22px', fontWeight: '700'}}>
              {fullName || email || 'Unknown Patient'}
            </h3>
            <p style={{margin: '0 0 4px 0', color: '#6b7280', fontSize: '14px'}}>
              📧 {email || 'No email'} {phone && `• 📱 ${phone}`}
            </p>
            <p style={{margin: '0', fontSize: '13px', color: '#9ca3af'}}>
              👤 Patient ID: {userId || 'N/A'} • 📅 Member since: {formatDateTime(createdAt).date}
            </p>
          </div>
        </div>

        {/* Tabs */}
        <div style={{
          display: 'flex',
          borderBottom: '2px solid #e5e7eb',
          background: '#fff'
        }}>
          {['info', 'appointments', 'results'].map(tab => (
            <button 
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                flex: 1,
                padding: '16px',
                border: 'none',
                background: activeTab === tab ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' : 'transparent',
                color: activeTab === tab ? 'white' : '#6b7280',
                cursor: 'pointer',
                fontWeight: '600',
                fontSize: '15px',
                transition: 'all 0.3s',
                textTransform: 'capitalize'
              }}
            >
              {tab === 'info' ? '📋 Personal Info' : 
               tab === 'appointments' ? `📅 Appointments (${sortedAppointments.length})` :
               `🧪 Test Results (${sortedTestResults.length})`}
            </button>
          ))}
        </div>

        {/* Content */}
        <div style={{
          flex: 1,
          overflow: 'auto',
          padding: '24px',
          position: 'relative',
          background: '#fafbfc'
        }}>
          {loading && (
            <div style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(255,255,255,0.9)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 10
            }}>
              <div style={{textAlign: 'center'}}>
                <div style={{fontSize: '40px', marginBottom: '12px'}}>⏳</div>
                <div style={{fontSize: '18px', fontWeight: '600', color: '#667eea'}}>Loading...</div>
              </div>
            </div>
          )}

          {activeTab === 'info' && (
            <div>
              {/* Basic Contact Info */}
              <div style={{
                background: 'white',
                borderRadius: '12px',
                padding: '20px',
                marginBottom: '20px',
                boxShadow: '0 1px 3px 0 rgba(0,0,0,0.1)'
              }}>
                <h3 style={{margin: '0 0 16px 0', fontSize: '18px', fontWeight: '700', color: '#111827'}}>
                  📞 Contact Information
                </h3>
                <div style={{display: 'grid', gridTemplateColumns: '1fr', gap: '16px'}}>
                  <InfoField label="Email Address" value={email} icon="📧" />
                  <InfoField label="Phone Number" value={phone} icon="📱" />
                  <InfoField label="Home Address" value={address} icon="🏠" />
                </div>
              </div>

              {/* Health Information */}
              <div style={{
                background: 'white',
                borderRadius: '12px',
                padding: '20px',
                boxShadow: '0 1px 3px 0 rgba(0,0,0,0.1)'
              }}>
                <h3 style={{margin: '0 0 16px 0', fontSize: '18px', fontWeight: '700', color: '#111827'}}>
                  🏥 Health Information
                </h3>
                
                <div style={{
                  display: 'grid', 
                  gridTemplateColumns: 'repeat(2, 1fr)', 
                  gap: '16px'
                }}>
                  <InfoField 
                    label="Date of Birth" 
                    value={dob ? `${formatDateTime(dob).date}${age ? ` (${age} years)` : ''}` : null}
                    icon="🎂" 
                  />
                  <InfoField 
                    label="Gender" 
                    value={gender ? gender.charAt(0).toUpperCase() + gender.slice(1).toLowerCase() : null}
                    icon="👤" 
                  />
                  <InfoField 
                    label="Height" 
                    value={height ? `${height} cm` : null}
                    icon="📏" 
                  />
                  <InfoField 
                    label="Weight" 
                    value={weight ? `${weight} kg` : null}
                    icon="⚖️" 
                  />
                  <InfoField 
                    label="Blood Group" 
                    value={bloodGroup}
                    icon="🩸"
                    highlight 
                  />
                  <InfoField 
                    label="Genotype" 
                    value={genotype}
                    icon="🧬"
                    highlight 
                  />
                </div>

                {/* Missing Fields Warning */}
                {(() => {
                  const missingFields = [];
                  if (!gender) missingFields.push('Gender');
                  if (!dob) missingFields.push('Date of Birth');
                  if (!height) missingFields.push('Height');
                  if (!weight) missingFields.push('Weight');
                  if (!bloodGroup) missingFields.push('Blood Group');
                  if (!genotype) missingFields.push('Genotype');
                  
                  if (missingFields.length > 0) {
                    return (
                      <div style={{
                        marginTop: '20px',
                        padding: '16px',
                        background: '#fef3c7',
                        border: '2px solid #fbbf24',
                        borderRadius: '8px',
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: '12px'
                      }}>
                        <div style={{fontSize: '24px'}}>⚠️</div>
                        <div>
                          <div style={{fontWeight: '700', color: '#92400e', marginBottom: '4px'}}>
                            Incomplete Health Profile
                          </div>
                          <div style={{fontSize: '14px', color: '#92400e'}}>
                            The following fields are missing: <strong>{missingFields.join(', ')}</strong>
                          </div>
                          <div style={{fontSize: '13px', color: '#92400e', marginTop: '8px', fontStyle: 'italic'}}>
                            💡 Ask the patient to complete their profile for better care management.
                          </div>
                        </div>
                      </div>
                    );
                  }
                  return null;
                })()}
              </div>
            </div>
          )}

          {activeTab === 'appointments' && (
            <div>
              {sortedAppointments.length === 0 ? (
                <EmptyState icon="📅" message="No appointments scheduled" />
              ) : (
                <>
                  <div style={{
                    marginBottom: '16px',
                    padding: '12px 16px',
                    background: '#e0e7ff',
                    borderRadius: '8px',
                    fontSize: '14px',
                    color: '#4338ca',
                    fontWeight: '600'
                  }}>
                    📋 Showing {sortedAppointments.length} appointment{sortedAppointments.length !== 1 ? 's' : ''} (A-Z)
                  </div>
                  {sortedAppointments.map(appointment => (
                    <AppointmentCard key={appointment.id} appointment={appointment} formatDateTime={formatDateTime} getStatusColor={getStatusColor} />
                  ))}
                </>
              )}
            </div>
          )}

          {activeTab === 'results' && (
            <div>
              {sortedTestResults.length === 0 ? (
                <EmptyState icon="🧪" message="No test results available" />
              ) : (
                <>
                  <div style={{
                    marginBottom: '16px',
                    padding: '12px 16px',
                    background: '#e0e7ff',
                    borderRadius: '8px',
                    fontSize: '14px',
                    color: '#4338ca',
                    fontWeight: '600'
                  }}>
                    🧪 Showing {sortedTestResults.length} test result{sortedTestResults.length !== 1 ? 's' : ''} (A-Z)
                  </div>
                  {sortedTestResults.map(result => (
                    <TestResultCard key={result.id} result={result} formatDateTime={formatDateTime} getStatusColor={getStatusColor} />
                  ))}
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const InfoField = ({ label, value, icon, highlight }) => (
  <div>
    <label style={{
      display: 'block',
      fontSize: '13px',
      fontWeight: '600',
      color: '#6b7280',
      marginBottom: '8px',
      textTransform: 'uppercase',
      letterSpacing: '0.5px'
    }}>
      {icon && `${icon} `}{label}
    </label>
    <div style={{
      padding: '12px 16px',
      background: highlight ? '#fef3c7' : '#f9fafb',
      borderRadius: '8px',
      border: `2px solid ${highlight ? '#fbbf24' : '#e5e7eb'}`,
      fontSize: '15px',
      fontWeight: highlight ? '700' : '500',
      color: highlight ? '#92400e' : '#111827'
    }}>
      {value || 'Not provided'}
    </div>
  </div>
);

const EmptyState = ({ icon, message }) => (
  <div style={{
    textAlign: 'center', 
    padding: '60px 20px', 
    background: 'white',
    borderRadius: '12px',
    boxShadow: '0 1px 3px 0 rgba(0,0,0,0.1)'
  }}>
    <div style={{fontSize: '64px', marginBottom: '16px'}}>{icon}</div>
    <p style={{fontSize: '16px', color: '#6b7280', fontWeight: '500'}}>{message}</p>
  </div>
);

const AppointmentCard = ({ appointment, formatDateTime, getStatusColor }) => {
  const dateStr = appointment.scheduledDate || appointment.appointmentDate || appointment.date || appointment.createdAt;
  const formatted = formatDateTime(dateStr);
  
  return (
    <div style={{
      marginBottom: '16px',
      padding: '20px',
      background: 'white',
      border: '2px solid #e5e7eb',
      borderRadius: '12px',
      boxShadow: '0 1px 3px 0 rgba(0,0,0,0.1)',
      transition: 'all 0.2s'
    }}>
      <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px'}}>
        <div style={{fontWeight: '700', fontSize: '17px', color: '#111827'}}>
          📋 {appointment.reason || appointment.testType || 'General Appointment'}
        </div>
        <div style={{
          padding: '6px 14px',
          borderRadius: '20px',
          background: getStatusColor(appointment.status) + '20',
          color: getStatusColor(appointment.status),
          fontSize: '13px',
          fontWeight: '700',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          {appointment.status || 'Pending'}
        </div>
      </div>
      <div style={{fontSize: '14px', color: '#6b7280', marginBottom: '4px'}}>
        📅 {formatted.date} {formatted.time && `⏰ ${formatted.time}`}
      </div>
      {appointment.notes && (
        <div style={{fontSize: '14px', color: '#6b7280', marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #e5e7eb', fontStyle: 'italic'}}>
          💬 {appointment.notes}
        </div>
      )}
    </div>
  );
};

const TestResultCard = ({ result, formatDateTime, getStatusColor }) => {
  const dateStr = result.testDate || result.date || result.createdAt;
  const formatted = formatDateTime(dateStr);
  
  return (
    <div style={{
      marginBottom: '16px',
      padding: '20px',
      background: 'white',
      border: '2px solid #e5e7eb',
      borderRadius: '12px',
      boxShadow: '0 1px 3px 0 rgba(0,0,0,0.1)'
    }}>
      <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px'}}>
        <h4 style={{margin: 0, fontSize: '17px', fontWeight: '700', color: '#111827'}}>
          🧪 {result.testType || result.testName || 'Medical Test'}
        </h4>
        <div style={{
          padding: '6px 14px',
          borderRadius: '20px',
          background: getStatusColor(result.status) + '20',
          color: getStatusColor(result.status),
          fontSize: '13px',
          fontWeight: '700',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          {result.status || 'Completed'}
        </div>
      </div>
      <p style={{margin: '0 0 8px 0', fontSize: '16px', color: '#667eea', fontWeight: '700'}}>
        📊 Result: {result.result || 'N/A'}
      </p>
      <p style={{margin: '0', fontSize: '13px', color: '#9ca3af'}}>
        📅 {formatted.date}
      </p>
      {result.notes && (
        <p style={{fontSize: '14px', color: '#6b7280', margin: '12px 0 0 0', paddingTop: '12px', borderTop: '1px solid #e5e7eb', fontStyle: 'italic'}}>
          💬 {result.notes}
        </p>
      )}
    </div>
  );
};