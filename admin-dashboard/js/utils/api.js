// Add this to your api.js file - replace the login function

const ApiService = {
    login: async function(username, password) {
        console.log('🔐 Login attempt started');
        console.log('📧 Username:', username);
        console.log('🔑 Password length:', password?.length);
        
        const loginData = {
            username: username,
            password: password
        };
        
        console.log('📤 Sending login request with data:', JSON.stringify(loginData, null, 2));
        
        const url = `${CONFIG.API_BASE_URL}/auth/login`;
        console.log('🌐 Login URL:', url);
        
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(loginData)
            });
            
            console.log('📥 Response status:', response.status);
            console.log('📥 Response headers:', [...response.headers.entries()]);
            
            if (!response.ok) {
                // Try to get error details from response
                let errorMessage = `HTTP error! status: ${response.status}`;
                try {
                    const errorData = await response.json();
                    console.error('❌ Error response body:', errorData);
                    errorMessage = errorData.message || errorData.error || errorMessage;
                } catch (e) {
                    console.error('❌ Could not parse error response');
                    const textError = await response.text();
                    console.error('❌ Error response text:', textError);
                }
                throw new Error(errorMessage);
            }
            
            const data = await response.json();
            console.log('✅ Login response:', data);
            
            // Check if we got a token
            if (data.token) {
                console.log('✅ Token received:', data.token.substring(0, 20) + '...');
                localStorage.setItem('authToken', data.token);
                
                // Store user data if available
                if (data.user) {
                    localStorage.setItem('userData', JSON.stringify(data.user));
                }
                
                console.log('✅ Login successful!');
                return data;
            } else {
                console.error('❌ No token in response');
                throw new Error('No authentication token received');
            }
            
        } catch (error) {
            console.error('❌ Login error:', error);
            throw error;
        }
    },

    // Keep your other ApiService methods here...
};

window.ApiService = ApiService;