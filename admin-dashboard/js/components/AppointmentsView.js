const { useState, useMemo, useEffect } = React;

const AppointmentsView = ({ appointments, setShowModal, onRefresh }) => {
    const [filterStatus, setFilterStatus]   = useState('all');
    const [filterPayment, setFilterPayment] = useState('all');
    const [filterDate, setFilterDate]       = useState('');
    const [searchName, setSearchName]       = useState('');
    const [actionLoading, setActionLoading] = useState(null);
    const [activeSection, setActiveSection] = useState('pending-payments');
    const [missedSubTab, setMissedSubTab]   = useState('all-missed');

    const [editingGroup, setEditingGroup]   = useState(null);
    const [editTests, setEditTests]         = useState([]);
    const [savingTests, setSavingTests]     = useState(false);

    const [rescheduleModal, setRescheduleModal] = useState(null);
    const [rescheduleDate, setRescheduleDate]   = useState('');
    const [rescheduleTime, setRescheduleTime]   = useState('');
    const [rescheduling, setRescheduling]       = useState(false);

    const [refundRequests, setRefundRequests]   = useState([]);
    const [refundLoading, setRefundLoading]     = useState(false);
    const [processingRefund, setProcessingRefund] = useState(null);

    const [rescheduleRequests, setRescheduleRequests] = useState([]);
    const [rescheduleReqLoading, setRescheduleReqLoading] = useState(false);

    useEffect(() => {
        loadRefundRequests();
        loadRescheduleRequests();
    }, []);

    const loadRefundRequests = async () => {
        setRefundLoading(true);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/refund-requests`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                const data = await res.json();
                const list = data.refundRequests || data.data || data.requests || (Array.isArray(data) ? data : []);
                setRefundRequests(list);
            } else {
                deriveRefundRequestsFromAppointments();
            }
        } catch {
            deriveRefundRequestsFromAppointments();
        } finally {
            setRefundLoading(false);
        }
    };

    const deriveRefundRequestsFromAppointments = () => {
        const refunds = appointments.filter(a =>
            a.refundRequested === true ||
            ['REQUESTED', 'APPROVED', 'REFUNDED'].includes((a.refundStatus || '').toUpperCase()) ||
            (a.paymentStatus || '').toUpperCase() === 'REFUND_REQUESTED'
        );
        setRefundRequests(refunds.map(a => ({
            id: a.id,
            appointmentId: a.id,
            patientName: a.patientName,
            patientId: a.patientId,
            amount: a.price,
            testType: a.testType || a.reason,
            requestedAt: a.refundRequestedAt || a.updatedAt || a.createdAt,
            status: (a.refundStatus || 'PENDING').toUpperCase(),
            reason: a.refundReason || 'Patient requested refund',
        })));
    };

    const loadRescheduleRequests = async () => {
        setRescheduleReqLoading(true);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/reschedule-requests`, {
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                const data = await res.json();
                const list = data.rescheduleRequests || data.data || data.requests || (Array.isArray(data) ? data : []);
                setRescheduleRequests(list);
            } else {
                const reqs = appointments.filter(a =>
                    a.rescheduleRequested === true ||
                    (a.rescheduleStatus || '').toUpperCase() === 'REQUESTED'
                );
                setRescheduleRequests(reqs);
            }
        } catch {
            setRescheduleRequests([]);
        } finally {
            setRescheduleReqLoading(false);
        }
    };

    const handleApproveRefund = async (refund) => {
        if (!window.confirm(`Approve refund for ${refund.patientName}?`)) return;
        setProcessingRefund(refund.id);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/refund-requests/${refund.id}/approve`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                window.showNotificationAlert && window.showNotificationAlert('Refund approved ✅ — The refund will be processed within 2-3 working days.');
                loadRefundRequests();
                onRefresh && onRefresh();
            } else {
                const d = await res.json().catch(() => ({}));
                alert(d.message || 'Failed to approve refund');
            }
        } catch (err) { alert('Network error: ' + err.message); }
        finally { setProcessingRefund(null); }
    };

    const handleMarkRefunded = async (refund) => {
        if (!window.confirm(`Mark refund as completed for ${refund.patientName}?`)) return;
        setProcessingRefund(refund.id);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/refund-requests/${refund.id}/mark-refunded`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                window.showNotificationAlert && window.showNotificationAlert('Refund marked as completed ✅');
                // Send notification to the patient about completed refund
                try {
                    await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/notifications/send`, {
                        method: 'POST',
                        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            userId: refund.patientId,
                            title: 'Refund Completed',
                            message: `Your refund of ${refund.amount || refund.price || ''} for ${refund.testType || refund.testName || 'your appointment'} has been processed successfully. Please check your bank account.`,
                            type: 'REFUND_COMPLETED'
                        }),
                    });
                } catch (e) { console.warn('Notification send failed:', e); }
                loadRefundRequests();
                onRefresh && onRefresh();
            } else {
                const d = await res.json().catch(() => ({}));
                alert(d.message || 'Failed to mark refund');
            }
        } catch (err) { alert('Network error: ' + err.message); }
        finally { setProcessingRefund(null); }
    };

    const handleDeclineRefund = async (refund) => {
        if (!window.confirm(`Decline refund request for ${refund.patientName}?`)) return;
        setProcessingRefund(refund.id);
        try {
            const token = localStorage.getItem('authToken');
            const res = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/refund-requests/${refund.id}/decline`, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            });
            if (res.ok) {
                window.showNotificationAlert && window.showNotificationAlert('Refund declined');
                loadRefundRequests();
            } else {
                alert('Failed to decline refund');
            }
        } catch (err) { alert('Network error: ' + err.message); }
        finally { setProcessingRefund(null); }
    };

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
        if (d.getHours() === 0 && d.getMinutes() === 0)
            return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
        return `${d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    };

    const parsePrice  = (str) => parseInt((str || '').replace(/[₦,\s]/g, '').split('.')[0], 10) || 0;
    const formatNaira = (n)   => '₦' + n.toLocaleString('en-NG') + '.00';

    const getPaymentBadge = (ps) => {
        switch ((ps || '').toUpperCase()) {
            case 'PAID':                  return { label: 'Paid',              bg: '#d1fae5', color: '#065f46', icon: '✅' };
            case 'PENDING_CONFIRMATION':  return { label: 'Awaiting Approval', bg: '#fef3c7', color: '#92400e', icon: '⏳' };
            case 'PAY_ON_ARRIVAL':        return { label: 'Pay on Arrival',    bg: '#dbeafe', color: '#1e40af', icon: '🕐' };
            case 'REFUND_REQUESTED':      return { label: 'Refund Requested',  bg: '#fce7f3', color: '#9d174d', icon: '💸' };
            default:                      return { label: 'Unpaid',            bg: '#fee2e2', color: '#991b1b', icon: '❌' };
        }
    };

    const getStatusBadge = (s) => {
        switch ((s || '').toUpperCase()) {
            case 'SCHEDULED':   return { bg: '#d1fae5', color: '#065f46' };
            case 'COMPLETED':   return { bg: '#dbeafe', color: '#1e40af' };
            case 'MISSED':      return { bg: '#ffedd5', color: '#9a3412' };
            case 'CANCELLED':   return { bg: '#fee2e2', color: '#991b1b' };
            case 'RESCHEDULED': return { bg: '#ede9fe', color: '#5b21b6' };
            default:            return { bg: '#f3f4f6', color: '#6b7280' };
        }
    };

    // ── Group appointments ────────────────────────────────────────────────────
    const pendingRefunds = refundRequests.filter(r => (r.status || '').toUpperCase() === 'PENDING');
    const approvedRefunds = refundRequests.filter(r => (r.status || '').toUpperCase() === 'APPROVED');
    const alreadyRefunded = refundRequests.filter(r => (r.status || '').toUpperCase() === 'REFUNDED');

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
                    appointments: [], totalPrice: 0,
                };
            }
            groups[key].appointments.push(apt);
            groups[key].totalPrice += parsePrice(apt.price);
            const statuses = groups[key].appointments.map(a => (a.status || '').toUpperCase());
            if (statuses.includes('SCHEDULED'))        groups[key].status = 'SCHEDULED';
            else if (statuses.includes('RESCHEDULED')) groups[key].status = 'RESCHEDULED';
            else if (statuses.includes('MISSED'))      groups[key].status = 'MISSED';
            else if (statuses.includes('COMPLETED'))   groups[key].status = 'COMPLETED';
            else if (statuses.includes('CANCELLED'))   groups[key].status = 'CANCELLED';
        });
        return Object.values(groups).sort((a, b) => {
            const da = parseDate(a.appointmentDate);
            const db = parseDate(b.appointmentDate);
            if (!da) return 1; if (!db) return -1;
            return da - db;
        });
    };

    // ── Computed sections ─────────────────────────────────────────────────────
    const pendingPayments = useMemo(() =>
        groupAppointments(appointments.filter(a => (a.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION')),
        [appointments]
    );

    const allMissed = useMemo(() =>
        groupAppointments(appointments.filter(a => (a.status || '').toUpperCase() === 'MISSED')),
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

    // ── API helpers ───────────────────────────────────────────────────────────
    const apiPatch = async (url) => {
        const token = localStorage.getItem('authToken');
        const res = await fetch(`${CONFIG.ADMIN_API_URL}${url}`, {
            method: 'PATCH', headers: { Authorization: `Bearer ${token}` },
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

    const handleReschedule = async () => {
        if (!rescheduleDate || !rescheduleTime) { alert('Please pick a date and time.'); return; }
        setRescheduling(true);
        try {
            for (const apt of rescheduleModal.appointments) {
                await apiPut(`/api/admin/appointments/${apt.id}`, {
                    scheduledDate: rescheduleDate, scheduledTime: rescheduleTime,
                    status: 'RESCHEDULED', rescheduleStatus: 'RESCHEDULED',
                });
            }
            window.showNotificationAlert && window.showNotificationAlert(`Rescheduled ${rescheduleModal.appointments.length} test(s) for ${rescheduleModal.patientName} ✅`);
            setRescheduleModal(null); setRescheduleDate(''); setRescheduleTime('');
            onRefresh && onRefresh();
        } catch (err) { alert('Error rescheduling: ' + err.message); }
        finally { setRescheduling(false); }
    };

    const openEditTests = (group) => {
        setEditingGroup(group);
        setEditTests(group.appointments.map(a => ({ id: a.id, testType: a.testType || a.reason || '', price: a.price || '' })));
    };

    const saveEditedTests = async () => {
        setSavingTests(true);
        try {
            for (const t of editTests) await apiPut(`/api/admin/appointments/${t.id}`, { testType: t.testType, price: t.price });
            window.showNotificationAlert && window.showNotificationAlert('Tests updated ✅');
            setEditingGroup(null); setEditTests([]);
            onRefresh && onRefresh();
        } catch (err) { alert('Error saving: ' + err.message); }
        finally { setSavingTests(false); }
    };

    // ── Section pill (NO red badges) ──────────────────────────────────────────
    const SectionPill = ({ id, label, count }) => (
        <button onClick={() => setActiveSection(id)} style={{
            padding: '8px 16px', borderRadius: '8px',
            border: `2px solid ${activeSection === id ? '#667eea' : '#e5e7eb'}`,
            background: activeSection === id ? '#667eea' : 'white',
            color: activeSection === id ? 'white' : '#374151',
            fontWeight: '700', fontSize: '13px', cursor: 'pointer',
            display: 'flex', alignItems: 'center', gap: '6px',
        }}>
            {label}
            {count !== undefined && (
                <span style={{ padding: '1px 7px', borderRadius: '12px', background: activeSection === id ? 'rgba(255,255,255,0.25)' : '#f3f4f6', color: activeSection === id ? 'white' : '#6b7280', fontSize: '11px', fontWeight: '700' }}>{count}</span>
            )}
        </button>
    );

    // ── Group card ─────────────────────────────────────────────────────────────
    const GroupCard = ({ group }) => {
        const sb = getStatusBadge(group.status);
        const pb = getPaymentBadge(group.paymentStatus);
        const aptDate = parseDate(group.appointmentDate);
        const isPast = aptDate && aptDate < new Date();
        const isSchd = (group.status || '').toUpperCase() === 'SCHEDULED';
        const isRescheduled = (group.status || '').toUpperCase() === 'RESCHEDULED';
        const isPending = (group.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION';
        const isMissedPaid = (group.status || '').toUpperCase() === 'MISSED' && (group.paymentStatus || '').toUpperCase() === 'PAID';
        const isGroupLoading = group.appointments.some(a => actionLoading === a.id + '-pay' || actionLoading === a.id + '-status' || actionLoading === a.id + '-miss');

        return (
            <div style={{ background: 'white', border: `1.5px solid ${isPending ? '#fbbf24' : isMissedPaid ? '#f97316' : isRescheduled ? '#8b5cf6' : '#e5e7eb'}`, borderRadius: '10px', padding: '12px 14px', marginBottom: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                    <div style={{ width: '34px', height: '34px', borderRadius: '50%', flexShrink: 0, background: 'linear-gradient(135deg,#667eea,#764ba2)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '12px' }}>
                        {group.patientName ? group.patientName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : 'U'}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: '700', fontSize: '14px', color: '#111827' }}>
                            {group.patientName || 'Unknown Patient'}
                            <span style={{ fontWeight: '400', color: '#9ca3af', fontSize: '11px', marginLeft: '6px' }}>ID: {group.patientId || 'N/A'}</span>
                        </div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '1px' }}>📅 {fmtDate(group.appointmentDate)}</div>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', alignItems: 'flex-end', flexShrink: 0 }}>
                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '700', background: sb.bg, color: sb.color, whiteSpace: 'nowrap' }}>{group.status || 'Unknown'}</span>
                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '10px', fontWeight: '600', background: pb.bg, color: pb.color, whiteSpace: 'nowrap' }}>{pb.icon} {pb.label}</span>
                    </div>
                </div>

                <div style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '7px', padding: '8px 10px', marginBottom: '8px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '5px' }}>
                        <span style={{ fontSize: '10px', fontWeight: '700', color: '#6b7280', textTransform: 'uppercase' }}>{group.appointments.length} Test{group.appointments.length > 1 ? 's' : ''}</span>
                        <button onClick={() => openEditTests(group)} style={{ fontSize: '10px', fontWeight: '700', color: '#667eea', background: '#ede9fe', border: 'none', borderRadius: '5px', padding: '2px 8px', cursor: 'pointer' }}>✏️ Edit Tests</button>
                    </div>
                    {group.appointments.map((apt, i) => (
                        <div key={apt.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', paddingTop: i > 0 ? '4px' : '0', borderTop: i > 0 ? '1px solid #e5e7eb' : 'none', marginTop: i > 0 ? '4px' : '0' }}>
                            <span style={{ color: '#374151', fontWeight: '500' }}>🧪 {apt.testType || apt.reason || 'Medical Test'}</span>
                            <span style={{ color: '#059669', fontWeight: '700', flexShrink: 0, marginLeft: '8px' }}>{apt.price || '—'}</span>
                        </div>
                    ))}
                    {group.appointments.length > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', fontWeight: '800', color: '#111827', borderTop: '2px solid #d1d5db', marginTop: '6px', paddingTop: '6px' }}>
                            <span>Total</span><span style={{ color: '#059669' }}>{formatNaira(group.totalPrice)}</span>
                        </div>
                    )}
                </div>

                {isPending && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fef3c7', border: '1px solid #fbbf24', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#92400e', fontWeight: '600' }}>📲 Patient clicked "I Have Paid" — verify Sterling Bank (0089364407) and approve</div>
                        <button onClick={() => handleApprovePayment(group)} disabled={isGroupLoading} style={{ padding: '4px 12px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap', opacity: isGroupLoading ? 0.6 : 1 }}>
                            {isGroupLoading ? '...' : '✅ Approve'}
                        </button>
                    </div>
                )}

                {isMissedPaid && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fff7ed', border: '1px solid #f97316', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#9a3412', fontWeight: '600' }}>💸 Patient paid but missed. Offer a refund or reschedule.</div>
                        <button onClick={() => { setRescheduleModal(group); setRescheduleDate(''); setRescheduleTime(''); }}
                            style={{ padding: '4px 12px', background: '#7c3aed', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap' }}>
                            📅 Reschedule
                        </button>
                    </div>
                )}

                {isRescheduled && (
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#f5f3ff', border: '1px solid #8b5cf6', borderRadius: '7px', padding: '7px 10px', marginBottom: '8px' }}>
                        <div style={{ fontSize: '11px', color: '#5b21b6', fontWeight: '600' }}>🔁 Rescheduled — awaiting new appointment attendance</div>
                        <button onClick={() => handleGroupStatus(group, 'COMPLETED')} disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '11px', marginLeft: '10px', whiteSpace: 'nowrap' }}>
                            ✓ Mark Completed
                        </button>
                    </div>
                )}

                {isSchd && (
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <button onClick={() => handleGroupStatus(group, 'COMPLETED')} disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>✓ Mark Done</button>
                        {isPast && (
                            <button onClick={() => { if (window.confirm('Mark all as missed?')) group.appointments.forEach(a => handleMarkMissed(a.id)); }}
                                disabled={!!actionLoading}
                                style={{ padding: '4px 12px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>⚠ Missed</button>
                        )}
                        <button onClick={() => { if (window.confirm('Cancel all?')) handleGroupStatus(group, 'CANCELLED'); }}
                            disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>✕ Cancel</button>
                    </div>
                )}
            </div>
        );
    };

    const SectionBanner = ({ icon, title, subtitle, count, bg, border, countBg }) => (
        <div style={{ background: bg, border: `2px solid ${border}`, borderRadius: '12px', padding: '12px 16px', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ fontSize: '26px' }}>{icon}</div>
            <div style={{ flex: 1 }}>
                <div style={{ fontSize: '15px', fontWeight: '800', color: '#111827' }}>{title}</div>
                <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '2px' }}>{subtitle}</div>
            </div>
            <div style={{ background: countBg, color: 'white', padding: '6px 14px', borderRadius: '8px', fontSize: '18px', fontWeight: '900', minWidth: '40px', textAlign: 'center' }}>{count}</div>
        </div>
    );

    const EmptyState = ({ emoji, title, sub }) => (
        <div style={{ textAlign: 'center', padding: '40px 20px', background: 'white', borderRadius: '12px', border: '2px dashed #e5e7eb' }}>
            <div style={{ fontSize: '44px', marginBottom: '8px' }}>{emoji}</div>
            <div style={{ fontSize: '15px', fontWeight: '700', color: '#374151', marginBottom: '4px' }}>{title}</div>
            <div style={{ fontSize: '12px', color: '#9ca3af' }}>{sub}</div>
        </div>
    );

    const SubTabPill = ({ id, label, count, activeColor }) => (
        <button onClick={() => setMissedSubTab(id)} style={{
            padding: '6px 14px', borderRadius: '20px',
            border: `2px solid ${missedSubTab === id ? activeColor : '#e5e7eb'}`,
            background: missedSubTab === id ? activeColor : 'white',
            color: missedSubTab === id ? 'white' : '#374151',
            fontWeight: '700', fontSize: '12px', cursor: 'pointer',
            display: 'inline-flex', alignItems: 'center', gap: '5px',
        }}>
            {label}
            <span style={{ padding: '0 6px', borderRadius: '10px', background: missedSubTab === id ? 'rgba(255,255,255,0.3)' : '#f3f4f6', color: missedSubTab === id ? 'white' : '#6b7280', fontSize: '11px', fontWeight: '700' }}>{count}</span>
        </button>
    );

    // ── Shared search bar component ───────────────────────────────────────────
    const SearchBar = () => (
        <div style={{ padding: '10px 16px 0' }}>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                <input type="text" placeholder="🔍 Search by patient name..." value={searchName} onChange={e => setSearchName(e.target.value)}
                    style={{ flex: 1, maxWidth: '280px', fontSize: '12px', padding: '7px 12px', border: '1.5px solid #d1d5db', borderRadius: '8px', outline: 'none' }} />
                {searchName && (
                    <button onClick={() => setSearchName('')}
                        style={{ padding: '5px 10px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}>Clear</button>
                )}
            </div>
        </div>
    );

    // ── Helper: filter refund list by search name ─────────────────────────────
    const filterByName = (list) => {
        const q = searchName.toLowerCase().trim();
        if (!q) return list;
        return list.filter(item => (item.patientName || '').toLowerCase().includes(q));
    };

    // ════════════════════════════════════════════════════════════════════════
    return (
        <div style={{ padding: 0 }}>

            {/* ── Reschedule Modal ── */}
            {rescheduleModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
                    <div style={{ background: 'white', borderRadius: '14px', padding: '22px', width: '100%', maxWidth: '400px', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                        <div style={{ fontSize: '16px', fontWeight: '800', marginBottom: '3px' }}>📅 Reschedule Appointment</div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '18px' }}>{rescheduleModal.patientName} · {rescheduleModal.appointments.length} test(s) · {formatNaira(rescheduleModal.totalPrice)}</div>
                        <div style={{ marginBottom: '12px' }}>
                            <label style={{ fontSize: '11px', fontWeight: '700', color: '#374151', display: 'block', marginBottom: '4px' }}>New Date</label>
                            <input type="date" value={rescheduleDate} onChange={e => setRescheduleDate(e.target.value)} min={new Date().toISOString().split('T')[0]}
                                style={{ width: '100%', padding: '8px 10px', borderRadius: '7px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                        </div>
                        <div style={{ marginBottom: '18px' }}>
                            <label style={{ fontSize: '11px', fontWeight: '700', color: '#374151', display: 'block', marginBottom: '4px' }}>New Time</label>
                            <input type="time" value={rescheduleTime} onChange={e => setRescheduleTime(e.target.value)}
                                style={{ width: '100%', padding: '8px 10px', borderRadius: '7px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                        </div>
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <button onClick={() => setRescheduleModal(null)} style={{ flex: 1, padding: '10px', background: '#f3f4f6', color: '#374151', border: '1.5px solid #e5e7eb', borderRadius: '8px', cursor: 'pointer', fontWeight: '700', fontSize: '13px' }}>Cancel</button>
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
                        <div style={{ fontSize: '16px', fontWeight: '800', marginBottom: '3px' }}>✏️ Edit Tests</div>
                        <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '16px' }}>{editingGroup.patientName} · {fmtDate(editingGroup.appointmentDate)}</div>
                        {editTests.map((t, i) => (
                            <div key={t.id} style={{ background: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '12px', marginBottom: '10px' }}>
                                <div style={{ fontSize: '10px', fontWeight: '700', color: '#6b7280', marginBottom: '8px', textTransform: 'uppercase' }}>Test {i + 1}</div>
                                <label style={{ fontSize: '11px', color: '#374151', fontWeight: '600', display: 'block', marginBottom: '3px' }}>Test Name</label>
                                <input value={t.testType} onChange={e => { const u = [...editTests]; u[i] = { ...u[i], testType: e.target.value }; setEditTests(u); }}
                                    style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box', marginBottom: '8px' }} />
                                <label style={{ fontSize: '11px', color: '#374151', fontWeight: '600', display: 'block', marginBottom: '3px' }}>Price</label>
                                <input value={t.price} onChange={e => { const u = [...editTests]; u[i] = { ...u[i], price: e.target.value }; setEditTests(u); }}
                                    style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box' }} />
                            </div>
                        ))}
                        <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
                            <button onClick={() => { setEditingGroup(null); setEditTests([]); }} style={{ flex: 1, padding: '10px', background: '#f3f4f6', color: '#374151', border: '1.5px solid #e5e7eb', borderRadius: '8px', cursor: 'pointer', fontWeight: '700', fontSize: '13px' }}>Cancel</button>
                            <button onClick={saveEditedTests} disabled={savingTests} style={{ flex: 2, padding: '10px', background: savingTests ? '#9ca3af' : '#667eea', color: 'white', border: 'none', borderRadius: '8px', cursor: savingTests ? 'not-allowed' : 'pointer', fontWeight: '700', fontSize: '13px' }}>
                                {savingTests ? 'Saving...' : '💾 Save Changes'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Section pills (NO red badges) ── */}
            <div style={{ display: 'flex', gap: '10px', padding: '16px 16px 0', flexWrap: 'wrap' }}>
                <SectionPill id="pending-payments"     label="💳 Pending Payments"     count={pendingPayments.length} />
                <SectionPill id="all-missed"           label="⚠️ All Missed"            count={allMissed.length} />
                <SectionPill id="refund-requests"      label="💰 Refund Requests"       count={pendingRefunds.length} />
                <SectionPill id="approved-refunds"     label="✅ Approved Refunds"      count={approvedRefunds.length} />
                <SectionPill id="already-refunded"     label="💸 Already Refunded"      count={alreadyRefunded.length} />
                <SectionPill id="reschedule-requests"  label="📅 Reschedule Requests"   count={rescheduleRequests.length} />
                <SectionPill id="rescheduled"          label="🔁 Rescheduled"           count={rescheduledGroups.length} />
                <SectionPill id="all-appointments"     label="📋 All Appointments"      count={appointments.length} />
            </div>

            {/* ── Shared search + filters (visible on ALL sections) ── */}
            <div style={{ padding: '14px 16px 0' }}>
                {/* ── Shared search + filters ── */}
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
                            style={{ padding: '6px 12px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}>Clear</button>
                    )}
                </div>
            </div>

            {/* ══ SECTION 1 — PENDING PAYMENTS ══ */}
            {activeSection === 'pending-payments' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="⏳" title="Payment Approvals"
                        subtitle="Patients who transferred to Sterling Bank (0089364407) and tapped 'I Have Paid'."
                        count={pendingPayments.length} bg="linear-gradient(135deg,#fef3c7,#fde68a)" border="#fbbf24" countBg="#d97706" />
                    {pendingPayments.length === 0
                        ? <EmptyState emoji="✅" title="All payments confirmed" sub="No pending bank transfers to review" />
                        : pendingPayments.map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}

            {/* ══ SECTION 2 — ALL MISSED (with sub-tabs) ══ */}
            {activeSection === 'all-missed' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="⚠️" title="Missed Appointments"
                        subtitle="All appointments marked as missed. Switch between sub-tabs to filter."
                        count={allMissed.length} bg="linear-gradient(135deg,#ffedd5,#fed7aa)" border="#f97316" countBg="#ea580c" />

                    <div style={{ display: 'flex', gap: '8px', marginBottom: '14px' }}>
                        <SubTabPill id="all-missed"  label="All Missed"  count={allMissed.length}    activeColor="#ea580c" />
                        <SubTabPill id="paid-missed" label="💸 Paid & Missed" count={paidButMissed.length} activeColor="#db2777" />
                    </div>

                    {missedSubTab === 'all-missed' && (
                        allMissed.length === 0
                            ? <EmptyState emoji="✅" title="No missed appointments" sub="Missed appointments will appear here" />
                            : allMissed.map(g => <GroupCard key={g.key} group={g} />)
                    )}

                    {missedSubTab === 'paid-missed' && (
                        <>
                            <div style={{ background: '#fce7f3', border: '1px solid #f472b6', borderRadius: '8px', padding: '10px 14px', marginBottom: '12px', fontSize: '12px', color: '#9d174d', fontWeight: '600' }}>
                                💸 These patients paid but missed their appointment. Use the <strong>Reschedule</strong> button to give them a new slot, or process a refund via the Refund Requests tab.
                            </div>
                            {paidButMissed.length === 0
                                ? <EmptyState emoji="✅" title="No paid missed appointments" sub="All paying patients attended their appointments" />
                                : paidButMissed.map(g => <GroupCard key={g.key} group={g} />)}
                        </>
                    )}
                </div>
            )}

            {/* ══ SECTION 3 — REFUND REQUESTS ══ */}
            {activeSection === 'refund-requests' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="💰" title="Refund Requests"
                        subtitle="Pending refund requests. Approve or decline each request."
                        count={filterByName(pendingRefunds).length} bg="linear-gradient(135deg,#fce7f3,#fbcfe8)" border="#ec4899" countBg="#db2777" />

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '10px' }}>
                        <button onClick={() => { loadRefundRequests(); onRefresh && onRefresh(); }}
                            style={{ padding: '6px 14px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600', color: '#374151' }}>
                            🔄 Refresh
                        </button>
                    </div>

                    {refundLoading ? (
                        <div style={{ textAlign: 'center', padding: '40px' }}><div className="spinner"></div></div>
                    ) : filterByName(pendingRefunds).length === 0 ? (
                        <EmptyState emoji="✅" title="No refund requests" sub="When patients request refunds from the app they will appear here" />
                    ) : filterByName(pendingRefunds).map(refund => (
                        <div key={refund.id} style={{ background: 'white', border: '1.5px solid #f9a8d4', borderRadius: '10px', padding: '14px', marginBottom: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
                            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '10px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                    <div style={{ width: '34px', height: '34px', borderRadius: '50%', background: 'linear-gradient(135deg,#db2777,#9d174d)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '12px', flexShrink: 0 }}>
                                        {(refund.patientName || 'U').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '700', fontSize: '14px', color: '#111827' }}>{refund.patientName || 'Unknown Patient'}</div>
                                        <div style={{ fontSize: '11px', color: '#6b7280' }}>
                                            {refund.testType || refund.testName || 'Medical Test'} · {refund.amount || refund.price || '—'}
                                        </div>
                                        {refund.requestedAt && (
                                            <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '2px' }}>
                                                Requested: {fmtDate(refund.requestedAt)}
                                            </div>
                                        )}
                                    </div>
                                </div>
                                <span style={{
                                    padding: '3px 10px', borderRadius: '10px', fontSize: '11px', fontWeight: '700',
                                    background: '#fce7f3', color: '#9d174d',
                                }}>
                                    {refund.status || 'PENDING'}
                                </span>
                            </div>

                            {refund.reason && (
                                <div style={{ background: '#fdf2f8', border: '1px solid #fbcfe8', borderRadius: '6px', padding: '8px 10px', marginBottom: '10px', fontSize: '12px', color: '#831843' }}>
                                    <strong>Reason:</strong> {refund.reason}
                                </div>
                            )}

                            {(refund.status || '').toUpperCase() === 'PENDING' && (
                                <div style={{ display: 'flex', gap: '8px' }}>
                                    <button onClick={() => handleApproveRefund(refund)} disabled={processingRefund === refund.id}
                                        style={{ flex: 1, padding: '7px', background: '#059669', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '12px', opacity: processingRefund === refund.id ? 0.6 : 1 }}>
                                        {processingRefund === refund.id ? '...' : '✅ Approve Refund'}
                                    </button>
                                    <button onClick={() => handleDeclineRefund(refund)} disabled={processingRefund === refund.id}
                                        style={{ flex: 1, padding: '7px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '12px', opacity: processingRefund === refund.id ? 0.6 : 1 }}>
                                        ✕ Decline
                                    </button>
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {/* ══ SECTION — APPROVED REFUNDS ══ */}
            {activeSection === 'approved-refunds' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="✅" title="Approved Refunds"
                        subtitle="These refunds have been approved. The patient has been notified that the refund will take 2-3 working days. Mark as refunded once processed."
                        count={filterByName(approvedRefunds).length} bg="linear-gradient(135deg,#d1fae5,#a7f3d0)" border="#059669" countBg="#047857" />

                    {filterByName(approvedRefunds).length === 0 ? (
                        <EmptyState emoji="📭" title="No approved refunds" sub="Approved refunds will appear here after you approve a refund request" />
                    ) : filterByName(approvedRefunds).map(refund => (
                        <div key={refund.id} style={{ background: 'white', border: '1.5px solid #6ee7b7', borderRadius: '10px', padding: '14px', marginBottom: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
                            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '10px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                    <div style={{ width: '34px', height: '34px', borderRadius: '50%', background: 'linear-gradient(135deg,#059669,#047857)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '12px', flexShrink: 0 }}>
                                        {(refund.patientName || 'U').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                                    </div>
                                    <div>
                                        <div style={{ fontWeight: '700', fontSize: '14px', color: '#111827' }}>{refund.patientName || 'Unknown Patient'}</div>
                                        <div style={{ fontSize: '11px', color: '#6b7280' }}>
                                            {refund.testType || refund.testName || 'Medical Test'} · {refund.amount || refund.price || '—'}
                                        </div>
                                    </div>
                                </div>
                                <span style={{ padding: '3px 10px', borderRadius: '10px', fontSize: '11px', fontWeight: '700', background: '#d1fae5', color: '#065f46' }}>
                                    APPROVED
                                </span>
                            </div>

                            <div style={{ background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '6px', padding: '8px 10px', marginBottom: '10px', fontSize: '12px', color: '#065f46' }}>
                                ⏳ Refund will be processed within <strong>2-3 working days</strong>. Patient has been notified.
                            </div>

                            <button onClick={() => handleMarkRefunded(refund)} disabled={processingRefund === refund.id}
                                style={{ width: '100%', padding: '8px', background: '#7c3aed', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '12px', opacity: processingRefund === refund.id ? 0.6 : 1 }}>
                                {processingRefund === refund.id ? '...' : '💸 Mark as Refunded'}
                            </button>
                        </div>
                    ))}
                </div>
            )}

            {/* ══ SECTION — ALREADY REFUNDED ══ */}
            {activeSection === 'already-refunded' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="💸" title="Already Refunded"
                        subtitle="Completed refunds. These patients have received their money back."
                        count={filterByName(alreadyRefunded).length} bg="linear-gradient(135deg,#e0e7ff,#c7d2fe)" border="#6366f1" countBg="#4f46e5" />

                    {filterByName(alreadyRefunded).length === 0 ? (
                        <EmptyState emoji="📭" title="No completed refunds yet" sub="Refunds marked as completed will appear here" />
                    ) : filterByName(alreadyRefunded).map(refund => (
                        <div key={refund.id} style={{ background: 'white', border: '1.5px solid #a5b4fc', borderRadius: '10px', padding: '14px', marginBottom: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <div style={{ width: '34px', height: '34px', borderRadius: '50%', background: 'linear-gradient(135deg,#6366f1,#4f46e5)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '12px', flexShrink: 0 }}>
                                    {(refund.patientName || 'U').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                                </div>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: '700', fontSize: '14px', color: '#111827' }}>{refund.patientName || 'Unknown Patient'}</div>
                                    <div style={{ fontSize: '11px', color: '#6b7280' }}>
                                        {refund.testType || refund.testName || 'Medical Test'} · {refund.amount || refund.price || '—'}
                                    </div>
                                </div>
                                <span style={{ padding: '3px 10px', borderRadius: '10px', fontSize: '11px', fontWeight: '700', background: '#e0e7ff', color: '#3730a3' }}>
                                    REFUNDED
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* ══ SECTION — RESCHEDULE REQUESTS ══ */}
            {activeSection === 'reschedule-requests' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="📅" title="Reschedule Requests"
                        subtitle="Patients who requested a reschedule from the app."
                        count={rescheduleRequests.length} bg="linear-gradient(135deg,#ede9fe,#ddd6fe)" border="#8b5cf6" countBg="#7c3aed" />
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '10px' }}>
                        <button onClick={() => { loadRescheduleRequests(); onRefresh && onRefresh(); }}
                            style={{ padding: '6px 14px', background: '#f3f4f6', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600', color: '#374151' }}>
                            🔄 Refresh
                        </button>
                    </div>
                    {rescheduleReqLoading ? (
                        <div style={{ textAlign: 'center', padding: '40px' }}><div className="spinner"></div></div>
                    ) : rescheduleRequests.length === 0 ? (
                        <EmptyState emoji="✅" title="No reschedule requests" sub="Patient-initiated reschedule requests will appear here" />
                    ) : rescheduleRequests.map((req, i) => (
                        <div key={req.id || i} style={{ background: 'white', border: '1.5px solid #c4b5fd', borderRadius: '10px', padding: '14px', marginBottom: '10px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                                <div style={{ width: '34px', height: '34px', borderRadius: '50%', background: 'linear-gradient(135deg,#7c3aed,#5b21b6)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '700', fontSize: '12px', flexShrink: 0 }}>
                                    {(req.patientName || 'U').split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                                </div>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: '700', fontSize: '14px' }}>{req.patientName || 'Unknown'}</div>
                                    <div style={{ fontSize: '11px', color: '#6b7280' }}>
                                        {req.testType || req.testName || 'Medical Test'} · Original: {fmtDate(req.originalDate || req.appointmentDate)}
                                    </div>
                                    {req.requestedDate && <div style={{ fontSize: '11px', color: '#7c3aed', fontWeight: '600' }}>Requested new date: {fmtDate(req.requestedDate)}</div>}
                                </div>
                            </div>
                            {req.reason && (
                                <div style={{ background: '#f5f3ff', border: '1px solid #c4b5fd', borderRadius: '6px', padding: '8px 10px', marginBottom: '8px', fontSize: '12px', color: '#5b21b6' }}>
                                    <strong>Reason:</strong> {req.reason}
                                </div>
                            )}
                            <button onClick={() => { setRescheduleModal({ appointments: [{ id: req.appointmentId || req.id }], patientName: req.patientName, totalPrice: 0 }); setRescheduleDate(req.requestedDate ? req.requestedDate.split('T')[0] : ''); setRescheduleTime(''); }}
                                style={{ padding: '6px 16px', background: '#7c3aed', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '700', fontSize: '12px' }}>
                                📅 Approve & Set New Date
                            </button>
                        </div>
                    ))}
                </div>
            )}

            {/* ══ SECTION — RESCHEDULED ══ */}
            {activeSection === 'rescheduled' && (
                <div style={{ padding: '14px 16px' }}>
                    <SectionBanner icon="🔁" title="Rescheduled Appointments"
                        subtitle="Previously missed (paid) appointments that have been given a new slot. Mark Completed once attended."
                        count={rescheduledGroups.length} bg="linear-gradient(135deg,#f5f3ff,#ede9fe)" border="#8b5cf6" countBg="#7c3aed" />
                    {rescheduledGroups.length === 0
                        ? <EmptyState emoji="✅" title="No rescheduled appointments" sub="Rescheduled appointments appear here" />
                        : rescheduledGroups.map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}

            {/* ══ SECTION — ALL APPOINTMENTS ══ */}
            {activeSection === 'all-appointments' && (
                <div style={{ padding: '14px 16px' }}>
                    {/* ── Status filter tabs (same size as section pills) ── */}
                    <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '14px' }}>
                        {[
                            { key: 'all',         label: 'All',         count: counts.all,         color: '#6b7280' },
                            { key: 'scheduled',   label: 'Scheduled',   count: counts.scheduled,   color: '#065f46' },
                            { key: 'completed',   label: 'Completed',   count: counts.completed,   color: '#1e40af' },
                            { key: 'rescheduled', label: 'Rescheduled', count: counts.rescheduled, color: '#5b21b6' },
                            { key: 'cancelled',   label: 'Cancelled',   count: counts.cancelled,   color: '#991b1b' },
                        ].map(st => (
                            <button key={st.key} onClick={() => setFilterStatus(st.key)} style={{
                                padding: '8px 16px', borderRadius: '8px',
                                border: `2px solid ${filterStatus === st.key ? st.color : '#e5e7eb'}`,
                                background: filterStatus === st.key ? st.color : 'white',
                                color: filterStatus === st.key ? 'white' : st.color,
                                fontWeight: '700', fontSize: '13px', cursor: 'pointer',
                                display: 'flex', alignItems: 'center', gap: '6px',
                            }}>
                                {st.label}
                                <span style={{ padding: '1px 7px', borderRadius: '12px', background: filterStatus === st.key ? 'rgba(255,255,255,0.25)' : '#f3f4f6', color: filterStatus === st.key ? 'white' : '#6b7280', fontSize: '11px', fontWeight: '700' }}>({st.count})</span>
                            </button>
                        ))}
                    </div>

                    <div style={{ padding: '5px 10px', background: '#f3f4f6', borderRadius: '6px', marginBottom: '12px', fontSize: '12px', color: '#6b7280' }}>
                        📅 Sorted oldest first · {filteredGroups.length} group(s) · {filteredGroups.reduce((s, g) => s + g.appointments.length, 0)} appointment(s)
                    </div>

                    {filteredGroups.length === 0
                        ? <div style={{ textAlign: 'center', padding: '40px', color: '#9ca3af' }}><div style={{ fontSize: '40px', marginBottom: '10px' }}>📅</div><div style={{ fontSize: '15px', fontWeight: '600', color: '#374151' }}>No appointments found</div></div>
                        : filteredGroups.map(g => <GroupCard key={g.key} group={g} />)}
                </div>
            )}
        </div>
    );
};
