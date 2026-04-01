// js/utils/api.js
const ApiService = {

    // ✅ Authentication
    login: async (email, password) => {
        const loginUrl = `${CONFIG.API_BASE_URL}/api/auth/login`;

        console.log('========== LOGIN REQUEST ==========');
        console.log('📍 URL:', loginUrl);
        console.log('📧 Email:', email);
        console.log('🌐 Frontend origin:', window.location.origin);

        try {
            const response = await fetch(loginUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                },
                credentials: 'include',
                body: JSON.stringify({
                    email: email.trim(),
                    password: password
                })
            });

            console.log('📊 Response Status:', response.status, response.statusText);

            if (response.status === 0) {
                throw new Error('CORS Error: Backend CORS configuration may be incorrect');
            }

            if (!response.ok) {
                let errorData = {};
                try {
                    errorData = await response.json();
                } catch (e) {
                    errorData = { message: response.statusText };
                }
                throw new Error(errorData.message || errorData.error || `Login failed: ${response.status}`);
            }

            const data = await response.json();

            if (!data.token) {
                throw new Error('No authentication token received from server');
            }

            localStorage.setItem('authToken', data.token);
            if (data.user) {
                localStorage.setItem('user', JSON.stringify(data.user));
            }
            localStorage.setItem('loginTime', new Date().toISOString());

            console.log('✅ Login successful! Token stored.');
            console.log('===================================');

            return {
                success: true,
                token: data.token,
                user: data.user,
                message: 'Login successful'
            };

        } catch (error) {
            console.error('❌ Login error:', error.message);

            if (error.message.includes('CORS')) {
                console.error('➜ Add this origin to CORS_ORIGINS:', window.location.origin);
            } else if (error.message.includes('Failed to fetch')) {
                console.error('➜ Backend may be down. Check:', CONFIG.API_BASE_URL + '/actuator/health');
            }

            throw error;
        }
    },

    logout: () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('user');
        localStorage.removeItem('loginTime');
        console.log('✅ Logged out, tokens cleared');
    },

    getToken: () => localStorage.getItem('authToken'),

    getUser: () => {
        const userJson = localStorage.getItem('user');
        return userJson ? JSON.parse(userJson) : null;
    },

    isAuthenticated: () => {
        const token = localStorage.getItem('authToken');
        return !!token && token.length > 0;
    },

    getAuthHeaders: () => {
        const token = ApiService.getToken();
        return {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        };
    },

    // Generic API request
    apiRequest: async (endpoint, options = {}) => {
        const url = `${CONFIG.API_BASE_URL}${endpoint}`;
        const headers = {
            ...ApiService.getAuthHeaders(),
            ...options.headers,
        };

        try {
            const response = await fetch(url, {
                ...options,
                headers,
                credentials: 'include',
            });

            if (response.status === 401) {
                ApiService.logout();
                throw new Error('Session expired. Please login again.');
            }

            if (!response.ok) {
                const error = await response.json().catch(() => ({ message: response.statusText }));
                throw new Error(error.message || `API Error: ${response.status}`);
            }

            return response.json();
        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    },

    // Patients
    getPatients: async (page = 0, size = 50) => {
        console.log('📥 Fetching patients...');
        try {
            // ✅ Fixed typo: ADMIN_API_UR → ADMIN_API_URL
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/patients?page=${page}&size=${size}`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });

            console.log('Patients response status:', response.status);

            if (response.status === 401) throw new Error('Authentication expired. Please login again.');

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
        console.log('📥 Fetching appointments...');
        try {
            let url = `${CONFIG.ADMIN_API_URL}/api/admin/appointments?page=${page}&size=${size}`;
            if (patientId) url += `&patientId=${patientId}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });

            console.log('Appointments response status:', response.status);

            if (response.status === 401) throw new Error('Authentication expired. Please login again.');
            if (response.ok) return response.json();

            throw new Error('Failed to fetch appointments');

        } catch (error) {
            console.error('Error fetching appointments:', error);
            throw error;
        }
    },

    // Test Results
    getTestResults: async (page = 0, size = 50) => {
        console.log('📥 Fetching test results...');
        try {
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/admin/test-results?page=${page}&size=${size}`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });

            console.log('Test results response status:', response.status);

            if (response.status === 401) throw new Error('Authentication expired. Please login again.');

            if (response.ok) {
                const data = await response.json();

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
                return ApiService.getMockTestResults();
            }
            throw error;
        }
    },

    getMockTestResults: () => ({
        results: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0
    }),

    // Add test result
    addTestResult: async (resultData) => {
        console.log('📤 Adding test result:', resultData);

        const backendData = {
            patientId: parseInt(resultData.patientId),
            testType: resultData.testType,
            result: resultData.result,
            status: resultData.status || 'NORMAL',
            notes: resultData.notes || '',
            doctorName: resultData.doctorName || 'Admin',
            testDate: resultData.testDate || new Date().toISOString().split('T')[0]
        };

        // ✅ Fixed: added /api prefix to match other endpoints
        const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/results/admin/upload`, {
            method: 'POST',
            headers: ApiService.getAuthHeaders(),
            body: JSON.stringify(backendData)
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: response.statusText }));
            throw new Error(error.message || 'Failed to add test result');
        }

        return response.json();
    },

    // Statistics
    getStatistics: async () => {
        console.log('📊 Fetching statistics...');
        try {
            // ✅ Fixed: added /api prefix
            const response = await fetch(`${CONFIG.ADMIN_API_URL}/api/stats`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders()
            });

            if (response.status === 401) throw new Error('Authentication expired. Please login again.');
            if (response.ok) return response.json();

            return { totalPatients: 0, totalAppointments: 0, totalResults: 0, pendingAppointments: 0 };

        } catch (error) {
            console.error('Error fetching statistics:', error);
            return { totalPatients: 0, totalAppointments: 0, totalResults: 0, pendingAppointments: 0 };
        }
    }
};

window.ApiService = ApiService;
console.log('✅ ApiService initialized');
console.log('   Backend URL:', CONFIG.API_BASE_URL);
console.log('   Admin API URL:', CONFIG.ADMIN_API_URL);