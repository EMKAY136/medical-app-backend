const { useState, useEffect } = React;

const SidePanel = ({ appointments, setShowModal, onApprovePayment, onRefresh }) => {
    const now = new Date();
    const todayStr = now.toDateString();

    // ── Robust date parser ───────────────────────────────────────────────────
    const parseDate = (v) => {
        if (!v) return null;
        try {
            if (Array.isArray(v)) {
                const [yr, mo, dy, hr = 0, mn = 0] = v;
                return new Date(yr, mo - 1, dy, hr, mn);
            }
            const d = new Date(v);
            return isNaN(d.getTime()) ? null : d;
        } catch { return null; }
    };

    const getAptDate = (apt) =>
        parseDate(apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt);

    // ── Today's appointments ─────────────────────────────────────────────────
    const todayAppointments = appointments.filter(apt => {
        const d = getAptDate(apt);
        return d && d.toDateString() === todayStr;
    });

    // ── Pending payment approvals ────────────────────────────────────────────
    const pendingPayments = appointments.filter(
        apt => (apt.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION'
    );

    // ── Missed (SCHEDULED but date passed) ──────────────────────────────────
    const localMissed = appointments.filter(apt => {
        const st = (apt.status || '').toUpperCase();
        const d  = getAptDate(apt);
        return st === 'MISSED' || (st === 'SCHEDULED' && d && d < now);
    });

    // ── Approve payment helper ───────────────────────────────────────────────
    const [approving, setApproving] = useState(null);

    const handleApprove = async (aptId) => {
        setApproving(aptId);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/appointments/${aptId}/approve-payment`,
                { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } }
            );
            const data = await res.json();
            if (data.success) {
                window.showNotificationAlert && window.showNotificationAlert('Payment approved ✅');
                onRefresh && onRefresh();
            } else {
                alert(data.message || 'Failed to approve');
            }
        } catch (err) {
            alert('Network error: ' + err.message);
        } finally {
            setApproving(null);
        }
    };

    const getPaymentBadge = (ps) => {
        switch ((ps || '').toUpperCase()) {
            case 'PAID':                 return { label: 'Paid',           bg: '#d1fae5', color: '#065f46' };
            case 'PENDING_CONFIRMATION': return { label: 'Pending',        bg: '#fef3c7', color: '#92400e' };
            case 'PAY_ON_ARRIVAL':       return { label: 'Pay on Arrival', bg: '#dbeafe', color: '#1e40af' };
            default:                     return { label: 'Unpaid',         bg: '#fee2e2', color: '#991b1b' };
        }
    };

    return (
        <div className="side-panel">

            {/* ── Pending Payments widget (only shown if any) ── */}
            {pendingPayments.length > 0 && (
                <div className="content-card" style={{ borderLeft: '4px solid #d97706' }}>
                    <div className="card-header" style={{ background: '#fef3c7' }}>
                        <h3 className="card-title" style={{ color: '#92400e' }}>
                            ⏳ Pending Payments ({pendingPayments.length})
                        </h3>
                    </div>
                    <div className="card-content">
                        {pendingPayments.map(apt => (
                            <div key={apt.id} style={{
                                padding: '12px',
                                marginBottom: '10px',
                                background: '#fffbeb',
                                border: '1px solid #fbbf24',
                                borderRadius: '8px',
                            }}>
                                <div style={{ fontWeight: '600', fontSize: '14px', color: '#111827', marginBottom: '4px' }}>
                                    {apt.patientName || 'Unknown Patient'}
                                </div>
                                <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '4px' }}>
                                    {apt.reason || apt.testType || 'Test'}
                                </div>
                                {apt.price && (
                                    <div style={{ fontSize: '13px', color: '#059669', fontWeight: '700', marginBottom: '8px' }}>
                                        {apt.price}
                                    </div>
                                )}
                                <button
                                    onClick={() => handleApprove(apt.id)}
                                    disabled={approving === apt.id}
                                    style={{
                                        width: '100%',
                                        padding: '7px',
                                        background: approving === apt.id ? '#9ca3af' : '#059669',
                                        color: 'white',
                                        border: 'none',
                                        borderRadius: '6px',
                                        cursor: approving === apt.id ? 'not-allowed' : 'pointer',
                                        fontWeight: '600',
                                        fontSize: '13px',
                                    }}
                                >
                                    {approving === apt.id ? 'Approving...' : '✅ Approve Payment'}
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* ── Today's appointments ── */}
            <div className="content-card">
                <div className="card-header">
                    <h3 className="card-title">Today's Appointments ({todayAppointments.length})</h3>
                </div>
                <div className="card-content">
                    {todayAppointments.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">📅</div>
                            <p>No appointments today</p>
                            <small style={{ color: '#6b7280' }}>
                                {now.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                            </small>
                        </div>
                    ) : todayAppointments.map(apt => {
                        const d = getAptDate(apt);
                        const pb = getPaymentBadge(apt.paymentStatus);
                        const status = (apt.status || '').toLowerCase();
                        return (
                            <div key={apt.id} className="appointment-item" style={{ flexDirection: 'column', gap: '8px', alignItems: 'stretch' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                    <div className="appointment-avatar">
                                        {apt.patientName ? apt.patientName.split(' ').map(n => n[0]).join('') : 'U'}
                                    </div>
                                    <div style={{ flex: 1 }}>
                                        <div className="appointment-patient">{apt.patientName || 'Unknown'}</div>
                                        <div className="appointment-details">{apt.reason || apt.testType}</div>
                                        <div className="appointment-details">
                                            {d ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', alignItems: 'flex-end' }}>
                                        <div className={`patient-status status-${status}`}>{apt.status}</div>
                                        <span style={{ fontSize: '10px', padding: '2px 7px', borderRadius: '10px', background: pb.bg, color: pb.color, fontWeight: '600' }}>
                                            {pb.label}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* ── Missed appointments alert ── */}
            {localMissed.length > 0 && (
                <div className="content-card" style={{ borderLeft: '4px solid #ea580c' }}>
                    <div className="card-header">
                        <h3 className="card-title" style={{ color: '#9a3412' }}>⚠️ Missed ({localMissed.length})</h3>
                    </div>
                    <div className="card-content">
                        {localMissed.slice(0, 5).map(apt => (
                            <div key={apt.id} style={{ padding: '8px 0', borderBottom: '1px solid #f3f4f6', fontSize: '13px' }}>
                                <div style={{ fontWeight: '600', color: '#111827' }}>{apt.patientName || 'Unknown'}</div>
                                <div style={{ color: '#6b7280' }}>{apt.reason || apt.testType}</div>
                            </div>
                        ))}
                        {localMissed.length > 5 && (
                            <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '8px', textAlign: 'center' }}>
                                +{localMissed.length - 5} more missed appointments
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* ── Quick Actions ── */}
            <div className="content-card">
                <div className="card-header">
                    <h3 className="card-title">Quick Actions</h3>
                </div>
                <div className="card-content">
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <button
                            className="btn btn-primary"
                            onClick={() => setShowModal('add-result')}
                            style={{ padding: '16px 12px', fontSize: '14px', background: 'linear-gradient(135deg, #48bb78, #38a169)' }}
                        >
                            <i className="fas fa-file-medical"></i>
                            Add Result
                        </button>
                        <button
                            className="btn btn-primary"
                            onClick={() => setShowModal('book-test')}
                            style={{ padding: '16px 12px', fontSize: '14px', background: 'linear-gradient(135deg, #667eea, #764ba2)' }}
                        >
                            <i className="fas fa-calendar-plus"></i>
                            Book Test
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
window.SidePanel = SidePanel;