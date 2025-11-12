const BookTestModal = ({ patients = [], onClose, onSubmit, selectedPatient }) => {
    const [formData, setFormData] = React.useState({
        patientId: selectedPatient?.id || '',
        testType: '',
        scheduledDate: '',
        scheduledTime: '',
        notes: '',
        priority: 'normal'
    });
    const [isSubmitting, setIsSubmitting] = React.useState(false);
    const [customTestType, setCustomTestType] = React.useState('');

    const commonTests = [
        'Blood Sugar Test',
        'Complete Blood Count (CBC)',
        'Lipid Profile',
        'Liver Function Test',
        'Kidney Function Test',
        'Thyroid Test',
        'ECG',
        'X-Ray',
        'Ultrasound',
        'CT Scan',
        'MRI',
        'Urine Analysis',
        'COVID-19 Test',
        'Other'
    ];

    const timeSlots = [
        '08:00', '08:30', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30',
        '12:00', '12:30', '13:00', '13:30', '14:00', '14:30', '15:00', '15:30', 
        '16:00', '16:30', '17:00'
    ];

    const handleSubmit = async (e) => {
        e.preventDefault();
        
        const finalTestType = formData.testType === 'Other' ? customTestType : formData.testType;
        
        if (!formData.patientId || !finalTestType || !formData.scheduledDate || !formData.scheduledTime) {
            alert('Please fill in all required fields');
            return;
        }

        const selectedDateTime = new Date(`${formData.scheduledDate}T${formData.scheduledTime}`);
        if (selectedDateTime <= new Date()) {
            alert('Please select a future date and time');
            return;
        }

        setIsSubmitting(true);
        
        try {
            const submitData = {
                ...formData,
                testType: finalTestType
            };
            
            if (typeof onSubmit === 'function') {
                await onSubmit(submitData);
                onClose();
            } else {
                console.error('onSubmit is not a function');
                alert('Error: Submit handler not configured');
            }
        } catch (error) {
            console.error('Error booking test:', error);
            alert('Failed to book test. Please try again.');
        } finally {
            setIsSubmitting(false);
        }
    };

    const getSelectedPatientInfo = () => {
        if (!formData.patientId) return null;
        const patient = patients.find(p => p.id === parseInt(formData.patientId));
        return patient || null;
    };

    const patientInfo = getSelectedPatientInfo();

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
            padding: '20px',
            backdropFilter: 'blur(4px)'
        }} onClick={onClose}>
            <div style={{
                background: 'white',
                borderRadius: '16px',
                maxWidth: '650px',
                width: '100%',
                maxHeight: '90vh',
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column',
                boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)'
            }} onClick={(e) => e.stopPropagation()}>
                
                {/* Header */}
                <div style={{
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    color: 'white',
                    padding: '24px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                }}>
                    <div>
                        <h2 style={{margin: 0, fontSize: '24px', fontWeight: '700'}}>
                            📅 Book Test Appointment
                        </h2>
                        <p style={{margin: '4px 0 0 0', opacity: 0.9, fontSize: '14px'}}>
                            Schedule a medical test for your patient
                        </p>
                    </div>
                    <button onClick={onClose} style={{
                        background: 'rgba(255,255,255,0.2)',
                        border: 'none',
                        color: 'white',
                        fontSize: '28px',
                        cursor: 'pointer',
                        padding: '4px 12px',
                        borderRadius: '8px',
                        lineHeight: 1,
                        transition: 'background 0.2s'
                    }}
                    onMouseEnter={(e) => e.target.style.background = 'rgba(255,255,255,0.3)'}
                    onMouseLeave={(e) => e.target.style.background = 'rgba(255,255,255,0.2)'}
                    >
                        ×
                    </button>
                </div>
                
                {/* Form Content */}
                <form onSubmit={handleSubmit} style={{
                    padding: '24px',
                    overflow: 'auto',
                    flex: 1
                }}>
                    
                    {/* Patient Selection */}
                    <div style={{marginBottom: '20px'}}>
                        <label style={{
                            display: 'block',
                            fontWeight: '600',
                            marginBottom: '8px',
                            color: '#374151',
                            fontSize: '14px'
                        }}>
                            👤 Select Patient *
                        </label>
                        <select 
                            value={formData.patientId}
                            onChange={(e) => setFormData({...formData, patientId: e.target.value})}
                            required
                            disabled={selectedPatient}
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                border: '2px solid #e5e7eb',
                                fontSize: '15px',
                                backgroundColor: selectedPatient ? '#f0f9ff' : 'white',
                                cursor: selectedPatient ? 'not-allowed' : 'pointer',
                                outline: 'none',
                                transition: 'border-color 0.2s'
                            }}
                            onFocus={(e) => e.target.style.borderColor = '#667eea'}
                            onBlur={(e) => e.target.style.borderColor = '#e5e7eb'}
                        >
                            <option value="">-- Select a patient --</option>
                            {patients.map(patient => (
                                <option key={patient.id} value={patient.id}>
                                    {patient.firstName && patient.lastName ? 
                                        `${patient.firstName} ${patient.lastName}` : 
                                        patient.email
                                    } ({patient.email})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Patient Info Card */}
                    {patientInfo && (
                        <div style={{
                            background: 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)',
                            padding: '16px',
                            borderRadius: '8px',
                            marginBottom: '20px',
                            border: '2px solid #bae6fd'
                        }}>
                            <div style={{fontSize: '13px', color: '#0c4a6e', lineHeight: '1.8'}}>
                                <div><strong>📧 Email:</strong> {patientInfo.email}</div>
                                {patientInfo.phone && <div><strong>📱 Phone:</strong> {patientInfo.phone}</div>}
                                {patientInfo.bloodGroup && <div><strong>🩸 Blood:</strong> {patientInfo.bloodGroup}</div>}
                                {patientInfo.age && <div><strong>🎂 Age:</strong> {patientInfo.age} years</div>}
                            </div>
                        </div>
                    )}

                    {/* Test Type */}
                    <div style={{marginBottom: '20px'}}>
                        <label style={{
                            display: 'block',
                            fontWeight: '600',
                            marginBottom: '8px',
                            color: '#374151',
                            fontSize: '14px'
                        }}>
                            🧪 Test Type *
                        </label>
                        <select
                            value={formData.testType}
                            onChange={(e) => setFormData({...formData, testType: e.target.value})}
                            required
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                border: '2px solid #e5e7eb',
                                fontSize: '15px',
                                outline: 'none',
                                transition: 'border-color 0.2s',
                                cursor: 'pointer'
                            }}
                            onFocus={(e) => e.target.style.borderColor = '#667eea'}
                            onBlur={(e) => e.target.style.borderColor = '#e5e7eb'}
                        >
                            <option value="">-- Select test type --</option>
                            {commonTests.map(test => (
                                <option key={test} value={test}>{test}</option>
                            ))}
                        </select>
                        
                        {formData.testType === 'Other' && (
                            <input
                                type="text"
                                placeholder="Enter custom test type"
                                value={customTestType}
                                onChange={(e) => setCustomTestType(e.target.value)}
                                required
                                style={{
                                    width: '100%',
                                    padding: '12px 16px',
                                    borderRadius: '8px',
                                    border: '2px solid #fbbf24',
                                    fontSize: '15px',
                                    marginTop: '8px',
                                    outline: 'none'
                                }}
                            />
                        )}
                    </div>

                    {/* Date and Time */}
                    <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '20px'}}>
                        <div>
                            <label style={{
                                display: 'block',
                                fontWeight: '600',
                                marginBottom: '8px',
                                color: '#374151',
                                fontSize: '14px'
                            }}>
                                📅 Date *
                            </label>
                            <input
                                type="date"
                                value={formData.scheduledDate}
                                onChange={(e) => setFormData({...formData, scheduledDate: e.target.value})}
                                min={new Date().toISOString().split('T')[0]}
                                required
                                style={{
                                    width: '100%',
                                    padding: '12px 16px',
                                    borderRadius: '8px',
                                    border: '2px solid #e5e7eb',
                                    fontSize: '15px',
                                    outline: 'none',
                                    transition: 'border-color 0.2s'
                                }}
                                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                                onBlur={(e) => e.target.style.borderColor = '#e5e7eb'}
                            />
                        </div>
                        <div>
                            <label style={{
                                display: 'block',
                                fontWeight: '600',
                                marginBottom: '8px',
                                color: '#374151',
                                fontSize: '14px'
                            }}>
                                ⏰ Time *
                            </label>
                            <select 
                                value={formData.scheduledTime}
                                onChange={(e) => setFormData({...formData, scheduledTime: e.target.value})}
                                required
                                style={{
                                    width: '100%',
                                    padding: '12px 16px',
                                    borderRadius: '8px',
                                    border: '2px solid #e5e7eb',
                                    fontSize: '15px',
                                    outline: 'none',
                                    transition: 'border-color 0.2s',
                                    cursor: 'pointer'
                                }}
                                onFocus={(e) => e.target.style.borderColor = '#667eea'}
                                onBlur={(e) => e.target.style.borderColor = '#e5e7eb'}
                            >
                                <option value="">-- Select time --</option>
                                {timeSlots.map(time => (
                                    <option key={time} value={time}>{time}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    {/* Priority */}
                    <div style={{marginBottom: '20px'}}>
                        <label style={{
                            display: 'block',
                            fontWeight: '600',
                            marginBottom: '8px',
                            color: '#374151',
                            fontSize: '14px'
                        }}>
                            🚨 Priority Level
                        </label>
                        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px'}}>
                            {[
                                {value: 'normal', label: 'Normal', color: '#10b981', bg: '#d1fae5'},
                                {value: 'urgent', label: 'Urgent', color: '#f59e0b', bg: '#fef3c7'},
                                {value: 'emergency', label: 'Emergency', color: '#ef4444', bg: '#fee2e2'}
                            ].map(priority => (
                                <label key={priority.value} style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    padding: '12px',
                                    borderRadius: '8px',
                                    border: `2px solid ${formData.priority === priority.value ? priority.color : '#e5e7eb'}`,
                                    background: formData.priority === priority.value ? priority.bg : 'white',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}>
                                    <input
                                        type="radio"
                                        name="priority"
                                        value={priority.value}
                                        checked={formData.priority === priority.value}
                                        onChange={(e) => setFormData({...formData, priority: e.target.value})}
                                        style={{marginRight: '8px', cursor: 'pointer'}}
                                    />
                                    <span style={{fontSize: '14px', fontWeight: '600', color: priority.color}}>
                                        {priority.label}
                                    </span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Notes */}
                    <div style={{marginBottom: '24px'}}>
                        <label style={{
                            display: 'block',
                            fontWeight: '600',
                            marginBottom: '8px',
                            color: '#374151',
                            fontSize: '14px'
                        }}>
                            📝 Additional Notes
                        </label>
                        <textarea
                            rows="3"
                            value={formData.notes}
                            onChange={(e) => setFormData({...formData, notes: e.target.value})}
                            placeholder="Any special instructions, medical history, or requirements..."
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '8px',
                                border: '2px solid #e5e7eb',
                                fontSize: '15px',
                                resize: 'vertical',
                                fontFamily: 'inherit',
                                outline: 'none',
                                transition: 'border-color 0.2s'
                            }}
                            onFocus={(e) => e.target.style.borderColor = '#667eea'}
                            onBlur={(e) => e.target.style.borderColor = '#e5e7eb'}
                        />
                    </div>

                    {/* Action Buttons */}
                    <div style={{
                        display: 'flex', 
                        gap: '12px', 
                        justifyContent: 'flex-end',
                        paddingTop: '16px',
                        borderTop: '2px solid #f3f4f6'
                    }}>
                        <button 
                            type="button" 
                            onClick={onClose}
                            disabled={isSubmitting}
                            style={{
                                padding: '12px 24px',
                                borderRadius: '8px',
                                border: '2px solid #e5e7eb',
                                background: 'white',
                                color: '#374151',
                                fontSize: '15px',
                                fontWeight: '600',
                                cursor: isSubmitting ? 'not-allowed' : 'pointer',
                                opacity: isSubmitting ? 0.5 : 1,
                                transition: 'all 0.2s'
                            }}
                            onMouseEnter={(e) => !isSubmitting && (e.target.style.background = '#f9fafb')}
                            onMouseLeave={(e) => !isSubmitting && (e.target.style.background = 'white')}
                        >
                            Cancel
                        </button>
                        <button 
                            type="submit"
                            disabled={isSubmitting}
                            style={{
                                padding: '12px 24px',
                                borderRadius: '8px',
                                border: 'none',
                                background: isSubmitting ? '#9ca3af' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                                color: 'white',
                                fontSize: '15px',
                                fontWeight: '600',
                                cursor: isSubmitting ? 'not-allowed' : 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                transition: 'transform 0.2s, box-shadow 0.2s',
                                boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)'
                            }}
                            onMouseEnter={(e) => !isSubmitting && (e.target.style.transform = 'translateY(-2px)', e.target.style.boxShadow = '0 10px 15px -3px rgba(0,0,0,0.1)')}
                            onMouseLeave={(e) => !isSubmitting && (e.target.style.transform = 'translateY(0)', e.target.style.boxShadow = '0 4px 6px -1px rgba(0,0,0,0.1)')}
                        >
                            {isSubmitting ? '⏳ Booking...' : '✓ Book Appointment'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};