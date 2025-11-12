const { useState } = React;

const Login = ({ onLoginSuccess }) => {
    const [credentials, setCredentials] = useState({ email: '', password: '' });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        console.log('Form submitted with:', credentials);
        setLoading(true);
        setError('');

        try {
            // ApiService.login() now returns parsed JSON data directly
            const data = await ApiService.login(credentials);
            
            console.log('Login successful:', data);
            
            // The token is already stored by ApiService.login(), but store user info
            localStorage.setItem('authToken', data.token); // Changed to match Dashboard
            localStorage.setItem('user_info', JSON.stringify(data.user || { email: credentials.email }));
            onLoginSuccess();
            
        } catch (error) {
            console.error('Login error:', error);
            setError(error.message || 'Login failed');
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
                            required
                        />
                    </div>
                    <button type="submit" className="btn btn-primary" disabled={loading} style={{width: '100%'}}>
                        {loading ? <div className="spinner" style={{width: '20px', height: '20px'}}></div> : 'Login'}
                    </button>
                </form>
            </div>
        </div>
    );
};