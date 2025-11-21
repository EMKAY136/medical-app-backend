// js/utils/api.js
// Fixed API configuration with CORS debugging
// Note: CONFIG must be defined in constants.js before this file loads

const ApiService = {
    
    // ✅ Authentication - Fixed with CORS debugging
    login: async (email, password) => {
        const loginUrl = `${CONFIG.API_BASE_URL}api/auth/login`;
        
        console.log('========== LOGIN REQUEST ==========');
        console.log('📍 URL:', loginUrl);
        console.log('📧 Email:', email);
        console.log('🔐 Password length:', password?.length || 0);
        console.log('🌐 Frontend origin:', window.location.origin);
        console.log('🌐 Frontend URL:', window.location.href);
        
        try {
            const response = await fetch(loginUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                },
                credentials: 'include', // Include cookies if needed
                body: JSON.stringify({
                    email: email.trim(),
                    password: password
                })
            });

            console.log('📊 Response Status:', response.status, response.statusText);
            
            // Log CORS and auth headers
            console.log('📋 Response Headers (CORS & Auth):');
            const headersToLog = [
                'access-control-allow-origin',
                'access-control-allow-credentials',
                'access-control-allow-methods',
                'access-control-allow-headers',
                'authorization',
                'content-type'
            ];
            
            headersToLog.forEach(header => {
                const value = response.headers.get(header);
                if (value) {
                    console.log(`  ${header}: ${value}`);
                }
            });

            // Handle response status
            if (response.status === 0) {
                console.error('❌ CORS Error: Request blocked by browser');
                console.error('🚨 This means the backend is not sending CORS headers');
                throw new Error('CORS Error: Backend CORS configuration may be incorrect');
            }

            if (!response.ok) {
                let errorData = {};
                try {
                    errorData = await response.json();
                } catch (e) {
                    errorData = { message: response.statusText };
                }
                
                console.error('❌ Login failed:');
                console.error('  Status:', response.status);
                console.error('  Error:', errorData);
                
                const errorMessage = errorData.message || errorData.error || `Login failed: ${response.status}`;
                throw new Error(errorMessage);
            }

            const data = await response.json();
            
            console.log('✅ Login successful!');
            console.log('🔑 Token present:', !!data.token);
            console.log('👤 User:', data.user?.email || 'Unknown');
            console.log('===================================');
            
            // Validate response has token
            if (!data.token) {
                console.error('❌ Response has no token!');
                console.error('Response data:', data);
                throw new Error('No authentication token received from server');
            }
            
            // Store token and user data
            localStorage.setItem('authToken', data.token);
            if (data.user) {
                localStorage.setItem('user', JSON.stringify(data.user));
            }
            localStorage.setItem('loginTime', new Date().toISOString());
            
            console.log('✅ Token stored in localStorage');
            console.log('✅ User data stored');
            
            return {
                success: true,
                token: data.token,
                user: data.user,
                message: 'Login successful'
            };

        } catch (error) {
            console.error('❌ Login failed with exception:');
            console.error('  Message:', error.message);
            console.error('  Stack:', error.stack);
            
            // Provide specific error guidance
            if (error.message.includes('CORS')) {
                console.error('\n🚨 CORS Problem Detected:');
                console.error('  Frontend origin:', window.location.origin);
                console.error('  Backend URL:', CONFIG.API_BASE_URL);
                console.error('  ➜ Make sure backend CORS_ORIGINS includes:', window.location.origin);
                console.error('  ➜ Check Railway environment variables');
                console.error('  ➜ Restart backend after changing CORS_ORIGINS');
            } else if (error.message.includes('Failed to fetch')) {
                console.error('\n🚨 Network Problem Detected:');
                console.error('  Backend might be down or unreachable');
                console.error('  Backend URL:', CONFIG.API_BASE_URL);
                console.error('  ➜ Check Railway deployment status');
                console.error('  ➜ Check if backend service is running');
                console.error('  ➜ Try the health endpoint: ' + CONFIG.API_BASE_URL + '/health');
            } else if (error.message.includes('401') || error.message.includes('Invalid')) {
                console.error('\n🚨 Authentication Problem:');
                console.error('  Check email and password are correct');
                console.error('  Check user exists in database');
            }
            
            throw error;
        }
    },

    logout: () => {
        console.log('👋 Logging out...');
        localStorage.removeItem('authToken');
        localStorage.removeItem('user');
        localStorage.removeItem('loginTime');
        console.log('✅ User logged out, tokens cleared');
    },

    // Get stored token
    getToken: () => {
        return localStorage.getItem('authToken');
    },

    // Get stored user data
    getUser: () => {
        const userJson = localStorage.getItem('user');
        return userJson ? JSON.parse(userJson) : null;
    },

    // Check if authenticated
    isAuthenticated: () => {
        const token = localStorage.getItem('authToken');
        return !!token && token.length > 0;
    },

    // Helper method to get auth headers
    getAuthHeaders: () => {
        const token = ApiService.getToken();
        return {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        };
    },

    // Generic API request with error handling
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
                console.warn('⚠️ Unauthorized - token expired or invalid');
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
        console.log('📥 Fetching appointments...');
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
        console.log('📥 Fetching test results...');
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
        console.log('📊 Fetching statistics...');
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
console.log('✅ ApiService initialized');
console.log('   Backend URL:', CONFIG.API_BASE_URL);
console.log('   Admin API URL:', CONFIG.ADMIN_API_URL);