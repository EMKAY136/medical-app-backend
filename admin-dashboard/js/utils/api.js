// js/utils/api.js
// API Service matching your CONFIG structure

const ApiService = {
    // Authentication
    login: async (credentials) => {
        console.log('🔐 Making login request to:', `${CONFIG.API_BASE_URL}/api/auth/login`);
        console.log('Credentials:', { email: credentials.email, passwordLength: credentials.password?.length });
        
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/api/auth/login`, {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(credentials),
                credentials: 'include'
            });
            
            console.log('Login response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                let errorMessage = 'Login failed';
                
                try {
                    const error = JSON.parse(errorText);
                    errorMessage = error.message || errorMessage;
                } catch (e) {
                    errorMessage = errorText || errorMessage;
                }
                
                console.error('❌ Login error:', errorMessage);
                throw new Error(errorMessage);
            }
            
            const data = await response.json();
            console.log('✅ Login successful, response:', data);
            
            if (data.token) {
                localStorage.setItem('authToken', data.token);
                console.log('✅ Token stored successfully');
            } else {
                console.error('⚠️ No token found in response!');
            }
            
            if (data.user) {
                localStorage.setItem('userId', data.user.id);
                localStorage.setItem('userEmail', data.user.email);
                localStorage.setItem('userRole', data.user.role);
                console.log('✅ User data stored');
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Login error:', error);
            throw error;
        }
    },

    logout: () => {
        localStorage.removeItem('authToken');
        localStorage.removeItem('userId');
        localStorage.removeItem('userEmail');
        localStorage.removeItem('userRole');
        console.log('✅ User logged out, all data cleared');
    },

    // Helper method to get auth headers
    getAuthHeaders: () => {
        const token = localStorage.getItem('authToken');
        console.log('Getting auth headers, token exists:', !!token);
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    },

    // Check if user is authenticated
    isAuthenticated: () => {
        const hasToken = !!localStorage.getItem('authToken');
        console.log('isAuthenticated:', hasToken);
        return hasToken;
    },

    // Validate token
    validateToken: async () => {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}/api/auth/validate-token`, {
                method: 'GET',
                headers: ApiService.getAuthHeaders(),
                credentials: 'include'
            });
            
            if (response.ok) {
                const data = await response.json();
                return data.valid === true;
            }
            
            return false;
        } catch (error) {
            console.error('Token validation error:', error);
            return false;
        }
    },

    // Patients
    getPatients: async (page = 0, size = 50) => {
        console.log(`📋 Fetching patients (page: ${page}, size: ${size})`);
        try {
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/patients?page=${page}&size=${size}`,
                {
                    method: 'GET',
                    headers: ApiService.getAuthHeaders(),
                    credentials: 'include'
                }
            );
            
            console.log('Patients response status:', response.status);
            
            if (response.status === 401) {
                console.error('❌ Authentication expired');
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('✅ Patients loaded:', data);
            
            // Handle different response formats
            if (data.success && data.patients) {
                return {
                    patients: data.patients,
                    totalElements: data.totalCount || data.patients.length,
                    totalPages: Math.ceil((data.totalCount || data.patients.length) / size),
                    currentPage: data.currentPage || page
                };
            }
            
            // Handle Spring paginated response
            if (data.content) {
                return {
                    patients: data.content,
                    totalElements: data.totalElements || 0,
                    totalPages: data.totalPages || 0,
                    currentPage: data.number || page
                };
            }
            
            // Handle array response
            if (Array.isArray(data)) {
                return {
                    patients: data,
                    totalElements: data.length,
                    totalPages: 1,
                    currentPage: 0
                };
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Error fetching patients:', error);
            throw error;
        }
    },

    // Appointments
    getAppointments: async (page = 0, size = 50, patientId = null) => {
        console.log(`📅 Fetching appointments (page: ${page}, size: ${size})`);
        try {
            let url = `${CONFIG.ADMIN_API_URL}/api/admin/appointments?page=${page}&size=${size}`;
            if (patientId) {
                url += `&patientId=${patientId}`;
            }
            
            const response = await fetch(url, {
                method: 'GET',
                headers: ApiService.getAuthHeaders(),
                credentials: 'include'
            });
            
            console.log('Appointments response status:', response.status);
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('✅ Appointments loaded:', data);
            
            // Handle different response formats
            if (data.content) {
                return {
                    appointments: data.content,
                    totalElements: data.totalElements || 0,
                    totalPages: data.totalPages || 0,
                    currentPage: data.number || page
                };
            }
            
            if (Array.isArray(data)) {
                return {
                    appointments: data,
                    totalElements: data.length,
                    totalPages: 1,
                    currentPage: 0
                };
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Error fetching appointments:', error);
            throw error;
        }
    },

    // Test Results
    getTestResults: async (page = 0, size = 50) => {
        console.log(`🧪 Fetching test results (page: ${page}, size: ${size})`);
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}/results/admin/all?page=${page}&size=${size}`,
                {
                    method: 'GET',
                    headers: ApiService.getAuthHeaders(),
                    credentials: 'include'
                }
            );
            
            console.log('Test results response status:', response.status);
            
            if (response.status === 401) {
                throw new Error('Authentication expired. Please login again.');
            }
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('✅ Test results loaded:', data);
            
            if (data.success && data.results) {
                return {
                    results: data.results,
                    totalElements: data.totalCount || data.results.length,
                    totalPages: Math.ceil((data.totalCount || data.results.length) / size),
                    currentPage: page
                };
            }
            
            if (data.content) {
                return {
                    results: data.content,
                    totalElements: data.totalElements || 0,
                    totalPages: data.totalPages || 0,
                    currentPage: data.number || page
                };
            }
            
            if (Array.isArray(data)) {
                return {
                    results: data,
                    totalElements: data.length,
                    totalPages: 1,
                    currentPage: 0
                };
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Error fetching test results:', error);
            throw error;
        }
    },

    // Notifications
    getNotifications: async () => {
        console.log('🔔 Fetching notifications...');
        try {
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/notifications`,
                {
                    method: 'GET',
                    headers: ApiService.getAuthHeaders(),
                    credentials: 'include'
                }
            );
            
            if (response.status === 401) {
                throw new Error('Authentication expired');
            }
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('✅ Notifications loaded:', data);
            return Array.isArray(data) ? data : (data.notifications || []);
            
        } catch (error) {
            console.error('❌ Error loading notifications:', error);
            return [];
        }
    },

    // Auto Notifications Settings
    getAutoNotifications: async () => {
        console.log('🔔 Fetching auto notifications...');
        try {
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/auto-notifications`,
                {
                    method: 'GET',
                    headers: ApiService.getAuthHeaders(),
                    credentials: 'include'
                }
            );
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('✅ Auto notifications loaded:', data);
            return data;
            
        } catch (error) {
            console.error('❌ Error loading auto notifications:', error);
            return [];
        }
    },

    // Add test result
    addTestResult: async (resultData) => {
        console.log('📝 Adding test result:', resultData);
        
        const backendData = {
            patientId: parseInt(resultData.patientId),
            testType: resultData.testType,
            result: resultData.result,
            status: resultData.status || 'NORMAL',
            notes: resultData.notes || '',
            doctorName: resultData.doctorName || 'Admin',
            testDate: resultData.testDate || new Date().toISOString().split('T')[0]
        };
        
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}/results/admin/upload`,
                {
                    method: 'POST',
                    headers: ApiService.getAuthHeaders(),
                    body: JSON.stringify(backendData),
                    credentials: 'include'
                }
            );
            
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to add test result');
            }
            
            const data = await response.json();
            console.log('✅ Test result added:', data);
            return data;
            
        } catch (error) {
            console.error('❌ Error adding test result:', error);
            throw error;
        }
    },

    // Statistics
    getStatistics: async () => {
        console.log('📊 Fetching statistics...');
        try {
            const response = await fetch(
                `${CONFIG.ADMIN_API_URL}/api/admin/stats`,
                {
                    method: 'GET',
                    headers: ApiService.getAuthHeaders(),
                    credentials: 'include'
                }
            );
            
            if (response.status === 401) {
                throw new Error('Authentication expired');
            }
            
            if (response.ok) {
                const data = await response.json();
                console.log('✅ Statistics loaded:', data);
                return data;
            }
            
            console.warn('⚠️ Statistics endpoint not available, using defaults');
            return {
                totalPatients: 0,
                totalAppointments: 0,
                totalResults: 0,
                pendingAppointments: 0
            };
            
        } catch (error) {
            console.error('❌ Error fetching statistics:', error);
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
console.log('✅ ApiService loaded and available globally');