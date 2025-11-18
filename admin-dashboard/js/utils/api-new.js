// api-new.js - BRAND NEW FILE
console.log('🚀🚀🚀 NEW API FILE LOADED 🚀🚀🚀');

const ApiService = {
    login: async function(email, password) {
        console.log('🔐 NEW ApiService.login called');
        console.log('📧 Email:', email);
        console.log('🔑 Password length:', password?.length);
        
        // Send both username and email
        const loginData = {
            username: email,
            email: email,
            password: password
        };
        
        console.log('📤 Login data:', JSON.stringify(loginData));
        
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
            
            console.log('📥 Status:', response.status);
            
            const responseText = await response.text();
            console.log('📥 Response:', responseText);
            
            if (!response.ok) {
                let errorData;
                try {
                    errorData = JSON.parse(responseText);
                    console.error('❌ Error:', errorData);
                } catch (e) {
                    console.error('❌ Text:', responseText);
                }
                throw new Error(errorData?.message || 'Login failed');
            }
            
            const data = JSON.parse(responseText);
            console.log('✅ Success:', data);
            
            if (data.token) {
                localStorage.setItem('authToken', data.token);
                console.log('✅ Token stored');
            }
            
            return data;
            
        } catch (error) {
            console.error('❌ Error:', error);
            throw error;
        }
    },

    getAuthHeaders: function() {
        const token = localStorage.getItem('authToken');
        return token ? {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        } : {
            'Content-Type': 'application/json'
        };
    },

    get: async function(endpoint) {
        const url = `${CONFIG.API_BASE_URL}${endpoint}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: this.getAuthHeaders()
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    },

    post: async function(endpoint, data) {
        const url = `${CONFIG.API_BASE_URL}${endpoint}`;
        const response = await fetch(url, {
            method: 'POST',
            headers: this.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.json();
    }
};

window.ApiService = ApiService;
console.log('✅ NEW ApiService ready');