// js/utils/api.js
// Note: CONFIG is loaded from constants.js which must be loaded before this file

const ApiService = {
    // Authentication - FIXED to accept email and password separately
    login: async (email, password) => {
        console.log('Making POST request to:', `${CONFIG.API_BASE_URL}/auth/login`);
        console.log('Login credentials:', { email, passwordLength: password?.length });
        
        const response = await fetch(`${CONFIG.API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }) // Pack into object
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
            }
        ],
        totalElements: 1,
        totalPages: 1,
        currentPage: 0
    }),

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
            
            throw new Error('Failed to fetch appointments');
            
        } catch (error) {
            console.error('Error fetching appointments:', error);
            throw error;
        }
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
            
            throw new Error('Failed to fetch test results');
            
        } catch (error) {
            console.error('Error fetching test results:', error);
            throw error;
        }
    },

    // Add test result
    addTestResult: async (resultData) => {
        console.log('=== ADDING TEST RESULT ===');
        
        const backendData = {
            patientId: parseInt(resultData.patientId),
            testType: resultData.testType,
            result: resultData.result,
            status: resultData.status || 'NORMAL',
            notes: resultData.notes || '',
            doctorName: resultData.doctorName || 'Admin',
            testDate: resultData.testDate || new Date().toISOString().split('T')[0]
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
    },

    // Statistics
    getStatistics: async () => {
        console.log('Fetching statistics...');
        try {
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/stats`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (response.ok) {
                return response.json();
            }
            
            return {
                totalPatients: 0,
                totalAppointments: 0,
                totalResults: 0,
                pendingAppointments: 0
            };
            
        } catch (error) {
            console.error('Error fetching statistics:', error);
            return {
                totalPatients: 0,
                totalAppointments: 0,
                totalResults: 0,
                pendingAppointments: 0
            };
        }
    }
};

// Make ApiService globally available
window.ApiService = ApiService;