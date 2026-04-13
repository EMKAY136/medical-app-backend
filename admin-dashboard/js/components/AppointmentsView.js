const { useState, useMemo } = React;

const AppointmentsView = ({ appointments, setShowModal, onRefresh }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterPayment, setFilterPayment] = useState('all');
    const [filterDate, setFilterDate] = useState('');
    const [searchName, setSearchName] = useState('');
    const [actionLoading, setActionLoading] = useState(null);
    const [activeSection, setActiveSection] = useState('pending-payments');
    const [editingGroup, setEditingGroup] = useState(null);
    const [editTests, setEditTests] = useState([]);
    const [savingTests, setSavingTests] = useState(false);
    const [rescheduleModal, setRescheduleModal] = useState(null);
    const [rescheduleDate, setRescheduleDate] = useState('');
    const [rescheduleTime, setRescheduleTime] = useState('');
    const [rescheduling, setRescheduling] = useState(false);

    // ── Refund section search states ─────────────────────────────────────────
    const [refundSearch, setRefundSearch] = useState('');
    const [pendingSearch, setPendingSearch] = useState('');
    const [missedSearch, setMissedSearch] = useState('');
    const [rescheduledSearch, setRescheduledSearch] = useState('');

    // ── Refund process modal ─────────────────────────────────────────────────
    const [refundModal, setRefundModal] = useState(null); // { group, action: 'APPROVED'|'REJECTED' }
    const [processingRefund, setProcessingRefund] = useState(false);
    const [refundSubFilter, setRefundSubFilter] = useState('all');

    // ── Date parser ──────────────────────────────────────────────────────────
    const parseDate = (v) => {
        if (!v) return null;
        try {
            if (Array.isArray(v)) {
                const [yr, mo, dy, hr = 0, mn = 0] = v;
                return new Date(yr, mo - 1, dy, hr, mn);
            }
            const s = String(v).trim();
            if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(s) && !s.endsWith('Z') && !/[+-]\d{2}:?\d{2}$/.test(s)) {
                const [datePart, timePart] = s.split('T');
                const [yr, mo, dy] = datePart.split('-').map(Number);
                const [hr, mn] = timePart.split(':').map(Number);
                return new Date(yr, mo - 1, dy, hr, mn);
            }
            const d = new Date(v);
            return isNaN(d.getTime()) ? null : d;
        } catch { return null; }
    };

    const fmtDate = (v) => {
        const d = parseDate(v);
        if (!d) return 'Not scheduled';
        if (d.getHours() === 0 && d.getMinutes() === 0) {
            return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
        }
        return `${d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    };

    // ── Price helpers ────────────────────────────────────────────────────────
    const parsePrice = (str) =>
        parseInt((str || '').replace(/[₦,\s]/g, '').split('.')[0], 10) || 0;
    const formatNaira = (n) => '₦' + n.toLocaleString('en-NG') + '.00';

    // ── Badge helpers ────────────────────────────────────────────────────────
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
            case 'SCHEDULED':    return { bg: '#d1fae5', color: '#065f46' };
            case 'COMPLETED':    return { bg: '#dbeafe', color: '#1e40af' };
            case 'MISSED':       return { bg: '#ffedd5', color: '#9a3412' };
            case 'CANCELLED':    return { bg: '#fee2e2', color: '#991b1b' };
            case 'RESCHEDULED':  return { bg: '#ede9fe', color: '#5b21b6' };
            default:             return { bg: '#f3f4f6', color: '#6b7280' };
        }
    };

    const getRefundBadge = (rs) => {
        switch ((rs || '').toUpperCase()) {
            case 'REQUESTED': return { label: 'Refund Requested', bg: '#fef3c7', color: '#92400e', icon: '🔄' };
            case 'APPROVED':  return { label: 'Refund Approved',  bg: '#d1fae5', color: '#065f46', icon: '✅' };
            case 'REJECTED':  return { label: 'Refund Rejected',  bg: '#fee2e2', color: '#991b1b', icon: '❌' };
            default:          return null;
        }
    };

    // ── Group same-patient same-datetime appointments ─────────────────────────
    const groupAppointments = (list) => {
        const groups = {};
        list.forEach(apt => {
            const dateVal = apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt;
            const d = parseDate(dateVal);
            const dateKey = d
                ? `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}-${d.getHours()}-${d.getMinutes()}`
                : 'unknown';
            const key = `${apt.patientId || apt.patientName || 'unknown'}_${dateKey}`;

            if (!groups[key]) {
                groups[key] = {
                    key, patientId: apt.patientId, patientName: apt.patientName,
                    appointmentDate: dateVal, status: apt.status,
                    paymentStatus: apt.paymentStatus, paymentMethod: apt.paymentMethod,
                    refundStatus: apt.refundStatus,
                    refundReason: apt.refundReason,
                    refundRequestedAt: apt.refundRequestedAt,
                    appointments: [], totalPrice: 0,
                };
            }
            groups[key].appointments.push(apt);
            groups[key].totalPrice += parsePrice(apt.price);

            // Bubble up refund status from any appointment in the group
            const refundStatuses = groups[key].appointments.map(a => (a.refundStatus || '').toUpperCase());
            if (refundStatuses.includes('REQUESTED')) groups[key].refundStatus = 'REQUESTED';
            else if (refundStatuses.includes('APPROVED')) groups[key].refundStatus = 'APPROVED';
            else if (refundStatuses.includes('REJECTED')) groups[key].refundStatus = 'REJECTED';

            // Bubble up refund reason
            const withReason = groups[key].appointments.find(a => a.refundReason);
            if (withReason) groups[key].refundReason = withReason.refundReason;

            const statuses = groups[key].appointments.map(a => (a.status || '').toUpperCase());
            if (statuses.includes('SCHEDULED'))         groups[key].status = 'SCHEDULED';
            else if (statuses.includes('RESCHEDULED'))  groups[key].status = 'RESCHEDULED';
            else if (statuses.includes('MISSED'))       groups[key].status = 'MISSED';
            else if (statuses.includes('COMPLETED'))    groups[key].status = 'COMPLETED';
            else if (statuses.includes('CANCELLED'))    groups[key].status = 'CANCELLED';
        });

        return Object.values(groups).sort((a, b) => {
            const da = parseDate(a.appointmentDate);
            const db = parseDate(b.appointmentDate);
            if (!da) return 1; if (!db) return -1;
            return da - db;
        });
    };

    // ── Search helper ────────────────────────────────────────────────────────
    const filterByName = (groups, query) => {
        if (!query.trim()) return groups;
        const q = query.toLowerCase();
        return groups.filter(g => (g.patientName || '').toLowerCase().includes(q));
    };

    // ── Computed sections ────────────────────────────────────────────────────
    const pendingPayments = useMemo(() =>
        groupAppointments(appointments.filter(a => (a.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION')),
        [appointments]
    );

    const paidButMissed = useMemo(() =>
        groupAppointments(appointments.filter(a =>
            (a.status || '').toUpperCase() === 'MISSED' &&
            (a.paymentStatus || '').toUpperCase() === 'PAID' &&
            (a.rescheduleStatus || '').toUpperCase() !== 'RESCHEDULED'
        )),
        [appointments]
    );

    const rescheduledGroups = useMemo(() =>
        groupAppointments(appointments.filter(a =>
            (a.status || '').toUpperCase() === 'RESCHEDULED' ||
            (a.rescheduleStatus || '').toUpperCase() === 'RESCHEDULED'
        )),
        [appointments]
    );

    // ── Refund requests section ──────────────────────────────────────────────
    const refundGroups = useMemo(() =>
        groupAppointments(appointments.filter(a =>
            (a.refundStatus || '').toUpperCase() === 'REQUESTED' ||
            (a.refundStatus || '').toUpperCase() === 'APPROVED' ||
            (a.refundStatus || '').toUpperCase() === 'REJECTED'
        )),
        [appointments]
    );

    const pendingRefundGroups = useMemo(() =>
        groupAppointments(appointments.filter(a =>
            (a.refundStatus || '').toUpperCase() === 'REQUESTED'
        )),
        [appointments]
    );

    const counts = useMemo(() => ({
        all:         appointments.length,
        scheduled:   appointments.filter(a => (a.status || '').toUpperCase() === 'SCHEDULED').length,
        completed:   appointments.filter(a => (a.status || '').toUpperCase() === 'COMPLETED').length,
        missed:      appointments.filter(a => (a.status || '').toUpperCase() === 'MISSED').length,
        cancelled:   appointments.filter(a => (a.status || '').toUpperCase() === 'CANCELLED').length,
        rescheduled: appointments.filter(a => (a.status || '').toUpperCase() === 'RESCHEDULED').length,
    }), [appointments]);

    const filteredGroups = useMemo(() => {
        const q = searchName.toLowerCase().trim();
        const filtered = appointments.filter(apt => {
            const stOk   = filterStatus  === 'all' || (apt.status || '').toLowerCase() === filterStatus;
            const payOk  = filterPayment === 'all' || (apt.paymentStatus || '').toLowerCase() === filterPayment;
            const nameOk = !q || (apt.patientName || '').toLowerCase().includes(q);
            let dateOk   = true;
            if (filterDate) {
                const d = parseDate(apt.appointmentDate || apt.scheduledDate || apt.date || apt.createdAt);
                dateOk = d ? d.toDateString() === new Date(filterDate).toDateString() : false;
            }
            return stOk && payOk && nameOk && dateOk;
        });
        return groupAppointments(filtered);
    }, [appointments, filterStatus, filterPayment, filterDate, searchName]);

    // ── API helpers ──────────────────────────────────────────────────────────
    const apiPatch = async (url, body) => {
        const token = localStorage.getItem('authToken');
        const res = await fetch(`${CONFIG.ADMIN_API_URL}${url}`, {
            method: 'PATCH',
            headers: {
                Authorization: `Bearer ${token}`,
                ...(body ? { 'Content-Type': 'application/json' } : {}),
            },
            ...(body ? { body: JSON.stringify(body) } : {}),
        });
        return res.json();
    };

    const apiPut = async (url, body) => {
        const token = localStorage.getItem('authToken');
        const res = await fetch(`${CONFIG.ADMIN_API_URL}${url}`, {
            method: 'PUT',
            headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined,
        });
        return res.json();
    };

    const handleApprovePayment = async (group) => {
        if (!window.confirm(`Confirm receipt of ${formatNaira(group.totalPrice)} for ${group.appointments.length} test(s) from ${group.patientName}?`)) return;
        for (const apt of group.appointments) {
            setActionLoading(apt.id + '-pay');
            try {
                const data = await apiPatch(`/api/admin/appointments/${apt.id}/approve-payment`);
                if (!data.success) { alert(data.message || `Failed to approve #${apt.id}`); setActionLoading(null); return; }
            } catch (err) { alert('Network error: ' + err.message); setActionLoading(null); return; }
        }
        setActionLoading(null);
        window.showNotificationAlert && window.showNotificationAlert(`Payment approved for ${group.patientName} ✅`);
        onRefresh && onRefresh();
    };

    const handleMarkMissed = async (aptId) => {
        setActionLoading(aptId + '-miss');
        try {
            const data = await apiPatch(`/api/admin/appointments/${aptId}/mark-missed`);
            if (data.success) { window.showNotificationAlert && window.showNotificationAlert('Marked as missed'); onRefresh && onRefresh(); }
            else alert(data.message || 'Could not mark as missed');
        } catch (err) { alert('Network error: ' + err.message); }
        finally { setActionLoading(null); }
    };

    const handleUpdateStatus = async (aptId, newStatus) => {
        setActionLoading(aptId + '-status');
        try {
            const data = await apiPut(`/api/admin/appointments/${aptId}/status?status=${newStatus}`);
            if (data.success) { window.showNotificationAlert && window.showNotificationAlert(`Status → ${newStatus}`); onRefresh && onRefresh(); }
            else alert(data.message || 'Failed to update status');
        } catch (err) { alert('Network error: ' + err.message); }
        finally { setActionLoading(null); }
    };

    const handleGroupStatus = async (group, newStatus) => {
        for (const apt of group.appointments) await handleUpdateStatus(apt.id, newStatus);
    };

    // ── Process refund (approve / reject) ────────────────────────────────────
    const handleProcessRefund = async (group, action) => {
        setProcessingRefund(true);
        try {
            for (const apt of group.appointments) {
                const data = await apiPatch(
                    `/api/appointments/${apt.id}/process-refund`,
                    { action }
                );
                if (!data.success) {
                    alert(data.message || `Failed to process refund for appointment #${apt.id}`);
                    setProcessingRefund(false);
                    return;
                }
            }
            window.showNotificationAlert && window.showNotificationAlert(
                `Refund ${action === 'APPROVED' ? 'approved ✅' : 'rejected'} for ${group.patientName}`
            );
            setRefundModal(null);
            onRefresh && onRefresh();
        } catch (err) {
            alert('Network error: ' + err.message);
        } finally {
            setProcessingRefund(false);
        }
    };

    const handleReschedule = async () => {
        if (!rescheduleDate || !rescheduleTime) { alert('Please select both a date and time.'); return; }
        setRescheduling(true);
        try {
            const group = rescheduleModal;
            for (const apt of group.appointments) {
                await apiPut(`/api/admin/appointments/${apt.id}`, {
                    scheduledDate: rescheduleDate,
                    scheduledTime: rescheduleTime,
                    status: 'RESCHEDULED',
                    rescheduleStatus: 'RESCHEDULED',
                });
            }
            window.showNotificationAlert && window.showNotificationAlert(
                `Rescheduled ${group.appointments.length} test(s) for ${group.patientName} ✅`
            );
            setRescheduleModal(null);
            setRescheduleDate('');
            setRescheduleTime('');
            onRefresh && onRefresh();
        } catch (err) {
            alert('Error rescheduling: ' + err.message);
        } finally {
            setRescheduling(false);
        }
    };

    const openEditTests = (group) => {
        setEditingGroup(group);
        setEditTests(group.appointments.map(a => ({
            id: a.id, testType: a.testType || a.reason || '',
            price: a.price || '', original: a.testType || a.reason || '',
        })));
    };

    const saveEditedTests = async () => {
        setSavingTests(true);
        try {
            for (const t of editTests) {
                await apiPut(`/api/admin/appointments/${t.id}`, { testType: t.testType, price: t.price });
            }
            window.showNotificationAlert && window.showNotificationAlert('Tests updated ✅');
            setEditingGroup(null); setEditTests([]);
            onRefresh && onRefresh();
        } catch (err) { alert('Error saving: ' + err.message); }
        finally { setSavingTests(false); }
    };

    // ── Search bar component ─────────────────────────────────────────────────
    const SearchBar = ({ value, onChange, placeholder = 'Search by patient name...' }) => (
        <div style={{
            display: 'flex', alignItems: 'center', gap: '8px',
            background: '#f9fafb', border: '1.5px solid #e5e7eb',
            borderRadius: '8px', padding: '7px 12px', marginBottom: '14px',
        }}>
            <span style={{ fontSize: '14px', color: '#9ca3af' }}>🔍</span>
            <input
                type="text"
                placeholder={placeholder}
                value={value}
                onChange={e => onChange(e.target.value)}
                style={{
                    flex: 1, border: 'none', background: 'transparent',
                    fontSize: '13px', color: '#374151', outline: 'none',
                }}
            />
            {value && (
                <button
                    onClick={() => onChange('')}
                    style={{
                        border: 'none', background: 'none', cursor: 'pointer',
                        color: '#9ca3af', fontSize: '16px', lineHeight: 1, padding: 0,
                    }}
                >×</button>
            )}
        </div>
    );

    // ── Section pill ─────────────────────────────────────────────────────────
    const SectionPill = ({ id, label, count, alertCount }) => (
        <button
            onClick={() => setActiveSection(id)}
            style={{
                padding: '8px 16px', borderRadius: '8px',
                border: `2px solid ${activeSection === id ? '#667eea' : '#e5e7eb'}`,
                background: activeSection === id ? '#667eea' : 'white',
                color: activeSection === id ? 'white' : '#374151',
                fontWeight: '700', fontSize: '13px', cursor: 'pointer',
                display: 'flex', alignItems: 'center', gap: '6px', position: 'relative',
            }}
        >
            {label}
            {count !== undefined && (
                <span style={{
                    padding: '1px 7px', borderRadius: '12px',
                    background: activeSection === id ? 'rgba(255,255,255,0.25)' : '#f3f4f6',
                    color: activeSection === id ? 'white' : '#6b7280',
                    fontSize: '11px', fontWeight: '700',
                }}>{count}</span>
            )}
            {alertCount > 0 && (
                <span style={{
                    position: 'absolute', top: '-6px', right: '-6px',
                    width: '16px', height: '16px', borderRadius: '50%',
                    background: '#ef4444', color: 'white',
                    fontSize: '9px', fontWeight: '900',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>{alertCount}</span>
            )}
        </button>
    );

    // ── Group card ────────────────────────────────────────────────────────────
    const GroupCard = ({ group, showRescheduleBtn = false }) => {
        const sb = getStatusBadge(group.status);
        const pb = getPaymentBadge(group.paymentStatus);
        const rb = getRefundBadge(group.refundStatus);
        const aptDate = parseDate(group.appointmentDate);
        const isPast = aptDate && aptDate < new Date();
        const isSchd = (group.status || '').toUpperCase() === 'SCHEDULED';
        const isRescheduled = (group.status || '').toUpperCase() === 'RESCHEDULED';
        const isPending = (group.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION';
        const isMissedPaid =
            (group.status || '').toUpperCase() === 'MISSED' &&
            (group.paymentStatus || '').toUpperCase() === 'PAID';
        const isRefundRequested = (group.refundStatus || '').toUpperCase() === 'REQUESTED';
        const isGroupLoading = group.appointments.some(a =>
            actionLoading === a.id + '-pay' ||
            actionLoading === a.id + '-status' ||
            actionLoading === a.id + '-miss'
        );

        // Show refund button: paid + (scheduled or missed) + no refund yet
        const isPaid = (group.paymentStatus || '').toUpperCase() === 'PAID';
        const noRefundYet = !group.refundStatus || group.refundStatus === 'NONE';
        const canRefund = isPaid && ((group.status || '').toUpperCase() === 'SCHEDULED' || isMissedPaid) && noRefundYet;

        const borderColor = isRefundRequested ? '#f59e0b'
            : isPending ? '#fbbf24'
            : isMissedPaid ? '#f97316'
            : isRescheduled ? '#8b5cf6'
            : '#e5e7eb';
        const shadow = isRefundRequested ? '0 2px 8px rgba(245,158,11,0.18)'
            : isPending ? '0 2px 8px rgba(251,191,36,0.15)'
            : isMissedPaid ? '0 2px 8px rgba(249,115,22,0.15)'
            : isRescheduled ? '0 2px 8px rgba(139,92,246,0.15)'
            : '0 1px 3px rgba(0,0,0,0.05)';

        return (
            <div style={{ background: 'white', border: `1.5px solid ${borderColor}`, borderRadius: '10px', padding: '12px 14px', marginBottom: '10px', boxShadow: shadow }}>
                {/* Row 1: avatar + name + date + badges */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                    <div style={{
                        width: '34px', height: '34px', borderRadius: '50%', flexShrink: 0,
                        background: 'linear-gradient(135deg, #667eea, #764ba2)',
                        color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontWeight: '700', fontSize: '12px',
                    }}>
                        {group.patientName ? group.patientName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : 'U'}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: '700', fontSize: '14px', color: '#111827' }}>
                            {group.patientName || 'Unknown Patient'}
                            <span style={{ fontWeight: '400', color: '#9ca3af', fontSize: '11px', marginLeft: '6px' }}>
                                ID: {group.patientId || 'N/A'}
                            </span>
                        </div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '1px' }}>
                            📅 {fmtDate(group.appointmentDate)}
                        </div>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', alignItems: 'flex-end', flexShrink: 0 }}>
                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '700', background: sb.bg, color: sb.color, whiteSpace: 'nowrap' }}>
                            {group.status || 'Unknown'}
                        </span>
                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '600', background: pb.bg, color: pb.color, whiteSpace: 'nowrap' }}>
                            {pb.icon} {pb.label}
                        </span>
                        {rb && (
                            <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '700', background: rb.bg, color: rb.color, whiteSpace: 'nowrap' }}>
                                {rb.icon} {rb.label}
                            </span>
                        )}
                    </div>
                </div>

                {/* Row 2: tests breakdown */}
                <div style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '7px', padding: '8px 10px', marginBottom: '8px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '5px' }}>
                        <span style={{ fontSize: '10px', fontWeight: '700', color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                            {group.appointments.length} Test{group.appointments.length > 1 ? 's' : ''}
                        </span>
                        <button
                            onClick={() => openEditTests(group)}
                            style={{ fontSize: '10px', fontWeight: '700', color: '#667eea', background: '#ede9fe', border: 'none', borderRadius: '5px', padding: '2px 8px', cursor: 'pointer' }}
                        >
                            ✏️ Edit Tests
                        </button>
                    </div>
                    {group.appointments.map((apt, i) => (
                        <div key={apt.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', paddingTop: i > 0 ? '4px' : '0', borderTop: i > 0 ? '1px solid #e5e7eb' : 'none', marginTop: i > 0 ? '4px' : '0' }}>
                            <span style={{ color: '#374151', fontWeight: '500' }}>🧪 {apt.testType || apt.reason || 'Medical Test'}</span>
                            <span style={{ color: '#059669', fontWeight: '700', flexShrink: 0, marginLeft: '8px' }}>{apt.price || '—'}</span>
                        </div>
                    ))}
                    {group.appointments.length > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: '800', color: '#111827', borderTop: '2px solid #d1d5db', marginTop: '6px', paddingTop: '6px' }}>
                            <span>Total</span>
                            <span style={{ color: '#059669' }}>{formatNaira(group.totalPrice)}</span>
                        </div>
                    )}
                </div>

                {/* Pending payment notice */}
                {isPending && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fef3c7', border: '1px solid #fbbf24', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#92400e', fontWeight: '600' }}>
                            📲 Patient clicked "I Have Paid" — verify Sterling Bank (0089364407) and approve
                        </div>
                        <button
                            onClick={() => handleApprovePayment(group)}
                            disabled={isGroupLoading}
                            style={{ padding: '4px 12px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap', opacity: isGroupLoading ? 0.6 : 1 }}
                        >
                            {isGroupLoading ? '...' : '✅ Approve'}
                        </button>
                    </div>
                )}

                {/* Refund requested notice */}
                {isRefundRequested && (
                    <div style={{ background: '#fffbeb', border: '1.5px solid #f59e0b', borderRadius: '7px', padding: '9px 12px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '12px', color: '#92400e', fontWeight: '700', marginBottom: '4px' }}>
                            🔄 Patient has requested a refund
                        </div>
                        {group.refundReason && (
                            <div style={{ fontSize: '11px', color: '#78350f', background: '#fef3c7', borderRadius: '5px', padding: '5px 8px', marginBottom: '8px', fontStyle: 'italic' }}>
                                "{group.refundReason}"
                            </div>
                        )}
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                                onClick={() => setRefundModal({ group, action: 'APPROVED' })}
                                disabled={processingRefund}
                                style={{ flex: 1, padding: '5px 10px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px' }}
                            >
                                ✅ Approve Refund
                            </button>
                            <button
                                onClick={() => setRefundModal({ group, action: 'REJECTED' })}
                                disabled={processingRefund}
                                style={{ flex: 1, padding: '5px 10px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px' }}
                            >
                                ✕ Reject Refund
                            </button>
                        </div>
                    </div>
                )}

                {/* Approved refund notice */}
                {(group.refundStatus || '').toUpperCase() === 'APPROVED' && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: '#d1fae5', border: '1px solid #6ee7b7', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <span style={{ fontSize: '14px' }}>✅</span>
                        <div style={{ fontSize: '11px', color: '#065f46', fontWeight: '600' }}>
                            Refund approved and processed for this patient.
                        </div>
                    </div>
                )}

                {/* Rejected refund notice */}
                {(group.refundStatus || '').toUpperCase() === 'REJECTED' && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: '#fee2e2', border: '1px solid #fca5a5', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <span style={{ fontSize: '14px' }}>❌</span>
                        <div style={{ fontSize: '11px', color: '#991b1b', fontWeight: '600' }}>
                            Refund request was rejected.
                        </div>
                    </div>
                )}

                {/* Admin-initiated refund button (paid + scheduled or missed + no refund yet) */}
                {canRefund && !isRefundRequested && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fff7ed', border: '1px solid #f97316', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#9a3412', fontWeight: '600' }}>
                            💸 This patient has paid. Issue a refund if needed.
                        </div>
                        <button
                            onClick={() => setRefundModal({ group, action: 'APPROVED' })}
                            style={{ padding: '4px 12px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap' }}
                        >
                            💰 Refund
                        </button>
                    </div>
                )}

                {/* Paid but missed notice */}
                {isMissedPaid && !isRefundRequested && noRefundYet && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fff7ed', border: '1px solid #f97316', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#9a3412', fontWeight: '600' }}>
                            💸 Patient paid but missed. Offer a refund or reschedule below.
                        </div>
                        <button
                            onClick={() => { setRescheduleModal(group); setRescheduleDate(''); setRescheduleTime(''); }}
                            style={{ padding: '4px 12px', background: '#7c3aed', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap' }}
                        >
                            📅 Reschedule
                        </button>
                    </div>
                )}

                {/* Rescheduled notice */}
                {isRescheduled && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#f5f3ff', border: '1px solid #8b5cf6', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#5b21b6', fontWeight: '600' }}>
                            🔁 Rescheduled — awaiting new appointment attendance
                        </div>
                        <button
                            onClick={() => handleGroupStatus(group, 'COMPLETED')}
                            disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap' }}
                        >
                            ✓ Mark Completed
                        </button>
                    </div>
                )}

                {/* Scheduled action buttons */}
                {isSchd && (
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <button onClick={() => handleGroupStatus(group, 'COMPLETED')} disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                            ✓ Mark Done
                        </button>
                        {isPast && (
                            <button onClick={() => { if (window.confirm('Mark all as missed?')) group.appointments.forEach(a => handleMarkMissed(a.id)); }}
                                disabled={!!actionLoading}
                                style={{ padding: '4px 12px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                                ⚠ Missed
                            </button>
                        )}
                        <button onClick={() => { if (window.confirm('Cancel all?')) handleGroupStatus(group, 'CANCELLED'); }}
                            disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>
                            ✕ Cancel
                        </button>
                    </div>
                )}
            </div>
        );
    };

    // ── Section header banner ─────────────────────────────────────────────────
    const SectionBanner = ({ icon, title, subtitle, count, bg, border, countBg }) => (
        <div style={{ background: bg, border: `2px solid ${border}`, borderRadius: '12px', padding: '12px 16px', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ fontSize: '26px' }}>{icon}</div>
            <div style={{ flex: 1 }}>
                <div style={{ fontSize: '15px', fontWeight: '800', color: '#111827' }}>{title}</div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>{subtitle}</div>
            </div>
            <div style={{ background: countBg, color: 'white', padding: '6px 14px', borderRadius: '8px', fontSize: '18px', fontWeight: '900', minWidth: '40px', textAlign: 'center' }}>
                {count}
            </div>
        </div>
    );

    const EmptyState = ({ emoji, title, sub }) => (
        <div style={{ textAlign: 'center', padding: '40px 20px', background: 'white', borderRadius: '12px', border: '2px dashed #e5e7eb' }}>
            <div style={{ fontSize: '44px', marginBottom: '8px' }}>{emoji}</div>
            <div style={{ fontSize: '15px', fontWeight: '700', color: '#374151', marginBottom: '4px' }}>{title}</div>
            <div style={{ fontSize: '12px', color: '#9ca3af' }}>{sub}</div>
        </div>
    );

    // ════════════════════════════════════════════════════════════════════════
    return (
        <div style={{ padding: '0' }}>

            {/* ── Refund Confirm Modal ── */}
            {refundModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
                    <div style={{ background: 'white', borderRadius: '14px', padding: '22px', width: '100%', maxWidth: '380px', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                        <div style={{ fontSize: '22px', textAlign: 'center', marginBottom: '8px' }}>
                            {refundModal.action === 'APPROVED' ? '✅' : '❌'}
                        </div>
                        <div style={{ fontSize: '16px', fontWeight: '800', color: '#111827', marginBottom: '6px', textAlign: 'center' }}>
                            {refundModal.action === 'APPROVED' ? 'Approve Refund' : 'Reject Refund'}
                        </div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '16px', textAlign: 'center' }}>
                            {refundModal.group.patientName} · {formatNaira(refundModal.group.totalPrice)}
                        </div>
                        {refundModal.group.refundReason && (
                            <div style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '7px', padding: '9px 12px', marginBottom: '14px', fontSize: '12px', color: '#374151', fontStyle: 'italic' }}>
                                Patient reason: "{refundModal.group.refundReason}"
                            </div>
                        )}
                        <div style={{ fontSize: '13px', color: '#374151', marginBottom: '18px', lineHeight: '1.5', textAlign: 'center' }}>
                            {refundModal.action === 'APPROVED'
                                ? `This will mark the refund as approved and reset the payment status to Unpaid. Confirm that you have issued ${formatNaira(refundModal.group.totalPrice)} back to ${refundModal.group.patientName}.`
                                : `This will reject the refund request for ${refundModal.group.patientName}. The patient will be notified.`
                            }
                        </div>
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <button onClick={() => setRefundModal(null)}
                                style={{ flex: 1, padding: '10px', background: '#f3f4f6', color: '#374151', border: '1.5px solid #e5e7eb', borderRadius: '8px', cursor: 'pointer', fontWeight: '700', fontSize: '13px' }}>
                                Cancel
                            </button>
                            <button
                                onClick={() => handleProcessRefund(refundModal.group, refundModal.action)}
                                disabled={processingRefund}
                                style={{ flex: 2, padding: '10px', background: processingRefund ? '#9ca3af' : (refundModal.action === 'APPROVED' ? '#059669' : '#dc2626'), color: 'white', border: 'none', borderRadius: '8px', cursor: processingRefund ? 'not-allowed' : 'pointer', fontWeight: '700', fontSize: '13px' }}
                            >
                                {processingRefund ? 'Processing...' : (refundModal.action === 'APPROVED' ? '✅ Confirm Approval' : '✕ Confirm Rejection')}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Reschedule Modal ── */}
            {rescheduleModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
                    <div style={{ background: 'white', borderRadius: '14px', padding: '22px', width: '100%', maxWidth: '400px', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                        <div style={{ fontSize: '16px', fontWeight: '800', color: '#111827', marginBottom: '3px' }}>📅 Reschedule Appointment</div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '18px' }}>
                            {rescheduleModal.patientName} · {rescheduleModal.appointments.length} test(s) · {formatNaira(rescheduleModal.totalPrice)}
                        </div>
                        <div style={{ marginBottom: '12px' }}>
                            <label style={{ fontSize: '11px', fontWeight: '700', color: '#374151', display: 'block', marginBottom: '4px' }}>New Date</label>
                            <input type="date" value={rescheduleDate} onChange={e => setRescheduleDate(e.target.value)}
                                min={new Date().toISOString().split('T')[0]}
                                style={{ width: '100%', padding: '8px 10px', borderRadius: '7px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                        </div>
                        <div style={{ marginBottom: '18px' }}>
                            <label style={{ fontSize: '11px', fontWeight: '700', color: '#374151', display: 'block', marginBottom: '4px' }}>New Time</label>
                            <input type="time" value={rescheduleTime} onChange={e => setRescheduleTime(e.target.value)}
                                style={{ width: '100%', padding: '8px 10px', borderRadius: '7px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                        </div>
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <button onClick={() => setRescheduleModal(null)}
                                style={{ flex: 1, padding: '10px', background: '#f3f4f6', color: '#374151', border: '1.5px solid #e5e7eb', borderRadius: '8px', cursor: 'pointer', fontWeight: '700', fontSize: '13px' }}>
                                Cancel
                            </button>
                            <button onClick={handleReschedule} disabled={rescheduling || !rescheduleDate || !rescheduleTime}
                                style={{ flex: 2, padding: '10px', background: rescheduling ? '#9ca3af' : '#7c3aed', color: 'white', border: 'none', borderRadius: '8px', cursor: rescheduling ? 'not-allowed' : 'pointer', fontWeight: '700', fontSize: '13px' }}>
                                {rescheduling ? 'Saving...' : '📅 Confirm Reschedule'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Edit Tests Modal ── */}
            {editingGroup && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
                    <div style={{ background: 'white', borderRadius: '14px', padding: '22px', width: '100%', maxWidth: '460px', maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                        <div style={{ fontSize: '16px', fontWeight: '800', color: '#111827', marginBottom: '3px' }}>✏️ Edit Tests</div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '16px' }}>
                            {editingGroup.patientName} · {fmtDate(editingGroup.appointmentDate)}
                        </div>
                        {editTests.map((t, i) => (
                            <div key={t.id} style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '12px', marginBottom: '10px' }}>
                                <div style={{ fontSize: '10px', fontWeight: '700', color: '#6b7280', marginBottom: '8px', textTransform: 'uppercase' }}>Test {i + 1}</div>
                                <div style={{ marginBottom: '8px' }}>
                                    <label style={{ fontSize: '11px', color: '#374151', fontWeight: '600', display: 'block', marginBottom: '3px' }}>Test Name</label>
                                    <input value={t.testType} onChange={e => { const u = [...editTests]; u[i] = { ...u[i], testType: e.target.value }; setEditTests(u); }}
                                        style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                                </div>
                                <div>
                                    <label style={{ fontSize: '11px', color: '#374151', fontWeight: '600', display: 'block', marginBottom: '3px' }}>Price</label>
                                    <input value={t.price} onChange={e => { const u = [...editTests]; u[i] = { ...u[i], price: e.target.value }; setEditTests(u); }}
                                        style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                                </div>
                            </div>
                        ))}
                        <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
                            <button onClick={() => { setEditingGroup(null); setEditTests([]); }}
                                style={{ flex: 1, padding: '10px', background: '#f3f4f6', color: '#374151', border: '1.5px solid #e5e7eb', borderRadius: '8px', cursor: 'pointer', fontWeight: '700', fontSize: '13px' }}>Cancel</button>
                            <button onClick={saveEditedTests} disabled={savingTests}
                                style={{ flex: 2, padding: '10px', background: savingTests ? '#9ca3af' : '#667eea', color: 'white', border: 'none', borderRadius: '8px', cursor: savingTests ? 'not-allowed' : 'pointer', fontWeight: '700', fontSize: '13px' }}>
                                {savingTests ? 'Saving...' : '💾 Save Changes'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Section pills ── */}
            <div style={{ display: 'flex', gap: '10px', padding: '16px 16px 0', flexWrap: 'wrap' }}>
                <SectionPill id="pending-payments" label="💳 Pending Payments" count={pendingPayments.length}    alertCount={pendingPayments.length} />
                <SectionPill id="paid-missed"      label="💸 Paid & Missed"    count={paidButMissed.length}     alertCount={paidButMissed.length} />
                <SectionPill id="rescheduled"      label="🔁 Rescheduled"      count={rescheduledGroups.length} alertCount={0} />
                <SectionPill id="refunds"          label="💰 Refunds"          count={refundGroups.length}      alertCount={pendingRefundGroups.length} />
                <SectionPill id="all-appointments" label="📅 All Appointments" count={appointments.length}      alertCount={0} />
            </div>

            {/* ══ SECTION 1 — PENDING PAYMENTS ══ */}
            {activeSection === 'pending-payments' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="⏳" title="Payment Approvals"
                        subtitle="Patients who transferred to Sterling Bank (0089364407) and tapped 'I Have Paid'."
                        count={pendingPayments.length}
                        bg="linear-gradient(135deg,#fef3c7,#fde68a)" border="#fbbf24" countBg="#d97706" />
                    <SearchBar value={pendingSearch} onChange={setPendingSearch} />
                    {filterByName(pendingPayments, pendingSearch).length === 0
                        ? <EmptyState emoji={pendingSearch ? '🔍' : '✅'} title={pendingSearch ? 'No results found' : 'All payments confirmed'} sub={pendingSearch ? `No pending payments matching "${pendingSearch}"` : 'No pending bank transfers to review'} />
                        : filterByName(pendingPayments, pendingSearch).map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}

            {/* ══ SECTION 2 — PAID & MISSED ══ */}
            {activeSection === 'paid-missed' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="💸" title="Paid but Missed Appointments"
                        subtitle="These patients paid but did not show up. Offer a refund or reschedule."
                        count={paidButMissed.length}
                        bg="linear-gradient(135deg,#fff7ed,#fed7aa)" border="#f97316" countBg="#ea580c" />
                    <SearchBar value={missedSearch} onChange={setMissedSearch} />
                    {filterByName(paidButMissed, missedSearch).length === 0
                        ? <EmptyState emoji={missedSearch ? '🔍' : '✅'} title={missedSearch ? 'No results found' : 'No paid missed appointments'} sub={missedSearch ? `No paid missed appointments matching "${missedSearch}"` : 'All paying patients attended their appointments'} />
                        : filterByName(paidButMissed, missedSearch).map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}

            {/* ══ SECTION 3 — RESCHEDULED ══ */}
            {activeSection === 'rescheduled' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="🔁" title="Rescheduled Appointments"
                        subtitle="Previously missed (paid) appointments that have been given a new slot. Mark as Completed once attended."
                        count={rescheduledGroups.length}
                        bg="linear-gradient(135deg,#f5f3ff,#ede9fe)" border="#8b5cf6" countBg="#7c3aed" />
                    <SearchBar value={rescheduledSearch} onChange={setRescheduledSearch} />
                    {filterByName(rescheduledGroups, rescheduledSearch).length === 0
                        ? <EmptyState emoji={rescheduledSearch ? '🔍' : '✅'} title={rescheduledSearch ? 'No results found' : 'No rescheduled appointments'} sub={rescheduledSearch ? `No rescheduled appointments matching "${rescheduledSearch}"` : 'Rescheduled appointments will appear here'} />
                        : filterByName(rescheduledGroups, rescheduledSearch).map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}

            {/* ══ SECTION 4 — REFUNDS ══ */}
            {activeSection === 'refunds' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="💰" title="Refund Requests"
                        subtitle="Patient refund requests. Approve to confirm payment was returned, or reject to decline the request."
                        count={refundGroups.length}
                        bg="linear-gradient(135deg,#fffbeb,#fef3c7)" border="#f59e0b" countBg="#d97706" />

                    {/* Refund sub-filter tabs */}
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
                        {[
                            { key: 'all',       label: 'All',          count: refundGroups.length,                                                                              color: '#6b7280' },
                            { key: 'REQUESTED', label: '🔄 Pending',   count: refundGroups.filter(g => (g.refundStatus || '').toUpperCase() === 'REQUESTED').length,            color: '#92400e' },
                            { key: 'APPROVED',  label: '✅ Approved',  count: refundGroups.filter(g => (g.refundStatus || '').toUpperCase() === 'APPROVED').length,             color: '#065f46' },
                            { key: 'REJECTED',  label: '❌ Rejected',  count: refundGroups.filter(g => (g.refundStatus || '').toUpperCase() === 'REJECTED').length,             color: '#991b1b' },
                        ].map(tab => (
                            <button key={tab.key} onClick={() => setRefundSubFilter(tab.key)}
                                style={{
                                    padding: '5px 12px', borderRadius: '20px',
                                    border: `2px solid ${refundSubFilter === tab.key ? tab.color : '#e5e7eb'}`,
                                    background: refundSubFilter === tab.key ? tab.color : 'white',
                                    color: refundSubFilter === tab.key ? 'white' : tab.color,
                                    fontWeight: '700', fontSize: '12px', cursor: 'pointer',
                                }}>
                                {tab.label} ({tab.count})
                            </button>
                        ))}
                    </div>

                    <SearchBar value={refundSearch} onChange={setRefundSearch} />

                    {(() => {
                        const subFiltered = refundSubFilter === 'all'
                            ? refundGroups
                            : refundGroups.filter(g => (g.refundStatus || '').toUpperCase() === refundSubFilter);
                        const nameFiltered = filterByName(subFiltered, refundSearch);
                        return nameFiltered.length === 0
                            ? <EmptyState emoji={refundSearch ? '🔍' : '🎉'} title={refundSearch ? 'No results found' : 'No refund requests'} sub={refundSearch ? `No refund requests matching "${refundSearch}"` : 'No patients have requested refunds yet'} />
                            : nameFiltered.map(g => <GroupCard key={g.key} group={g} />);
                    })()}
                </div>
            )}

            {/* ══ SECTION 5 — ALL APPOINTMENTS ══ */}
            {activeSection === 'all-appointments' && (
                <div style={{ padding: '14px 16px' }}>
                    {/* Status pills */}
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
                        {[
                            { key: 'all',         label: 'All',         count: counts.all,         color: '#6b7280' },
                            { key: 'scheduled',   label: 'Scheduled',   count: counts.scheduled,   color: '#065f46' },
                            { key: 'completed',   label: 'Completed',   count: counts.completed,   color: '#1e40af' },
                            { key: 'missed',      label: 'Missed',      count: counts.missed,      color: '#9a3412' },
                            { key: 'rescheduled', label: 'Rescheduled', count: counts.rescheduled, color: '#5b21b6' },
                            { key: 'cancelled',   label: 'Cancelled',   count: counts.cancelled,   color: '#991b1b' },
                        ].map(st => (
                            <button key={st.key} onClick={() => setFilterStatus(st.key)}
                                style={{
                                    padding: '5px 12px', borderRadius: '20px',
                                    border: `2px solid ${filterStatus === st.key ? st.color : '#e5e7eb'}`,
                                    background: filterStatus === st.key ? st.color : 'white',
                                    color: filterStatus === st.key ? 'white' : st.color,
                                    fontWeight: '700', fontSize: '12px', cursor: 'pointer',
                                }}>
                                {st.label} ({st.count})
                            </button>
                        ))}
                    </div>

                    {/* Filters */}
                    <div style={{ display: 'flex', gap: '10px', marginBottom: '12px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Patient Name</label>
                            <input type="text" placeholder="Search by name..." value={searchName} onChange={e => setSearchName(e.target.value)}
                                style={{ width: '180px', fontSize: '12px', padding: '5px 8px', border: '1px solid #d1d5db', borderRadius: '6px' }} />
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Payment</label>
                            <select className="form-input" value={filterPayment} onChange={e => setFilterPayment(e.target.value)} style={{ width: '160px', fontSize: '12px', padding: '5px 8px' }}>
                                <option value="all">All Payments</option>
                                <option value="paid">Paid</option>
                                <option value="pending_confirmation">Pending Confirmation</option>
                                <option value="pay_on_arrival">Pay on Arrival</option>
                                <option value="unpaid">Unpaid</option>
                            </select>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Date</label>
                            <input type="date" className="form-input" value={filterDate} onChange={e => setFilterDate(e.target.value)} style={{ width: '150px', fontSize: '12px', padding: '5px 8px' }} />
                        </div>
                        {(filterStatus !== 'all' || filterPayment !== 'all' || filterDate || searchName) && (
                            <button onClick={() => { setFilterStatus('all'); setFilterPayment('all'); setFilterDate(''); setSearchName(''); }}
                                style={{ padding: '6px 12px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}>
                                Clear
                            </button>
                        )}
                    </div>

                    <div style={{ padding: '5px 10px', background: '#f3f4f6', borderRadius: '6px', marginBottom: '12px', fontSize: '12px', color: '#6b7280' }}>
                        📅 Sorted oldest first · {filteredGroups.length} group(s) · {filteredGroups.reduce((s, g) => s + g.appointments.length, 0)} appointment(s)
                    </div>

                    {filteredGroups.length === 0
                        ? <div style={{ textAlign: 'center', padding: '40px', color: '#9ca3af' }}>
                            <div style={{ fontSize: '40px', marginBottom: '10px' }}>📅</div>
                            <div style={{ fontSize: '15px', fontWeight: '600', color: '#374151' }}>No appointments found</div>
                          </div>
                        : filteredGroups.map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}
        </div>
    );
};