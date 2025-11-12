const Header = () => {
    const userInfo = JSON.parse(localStorage.getItem('user_info') || '{}');
    const currentUser = {
        name: userInfo.firstName ? `${userInfo.firstName} ${userInfo.lastName}` : userInfo.email || 'Admin User',
        role: 'Medical Administrator',
        initials: userInfo.firstName ? `${userInfo.firstName[0]}${userInfo.lastName ? userInfo.lastName[0] : ''}` : 'AU',
        id: userInfo.id || 1,
        firstName: userInfo.firstName || 'Admin',
        lastName: userInfo.lastName || 'User',
        email: userInfo.email || 'admin@hospital.com'
    };

    return (
        <div className="header">
            <div className="header-left">
                <h1>Medical Dashboard</h1>
                <p>Welcome back, {currentUser.name}</p>
            </div>
            <div className="header-right">
                <button className="btn btn-secondary">
                    <i className="fas fa-bell"></i>
                </button>                
                <div className="user-info">
                    <span>{currentUser.name}</span>
                    <small>{currentUser.role}</small>
                </div>
            </div>
        </div>
    );
};