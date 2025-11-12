const { useState, useEffect } = React;

const ReportsView = ({ testResults, setShowModal }) => {
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterType, setFilterType] = useState('all');

    const filteredResults = testResults.filter(result => {
        const statusMatch = filterStatus === 'all' || result.status.toLowerCase() === filterStatus.toLowerCase();
        const typeMatch = filterType === 'all' || result.testType === filterType;
        return statusMatch && typeMatch;
    });

    const testTypes = [...new Set(testResults.map(r => r.testType))];

    return (
        <div className="content-card">
            <div className="card-header">
                <h2 className="card-title">Test Results & Reports</h2>
                <button 
                    className="btn btn-primary"
                    onClick={() => setShowModal('add-result')}
                >
                    <i className="fas fa-plus"></i>
                    Add Result
                </button>
            </div>
            <div className="card-content">
                <div style={{display: 'flex', gap: '12px', marginBottom: '20px'}}>
                    <select 
                        className="form-input"
                        value={filterStatus}
                        onChange={(e) => setFilterStatus(e.target.value)}
                        style={{width: 'auto'}}
                    >
                        <option value="all">All Results</option>
                        <option value="normal">Normal</option>
                        <option value="abnormal">Abnormal</option>
                        <option value="pending">Pending</option>
                    </select>
                    <select 
                        className="form-input"
                        value={filterType}
                        onChange={(e) => setFilterType(e.target.value)}
                        style={{width: 'auto'}}
                    >
                        <option value="all">All Tests</option>
                        {testTypes.map(type => (
                            <option key={type} value={type}>{type}</option>
                        ))}
                    </select>
                </div>

                <div className="patient-list">
                    {filteredResults.map(result => (
                        <div key={result.id} className="appointment-item">
                            <div className="appointment-info">
                                <h4>{result.patientName || 'Unknown Patient'}</h4>
                                <p>{result.testType} - {result.result}</p>
                                <p style={{fontSize: '14px', color: '#6b7280'}}>
                                    {new Date(result.date || result.createdAt).toLocaleDateString()}
                                </p>
                                {result.notes && (
                                    <p style={{fontSize: '12px', color: '#6b7280', marginTop: '4px'}}>
                                        {result.notes}
                                    </p>
                                )}
                            </div>
                            <div className={`patient-status status-${result.status.toLowerCase()}`}>
                                {result.status}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};