const { useState, useEffect, useRef } = React;

const AddResultModal = ({ patients, onClose, onSubmit }) => {
    const [formData, setFormData] = useState({
        patientId: '',
        appointmentId: '',
        testType: '',
        result: '',
        status: 'NORMAL',
        notes: '',
        doctorName: '',
        testDate: new Date().toISOString().split('T')[0],
        markAppointmentCompleted: false
    });
    const [patientAppointments, setPatientAppointments] = useState([]);
    const [patientSearch, setPatientSearch] = useState('');
    const [showPatientDropdown, setShowPatientDropdown] = useState(false);
    const [selectedPatient, setSelectedPatient] = useState(null);
    const [uploadedFiles, setUploadedFiles] = useState([]);
    const [uploadProgress, setUploadProgress] = useState({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const fileInputRef = useRef(null);
    const patientSearchRef = useRef(null);

    const filteredPatients = patients.filter(patient => {
        const fullName = `${patient.firstName || ''} ${patient.lastName || ''}`.toLowerCase();
        const email = (patient.email || '').toLowerCase();
        const search = patientSearch.toLowerCase();
        return fullName.includes(search) || email.includes(search);
    });

    useEffect(() => {
        const loadPatientAppointments = async () => {
            if (formData.patientId) {
                try {
                    const data = await ApiService.getAppointments(0, 50, formData.patientId);
                    const relevantAppointments = (data.appointments || []).filter(apt => 
                        apt.status && (
                            apt.status.toLowerCase() === 'scheduled' || 
                            apt.status.toLowerCase() === 'completed' ||
                            apt.status.toLowerCase() === 'pending'
                        )
                    );
                    
                    // Sort appointments: SCHEDULED first, then others
                    const sortedAppointments = relevantAppointments.sort((a, b) => {
                        const aStatus = a.status.toLowerCase();
                        const bStatus = b.status.toLowerCase();
                        
                        // Scheduled comes first
                        if (aStatus === 'scheduled' && bStatus !== 'scheduled') return -1;
                        if (bStatus === 'scheduled' && aStatus !== 'scheduled') return 1;
                        
                        // Within same status group, sort by date (most recent first)
                        const aDate = new Date(a.scheduledDate || a.appointmentDate);
                        const bDate = new Date(b.scheduledDate || b.appointmentDate);
                        return bDate - aDate;
                    });
                    
                    setPatientAppointments(sortedAppointments);
                    
                    if (sortedAppointments.length > 0) {
                        const mostRecent = sortedAppointments[0];
                        const appointmentDate = mostRecent.scheduledDate || mostRecent.appointmentDate;
                        setFormData(prev => ({
                            ...prev,
                            appointmentId: mostRecent.id.toString(),
                            testType: mostRecent.reason || mostRecent.testType || mostRecent.appointmentType || '',
                            testDate: appointmentDate ? 
                                new Date(appointmentDate).toISOString().split('T')[0] : 
                                new Date().toISOString().split('T')[0]
                        }));
                    }
                } catch (error) {
                    console.error('Error loading patient appointments:', error);
                }
            } else {
                setPatientAppointments([]);
            }
        };
        
        loadPatientAppointments();
    }, [formData.patientId]);

    const handlePatientSelect = (patient) => {
        setSelectedPatient(patient);
        setPatientSearch(`${patient.firstName} ${patient.lastName}`);
        setFormData({
            ...formData,
            patientId: patient.id.toString(),
            appointmentId: '',
            testType: ''
        });
        setShowPatientDropdown(false);
    };

    const handlePatientSearchChange = (e) => {
        setPatientSearch(e.target.value);
        setShowPatientDropdown(true);
        if (!e.target.value) {
            setSelectedPatient(null);
            setFormData({
                ...formData,
                patientId: '',
                appointmentId: '',
                testType: ''
            });
        }
    };

    const handleAppointmentSelect = (appointmentId) => {
        const selectedAppointment = patientAppointments.find(apt => apt.id == appointmentId);
        if (selectedAppointment) {
            const appointmentDate = selectedAppointment.appointmentDate || selectedAppointment.scheduledDate;
            setFormData({
                ...formData,
                appointmentId,
                testType: selectedAppointment.reason || selectedAppointment.testType || selectedAppointment.appointmentType || '',
                testDate: appointmentDate ? 
                    new Date(appointmentDate).toISOString().split('T')[0] : 
                    new Date().toISOString().split('T')[0]
            });
        }
    };

    const handleFileUpload = (e) => {
        const files = Array.from(e.target.files);
        files.forEach(file => {
            const allowedTypes = ['image/jpeg', 'image/png', 'image/jpg', 'application/pdf', 'text/plain'];
            if (!allowedTypes.includes(file.type)) {
                alert(`File type ${file.type} not allowed. Please upload images, PDF, or text files.`);
                return;
            }

            if (file.size > 5 * 1024 * 1024) {
                alert(`File ${file.name} is too large. Maximum size is 5MB.`);
                return;
            }

            const fileId = Date.now() + Math.random();
            setUploadProgress(prev => ({ ...prev, [fileId]: 0 }));
            
            const reader = new FileReader();
            reader.onload = (e) => {
                const newFile = {
                    id: fileId,
                    name: file.name,
                    type: file.type,
                    size: file.size,
                    data: e.target.result,
                    uploadedAt: new Date().toISOString()
                };
                
                setUploadedFiles(prev => [...prev, newFile]);
                setUploadProgress(prev => ({ ...prev, [fileId]: 100 }));
                
                setTimeout(() => {
                    setUploadProgress(prev => {
                        const newProgress = { ...prev };
                        delete newProgress[fileId];
                        return newProgress;
                    });
                }, 2000);
            };
            
            reader.readAsDataURL(file);
        });
        
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    const removeFile = (fileId) => {
        setUploadedFiles(prev => prev.filter(file => file.id !== fileId));
    };

    const convertBase64ToFile = (base64Data, fileName, mimeType) => {
        try {
            const base64String = base64Data.includes(',') 
                ? base64Data.split(',')[1] 
                : base64Data;
            
            const byteCharacters = atob(base64String);
            const byteNumbers = new Array(byteCharacters.length);
            
            for (let i = 0; i < byteCharacters.length; i++) {
                byteNumbers[i] = byteCharacters.charCodeAt(i);
            }
            
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray], { type: mimeType });
            
            try {
                return new File([blob], fileName, { 
                    type: mimeType,
                    lastModified: new Date().getTime()
                });
            } catch (e) {
                blob.name = fileName;
                return blob;
            }
        } catch (error) {
            console.error('Error converting base64 to file:', error);
            throw new Error(`Failed to prepare file ${fileName}: ${error.message}`);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        
        if (!formData.patientId || !formData.testType || !formData.result) {
            alert('Please fill in all required fields (Patient, Test Type, and Result)');
            return;
        }
        
        if (isSubmitting) {
            return;
        }
        
        setIsSubmitting(true);
        
        try {
            const hasFiles = uploadedFiles.length > 0;
            
            if (hasFiles) {
                console.log('=== UPLOAD WITH FILE ===');
                console.log('Files to upload:', uploadedFiles.length);
                
                const formDataToSend = new FormData();
                
                formDataToSend.append('patientId', formData.patientId);
                formDataToSend.append('testType', formData.testType);
                formDataToSend.append('result', formData.result);
                formDataToSend.append('notes', formData.notes || '');
                formDataToSend.append('category', 'General');
                formDataToSend.append('status', formData.status || 'COMPLETED');
                formDataToSend.append('doctorName', formData.doctorName || 'Admin');
                formDataToSend.append('testDate', formData.testDate);
                
                if (formData.appointmentId) {
                    formDataToSend.append('appointmentId', formData.appointmentId);
                }
                
                formDataToSend.append('markAppointmentCompleted', formData.markAppointmentCompleted ? 'true' : 'false');
                
                const attachment = uploadedFiles[0];
                console.log('Processing file:', {
                    name: attachment.name,
                    type: attachment.type,
                    size: attachment.size
                });
                
                const fileToUpload = convertBase64ToFile(
                    attachment.data,
                    attachment.name,
                    attachment.type
                );
                
                console.log('File converted:', {
                    name: fileToUpload.name || attachment.name,
                    size: fileToUpload.size,
                    type: fileToUpload.type
                });
                
                formDataToSend.append('file', fileToUpload, attachment.name);
                
                console.log('=== FormData Contents ===');
                console.log('Total entries:', Array.from(formDataToSend.entries()).length);
                for (let pair of formDataToSend.entries()) {
                    if (pair[0] === 'file') {
                        console.log('file:', {
                            name: pair[1].name,
                            size: pair[1].size,
                            type: pair[1].type
                        });
                    } else {
                        console.log(`${pair[0]}: "${pair[1]}" (type: ${typeof pair[1]})`);
                    }
                }
                
                const entries = Array.from(formDataToSend.entries());
                const lastEntry = entries[entries.length - 1];
                console.log('Last entry is file?', lastEntry[0] === 'file');
                
                const token = localStorage.getItem('authToken');
                
                console.log('Sending request to:', `${CONFIG.ADMIN_API_URL}/api/results/admin/upload-with-file`);
                
                const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/results/admin/upload-with-file`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${token}`
                    },
                    body: formDataToSend
                });
                
                console.log('Response status:', response.status);
                console.log('Response headers:', Object.fromEntries(response.headers.entries()));
                
                const responseText = await response.text();
                console.log('Response body:', responseText);
                
                if (!response.ok) {
                    let errorMessage;
                    try {
                        const errorData = JSON.parse(responseText);
                        errorMessage = errorData.message || errorData.error || 'Upload failed';
                        console.error('Error details:', errorData);
                    } catch (e) {
                        errorMessage = responseText || `Server error: ${response.status}`;
                    }
                    
                    throw new Error(errorMessage);
                }
                
                let data;
                try {
                    data = JSON.parse(responseText);
                } catch (e) {
                    data = { success: true };
                }
                
                console.log('Success response:', data);
                
                if (data.success !== false) {
                    alert('Result uploaded successfully!');
                    onClose();
                    if (window.refreshDashboard) {
                        window.refreshDashboard();
                    }
                } else {
                    alert(data.message || 'Upload failed');
                }
            } else {
                console.log('=== UPLOAD WITHOUT FILE ===');
                onSubmit(formData);
            }
        } catch (error) {
            console.error('Upload error:', error);
            alert(`Error uploading result: ${error.message}`);
        } finally {
            setIsSubmitting(false);
        }
    };

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (patientSearchRef.current && !patientSearchRef.current.contains(event.target)) {
                setShowPatientDropdown(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Group appointments by status for better display
    const groupedAppointments = {
        scheduled: patientAppointments.filter(apt => apt.status.toLowerCase() === 'scheduled'),
        other: patientAppointments.filter(apt => apt.status.toLowerCase() !== 'scheduled')
    };

    return (
        <div className="modal">
            <div className="modal-content" style={{ maxWidth: '600px', maxHeight: '90vh', overflowY: 'auto' }}>
                <div className="card-header">
                    <h2 className="card-title">Add Test Result</h2>
                    <button className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>
                        <i className="fas fa-times"></i>
                    </button>
                </div>
                
                <form onSubmit={handleSubmit}>
                    <div className="form-group" ref={patientSearchRef}>
                        <label className="form-label">Patient * <span style={{color: '#6b7280', fontSize: '12px'}}>(Type to search)</span></label>
                        <div style={{ position: 'relative' }}>
                            <input
                                type="text"
                                className="form-input"
                                value={patientSearch}
                                onChange={handlePatientSearchChange}
                                onFocus={() => setShowPatientDropdown(true)}
                                placeholder="Type patient name or email to search..."
                                style={{
                                    paddingRight: selectedPatient ? '40px' : '12px'
                                }}
                                disabled={isSubmitting}
                                required
                            />
                            {selectedPatient && (
                                <div style={{
                                    position: 'absolute',
                                    right: '8px',
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    color: '#10b981'
                                }}>
                                    <i className="fas fa-check"></i>
                                </div>
                            )}
                            
                            {showPatientDropdown && filteredPatients.length > 0 && (
                                <div style={{
                                    position: 'absolute',
                                    top: '100%',
                                    left: 0,
                                    right: 0,
                                    backgroundColor: 'white',
                                    border: '1px solid #e5e7eb',
                                    borderRadius: '6px',
                                    boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
                                    zIndex: 1000,
                                    maxHeight: '200px',
                                    overflowY: 'auto'
                                }}>
                                    {filteredPatients.slice(0, 5).map(patient => (
                                        <div
                                            key={patient.id}
                                            onClick={() => handlePatientSelect(patient)}
                                            style={{
                                                padding: '12px',
                                                cursor: 'pointer',
                                                borderBottom: '1px solid #f3f4f6'
                                            }}
                                            onMouseEnter={(e) => e.target.style.backgroundColor = '#f9fafb'}
                                            onMouseLeave={(e) => e.target.style.backgroundColor = 'white'}
                                        >
                                            <div style={{ fontWeight: '500' }}>
                                                {patient.firstName} {patient.lastName}
                                            </div>
                                            <div style={{ fontSize: '12px', color: '#6b7280' }}>
                                                {patient.email}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {patientAppointments.length > 0 && (
                        <div className="form-group">
                            <label className="form-label">
                                Available Tests 
                                <span style={{color: '#10b981', fontSize: '12px', marginLeft: '8px'}}>
                                    <i className="fas fa-check-circle"></i> Auto-selected most recent
                                </span>
                            </label>
                            <select 
                                className="form-input"
                                value={formData.appointmentId}
                                onChange={(e) => handleAppointmentSelect(e.target.value)}
                                disabled={isSubmitting}
                            >
                                {/* Scheduled appointments first with clear markers */}
                                {groupedAppointments.scheduled.map(appointment => {
                                    const appointmentDate = appointment.scheduledDate || appointment.appointmentDate;
                                    return (
                                        <option key={appointment.id} value={appointment.id}>
                                            🟢 SCHEDULED: {appointment.reason || appointment.testType || 'Test'} - {appointmentDate ? new Date(appointmentDate).toLocaleDateString() : 'No date'}
                                        </option>
                                    );
                                })}
                                
                                {/* Separator if there are scheduled tests */}
                                {groupedAppointments.scheduled.length > 0 && groupedAppointments.other.length > 0 && (
                                    <option disabled>──────────────────────</option>
                                )}
                                
                                {/* Other appointments */}
                                {groupedAppointments.other.map(appointment => {
                                    const appointmentDate = appointment.scheduledDate || appointment.appointmentDate;
                                    return (
                                        <option key={appointment.id} value={appointment.id}>
                                            {appointment.reason || appointment.testType || 'Test'} - {appointmentDate ? new Date(appointmentDate).toLocaleDateString() : 'No date'} 
                                            ({appointment.status})
                                        </option>
                                    );
                                })}
                            </select>
                        </div>
                    )}

                    {formData.appointmentId && (
                        <div className="form-group">
                            <div style={{ 
                                display: 'flex', 
                                alignItems: 'center', 
                                padding: '12px', 
                                backgroundColor: '#f0f9ff', 
                                border: '1px solid #0ea5e9', 
                                borderRadius: '6px' 
                            }}>
                                <input
                                    type="checkbox"
                                    id="markCompleted"
                                    checked={formData.markAppointmentCompleted}
                                    onChange={(e) => setFormData({...formData, markAppointmentCompleted: e.target.checked})}
                                    disabled={isSubmitting}
                                    style={{ marginRight: '8px' }}
                                />
                                <label htmlFor="markCompleted" style={{ 
                                    cursor: 'pointer', 
                                    fontWeight: '500',
                                    color: '#0ea5e9',
                                    margin: 0
                                }}>
                                    <i className="fas fa-check-circle" style={{ marginRight: '6px' }}></i>
                                    Mark this appointment as completed
                                </label>
                            </div>
                            <small style={{color: '#6b7280', fontSize: '12px', marginTop: '4px', display: 'block'}}>
                                This will update the appointment status to "Completed" when you save the result
                            </small>
                        </div>
                    )}

                    <div className="form-row">
                        <div className="form-group">
                            <label className="form-label">Test Type *</label>
                            <input
                                type="text"
                                className="form-input"
                                value={formData.testType}
                                onChange={(e) => setFormData({...formData, testType: e.target.value})}
                                placeholder="Enter test type (e.g., Blood Sugar, ECG, Chest X-Ray)"
                                disabled={isSubmitting}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label className="form-label">Test Date</label>
                            <input
                                type="date"
                                className="form-input"
                                value={formData.testDate}
                                onChange={(e) => setFormData({...formData, testDate: e.target.value})}
                                disabled={isSubmitting}
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label">Test Result *</label>
                        <textarea
                            className="form-input"
                            rows="3"
                            value={formData.result}
                            onChange={(e) => setFormData({...formData, result: e.target.value})}
                            placeholder="Enter test result (e.g., 120 mg/dL, Normal, Clear lungs, detailed analysis...)"
                            disabled={isSubmitting}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label">
                            Upload Documents/Images 
                            <span style={{color: '#6b7280', fontSize: '12px'}}>(Optional - Images, PDFs, Reports)</span>
                        </label>
                        <div style={{ 
                            border: '2px dashed #e5e7eb', 
                            borderRadius: '6px', 
                            padding: '20px', 
                            textAlign: 'center',
                            cursor: isSubmitting ? 'not-allowed' : 'pointer',
                            opacity: isSubmitting ? 0.6 : 1,
                            transition: 'border-color 0.2s'
                        }}
                        onClick={() => !isSubmitting && fileInputRef.current?.click()}
                        onDragOver={(e) => {
                            if (isSubmitting) return;
                            e.preventDefault();
                            e.currentTarget.style.borderColor = '#3b82f6';
                        }}
                        onDragLeave={(e) => {
                            e.currentTarget.style.borderColor = '#e5e7eb';
                        }}
                        onDrop={(e) => {
                            if (isSubmitting) return;
                            e.preventDefault();
                            e.currentTarget.style.borderColor = '#e5e7eb';
                            const files = e.dataTransfer.files;
                            if (files.length > 0) {
                                handleFileUpload({ target: { files } });
                            }
                        }}>
                            <i className="fas fa-cloud-upload-alt" style={{ fontSize: '24px', color: '#6b7280', marginBottom: '8px' }}></i>
                            <p style={{ margin: 0, color: '#6b7280' }}>
                                Click to upload or drag and drop files here
                            </p>
                            <small style={{ color: '#9ca3af' }}>
                                Supported: JPG, PNG, PDF, TXT (Max 5MB each)
                            </small>
                        </div>
                        <input
                            ref={fileInputRef}
                            type="file"
                            multiple
                            accept="image/*,.pdf,.txt"
                            style={{ display: 'none' }}
                            onChange={handleFileUpload}
                            disabled={isSubmitting}
                        />
                    </div>

                    {uploadedFiles.length > 0 && (
                        <div className="form-group">
                            <label className="form-label">Uploaded Files</label>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {uploadedFiles.map(file => (
                                    <div key={file.id} style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        padding: '8px 12px',
                                        backgroundColor: '#f9fafb',
                                        borderRadius: '6px',
                                        border: '1px solid #e5e7eb'
                                    }}>
                                        <i className={`fas ${file.type.startsWith('image/') ? 'fa-image' : file.type === 'application/pdf' ? 'fa-file-pdf' : 'fa-file-text'}`} 
                                           style={{ marginRight: '8px', color: '#6b7280' }}></i>
                                        <div style={{ flex: 1, fontSize: '14px' }}>
                                            <div style={{ fontWeight: '500' }}>{file.name}</div>
                                            <div style={{ color: '#6b7280', fontSize: '12px' }}>
                                                {(file.size / 1024).toFixed(1)} KB
                                            </div>
                                        </div>
                                        {uploadProgress[file.id] !== undefined && uploadProgress[file.id] < 100 && (
                                            <div style={{ marginRight: '8px', color: '#3b82f6' }}>
                                                {uploadProgress[file.id]}%
                                            </div>
                                        )}
                                        <button
                                            type="button"
                                            onClick={() => removeFile(file.id)}
                                            disabled={isSubmitting}
                                            style={{
                                                background: 'none',
                                                border: 'none',
                                                color: '#ef4444',
                                                cursor: isSubmitting ? 'not-allowed' : 'pointer',
                                                padding: '4px',
                                                opacity: isSubmitting ? 0.5 : 1
                                            }}
                                        >
                                            <i className="fas fa-times"></i>
                                        </button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    <div className="form-row">
                        <div className="form-group">
                            <label className="form-label">Status</label>
                            <select 
                                className="form-input"
                                value={formData.status}
                                onChange={(e) => setFormData({...formData, status: e.target.value})}
                                disabled={isSubmitting}
                            >
                                <option value="NORMAL">Normal</option>
                                <option value="ABNORMAL">Abnormal</option>
                                <option value="PENDING">Pending Review</option>
                            </select>
                        </div>
                        <div className="form-group">
                            <label className="form-label">Doctor/Technician</label>
                            <input
                                type="text"
                                className="form-input"
                                value={formData.doctorName}
                                onChange={(e) => setFormData({...formData, doctorName: e.target.value})}
                                placeholder="Dr. Smith, Lab Tech John"
                                disabled={isSubmitting}
                            />
                        </div>
                    </div>

                    <div className="form-group">
                        <label className="form-label">Clinical Notes</label>
                        <textarea
                            className="form-input"
                            rows="3"
                            value={formData.notes}
                            onChange={(e) => setFormData({...formData, notes: e.target.value})}
                            placeholder="Additional observations, recommendations, or follow-up instructions..."
                            disabled={isSubmitting}
                        />
                    </div>

                    <div style={{display: 'flex', gap: '12px', justifyContent: 'flex-end', marginTop: '24px'}}>
                        <button 
                            type="button" 
                            className="btn btn-secondary" 
                            onClick={onClose}
                            disabled={isSubmitting}
                        >
                            Cancel
                        </button>
                        <button 
                            type="submit" 
                            className="btn btn-primary"
                            disabled={isSubmitting}
                        >
                            {isSubmitting ? (
                                <>
                                    <i className="fas fa-spinner fa-spin" style={{ marginRight: '8px' }}></i>
                                    Uploading...
                                </>
                            ) : (
                                <>
                                    <i className="fas fa-save" style={{ marginRight: '8px' }}></i>
                                    Save Result
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};