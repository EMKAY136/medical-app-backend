const { useState } = React;

const Login = ({ onLoginSuccess }) => {
    const [credentials, setCredentials] = useState({ email: '', password: '' });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        console.log('🔐 Form submitted with:', { 
            email: credentials.email, 
            passwordLength: credentials.password?.length 
        });
        
        setLoading(true);
        setError('');

        try {
            // FIXED: Pass email and password as separate parameters
            const data = await ApiService.login(credentials.email, credentials.password);
            
            console.log('✅ Login successful:', data);
            
            // Store authentication data
            if (data.token) {
                localStorage.setItem('authToken', data.token);
                console.log('✅ Token stored');
            }
            
            if (data.user) {
                localStorage.setItem('user_info', JSON.stringify(data.user));
                console.log('✅ User info stored');
            } else {
                // Fallback if no user object returned
                localStorage.setItem('user_info', JSON.stringify({ email: credentials.email }));
            }
            
            // Trigger the success callback
            onLoginSuccess();
            
        } catch (error) {
            console.error('❌ Login error:', error);
            setError(error.message || 'Login failed. Please check your credentials.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="login-header">
                    <div className="login-logo">
                        <i className="fas fa-heartbeat"></i> Qualitest Medical
                    </div>
                    <p>Admin Portal</p>
                </div>

                {error && (
                    <div className="alert alert-error">
                        <i className="fas fa-exclamation-triangle"></i>
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label className="form-label">Email</label>
                        <input
                            type="email"
                            className="form-input"
                            value={credentials.email}
                            onChange={(e) => setCredentials({...credentials, email: e.target.value})}
                            placeholder="admin@example.com"
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label className="form-label">Password</label>
                        <input
                            type="password"
                            className="form-input"
                            value={credentials.password}
                            onChange={(e) => setCredentials({...credentials, password: e.target.value})}
                            placeholder="Enter your password"
                            required
                        />
                    </div>
                    <button 
                        type="submit" 
                        className="btn btn-primary" 
                        disabled={loading} 
                        style={{width: '100%'}}
                    >
                        {loading ? (
                            <div className="spinner" style={{width: '20px', height: '20px'}}></div>
                        ) : (
                            <>
                                <i className="fas fa-sign-in-alt"></i> Login
                            </>
                        )}
                    </button>
                </form>
            </div>
        </div>
    );
};