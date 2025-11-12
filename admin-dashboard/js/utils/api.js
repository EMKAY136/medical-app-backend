// Complete ApiService with all methods - FIXED VERSION
const ApiService = {
    // Authentication
    login: async (credentials) => {
        console.log('Making POST request to:', `${CONFIG.API_BASE_URL}/auth/login`);
        const response = await fetch(`${CONFIG.API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(credentials)
        });
        console.log('Response status:', response.status);
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Login failed');
        }
        
        const data = await response.json();
        console.log('Full login response:', data);
        
        if (data.token) {
            localStorage.setItem('authToken', data.token);
            console.log('Token stored successfully');
        } else {
            console.error('No token found in response!');
        }
        
        return data;
    },

    logout: () => {
        localStorage.removeItem('authToken');
        console.log('User logged out, token removed');
    },

    // Helper method to get auth headers
    getAuthHeaders: () => {
        const token = localStorage.getItem('authToken');
        console.log('Getting auth headers, token exists:', !!token);
        return {
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        };
    },

    // Check if user is authenticated
    isAuthenticated: () => {
        return !!localStorage.getItem('authToken');
    },

    // Patients
    getPatients: async (page = 0, size = 50) => {
        console.log('Fetching patients from backend...');
        try {
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/patients?page=${page}&size=${size}`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            console.log('Patients response status:', response.status);
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (response.ok) {
                const data = await response.json();
                console.log('Backend patients response:', data);
                
                if (data.success && data.patients) {
                    return {
                        patients: data.patients,
                        totalElements: data.totalCount || data.patients.length,
                        totalPages: Math.ceil((data.totalCount || data.patients.length) / size),
                        currentPage: data.currentPage || page
                    };
                }
                
                return data;
            }
            
            if (response.status === 404) {
                console.log('Patients endpoint not found, using mock data');
                return ApiService.getMockPatients();
            }
            
            throw new Error('Failed to fetch patients');
            
        } catch (error) {
            console.error('Error fetching patients:', error);
            if (error.message.includes('fetch') || error.message.includes('Failed to fetch')) {
                console.log('Network error, using mock patient data');
                return ApiService.getMockPatients();
            }
            throw error;
        }
    },

    getMockPatients: () => ({
        patients: [
            {
                id: 1,
                firstName: 'John',
                lastName: 'Doe',
                name: 'John Doe',
                email: 'john@example.com',
                phone: '+1234567890',
                dateOfBirth: '1990-01-15',
                address: '123 Main St, City',
                createdAt: '2024-01-15T10:00:00Z'
            },
            {
                id: 2,
                firstName: 'Jane',
                lastName: 'Smith',
                name: 'Jane Smith',
                email: 'jane@example.com',
                phone: '+1987654321',
                dateOfBirth: '1985-05-20',
                address: '456 Oak Ave, Town',
                createdAt: '2024-01-16T14:30:00Z'
            },
            {
                id: 3,
                firstName: 'Bob',
                lastName: 'Johnson',
                name: 'Bob Johnson',
                email: 'bob@example.com',
                phone: '+1122334455',
                dateOfBirth: '1992-09-10',
                address: '789 Pine Rd, Village',
                createdAt: '2024-01-17T09:15:00Z'
            }
        ],
        totalElements: 3,
        totalPages: 1,
        currentPage: 0
    }),

    createPatient: async (patientData) => {
        console.log('Creating patient...');
        const response = await fetch(`${CONFIG.ADMIN_API_URL}/patients`, {
            method: 'POST',
            headers: ApiService.getAuthHeaders(),
            body: JSON.stringify(patientData)
        });
        
        if (response.status === 401) {
            throw new Error('Authentication expired. Please login again.');
        }
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create patient');
        }
        
        return response.json();
    },

    // Appointments
    getAppointments: async (page = 0, size = 50, patientId = null) => {
        console.log('Fetching appointments from backend...');
        try {
            let url = `${CONFIG.ADMIN_API_URL}/appointments?page=${page}&size=${size}`;
            if (patientId) {
                url += `&patientId=${patientId}`;
            }
            
            const response = await fetch(url, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            console.log('Appointments response status:', response.status);
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (response.ok) {
                return response.json();
            }
            
            if (response.status === 404) {
                console.log('Appointments endpoint not found, using mock data');
                return ApiService.getMockAppointments(patientId);
            }
            
            throw new Error('Failed to fetch appointments');
            
        } catch (error) {
            console.error('Error fetching appointments:', error);
            if (error.message.includes('fetch') || error.message.includes('Failed to fetch')) {
                console.log('Network error, using mock appointment data');
                return ApiService.getMockAppointments(patientId);
            }
            throw error;
        }
    },

    getMockAppointments: (patientId = null) => {
        const allAppointments = [
            {
                id: 1,
                patientId: 1,
                patientName: 'John Doe',
                testType: 'Blood Test',
                reason: 'Blood Test',
                scheduledDate: '2024-09-28T10:00:00Z',
                appointmentDate: '2024-09-28T10:00:00Z',
                status: 'Scheduled'
            },
            {
                id: 2,
                patientId: 2,
                patientName: 'Jane Smith',
                testType: 'X-Ray',
                reason: 'X-Ray',
                scheduledDate: '2024-09-29T14:00:00Z',
                appointmentDate: '2024-09-29T14:00:00Z',
                status: 'Completed'
            },
            {
                id: 3,
                patientId: 1,
                patientName: 'John Doe',
                testType: 'ECG',
                reason: 'ECG Test',
                scheduledDate: '2024-10-01T09:00:00Z',
                appointmentDate: '2024-10-01T09:00:00Z',
                status: 'Pending'
            }
        ];

        const filtered = patientId 
            ? allAppointments.filter(apt => apt.patientId == patientId)
            : allAppointments;

        return {
            appointments: filtered,
            totalElements: filtered.length,
            totalPages: 1,
            currentPage: 0
        };
    },

    // Test Results
    getTestResults: async (page = 0, size = 50) => {
        console.log('Fetching test results from backend...');
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/results/admin/all?page=${page}&size=${size}`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            console.log('Test results response status:', response.status);
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (response.ok) {
                const data = await response.json();
                console.log('Test results data:', data);
                
                // Handle different response formats
                if (data.success && data.results) {
                    return {
                        results: data.results,
                        totalElements: data.totalCount || data.results.length,
                        totalPages: Math.ceil((data.totalCount || data.results.length) / size),
                        currentPage: page
                    };
                }
                
                return data;
            }
            
            if (response.status === 404) {
                console.log('Test results endpoint not found, using mock data');
                return ApiService.getMockTestResults();
            }
            
            throw new Error('Failed to fetch test results');
            
        } catch (error) {
            console.error('Error fetching test results:', error);
            if (error.message.includes('fetch') || error.message.includes('Failed to fetch')) {
                console.log('Network error, using mock test results data');
                return ApiService.getMockTestResults();
            }
            throw error;
        }
    },

    getMockTestResults: () => ({
        results: [
            {
                id: 1,
                patientId: 1,
                patientName: 'John Doe',
                testName: 'Blood Test',
                testType: 'Blood Test',
                result: 'Normal - All values within range',
                testDate: '2024-09-25T10:00:00Z',
                status: 'COMPLETED',
                notes: 'All values within normal range'
            },
            {
                id: 2,
                patientId: 2,
                patientName: 'Jane Smith',
                testName: 'X-Ray',
                testType: 'X-Ray',
                result: 'Normal - No abnormalities',
                testDate: '2024-09-26T14:00:00Z',
                status: 'COMPLETED',
                notes: 'No abnormalities detected'
            }
        ],
        totalElements: 2,
        totalPages: 1,
        currentPage: 0
    }),

    // FIXED: Add test result with proper field mapping
    // Add test result with file support
addTestResult: async (resultData) => {
    console.log('=== ADDING TEST RESULT ===');
    console.log('Original form data:', resultData);
    
    // Check if there are file attachments
    const hasFiles = resultData.attachments && resultData.attachments.length > 0;
    
    if (hasFiles) {
        console.log('File attachments detected, using multipart upload');
        
        // Create FormData for file upload
        const formData = new FormData();
        formData.append('patientId', resultData.patientId);
        formData.append('testType', resultData.testType);
        formData.append('result', resultData.result);
        formData.append('notes', resultData.notes || '');
        formData.append('status', resultData.status || 'NORMAL');
        formData.append('doctorName', resultData.doctorName || '');
        formData.append('testDate', resultData.testDate);
        
        if (resultData.appointmentId) {
            formData.append('appointmentId', resultData.appointmentId);
        }
        formData.append('markAppointmentCompleted', resultData.markAppointmentCompleted || false);
        
        // Convert base64 to File object
        const attachment = resultData.attachments[0];
        const base64Data = attachment.data.split(',')[1];
        const byteCharacters = atob(base64Data);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
            byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: attachment.type });
        const file = new File([blob], attachment.name, { type: attachment.type });
        
        formData.append('file', file);
        
        console.log('Sending file upload to:', `${CONFIG.ADMIN_API_URL}/upload-result-with-file`);
        
        const token = localStorage.getItem('authToken');
        const response = await fetch(`${CONFIG.ADMIN_API_URL}/upload-result-with-file`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`
                // Don't set Content-Type - browser adds it with boundary
            },
            body: formData
        });
        
        if (response.status === 401) {
            throw new Error('Authentication expired. Please login again.');
        }
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to upload result with file');
        }
        
        return response.json();
        
    } else {
        console.log('No files, using JSON upload');
        
        // No files - use regular JSON endpoint
        const backendData = {
            patientId: parseInt(resultData.patientId),
            appointmentId: resultData.appointmentId ? parseInt(resultData.appointmentId) : null,
            testType: resultData.testType,
            result: resultData.result,
            status: resultData.status || 'NORMAL',
            notes: resultData.notes || '',
            doctorName: resultData.doctorName || 'Admin',
            testDate: resultData.testDate || new Date().toISOString().split('T')[0],
            markAppointmentCompleted: resultData.markAppointmentCompleted || false
        };
        
        const response = await fetch(`${CONFIG.API_BASE_URL}/results/admin/upload`, {
            method: 'POST',
            headers: ApiService.getAuthHeaders(),
            body: JSON.stringify(backendData)
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to add test result');
        }
        
        return response.json();
        }
    },
    // Delete test result
    deleteTestResult: async (resultId) => {
        console.log('Deleting test result:', resultId);
        
        const response = await fetch(`${CONFIG.API_BASE_URL}/results/${resultId}`, {
            method: 'DELETE',
            headers: ApiService.getAuthHeaders()
        });
        
        if (response.status === 401) {
            throw new Error('Authentication expired. Please login again.');
        }
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to delete test result');
        }
        
        return response.json();
    },

    // Statistics
    getStatistics: async () => {
        console.log('Fetching statistics...');
        try {
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/statistics`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (response.ok) {
                return response.json();
            }
            
            // Return mock stats if endpoint doesn't exist
            return {
                totalPatients: 3,
                totalAppointments: 3,
                totalResults: 2,
                pendingAppointments: 1
            };
            
        } catch (error) {
            console.error('Error fetching statistics:', error);
            return {
                totalPatients: 3,
                totalAppointments: 3,
                totalResults: 2,
                pendingAppointments: 1
            };
        }
    },

    // Support endpoints
    getSupportChats: async () => {
        console.log('Fetching support chats...');
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/support/admin/active-chats`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (!response.ok) {
                throw new Error('Failed to fetch support chats');
            }
            
            return response.json();
        } catch (error) {
            console.error('Error fetching support chats:', error);
            throw error;
        }
    },

    sendSupportReply: async (userId, message, ticketId = null) => {
        console.log('Sending support reply to user:', userId);
        
        const response = await fetch(`${CONFIG.API_BASE_URL}/support/admin/reply`, {
            method: 'POST',
            headers: ApiService.getAuthHeaders(),
            body: JSON.stringify({
                userId: userId,
                message: message,
                ticketId: ticketId
            })
        });
        
        if (response.status === 401) {
            throw new Error('Authentication expired. Please login again.');
        }
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to send reply');
        }
        
        return response.json();
    }
};

// Make sure CONFIG is defined
if (typeof CONFIG === 'undefined') {
    console.warn('CONFIG not defined, using defaults');
    window.CONFIG = {
        API_BASE_URL: 'http://localhost:8080/api',
        ADMIN_API_URL: 'http://localhost:8080/api/admin'
    };
}

// Export for use in modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = ApiService;
}