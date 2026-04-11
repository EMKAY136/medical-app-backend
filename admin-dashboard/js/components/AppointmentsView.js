const { useState, useMemo } = React;

const AppointmentsView = ({ appointments, setShowModal, onRefresh }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterPayment, setFilterPayment] = useState('all');
    const [filterDate, setFilterDate] = useState('');
    const [actionLoading, setActionLoading] = useState(null);
    const [activeSection, setActiveSection] = useState('pending-payments'); // 'pending-payments' | 'all-appointments'

    // ── Date parser ──────────────────────────────────────────────────────────
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

    const fmtDate = (v) => {
        const d = parseDate(v);
        if (!d) return 'Not scheduled';
        return `${d.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    };

    // ── Payment helpers ──────────────────────────────────────────────────────
    const getPaymentBadge = (ps) => {
        switch ((ps || '').toUpperCase()) {
            case 'PAID':                 return { label: 'Paid',              bg: '#d1fae5', color: '#065f46', icon: '✅' };
            case 'PENDING_CONFIRMATION': return { label: 'Awaiting Approval', bg: '#fef3c7', color: '#92400e', icon: '⏳' };
            case 'PAY_ON_ARRIVAL':       return { label: 'Pay on Arrival',    bg: '#dbeafe', color: '#1e40af', icon: '🕐' };
            default:                     return { label: 'Unpaid',            bg: '#fee2e2', color: '#991b1b', icon: '❌' };
        }
    };

    const getStatusBadge = (s) => {
        switch ((s || '').toUpperCase()) {
            case 'SCHEDULED':  return { bg: '#d1fae5', color: '#065f46' };
            case 'COMPLETED':  return { bg: '#dbeafe', color: '#1e40af' };
            case 'MISSED':     return { bg: '#ffedd5', color: '#9a3412' };
            case 'CANCELLED':  return { bg: '#fee2e2', color: '#991b1b' };
            default:           return { bg: '#f3f4f6', color: '#6b7280' };
        }
    };

    // ── Pending payments (patient clicked "I Have Paid") ─────────────────────
    const pendingPayments = useMemo(() =>
        appointments.filter(a => (a.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION'),
        [appointments]
    );

    // ── Approve payment ──────────────────────────────────────────────────────
    const handleApprovePayment = async (appointmentId) => {
        if (!window.confirm('Confirm that you have received this bank transfer?')) return;
        setActionLoading(appointmentId + '-pay');
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/appointments/${appointmentId}/approve-payment`,
                { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } }
            );
            const data = await res.json();
            if (data.success) {
                window.showNotificationAlert && window.showNotificationAlert('Payment approved — patient has been notified ✅');
                onRefresh && onRefresh();
            } else {
                alert(data.message || 'Failed to approve payment');
            }
        } catch (err) {
            alert('Network error: ' + err.message);
        } finally {
            setActionLoading(null);
        }
    };

    // ── Mark missed ──────────────────────────────────────────────────────────
    const handleMarkMissed = async (appointmentId) => {
        if (!window.confirm('Mark this appointment as missed?')) return;
        setActionLoading(appointmentId + '-miss');
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/appointments/${appointmentId}/mark-missed`,
                { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } }
            );
            const data = await res.json();
            if (data.success) {
                window.showNotificationAlert && window.showNotificationAlert('Appointment marked as missed');
                onRefresh && onRefresh();
            } else {
                alert(data.message || 'Could not mark as missed');
            }
        } catch (err) {
            alert('Network error: ' + err.message);
        } finally {
            setActionLoading(null);
        }
    };

    // ── Update status ────────────────────────────────────────────────────────
    const handleUpdateStatus = async (appointmentId, newStatus) => {
        setActionLoading(appointmentId + '-status');
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/appointments/${appointmentId}/status?status=${newStatus}`,
                { method: 'PUT', headers: { Authorization: `Bearer ${token}` } }
            );
            const data = await res.json();
            if (data.success) {
                window.showNotificationAlert && window.showNotificationAlert(`Status updated to ${newStatus}`);
                onRefresh && onRefresh();
            } else {
                alert(data.message || 'Failed to update status');
            }
        } catch (err) {
            alert('Network error: ' + err.message);
        } finally {
            setActionLoading(null);
        }
    };

    // ── Counts for pills ─────────────────────────────────────────────────────
    const counts = useMemo(() => ({
        all:       appointments.length,
        scheduled: appointments.filter(a => (a.status || '').toUpperCase() === 'SCHEDULED').length,
        completed: appointments.filter(a => (a.status || '').toUpperCase() === 'COMPLETED').length,
        missed:    appointments.filter(a => (a.status || '').toUpperCase() === 'MISSED').length,
        cancelled: appointments.filter(a => (a.status || '').toUpperCase() === 'CANCELLED').length,
    }), [appointments]);

    // ── Filtered all-appointments list ────────────────────────────────────────
    const filtered = useMemo(() => {
        return appointments
            .filter(apt => {
                const stOk  = filterStatus  === 'all' || (apt.status || '').toLowerCase() === filterStatus;
                const payOk = filterPayment === 'all' || (apt.paymentStatus || '').toLowerCase() === filterPayment;
                let dateOk  = true;
                if (filterDate) {
                    const d = parseDate(apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt);
                    dateOk = d ? d.toDateString() === new Date(filterDate).toDateString() : false;
                }
                return stOk && payOk && dateOk;
            })
            .sort((a, b) => {
                const da = parseDate(a.appointmentDate || a.scheduledDate || a.date || a.createdAt);
                const db = parseDate(b.appointmentDate || b.scheduledDate || b.date || b.createdAt);
                if (!da) return 1; if (!db) return -1;
                return da - db;
            });
    }, [appointments, filterStatus, filterPayment, filterDate]);

    // ── Section toggle pills ─────────────────────────────────────────────────
    const SectionPill = ({ id, label, count, alertCount }) => (
        <button
            onClick={() => setActiveSection(id)}
            style={{
                padding: '10px 20px',
                borderRadius: '8px',
                border: `2px solid ${activeSection === id ? '#667eea' : '#e5e7eb'}`,
                background: activeSection === id ? '#667eea' : 'white',
                color: activeSection === id ? 'white' : '#374151',
                fontWeight: '700',
                fontSize: '14px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                position: 'relative',
            }}
        >
            {label}
            {count !== undefined && (
                <span style={{
                    padding: '2px 8px',
                    borderRadius: '12px',
                    background: activeSection === id ? 'rgba(255,255,255,0.25)' : '#f3f4f6',
                    color: activeSection === id ? 'white' : '#6b7280',
                    fontSize: '12px',
                    fontWeight: '700',
                }}>
                    {count}
                </span>
            )}
            {alertCount > 0 && (
                <span style={{
                    position: 'absolute',
                    top: '-6px',
                    right: '-6px',
                    width: '18px',
                    height: '18px',
                    borderRadius: '50%',
                    background: '#ef4444',
                    color: 'white',
                    fontSize: '10px',
                    fontWeight: '900',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                    {alertCount}
                </span>
            )}
        </button>
    );

    // ════════════════════════════════════════════════════════════════════════
    return (
        <div style={{ padding: '0' }}>

            {/* ── Section toggle ── */}
            <div style={{ display: 'flex', gap: '12px', padding: '20px 20px 0', flexWrap: 'wrap' }}>
                <SectionPill
                    id="pending-payments"
                    label="💳 Pending Payments"
                    count={pendingPayments.length}
                    alertCount={pendingPayments.length}
                />
                <SectionPill
                    id="all-appointments"
                    label="📅 All Appointments"
                    count={appointments.length}
                    alertCount={0}
                />
            </div>

            {/* ════════════════════════════════════════════════════
                SECTION 1 — PENDING PAYMENTS
            ════════════════════════════════════════════════════ */}
            {activeSection === 'pending-payments' && (
                <div style={{ padding: '20px' }}>

                    {/* Header */}
                    <div style={{
                        background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                        border: '2px solid #fbbf24',
                        borderRadius: '14px',
                        padding: '20px 24px',
                        marginBottom: '20px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '16px',
                    }}>
                        <div style={{ fontSize: '36px' }}>⏳</div>
                        <div>
                            <div style={{ fontSize: '20px', fontWeight: '800', color: '#92400e' }}>
                                Payment Approvals
                            </div>
                            <div style={{ fontSize: '14px', color: '#b45309', marginTop: '2px' }}>
                                These patients have transferred money to the Sterling Bank account and clicked "I Have Paid". Verify receipt and approve below.
                            </div>
                        </div>
                        <div style={{
                            marginLeft: 'auto',
                            background: '#d97706',
                            color: 'white',
                            padding: '10px 20px',
                            borderRadius: '10px',
                            fontSize: '24px',
                            fontWeight: '900',
                            minWidth: '60px',
                            textAlign: 'center',
                        }}>
                            {pendingPayments.length}
                        </div>
                    </div>

                    {pendingPayments.length === 0 ? (
                        <div style={{
                            textAlign: 'center',
                            padding: '60px 20px',
                            background: 'white',
                            borderRadius: '14px',
                            border: '2px dashed #e5e7eb',
                        }}>
                            <div style={{ fontSize: '56px', marginBottom: '12px' }}>✅</div>
                            <div style={{ fontSize: '18px', fontWeight: '700', color: '#374151', marginBottom: '6px' }}>
                                All payments confirmed
                            </div>
                            <div style={{ fontSize: '14px', color: '#9ca3af' }}>
                                No pending bank transfers to review
                            </div>
                        </div>
                    ) : pendingPayments.map(apt => {
                        const dateVal = apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt;
                        const isLoading = actionLoading === apt.id + '-pay';

                        return (
                            <div key={apt.id} style={{
                                background: 'white',
                                border: '2px solid #fbbf24',
                                borderRadius: '14px',
                                marginBottom: '16px',
                                overflow: 'hidden',
                                boxShadow: '0 4px 12px rgba(251,191,36,0.15)',
                            }}>
                                {/* Card top bar */}
                                <div style={{
                                    background: 'linear-gradient(135deg, #fef3c7, #fde68a)',
                                    padding: '10px 20px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                }}>
                                    <div style={{ fontWeight: '700', color: '#92400e', fontSize: '13px' }}>
                                        ⏳ Patient submitted payment — awaiting your confirmation
                                    </div>
                                    <div style={{ fontSize: '12px', color: '#b45309', fontWeight: '600' }}>
                                        #{apt.id}
                                    </div>
                                </div>

                                {/* Card body */}
                                <div style={{ padding: '20px', display: 'grid', gridTemplateColumns: '1fr auto', gap: '20px', alignItems: 'start' }}>

                                    {/* Patient & test info */}
                                    <div>
                                        {/* Patient name */}
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                                            <div style={{
                                                width: '44px',
                                                height: '44px',
                                                borderRadius: '50%',
                                                background: 'linear-gradient(135deg, #667eea, #764ba2)',
                                                color: 'white',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                fontWeight: '700',
                                                fontSize: '16px',
                                                flexShrink: 0,
                                            }}>
                                                {apt.patientName ? apt.patientName.split(' ').map(n => n[0]).join('') : 'U'}
                                            </div>
                                            <div>
                                                <div style={{ fontWeight: '700', fontSize: '17px', color: '#111827' }}>
                                                    {apt.patientName || 'Unknown Patient'}
                                                </div>
                                                <div style={{ fontSize: '12px', color: '#9ca3af', marginTop: '2px' }}>
                                                    Patient ID: {apt.patientId || 'N/A'}
                                                </div>
                                            </div>
                                        </div>

                                        {/* Test details table */}
                                        <div style={{
                                            background: '#f9fafb',
                                            border: '1px solid #e5e7eb',
                                            borderRadius: '10px',
                                            overflow: 'hidden',
                                            marginBottom: '14px',
                                        }}>
                                            <div style={{ padding: '10px 14px', background: '#f3f4f6', borderBottom: '1px solid #e5e7eb', fontSize: '12px', fontWeight: '700', color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                                                Test Details
                                            </div>
                                            <div style={{ padding: '14px' }}>
                                                <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '8px 16px', fontSize: '14px' }}>
                                                    <span style={{ color: '#6b7280', fontWeight: '600' }}>Test:</span>
                                                    <span style={{ color: '#111827', fontWeight: '600' }}>{apt.testType || apt.reason || 'N/A'}</span>

                                                    <span style={{ color: '#6b7280', fontWeight: '600' }}>Date:</span>
                                                    <span style={{ color: '#374151' }}>{fmtDate(dateVal)}</span>

                                                    <span style={{ color: '#6b7280', fontWeight: '600' }}>Status:</span>
                                                    <span>
                                                        <span style={{
                                                            padding: '2px 10px',
                                                            borderRadius: '12px',
                                                            fontSize: '12px',
                                                            fontWeight: '700',
                                                            background: getStatusBadge(apt.status).bg,
                                                            color: getStatusBadge(apt.status).color,
                                                        }}>
                                                            {apt.status || 'Unknown'}
                                                        </span>
                                                    </span>

                                                    <span style={{ color: '#6b7280', fontWeight: '600' }}>Payment Method:</span>
                                                    <span style={{ color: '#374151' }}>{apt.paymentMethod === 'PAY_NOW' ? '🏦 Bank Transfer (Sterling Bank)' : apt.paymentMethod || 'Bank Transfer'}</span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Price highlight */}
                                        <div style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '10px',
                                            padding: '12px 16px',
                                            background: 'linear-gradient(135deg, #ecfdf5, #d1fae5)',
                                            border: '2px solid #6ee7b7',
                                            borderRadius: '10px',
                                        }}>
                                            <span style={{ fontSize: '20px' }}>💰</span>
                                            <div>
                                                <div style={{ fontSize: '11px', color: '#065f46', fontWeight: '600', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Amount Patient Claims to Have Paid</div>
                                                <div style={{ fontSize: '22px', fontWeight: '900', color: '#065f46', marginTop: '2px' }}>
                                                    {apt.price || 'Price not set'}
                                                </div>
                                            </div>
                                        </div>

                                        {/* Bank account reminder */}
                                        <div style={{
                                            marginTop: '12px',
                                            padding: '10px 14px',
                                            background: '#f0f9ff',
                                            border: '1px solid #bae6fd',
                                            borderRadius: '8px',
                                            fontSize: '12px',
                                            color: '#0c4a6e',
                                        }}>
                                            <strong>Verify in your Sterling Bank app:</strong> Account 0089364407 · QUALITEST MEDICAL DIAGNOSTIC SERVICES
                                        </div>
                                    </div>

                                    {/* Right column — Approve / Reject */}
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', minWidth: '180px' }}>
                                        {/* Submitted badge */}
                                        <div style={{
                                            padding: '10px 14px',
                                            background: '#fef3c7',
                                            border: '2px solid #fbbf24',
                                            borderRadius: '10px',
                                            textAlign: 'center',
                                        }}>
                                            <div style={{ fontSize: '24px', marginBottom: '4px' }}>📲</div>
                                            <div style={{ fontSize: '12px', fontWeight: '700', color: '#92400e' }}>Patient clicked</div>
                                            <div style={{ fontSize: '14px', fontWeight: '900', color: '#78350f', marginTop: '2px' }}>"I Have Paid"</div>
                                        </div>

                                        {/* Approve button */}
                                        <button
                                            onClick={() => handleApprovePayment(apt.id)}
                                            disabled={isLoading}
                                            style={{
                                                padding: '14px 18px',
                                                background: isLoading ? '#9ca3af' : 'linear-gradient(135deg, #059669, #047857)',
                                                color: 'white',
                                                border: 'none',
                                                borderRadius: '10px',
                                                cursor: isLoading ? 'not-allowed' : 'pointer',
                                                fontWeight: '800',
                                                fontSize: '14px',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                gap: '8px',
                                                boxShadow: isLoading ? 'none' : '0 4px 12px rgba(5,150,105,0.3)',
                                                transition: 'all 0.2s',
                                            }}
                                            onMouseEnter={e => { if (!isLoading) e.currentTarget.style.transform = 'translateY(-2px)'; }}
                                            onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; }}
                                        >
                                            {isLoading ? (
                                                <><span>⏳</span> Approving...</>
                                            ) : (
                                                <><span style={{ fontSize: '18px' }}>✅</span> Approve Payment</>
                                            )}
                                        </button>

                                        {/* Reject / flag button */}
                                        <button
                                            onClick={() => {
                                                const reason = window.prompt('Reason for rejection (optional):');
                                                if (reason !== null) {
                                                    window.showNotificationAlert && window.showNotificationAlert('Payment flagged — patient should be contacted');
                                                }
                                            }}
                                            style={{
                                                padding: '10px 18px',
                                                background: 'white',
                                                color: '#dc2626',
                                                border: '2px solid #fca5a5',
                                                borderRadius: '10px',
                                                cursor: 'pointer',
                                                fontWeight: '700',
                                                fontSize: '13px',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                gap: '6px',
                                            }}
                                        >
                                            <span>⚠️</span> Flag / Query
                                        </button>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* ════════════════════════════════════════════════════
                SECTION 2 — ALL APPOINTMENTS
            ════════════════════════════════════════════════════ */}
            {activeSection === 'all-appointments' && (
                <div style={{ padding: '20px' }}>

                    {/* Status pills */}
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '16px' }}>
                        {[
                            { key: 'all',       label: 'All',       count: counts.all,       color: '#6b7280' },
                            { key: 'scheduled', label: 'Scheduled', count: counts.scheduled, color: '#065f46' },
                            { key: 'completed', label: 'Completed', count: counts.completed, color: '#1e40af' },
                            { key: 'missed',    label: 'Missed',    count: counts.missed,    color: '#9a3412' },
                            { key: 'cancelled', label: 'Cancelled', count: counts.cancelled, color: '#991b1b' },
                        ].map(st => (
                            <button
                                key={st.key}
                                onClick={() => setFilterStatus(st.key)}
                                style={{
                                    padding: '7px 16px',
                                    borderRadius: '20px',
                                    border: `2px solid ${filterStatus === st.key ? st.color : '#e5e7eb'}`,
                                    background: filterStatus === st.key ? st.color : 'white',
                                    color: filterStatus === st.key ? 'white' : st.color,
                                    fontWeight: '700',
                                    fontSize: '13px',
                                    cursor: 'pointer',
                                }}
                            >
                                {st.label} ({st.count})
                            </button>
                        ))}
                    </div>

                    {/* Filter controls */}
                    <div style={{ display: 'flex', gap: '12px', marginBottom: '14px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '600' }}>Status</label>
                            <select className="form-input" value={filterStatus} onChange={e => setFilterStatus(e.target.value)} style={{ width: '150px' }}>
                                <option value="all">All Status</option>
                                <option value="scheduled">Scheduled</option>
                                <option value="completed">Completed</option>
                                <option value="missed">Missed</option>
                                <option value="cancelled">Cancelled</option>
                            </select>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '600' }}>Payment</label>
                            <select className="form-input" value={filterPayment} onChange={e => setFilterPayment(e.target.value)} style={{ width: '180px' }}>
                                <option value="all">All Payments</option>
                                <option value="paid">Paid</option>
                                <option value="pending_confirmation">Pending Confirmation</option>
                                <option value="pay_on_arrival">Pay on Arrival</option>
                                <option value="unpaid">Unpaid</option>
                            </select>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '600' }}>Date</label>
                            <input type="date" className="form-input" value={filterDate} onChange={e => setFilterDate(e.target.value)} style={{ width: '160px' }} />
                        </div>
                        {(filterStatus !== 'all' || filterPayment !== 'all' || filterDate) && (
                            <button onClick={() => { setFilterStatus('all'); setFilterPayment('all'); setFilterDate(''); }}
                                style={{ padding: '8px 14px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '13px', fontWeight: '600' }}>
                                Clear
                            </button>
                        )}
                    </div>

                    <div style={{ padding: '7px 12px', background: '#f3f4f6', borderRadius: '6px', marginBottom: '16px', fontSize: '13px', color: '#6b7280' }}>
                        📅 Sorted oldest first · Showing {filtered.length} of {appointments.length}
                    </div>

                    {/* Appointment cards */}
                    {filtered.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '48px', color: '#9ca3af' }}>
                            <div style={{ fontSize: '48px', marginBottom: '12px' }}>📅</div>
                            <div style={{ fontSize: '16px', fontWeight: '600', color: '#374151' }}>No appointments found</div>
                        </div>
                    ) : filtered.map((apt, idx) => {
                        const dateVal  = apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt;
                        const sb       = getStatusBadge(apt.status);
                        const pb       = getPaymentBadge(apt.paymentStatus);
                        const aptDate  = parseDate(dateVal);
                        const isPast   = aptDate && aptDate < new Date();
                        const isSchd   = (apt.status || '').toUpperCase() === 'SCHEDULED';
                        const isPending = (apt.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION';

                        return (
                            <div key={apt.id} style={{
                                background: 'white',
                                border: `2px solid ${isPending ? '#fbbf24' : '#e5e7eb'}`,
                                borderRadius: '12px',
                                padding: '16px',
                                marginBottom: '12px',
                                position: 'relative',
                                boxShadow: isPending ? '0 2px 8px rgba(251,191,36,0.2)' : '0 1px 3px rgba(0,0,0,0.06)',
                            }}>
                                {/* Index */}
                                <div style={{ position: 'absolute', top: '8px', left: '8px', background: '#e5e7eb', color: '#6b7280', fontSize: '11px', padding: '2px 6px', borderRadius: '4px', fontWeight: '600' }}>
                                    #{idx + 1}
                                </div>

                                <div style={{ display: 'flex', gap: '12px', paddingLeft: '32px' }}>
                                    {/* Avatar */}
                                    <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: 'linear-gradient(135deg, #667eea, #764ba2)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '14px', flexShrink: 0 }}>
                                        {apt.patientName ? apt.patientName.split(' ').map(n => n[0]).join('') : 'U'}
                                    </div>

                                    {/* Info */}
                                    <div style={{ flex: 1 }}>
                                        <div style={{ fontWeight: '700', fontSize: '15px', color: '#111827', marginBottom: '3px' }}>
                                            {apt.patientName || 'Unknown Patient'}
                                        </div>
                                        <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: '2px' }}>
                                            🧪 {apt.testType || apt.reason || 'Test'}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '4px' }}>
                                            📅 {fmtDate(dateVal)}
                                        </div>
                                        {apt.price && (
                                            <div style={{ fontSize: '13px', color: '#059669', fontWeight: '700', marginBottom: '4px' }}>
                                                💰 {apt.price}
                                            </div>
                                        )}

                                        {/* Pending payment inline notice */}
                                        {isPending && (
                                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fef3c7', border: '1px solid #fbbf24', borderRadius: '8px', padding: '8px 12px', marginTop: '8px' }}>
                                                <div style={{ fontSize: '12px', color: '#92400e', fontWeight: '600' }}>
                                                    📲 Patient clicked "I Have Paid" — verify and approve
                                                </div>
                                                <button
                                                    onClick={() => handleApprovePayment(apt.id)}
                                                    disabled={actionLoading === apt.id + '-pay'}
                                                    style={{ padding: '5px 12px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '12px', marginLeft: '10px', whiteSpace: 'nowrap' }}
                                                >
                                                    {actionLoading === apt.id + '-pay' ? '...' : '✅ Approve'}
                                                </button>
                                            </div>
                                        )}
                                    </div>

                                    {/* Badges + actions */}
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end' }}>
                                        <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: '700', background: sb.bg, color: sb.color }}>
                                            {apt.status || 'Unknown'}
                                        </span>
                                        <span style={{ padding: '3px 9px', borderRadius: '12px', fontSize: '11px', fontWeight: '600', background: pb.bg, color: pb.color }}>
                                            {pb.icon} {pb.label}
                                        </span>

                                        {/* Action buttons */}
                                        <div style={{ display: 'flex', gap: '5px', marginTop: '4px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                                            {isSchd && (
                                                <button onClick={() => handleUpdateStatus(apt.id, 'COMPLETED')} disabled={!!actionLoading}
                                                    style={{ padding: '4px 10px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                                                    ✓ Done
                                                </button>
                                            )}
                                            {isSchd && isPast && (
                                                <button onClick={() => handleMarkMissed(apt.id)} disabled={!!actionLoading}
                                                    style={{ padding: '4px 10px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                                                    ⚠ Missed
                                                </button>
                                            )}
                                            {isSchd && (
                                                <button onClick={() => handleUpdateStatus(apt.id, 'CANCELLED')} disabled={!!actionLoading}
                                                    style={{ padding: '4px 10px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                                                    ✕
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};