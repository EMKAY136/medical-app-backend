const PatientDetailsModal = ({
    patient,
    patientId,
    testResults = [],
    appointments = [],
    onClose,
    showNotification = () => {},
    loadTestResults = () => {},
}) => {
    const [activeTab, setActiveTab] = useState('info');
    const [patientData, setPatientData] = useState(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (patientId) fetchPatientDetails();
    }, [patientId]);

    const fetchPatientDetails = async () => {
        setLoading(true);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.API_BASE_URL}/api/users/profile/${patientId}`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            const user = data.success && data.user ? data.user : data.user || data.data || (data.id || data.email ? data : null);
            setPatientData(user);
        } catch (err) {
            console.error('Fetch error:', err);
            showNotification('error', 'Failed to load patient details');
        } finally {
            setLoading(false);
        }
    };

    const getValue = (obj, ...keys) => {
        if (!obj) return null;
        for (const key of keys) {
            const v = obj[key];
            if (v !== null && v !== undefined && v !== '') return v;
            if (v === 0) return v;
        }
        return null;
    };

    const formatDateTime = (v) => {
        if (!v || (Array.isArray(v) && v.length === 0)) return { date: 'Not provided', time: '' };
        try {
            const d = Array.isArray(v) ? new Date(v[0], v[1] - 1, v[2], v[3] || 0, v[4] || 0) : new Date(v);
            if (isNaN(d.getTime())) return { date: 'Invalid date', time: '' };
            return {
                date: d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }),
                time: d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }),
            };
        } catch { return { date: 'Date error', time: '' }; }
    };

    const calculateAge = (dob) => {
        if (!dob) return null;
        try {
            const bd = Array.isArray(dob) ? new Date(dob[0], dob[1] - 1, dob[2]) : new Date(dob);
            if (isNaN(bd.getTime())) return null;
            const today = new Date();
            let age = today.getFullYear() - bd.getFullYear();
            const m = today.getMonth() - bd.getMonth();
            if (m < 0 || (m === 0 && today.getDate() < bd.getDate())) age--;
            return age > 0 ? age : null;
        } catch { return null; }
    };

    // ── Payment badge ────────────────────────────────────────────────────────
    const getPaymentBadge = (ps) => {
        switch ((ps || '').toUpperCase()) {
            case 'PAID':                 return { label: '✅ Paid',            bg: '#d1fae5', color: '#065f46' };
            case 'PENDING_CONFIRMATION': return { label: '⏳ Payment Pending', bg: '#fef3c7', color: '#92400e' };
            case 'PAY_ON_ARRIVAL':       return { label: '🕐 Pay on Arrival', bg: '#dbeafe', color: '#1e40af' };
            default:                     return { label: '❌ Unpaid',          bg: '#fee2e2', color: '#991b1b' };
        }
    };

    const getStatusColor = (status) => {
        const s = (status || '').toLowerCase();
        if (s === 'completed')  return '#10b981';
        if (s === 'scheduled')  return '#3b82f6';
        if (s === 'missed')     return '#ea580c';
        if (s === 'cancelled')  return '#ef4444';
        return '#6b7280';
    };

    // Sort helpers
    const sortedAppointments = [...(appointments || [])].sort((a, b) =>
        (a.reason || a.testType || '').localeCompare(b.reason || b.testType || '')
    );
    const sortedTestResults = [...(testResults || [])].sort((a, b) =>
        (a.testType || a.testName || '').localeCompare(b.testType || b.testName || '')
    );

    const p = patientData || patient || {};

    const firstName  = getValue(p, 'firstName', 'first_name', 'firstname');
    const lastName   = getValue(p, 'lastName',  'last_name',  'lastname');
    const email      = getValue(p, 'email', 'emailAddress');
    const phone      = getValue(p, 'phone', 'phoneNumber', 'mobile');
    const address    = getValue(p, 'address', 'fullAddress');
    const dob        = getValue(p, 'dob', 'dateOfBirth', 'date_of_birth', 'birthDate');
    const gender     = getValue(p, 'gender', 'sex');
    const height     = getValue(p, 'height');
    const weight     = getValue(p, 'weight');
    const bloodGroup = getValue(p, 'bloodGroup', 'blood_group', 'bloodType');
    const genotype   = getValue(p, 'genotype');
    const createdAt  = getValue(p, 'createdAt', 'created_at', 'registeredDate');
    const userId     = getValue(p, 'id', 'userId', 'patientId');

    const age      = calculateAge(dob);
    const fullName = firstName && lastName ? `${firstName} ${lastName}` : null;

    return (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '20px' }}>
            <div style={{ background: 'white', borderRadius: '16px', maxWidth: '900px', width: '100%', maxHeight: '90vh', overflow: 'hidden', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.1)' }}>

                {/* Header */}
                <div style={{ padding: '24px', borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
                    <h2 style={{ margin: 0, fontSize: '24px', fontWeight: '700', color: 'white' }}>Patient Details</h2>
                    <button onClick={onClose} style={{ background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: '8px', padding: '8px 12px', cursor: 'pointer', fontSize: '24px', color: 'white', fontWeight: 'bold' }}>×</button>
                </div>

                {/* Summary */}
                <div style={{ display: 'flex', alignItems: 'center', padding: '24px', background: '#f8fafc', borderBottom: '1px solid #e5e7eb' }}>
                    <div style={{ width: '90px', height: '90px', borderRadius: '50%', background: 'linear-gradient(135deg, #667eea, #764ba2)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '36px', fontWeight: 'bold', marginRight: '20px' }}>
                        {fullName ? `${firstName[0]}${lastName[0]}` : (email ? email[0].toUpperCase() : '?')}
                    </div>
                    <div style={{ flex: 1 }}>
                        <h3 style={{ margin: '0 0 8px', fontSize: '22px', fontWeight: '700' }}>{fullName || email || 'Unknown Patient'}</h3>
                        <p style={{ margin: '0 0 4px', color: '#6b7280', fontSize: '14px' }}>📧 {email || 'No email'}{phone && ` • 📱 ${phone}`}</p>
                        <p style={{ margin: 0, fontSize: '13px', color: '#9ca3af' }}>
                            👤 Patient ID: {userId || 'N/A'} • 📅 Member since: {formatDateTime(createdAt).date}
                        </p>
                    </div>
                </div>

                {/* Tabs */}
                <div style={{ display: 'flex', borderBottom: '2px solid #e5e7eb', background: '#fff' }}>
                    {['info', 'appointments', 'results'].map(tab => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab)}
                            style={{ flex: 1, padding: '16px', border: 'none', background: activeTab === tab ? 'linear-gradient(135deg, #667eea, #764ba2)' : 'transparent', color: activeTab === tab ? 'white' : '#6b7280', cursor: 'pointer', fontWeight: '600', fontSize: '15px' }}
                        >
                            {tab === 'info' ? '📋 Personal Info' : tab === 'appointments' ? `📅 Appointments (${sortedAppointments.length})` : `🧪 Results (${sortedTestResults.length})`}
                        </button>
                    ))}
                </div>

                {/* Content */}
                <div style={{ flex: 1, overflow: 'auto', padding: '24px', background: '#fafbfc', position: 'relative' }}>
                    {loading && (
                        <div style={{ position: 'absolute', inset: 0, background: 'rgba(255,255,255,0.9)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10 }}>
                            <div style={{ textAlign: 'center' }}><div style={{ fontSize: '40px', marginBottom: '12px' }}>⏳</div><div style={{ fontSize: '18px', fontWeight: '600', color: '#667eea' }}>Loading...</div></div>
                        </div>
                    )}

                    {/* Info tab */}
                    {activeTab === 'info' && (
                        <div>
                            <div style={{ background: 'white', borderRadius: '12px', padding: '20px', marginBottom: '20px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                                <h3 style={{ margin: '0 0 16px', fontSize: '18px', fontWeight: '700' }}>📞 Contact Information</h3>
                                <InfoField label="Email" value={email} icon="📧" />
                                <InfoField label="Phone" value={phone} icon="📱" />
                                <InfoField label="Address" value={address} icon="🏠" />
                            </div>
                            <div style={{ background: 'white', borderRadius: '12px', padding: '20px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                                <h3 style={{ margin: '0 0 16px', fontSize: '18px', fontWeight: '700' }}>🏥 Health Information</h3>
                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
                                    <InfoField label="Date of Birth" value={dob ? `${formatDateTime(dob).date}${age ? ` (${age} yrs)` : ''}` : null} icon="🎂" />
                                    <InfoField label="Gender" value={gender ? gender.charAt(0).toUpperCase() + gender.slice(1).toLowerCase() : null} icon="👤" />
                                    <InfoField label="Height" value={height ? `${height} cm` : null} icon="📏" />
                                    <InfoField label="Weight" value={weight ? `${weight} kg` : null} icon="⚖️" />
                                    <InfoField label="Blood Group" value={bloodGroup} icon="🩸" highlight />
                                    <InfoField label="Genotype" value={genotype} icon="🧬" highlight />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Appointments tab */}
                    {activeTab === 'appointments' && (
                        <div>
                            {sortedAppointments.length === 0 ? (
                                <EmptyState icon="📅" message="No appointments scheduled" />
                            ) : (
                                <>
                                    <div style={{ marginBottom: '16px', padding: '12px 16px', background: '#e0e7ff', borderRadius: '8px', fontSize: '14px', color: '#4338ca', fontWeight: '600' }}>
                                        📋 {sortedAppointments.length} appointment{sortedAppointments.length !== 1 ? 's' : ''} (A-Z)
                                    </div>
                                    {sortedAppointments.map(apt => {
                                        const pb = getPaymentBadge(apt.paymentStatus);
                                        const dateStr = apt.scheduledDate || apt.appointmentDate || apt.date || apt.createdAt;
                                        const fmt = formatDateTime(dateStr);
                                        const sc = getStatusColor(apt.status);
                                        const isMissed = (apt.status || '').toUpperCase() === 'MISSED';

                                        return (
                                            <div key={apt.id} style={{ marginBottom: '16px', padding: '20px', background: 'white', border: `2px solid ${isMissed ? '#ea580c40' : '#e5e7eb'}`, borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                                                    <div style={{ fontWeight: '700', fontSize: '17px', color: '#111827' }}>
                                                        📋 {apt.reason || apt.testType || 'Appointment'}
                                                    </div>
                                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-end' }}>
                                                        <span style={{ padding: '5px 12px', borderRadius: '16px', background: sc + '20', color: sc, fontSize: '12px', fontWeight: '700' }}>
                                                            {apt.status || 'Unknown'}
                                                        </span>
                                                        <span style={{ padding: '4px 10px', borderRadius: '12px', background: pb.bg, color: pb.color, fontSize: '11px', fontWeight: '600' }}>
                                                            {pb.label}
                                                        </span>
                                                    </div>
                                                </div>
                                                <div style={{ fontSize: '13px', color: '#6b7280' }}>
                                                    📅 {fmt.date} {fmt.time && `⏰ ${fmt.time}`}
                                                </div>
                                                {apt.price && (
                                                    <div style={{ fontSize: '13px', color: '#059669', fontWeight: '600', marginTop: '4px' }}>
                                                        💰 {apt.price}
                                                    </div>
                                                )}
                                                {apt.paymentStatus === 'PENDING_CONFIRMATION' && (
                                                    <div style={{ marginTop: '8px', padding: '8px 12px', background: '#fef3c7', borderRadius: '6px', fontSize: '12px', color: '#92400e' }}>
                                                        ⏳ Patient submitted payment — admin approval pending
                                                    </div>
                                                )}
                                                {isMissed && (
                                                    <div style={{ marginTop: '8px', padding: '8px 12px', background: '#ffedd5', borderRadius: '6px', fontSize: '12px', color: '#9a3412' }}>
                                                        ⚠️ This appointment was missed
                                                    </div>
                                                )}
                                                {apt.notes && (
                                                    <div style={{ fontSize: '13px', color: '#6b7280', marginTop: '10px', paddingTop: '10px', borderTop: '1px solid #e5e7eb', fontStyle: 'italic' }}>
                                                        💬 {apt.notes}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </>
                            )}
                        </div>
                    )}

                    {/* Results tab */}
                    {activeTab === 'results' && (
                        <div>
                            {sortedTestResults.length === 0 ? (
                                <EmptyState icon="🧪" message="No test results available" />
                            ) : (
                                <>
                                    <div style={{ marginBottom: '16px', padding: '12px 16px', background: '#e0e7ff', borderRadius: '8px', fontSize: '14px', color: '#4338ca', fontWeight: '600' }}>
                                        🧪 {sortedTestResults.length} result{sortedTestResults.length !== 1 ? 's' : ''} (A-Z)
                                    </div>
                                    {sortedTestResults.map(r => {
                                        const fmt = formatDateTime(r.testDate || r.date || r.createdAt);
                                        return (
                                            <div key={r.id} style={{ marginBottom: '16px', padding: '20px', background: 'white', border: '2px solid #e5e7eb', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
                                                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                                                    <h4 style={{ margin: 0, fontSize: '17px', fontWeight: '700' }}>🧪 {r.testType || r.testName || 'Test'}</h4>
                                                    <span style={{ padding: '5px 12px', borderRadius: '16px', background: getStatusColor(r.status) + '20', color: getStatusColor(r.status), fontSize: '12px', fontWeight: '700' }}>
                                                        {r.status || 'Completed'}
                                                    </span>
                                                </div>
                                                <p style={{ margin: '0 0 6px', fontSize: '16px', color: '#667eea', fontWeight: '700' }}>📊 Result: {r.result || 'N/A'}</p>
                                                <p style={{ margin: 0, fontSize: '13px', color: '#9ca3af' }}>📅 {fmt.date}</p>
                                                {r.notes && <p style={{ fontSize: '13px', color: '#6b7280', margin: '10px 0 0', paddingTop: '10px', borderTop: '1px solid #e5e7eb', fontStyle: 'italic' }}>💬 {r.notes}</p>}
                                            </div>
                                        );
                                    })}
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
    <div style={{ marginBottom: '12px' }}>
        <label style={{ display: 'block', fontSize: '12px', fontWeight: '600', color: '#6b7280', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            {icon && `${icon} `}{label}
        </label>
        <div style={{ padding: '10px 14px', background: highlight ? '#fef3c7' : '#f9fafb', borderRadius: '8px', border: `2px solid ${highlight ? '#fbbf24' : '#e5e7eb'}`, fontSize: '14px', fontWeight: highlight ? '700' : '500', color: highlight ? '#92400e' : '#111827' }}>
            {value || 'Not provided'}
        </div>
    </div>
);

const EmptyState = ({ icon, message }) => (
    <div style={{ textAlign: 'center', padding: '60px 20px', background: 'white', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }}>
        <div style={{ fontSize: '64px', marginBottom: '16px' }}>{icon}</div>
        <p style={{ fontSize: '16px', color: '#6b7280', fontWeight: '500' }}>{message}</p>
    </div>
);
window.PatientDetailsModal = PatientDetailsModal;