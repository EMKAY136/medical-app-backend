// Debug version - logs everything
console.log('main.js loaded');

const { useState, useEffect } = React;

const checkComponents = () => {
    const components = [
        'CONFIG', 'ApiService', 'Login', 'Sidebar', 'Header', 'StatsGrid', 
        'MainPanel', 'SidePanel', 'MedicalAdminDashboard'
    ];
    components.forEach(component => {
        if (window[component]) {
            console.log(`✓ ${component} loaded`);
        } else {
            console.error(`✗ ${component} NOT loaded`);
        }
    });
};

const TestComponent = () => {
    return React.createElement('div', { 
        style: { padding: '20px', background: 'white', margin: '20px', borderRadius: '8px', textAlign: 'center' } 
    }, 
        React.createElement('h1', null, 'React is Working!'),
        React.createElement('p', null, 'If you see this, the basic setup is correct.'),
        React.createElement('button', {
            onClick: () => alert('Button clicked!'),
            style: { padding: '10px 20px', margin: '10px' }
        }, 'Test Button')
    );
};

const initializeApp = () => {
    console.log('Initializing app...');
    checkComponents();
    try {
        if (window.MedicalAdminDashboard) {
            console.log('Rendering MedicalAdminDashboard...');
            ReactDOM.render(
                React.createElement(window.MedicalAdminDashboard), 
                document.getElementById('root')
            );
        } else {
            console.warn('MedicalAdminDashboard not found, rendering test component');
            ReactDOM.render(React.createElement(TestComponent), document.getElementById('root'));
        }
    } catch (error) {
        console.error('Error rendering app:', error);
        ReactDOM.render(
            React.createElement('div', { style: { padding: '20px', background: 'white', margin: '20px', color: 'red' } }, 
                `Error: ${error.message}`),
            document.getElementById('root')
        );
    }
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => setTimeout(initializeApp, 100));
} else {
    setTimeout(initializeApp, 100);
}
