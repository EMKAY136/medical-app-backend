// api.js - API Service for Medical Admin Dashboard
console.log('📦 api.js VERSION 5.0 loading...');

const ApiService = {
    // Login function - handles authentication
    login: async function(email, password) {
        console.log('🔐 ApiService.login called');
        console.log('📧 Email:', email);
        console.log('🔑 Password length:', password?.length);
        
        // Backend checks for 'email' first
        const loginData = {
            email: email,
            password: password
        };
        
        console.log('📤 Sending login request:', JSON.stringify(loginData));
        
        // Backend expects /auth/login
        const url = `${CONFIG.API_BASE_URL}/auth/login`;
        console.log('🌐 URL:', url);
        
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(loginData)
            });
            
            console.log('📥 Response status:', response.status);
            
            const responseText = await response.text();
            console.log('📥 Response body:', responseText.substring(0, 200));
            
            if (!response.ok) {
                let errorMessage = 'Authentication failed';
                try {
                    const errorData = JSON.parse(responseText);
                    errorMessage = errorData.message || errorData.error || errorMessage;
                    console.error('❌ Backend error:', errorData);
                } catch (e) {
                    console.error('❌ Response text:', responseText);
                }
                throw new Error(errorMessage);
            }
            
            const data = JSON.parse(responseText);
            console.log('✅ Login successful!');
            
            // Store token if present
            if (data.token) {
                localStorage.setItem('authToken', data.token);
                console.log('✅ Token stored');
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Login failed:', error);
            throw error;
        }
    },

    // Get auth headers for authenticated requests
    getAuthHeaders: function() {
        const token = localStorage.getItem('authToken');
        
        if (!token) {
            return {
                'Content-Type': 'application/json'
            };
        }
        
        return {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        };
    },

    // Generic GET request
    get: async function(endpoint) {
        // Add /api prefix if not already present
        const url = endpoint.startsWith('/api') 
            ? `${CONFIG.API_BASE_URL}${endpoint}`
            : `${CONFIG.API_BASE_URL}/api${endpoint}`;
        
        console.log(`📥 GET ${url}`);
        
        try {
            const response = await fetch(url, {
                method: 'GET',
                headers: this.getAuthHeaders()
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error(`❌ GET ${endpoint} failed:`, error);
            throw error;
        }
    },

    // Generic POST request
    post: async function(endpoint, data) {
        // Add /api prefix if not already present
        const url = endpoint.startsWith('/api') 
            ? `${CONFIG.API_BASE_URL}${endpoint}`
            : `${CONFIG.API_BASE_URL}/api${endpoint}`;
        
        console.log(`📤 POST ${url}`);
        
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: this.getAuthHeaders(),
                body: JSON.stringify(data)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error(`❌ POST ${endpoint} failed:`, error);
            throw error;
        }
    },

    // Generic PUT request
    put: async function(endpoint, data) {
        // Add /api prefix if not already present
        const url = endpoint.startsWith('/api') 
            ? `${CONFIG.API_BASE_URL}${endpoint}`
            : `${CONFIG.API_BASE_URL}/api${endpoint}`;
        
        console.log(`📝 PUT ${url}`);
        
        try {
            const response = await fetch(url, {
                method: 'PUT',
                headers: this.getAuthHeaders(),
                body: JSON.stringify(data)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error(`❌ PUT ${endpoint} failed:`, error);
            throw error;
        }
    },

    // Generic DELETE request
    delete: async function(endpoint) {
        // Add /api prefix if not already present
        const url = endpoint.startsWith('/api') 
            ? `${CONFIG.API_BASE_URL}${endpoint}`
            : `${CONFIG.API_BASE_URL}/api${endpoint}`;
        
        console.log(`🗑️ DELETE ${url}`);
        
        try {
            const response = await fetch(url, {
                method: 'DELETE',
                headers: this.getAuthHeaders()
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error(`❌ DELETE ${endpoint} failed:`, error);
            throw error;
        }
    },

    // Fetch patients
    getPatients: async function(page = 0, size = 50) {
        return await this.get(`/admin/patients?page=${page}&size=${size}`);
    },

    // Fetch appointments
    getAppointments: async function(page = 0, size = 50) {
        return await this.get(`/admin/appointments?page=${page}&size=${size}`);
    },

    // Fetch notifications
    getNotifications: async function() {
        return await this.get('/admin/notifications');
    },

    // Fetch auto notifications
    getAutoNotifications: async function() {
        return await this.get('/admin/auto-notifications');
    },

    // Fetch test results
    getTestResults: async function(page = 0, size = 50) {
        console.log('Fetching test results from backend...');
        
        try {
            // Backend endpoint is /results/admin/all (no /api prefix)
            const response = await fetch(
                `${CONFIG.API_BASE_URL}/results/admin/all?page=${page}&size=${size}`,
                {
                    method: 'GET',
                    headers: this.getAuthHeaders()
                }
            );
            
            console.log('Test results response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('Test results error response:', errorText);
                throw new Error('Failed to fetch test results');
            }
            
            const data = await response.json();
            console.log('Test results loaded successfully');
            return data;
            
        } catch (error) {
            console.error('Error fetching test results:', error);
            throw error;
        }
    },

    // Update patient
    updatePatient: async function(patientId, data) {
        return await this.put(`/admin/patients/${patientId}`, data);
    },

    // Delete patient
    deletePatient: async function(patientId) {
        return await this.delete(`/admin/patients/${patientId}`);
    },

    // Update appointment
    updateAppointment: async function(appointmentId, data) {
        return await this.put(`/admin/appointments/${appointmentId}`, data);
    },

    // Logout
    logout: function() {
        console.log('🚪 Logging out...');
        localStorage.removeItem('authToken');
        localStorage.removeItem('user_info');
        localStorage.removeItem('userData');
        console.log('✅ Logged out successfully');
    }
};

// Make it globally available
window.ApiService = ApiService;
console.log('✅ ApiService VERSION 5.0 loaded and available globally');