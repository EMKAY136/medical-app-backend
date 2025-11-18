// Add this to your api.js file - replace the login function

const ApiService = {
    login: async function(email, password) {
    console.log('🔐 ApiService.login called');
    console.log('📧 Email:', email);
    console.log('🔑 Password length:', password?.length);
    
    // Backend might expect 'email' or 'username' - try email first
    const loginData = {
        email: email,      // Use 'email' field
        password: password
    };
    
    console.log('📤 Sending login request:', JSON.stringify(loginData));
    
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
        console.log('📥 Response body:', responseText);
        
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
        console.log('✅ Login response:', data);
        
        return data;
        
    } catch (error) {
        console.error('❌ Login failed:', error);
        throw error;
    }
},

    // Keep your other ApiService methods here...
};

window.ApiService = ApiService;