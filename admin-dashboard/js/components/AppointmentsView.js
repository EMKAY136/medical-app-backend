const { useState, useMemo } = React;

const AppointmentsView = ({ appointments, setShowModal, onRefresh }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterPayment, setFilterPayment] = useState('all');
    const [filterDate, setFilterDate] = useState('');
    const [searchName, setSearchName] = useState('');          // ✅ NEW: patient name search
    const [actionLoading, setActionLoading] = useState(null);
    const [activeSection, setActiveSection] = useState('pending-payments');
    const [editingGroup, setEditingGroup] = useState(null);
    const [editTests, setEditTests] = useState([]);
    const [savingTests, setSavingTests] = useState(false);

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
        // Hide midnight time — means no time was set
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
            case 'SCHEDULED':  return { bg: '#d1fae5', color: '#065f46' };
            case 'COMPLETED':  return { bg: '#dbeafe', color: '#1e40af' };
            case 'MISSED':     return { bg: '#ffedd5', color: '#9a3412' };
            case 'CANCELLED':  return { bg: '#fee2e2', color: '#991b1b' };
            default:           return { bg: '#f3f4f6', color: '#6b7280' };
        }
    };

    // ── Group same-patient same-datetime appointments into one card ──────────
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
                    key,
                    patientId: apt.patientId,
                    patientName: apt.patientName,
                    appointmentDate: dateVal,
                    status: apt.status,
                    paymentStatus: apt.paymentStatus,
                    paymentMethod: apt.paymentMethod,
                    appointments: [],
                    totalPrice: 0,
                };
            }
            groups[key].appointments.push(apt);
            groups[key].totalPrice += parsePrice(apt.price);

            const statuses = groups[key].appointments.map(a => (a.status || '').toUpperCase());
            if (statuses.includes('SCHEDULED'))      groups[key].status = 'SCHEDULED';
            else if (statuses.includes('MISSED'))    groups[key].status = 'MISSED';
            else if (statuses.includes('COMPLETED')) groups[key].status = 'COMPLETED';
            else if (statuses.includes('CANCELLED')) groups[key].status = 'CANCELLED';
        });

        return Object.values(groups).sort((a, b) => {
            const da = parseDate(a.appointmentDate);
            const db = parseDate(b.appointmentDate);
            if (!da) return 1; if (!db) return -1;
            return da - db;
        });
    };

    // ── Pending payment groups ───────────────────────────────────────────────
    const pendingPayments = useMemo(() => {
        const pending = appointments.filter(a =>
            (a.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION'
        );
        return groupAppointments(pending);
    }, [appointments]);

    // ── ✅ NEW: Paid but missed groups ───────────────────────────────────────
    const paidButMissed = useMemo(() => {
        const missed = appointments.filter(a =>
            (a.status || '').toUpperCase() === 'MISSED' &&
            (a.paymentStatus || '').toUpperCase() === 'PAID'
        );
        return groupAppointments(missed);
    }, [appointments]);

    // ── Status counts ────────────────────────────────────────────────────────
    const counts = useMemo(() => ({
        all:       appointments.length,
        scheduled: appointments.filter(a => (a.status || '').toUpperCase() === 'SCHEDULED').length,
        completed: appointments.filter(a => (a.status || '').toUpperCase() === 'COMPLETED').length,
        missed:    appointments.filter(a => (a.status || '').toUpperCase() === 'MISSED').length,
        cancelled: appointments.filter(a => (a.status || '').toUpperCase() === 'CANCELLED').length,
    }), [appointments]);

    // ── Filtered + grouped list (with name search) ───────────────────────────
    const filteredGroups = useMemo(() => {
        const q = searchName.toLowerCase().trim();
        const filtered = appointments.filter(apt => {
            const stOk   = filterStatus  === 'all' || (apt.status || '').toLowerCase() === filterStatus;
            const payOk  = filterPayment === 'all' || (apt.paymentStatus || '').toLowerCase() === filterPayment;
            // ✅ NEW: name search
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
    const apiPatch = async (url) => {
        const token = localStorage.getItem('authToken');
        const res = await fetch(`${CONFIG.ADMIN_API_URL}${url}`, {
            method: 'PATCH',
            headers: { Authorization: `Bearer ${token}` },
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
        if (!window.confirm(
            `Confirm receipt of ${formatNaira(group.totalPrice)} for ${group.appointments.length} test(s) from ${group.patientName}?`
        )) return;
        for (const apt of group.appointments) {
            setActionLoading(apt.id + '-pay');
            try {
                const data = await apiPatch(`/api/admin/appointments/${apt.id}/approve-payment`);
                if (!data.success) {
                    alert(data.message || `Failed to approve appointment #${apt.id}`);
                    setActionLoading(null);
                    return;
                }
            } catch (err) {
                alert('Network error: ' + err.message);
                setActionLoading(null);
                return;
            }
        }
        setActionLoading(null);
        window.showNotificationAlert && window.showNotificationAlert(
            `Payment approved for ${group.patientName} — ${group.appointments.length} test(s) ✅`
        );
        onRefresh && onRefresh();
    };

    const handleMarkMissed = async (aptId) => {
        setActionLoading(aptId + '-miss');
        try {
            const data = await apiPatch(`/api/admin/appointments/${aptId}/mark-missed`);
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

    const handleUpdateStatus = async (aptId, newStatus) => {
        setActionLoading(aptId + '-status');
        try {
            const data = await apiPut(`/api/admin/appointments/${aptId}/status?status=${newStatus}`);
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

    const handleGroupStatus = async (group, newStatus) => {
        for (const apt of group.appointments) {
            await handleUpdateStatus(apt.id, newStatus);
        }
    };

    const openEditTests = (group) => {
        setEditingGroup(group);
        setEditTests(group.appointments.map(a => ({
            id: a.id,
            testType: a.testType || a.reason || '',
            price: a.price || '',
            original: a.testType || a.reason || '',
        })));
    };

    const saveEditedTests = async () => {
        setSavingTests(true);
        try {
            for (const t of editTests) {
                await apiPut(`/api/admin/appointments/${t.id}`, { testType: t.testType, price: t.price });
            }
            window.showNotificationAlert && window.showNotificationAlert('Tests updated successfully ✅');
            setEditingGroup(null);
            setEditTests([]);
            onRefresh && onRefresh();
        } catch (err) {
            alert('Error saving tests: ' + err.message);
        } finally {
            setSavingTests(false);
        }
    };

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
                }}>
                    {count}
                </span>
            )}
            {alertCount > 0 && (
                <span style={{
                    position: 'absolute', top: '-6px', right: '-6px',
                    width: '16px', height: '16px', borderRadius: '50%',
                    background: '#ef4444', color: 'white',
                    fontSize: '9px', fontWeight: '900',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                    {alertCount}
                </span>
            )}
        </button>
    );

    // ── Group card ────────────────────────────────────────────────────────────
    const GroupCard = ({ group }) => {
        const sb = getStatusBadge(group.status);
        const pb = getPaymentBadge(group.paymentStatus);
        const aptDate = parseDate(group.appointmentDate);
        const isPast = aptDate && aptDate < new Date();
        const isSchd = (group.status || '').toUpperCase() === 'SCHEDULED';
        const isPending = (group.paymentStatus || '').toUpperCase() === 'PENDING_CONFIRMATION';
        const isMissedPaid =
            (group.status || '').toUpperCase() === 'MISSED' &&
            (group.paymentStatus || '').toUpperCase() === 'PAID';
        const isGroupLoading = group.appointments.some(a =>
            actionLoading === a.id + '-pay' ||
            actionLoading === a.id + '-status' ||
            actionLoading === a.id + '-miss'
        );

        return (
            <div style={{
                background: 'white',
                border: `1.5px solid ${isPending ? '#fbbf24' : isMissedPaid ? '#f97316' : '#e5e7eb'}`,
                borderRadius: '10px', padding: '12px 14px', marginBottom: '10px',
                boxShadow: isPending
                    ? '0 2px 8px rgba(251,191,36,0.15)'
                    : isMissedPaid
                    ? '0 2px 8px rgba(249,115,22,0.15)'
                    : '0 1px 3px rgba(0,0,0,0.05)',
            }}>
                {/* Row 1: avatar + name + date + badges */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
                    <div style={{
                        width: '34px', height: '34px', borderRadius: '50%', flexShrink: 0,
                        background: 'linear-gradient(135deg, #667eea, #764ba2)',
                        color: 'white', display: 'flex', alignItems: 'center',
                        justifyContent: 'center', fontWeight: '700', fontSize: '12px',
                    }}>
                        {group.patientName
                            ? group.patientName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()
                            : 'U'}
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
                    </div>
                </div>

                {/* Row 2: tests */}
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
                        <div key={apt.id} style={{
                            display: 'flex', justifyContent: 'space-between',
                            fontSize: '12px', paddingTop: i > 0 ? '4px' : '0',
                            borderTop: i > 0 ? '1px solid #e5e7eb' : 'none',
                            marginTop: i > 0 ? '4px' : '0',
                        }}>
                            <span style={{ color: '#374151', fontWeight: '500' }}>
                                🧪 {apt.testType || apt.reason || 'Medical Test'}
                            </span>
                            <span style={{ color: '#059669', fontWeight: '700', flexShrink: 0, marginLeft: '8px' }}>
                                {apt.price || '—'}
                            </span>
                        </div>
                    ))}
                    {group.appointments.length > 1 && (
                        <div style={{
                            display: 'flex', justifyContent: 'space-between',
                            fontSize: '12px', fontWeight: '800', color: '#111827',
                            borderTop: '2px solid #d1d5db', marginTop: '6px', paddingTop: '6px',
                        }}>
                            <span>Total</span>
                            <span style={{ color: '#059669' }}>{formatNaira(group.totalPrice)}</span>
                        </div>
                    )}
                </div>

                {/* Row 3: pending payment */}
                {isPending && (
                    <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        background: '#fef3c7', border: '1px solid #fbbf24',
                        borderRadius: '7px', padding: '7px 10px', marginBottom: '8px',
                    }}>
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

                {/* ✅ NEW: Paid but missed notice */}
                {isMissedPaid && (
                    <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        background: '#fff7ed', border: '1px solid #f97316',
                        borderRadius: '7px', padding: '7px 10px', marginBottom: '8px',
                    }}>
                        <div style={{ fontSize: '11px', color: '#9a3412', fontWeight: '600' }}>
                            💸 Patient paid but missed this appointment. Consider a refund or reschedule.
                        </div>
                    </div>
                )}

                {/* Row 4: action buttons */}
                {isSchd && (
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <button
                            onClick={() => handleGroupStatus(group, 'COMPLETED')}
                            disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#1e40af', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}
                        >
                            ✓ Mark Done
                        </button>
                        {isPast && (
                            <button
                                onClick={() => {
                                    if (window.confirm('Mark all appointments in this group as missed?')) {
                                        group.appointments.forEach(a => handleMarkMissed(a.id));
                                    }
                                }}
                                disabled={!!actionLoading}
                                style={{ padding: '4px 12px', background: '#ea580c', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}
                            >
                                ⚠ Missed
                            </button>
                        )}
                        <button
                            onClick={() => {
                                if (window.confirm('Cancel all appointments in this group?')) {
                                    handleGroupStatus(group, 'CANCELLED');
                                }
                            }}
                            disabled={!!actionLoading}
                            style={{ padding: '4px 12px', background: '#dc2626', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '11px', fontWeight: '600' }}
                        >
                            ✕ Cancel
                        </button>
                    </div>
                )}
            </div>
        );
    };

    // ════════════════════════════════════════════════════════════════════════
    return (
        <div style={{ padding: '0' }}>

            {/* Edit Tests Modal */}
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
                                    <input
                                        value={t.testType}
                                        onChange={e => { const u = [...editTests]; u[i] = { ...u[i], testType: e.target.value }; setEditTests(u); }}
                                        style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box', outline: 'none' }}
                                    />
                                </div>
                                <div>
                                    <label style={{ fontSize: '11px', color: '#374151', fontWeight: '600', display: 'block', marginBottom: '3px' }}>Price</label>
                                    <input
                                        value={t.price}
                                        onChange={e => { const u = [...editTests]; u[i] = { ...u[i], price: e.target.value }; setEditTests(u); }}
                                        style={{ width: '100%', padding: '7px 10px', borderRadius: '6px', border: '1.5px solid #d1d5db', fontSize: '13px', boxSizing: 'border-box', outline: 'none' }}
                                    />
                                </div>
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

            {/* Section toggle pills */}
            <div style={{ display: 'flex', gap: '10px', padding: '16px 16px 0', flexWrap: 'wrap' }}>
                <SectionPill id="pending-payments" label="💳 Pending Payments" count={pendingPayments.length} alertCount={pendingPayments.length} />
                {/* ✅ NEW: Paid but Missed tab */}
                <SectionPill id="paid-missed" label="💸 Paid & Missed" count={paidButMissed.length} alertCount={paidButMissed.length} />
                <SectionPill id="all-appointments" label="📅 All Appointments" count={appointments.length} alertCount={0} />
            </div>

            {/* ══ SECTION 1 — PENDING PAYMENTS ══ */}
            {activeSection === 'pending-payments' && (
                <div style={{ padding: '14px 16px' }}>
                    <div style={{ background: 'linear-gradient(135deg, #fef3c7, #fde68a)', border: '2px solid #fbbf24', borderRadius: '12px', padding: '12px 16px', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ fontSize: '26px' }}>⏳</div>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontSize: '15px', fontWeight: '800', color: '#92400e' }}>Payment Approvals</div>
                            <div style={{ fontSize: '11px', color: '#b45309', marginTop: '2px' }}>
                                Patients who transferred to Sterling Bank (0089364407) and tapped "I Have Paid". Verify and approve.
                            </div>
                        </div>
                        <div style={{ background: '#d97706', color: 'white', padding: '6px 14px', borderRadius: '8px', fontSize: '18px', fontWeight: '900', minWidth: '40px', textAlign: 'center' }}>
                            {pendingPayments.length}
                        </div>
                    </div>
                    {pendingPayments.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '40px 20px', background: 'white', borderRadius: '12px', border: '2px dashed #e5e7eb' }}>
                            <div style={{ fontSize: '44px', marginBottom: '8px' }}>✅</div>
                            <div style={{ fontSize: '15px', fontWeight: '700', color: '#374151', marginBottom: '4px' }}>All payments confirmed</div>
                            <div style={{ fontSize: '12px', color: '#9ca3af' }}>No pending bank transfers to review</div>
                        </div>
                    ) : pendingPayments.map(group => <GroupCard key={group.key} group={group} />)}
                </div>
            )}

            {/* ✅ NEW ══ SECTION 2 — PAID BUT MISSED ══ */}
            {activeSection === 'paid-missed' && (
                <div style={{ padding: '14px 16px' }}>
                    <div style={{ background: 'linear-gradient(135deg, #fff7ed, #fed7aa)', border: '2px solid #f97316', borderRadius: '12px', padding: '12px 16px', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ fontSize: '26px' }}>💸</div>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontSize: '15px', fontWeight: '800', color: '#9a3412' }}>Paid but Missed Appointments</div>
                            <div style={{ fontSize: '11px', color: '#c2410c', marginTop: '2px' }}>
                                These patients paid but did not show up. Consider offering a refund or rescheduling.
                            </div>
                        </div>
                        <div style={{ background: '#ea580c', color: 'white', padding: '6px 14px', borderRadius: '8px', fontSize: '18px', fontWeight: '900', minWidth: '40px', textAlign: 'center' }}>
                            {paidButMissed.length}
                        </div>
                    </div>
                    {paidButMissed.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '40px 20px', background: 'white', borderRadius: '12px', border: '2px dashed #e5e7eb' }}>
                            <div style={{ fontSize: '44px', marginBottom: '8px' }}>✅</div>
                            <div style={{ fontSize: '15px', fontWeight: '700', color: '#374151', marginBottom: '4px' }}>No paid missed appointments</div>
                            <div style={{ fontSize: '12px', color: '#9ca3af' }}>All paying patients attended their appointments</div>
                        </div>
                    ) : paidButMissed.map(group => <GroupCard key={group.key} group={group} />)}
                </div>
            )}

            {/* ══ SECTION 3 — ALL APPOINTMENTS ══ */}
            {activeSection === 'all-appointments' && (
                <div style={{ padding: '14px 16px' }}>
                    {/* Status pills */}
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
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
                                    padding: '5px 12px', borderRadius: '20px',
                                    border: `2px solid ${filterStatus === st.key ? st.color : '#e5e7eb'}`,
                                    background: filterStatus === st.key ? st.color : 'white',
                                    color: filterStatus === st.key ? 'white' : st.color,
                                    fontWeight: '700', fontSize: '12px', cursor: 'pointer',
                                }}
                            >
                                {st.label} ({st.count})
                            </button>
                        ))}
                    </div>

                    {/* Filters */}
                    <div style={{ display: 'flex', gap: '10px', marginBottom: '12px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                        {/* ✅ NEW: Patient name search */}
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Patient Name</label>
                            <input
                                type="text"
                                placeholder="Search by name..."
                                value={searchName}
                                onChange={e => setSearchName(e.target.value)}
                                style={{ width: '180px', fontSize: '12px', padding: '5px 8px', border: '1px solid #d1d5db', borderRadius: '6px', outline: 'none' }}
                            />
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Payment</label>
                            <select
                                className="form-input"
                                value={filterPayment}
                                onChange={e => setFilterPayment(e.target.value)}
                                style={{ width: '160px', fontSize: '12px', padding: '5px 8px' }}
                            >
                                <option value="all">All Payments</option>
                                <option value="paid">Paid</option>
                                <option value="pending_confirmation">Pending Confirmation</option>
                                <option value="pay_on_arrival">Pay on Arrival</option>
                                <option value="unpaid">Unpaid</option>
                            </select>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '11px', color: '#6b7280', marginBottom: '3px', fontWeight: '600' }}>Date</label>
                            <input
                                type="date"
                                className="form-input"
                                value={filterDate}
                                onChange={e => setFilterDate(e.target.value)}
                                style={{ width: '150px', fontSize: '12px', padding: '5px 8px' }}
                            />
                        </div>
                        {(filterStatus !== 'all' || filterPayment !== 'all' || filterDate || searchName) && (
                            <button
                                onClick={() => { setFilterStatus('all'); setFilterPayment('all'); setFilterDate(''); setSearchName(''); }}
                                style={{ padding: '6px 12px', background: '#6b7280', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '12px', fontWeight: '600' }}
                            >
                                Clear
                            </button>
                        )}
                    </div>

                    <div style={{ padding: '5px 10px', background: '#f3f4f6', borderRadius: '6px', marginBottom: '12px', fontSize: '12px', color: '#6b7280' }}>
                        📅 Sorted oldest first · {filteredGroups.length} group(s) · {filteredGroups.reduce((s, g) => s + g.appointments.length, 0)} appointment(s)
                    </div>

                    {filteredGroups.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '40px', color: '#9ca3af' }}>
                            <div style={{ fontSize: '40px', marginBottom: '10px' }}>📅</div>
                            <div style={{ fontSize: '15px', fontWeight: '600', color: '#374151' }}>No appointments found</div>
                        </div>
                    ) : filteredGroups.map(group => <GroupCard key={group.key} group={group} />)}
                </div>
            )}
        </div>
    );
};