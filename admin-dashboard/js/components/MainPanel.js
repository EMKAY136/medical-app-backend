const MainPanel = ({ patients, searchQuery, setSearchQuery, setSelectedPatient, setShowModal, onUpdateAppointment }) => {
    return (
        <div className="content-card">
            <div className="card-header">
                <h3 className="card-title">Recent Patients</h3>
            </div>
            <div className="card-content">
                <div className="patient-search" style={{marginBottom: '20px'}}>
                    <input
                        type="text"
                        placeholder="Search patients..."
                        className="form-input"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>
                <div className="patient-list">
                    {patients.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">👥</div>
                            <p>No patients found</p>
                        </div>
                    ) : (
                        patients.map(patient => (
                            <PatientItem 
                                key={patient.id}
                                patient={patient}
                                setSelectedPatient={setSelectedPatient}
                                setShowModal={setShowModal}
                                onUpdateAppointment={onUpdateAppointment}
                            />
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};