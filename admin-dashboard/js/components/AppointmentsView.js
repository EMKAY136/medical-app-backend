const { useState, useMemo } = React;

const AppointmentsView = ({ appointments, setShowModal, onRefresh }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterPayment, setFilterPayment] = useState('all');
    const [filterDate, setFilterDate] = useState('');
    const [actionLoading, setActionLoading] = useState(null); // appointmentId being acted on

    // ── Date parser — handles array, ISO string, epoch ──────────────────────
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
        return `${d.toLocaleDateString()} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    };

    // ── Payment info ─────────────────────────────────────────────────────────
    const getPaymentBadge = (ps) => {
        switch ((ps || '').toUpperCase()) {
            case 'PAID':                 return { label: 'Paid',              bg: '#d1fae5', color: '#065f46', icon: '✅' };
            case 'PENDING_CONFIRMATION': return { label: 'Payment Pending',   bg: '#fef3c7', color: '#92400e', icon: '⏳' };
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

    // ── Approve payment ──────────────────────────────────────────────────────
    const handleApprovePayment = async (appointmentId) => {
        if (!window.confirm('Confirm that you have received this payment?')) return;
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

    // ── Stats ────────────────────────────────────────────────────────────────
    const counts = useMemo(() => ({
        all:       appointments.length,
        scheduled: appointments.filter(a => (a.status || '').toUpperCase() === 'SCHEDULED').length,
        completed: appointments.filter(a => (a.status || '').toUpperCase() === 'COMPLETED').length,
        missed:    appointments.filter(a => (a.status || '').toUpperCase() === 'MISSED').length,
        cancelled: appointments.filter(a => (a.status || '').toUpperCase() === 'CANCELLED').length,
        pendingPay: appointments.filter(a => (a.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION').length,
    }), [appointments]);

    // ── Filter + sort ────────────────────────────────────────────────────────
    const filtered = useMemo(() => {
        return appointments
            .filter(apt => {
                const statusOk = filterStatus === 'all' || (apt.status || '').toLowerCase() === filterStatus;
                const payOk    = filterPayment === 'all' || (apt.paymentStatus || '').toLowerCase() === filterPayment;
                let dateOk = true;
                if (filterDate) {
                    const d = parseDate(apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt);
                    dateOk = d ? d.toDateString() === new Date(filterDate).toDateString() : false;
                }
                return statusOk && payOk && dateOk;
            })
            .sort((a, b) => {
                const da = parseDate(a.appointmentDate || a.scheduledDate || a.date || a.createdAt);
                const db = parseDate(b.appointmentDate || b.scheduledDate || b.date || b.createdAt);
                if (!da) return 1; if (!db) return -1;
                return da - db; // oldest first
            });
    }, [appointments, filterStatus, filterPayment, filterDate]);

    return (
        <div className="content-card">
            <div className="card-header">
                <h2 className="card-title">Appointments ({filtered.length})</h2>
            </div>

            <div className="card-content">

                {/* ── Stat pills ── */}
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '20px' }}>
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
                                padding: '6px 14px',
                                borderRadius: '20px',
                                border: `2px solid ${filterStatus === st.key ? st.color : '#e5e7eb'}`,
                                background: filterStatus === st.key ? st.color : 'white',
                                color: filterStatus === st.key ? 'white' : st.color,
                                fontWeight: '600',
                                fontSize: '13px',
                                cursor: 'pointer',
                            }}
                        >
                            {st.label} ({st.count})
                        </button>
                    ))}

                    {/* Pending payment alert pill */}
                    {counts.pendingPay > 0 && (
                        <button
                            onClick={() => setFilterPayment(filterPayment === 'pending_confirmation' ? 'all' : 'pending_confirmation')}
                            style={{
                                padding: '6px 14px',
                                borderRadius: '20px',
                                border: '2px solid #d97706',
                                background: filterPayment === 'pending_confirmation' ? '#d97706' : '#fef3c7',
                                color: filterPayment === 'pending_confirmation' ? 'white' : '#92400e',
                                fontWeight: '700',
                                fontSize: '13px',
                                cursor: 'pointer',
                                display: 'flex', alignItems: 'center', gap: '6px',
                            }}
                        >
                            ⏳ Pending Payments ({counts.pendingPay})
                        </button>
                    )}
                </div>

                {/* ── Filter controls ── */}
                <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                    <div>
                        <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '500' }}>Status</label>
                        <select className="form-input" value={filterStatus} onChange={e => setFilterStatus(e.target.value)} style={{ width: '160px' }}>
                            <option value="all">All Status</option>
                            <option value="scheduled">Scheduled</option>
                            <option value="completed">Completed</option>
                            <option value="missed">Missed</option>
                            <option value="cancelled">Cancelled</option>
                        </select>
                    </div>
                    <div>
                        <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '500' }}>Payment</label>
                        <select className="form-input" value={filterPayment} onChange={e => setFilterPayment(e.target.value)} style={{ width: '180px' }}>
                            <option value="all">All Payments</option>
                            <option value="paid">Paid</option>
                            <option value="pending_confirmation">Pending Confirmation</option>
                            <option value="pay_on_arrival">Pay on Arrival</option>
                            <option value="unpaid">Unpaid</option>
                        </select>
                    </div>
                    <div>
                        <label style={{ display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: '500' }}>Date</label>
                        <input type="date" className="form-input" value={filterDate} onChange={e => setFilterDate(e.target.value)} style={{ width: '160px' }} />
                    </div>
                    {(filterStatus !== 'all' || filterPayment !== 'all' || filterDate) && (
                        <button
                            onClick={() => { setFilterStatus('all'); setFilterPayment('all'); setFilterDate(''); }}
                            style={{ padding: '8px 14px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '13px' }}
                        >
                            Clear
                        </button>
                    )}
                </div>

                <div style={{ padding: '7px 12px', background: '#f3f4f6', borderRadius: '6px', marginBottom: '16px', fontSize: '13px', color: '#6b7280' }}>
                    📅 Sorted oldest first · Showing {filtered.length} of {appointments.length} appointments
                </div>

                {/* ── Appointment cards ── */}
                <div className="patient-list">
                    {filtered.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '48px 20px', color: '#9ca3af' }}>
                            <div style={{ fontSize: '48px', marginBottom: '12px' }}>📅</div>
                            <div style={{ fontSize: '16px', fontWeight: '500', color: '#374151' }}>No appointments found</div>
                            <div style={{ fontSize: '14px', marginTop: '4px' }}>
                                {filterDate || filterStatus !== 'all' || filterPayment !== 'all' ? 'Try adjusting your filters' : 'No appointments yet'}
                            </div>
                        </div>
                    ) : filtered.map((apt, idx) => {
                        const dateVal = apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt;
                        const sb = getStatusBadge(apt.status);
                        const pb = getPaymentBadge(apt.paymentStatus);
                        const aptDate = parseDate(dateVal);
                        const isPast  = aptDate && aptDate < new Date();
                        const isScheduled = (apt.status || '').toUpperCase() === 'SCHEDULED';
                        const isPendingPay = (apt.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION';

                        return (
                            <div key={apt.id} className="appointment-item" style={{ position: 'relative', flexDirection: 'column', alignItems: 'stretch', gap: '10px' }}>
                                {/* Index */}
                                <div style={{ position: 'absolute', top: '8px', left: '8px', background: '#e5e7eb', color: '#6b7280', fontSize: '11px', padding: '2px 6px', borderRadius: '4px', fontWeight: '600' }}>
                                    #{idx + 1}
                                </div>

                                {/* Top row */}
                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', paddingLeft: '28px' }}>
                                    <div className="appointment-avatar">
                                        {apt.patientName ? apt.patientName.split(' ').map(n => n[0]).join('') : 'U'}
                                    </div>
                                    <div style={{ flex: 1 }}>
                                        <div style={{ fontWeight: '600', fontSize: '15px', color: '#111827', marginBottom: '4px' }}>
                                            {apt.patientName || 'Unknown Patient'}
                                        </div>
                                        <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: '3px' }}>
                                            {apt.reason || apt.testType || 'Appointment'}
                                        </div>
                                        <div style={{ fontSize: '12px', color: '#9ca3af', marginBottom: '3px' }}>
                                            📅 {fmtDate(dateVal)}
                                        </div>
                                        {apt.price && (
                                            <div style={{ fontSize: '13px', color: '#059669', fontWeight: '600' }}>
                                                💰 {apt.price}
                                            </div>
                                        )}
                                        {apt.notes && (
                                            <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px', fontStyle: 'italic' }}>
                                                💬 {apt.notes}
                                            </div>
                                        )}
                                    </div>

                                    {/* Badges column */}
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end' }}>
                                        {/* Status badge */}
                                        <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: '700', background: sb.bg, color: sb.color }}>
                                            {apt.status || 'Unknown'}
                                        </span>
                                        {/* Payment badge */}
                                        <span style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: '600', background: pb.bg, color: pb.color, display: 'flex', alignItems: 'center', gap: '4px' }}>
                                            {pb.icon} {pb.label}
                                        </span>
                                    </div>
                                </div>

                                {/* ── Pending payment notice + Approve button ── */}
                                {isPendingPay && (
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fef3c7', border: '1px solid #fbbf24', borderRadius: '8px', padding: '10px 14px' }}>
                                        <div style={{ fontSize: '13px', color: '#92400e', fontWeight: '500' }}>
                                            ⏳ Patient has submitted payment — awaiting your confirmation
                                        </div>
                                        <button
                                            onClick={() => handleApprovePayment(apt.id)}
                                            disabled={actionLoading === apt.id + '-pay'}
                                            style={{ padding: '7px 16px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '13px', whiteSpace: 'nowrap', marginLeft: '12px' }}
                                        >
                                            {actionLoading === apt.id + '-pay' ? 'Approving...' : '✅ Approve Payment'}
                                        </button>
                                    </div>
                                )}

                                {/* ── Action buttons ── */}
                                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                    {/* Mark completed */}
                                    {isScheduled && (
                                        <button
                                            onClick={() => handleUpdateStatus(apt.id, 'COMPLETED')}
                                            disabled={!!actionLoading}
                                            style={{ padding: '6px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}
                                        >
                                            {actionLoading === apt.id + '-status' ? '...' : '✓ Mark Completed'}
                                        </button>
                                    )}
                                    {/* Mark missed (scheduled + past date) */}
                                    {isScheduled && isPast && (
                                        <button
                                            onClick={() => handleMarkMissed(apt.id)}
                                            disabled={!!actionLoading}
                                            style={{ padding: '6px 12px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}
                                        >
                                            {actionLoading === apt.id + '-miss' ? '...' : '⚠ Mark Missed'}
                                        </button>
                                    )}
                                    {/* Cancel */}
                                    {isScheduled && (
                                        <button
                                            onClick={() => handleUpdateStatus(apt.id, 'CANCELLED')}
                                            disabled={!!actionLoading}
                                            style={{ padding: '6px 12px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}
                                        >
                                            ✕ Cancel
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};