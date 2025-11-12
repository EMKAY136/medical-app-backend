// Debug version - logs everything
console.log('main.js loaded');

const { useState, useEffect } = React;

// Check if all components are loaded
const checkComponents = () => {
    const components = [
        'CONFIG', 'ApiService', 'Login', 'Sidebar', 'Header', 'StatsGrid', 
        'MainPanel', 'SidePanel', 'MedicalAdminDashboard'
    ];
    
    components.forEach(component => {
        if (window[component] || eval(`typeof ${component} !== 'undefined'`)) {
            console.log(`✓ ${component} loaded`);
        } else {
            console.error(`✗ ${component} NOT loaded`);
        }
    });
};

// Simple test component to verify React is working
const TestComponent = () => {
    return React.createElement('div', { 
        style: { 
            padding: '20px', 
            background: 'white', 
            margin: '20px', 
            borderRadius: '8px',
            textAlign: 'center'
        } 
    }, 
    React.createElement('h1', null, 'React is Working!'),
    React.createElement('p', null, 'If you see this, the basic setup is correct.'),
    React.createElement('button', {
        onClick: () => alert('Button clicked!'),
        style: { padding: '10px 20px', margin: '10px' }
    }, 'Test Button')
    );
};

// Initialize function
const initializeApp = () => {
    console.log('Initializing app...');
    
    // Check what's available
    checkComponents();
    
    try {
        // Try to render the dashboard
        if (typeof MedicalAdminDashboard !== 'undefined') {
            console.log('Rendering MedicalAdminDashboard...');
            ReactDOM.render(
                React.createElement(MedicalAdminDashboard), 
                document.getElementById('root')
            );
        } else {
            console.warn('MedicalAdminDashboard not found, rendering test component');
            ReactDOM.render(
                React.createElement(TestComponent), 
                document.getElementById('root')
            );
        }
    } catch (error) {
        console.error('Error rendering app:', error);
        // Fallback to simple div
        ReactDOM.render(
            React.createElement('div', { 
                style: { 
                    padding: '20px', 
                    background: 'white', 
                    margin: '20px', 
                    color: 'red' 
                } 
            }, `Error: ${error.message}`),
            document.getElementById('root')
        );
    }
};

// Wait for DOM and all scripts to load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        setTimeout(initializeApp, 100); // Small delay to ensure all scripts are loaded
    });
} else {
    setTimeout(initializeApp, 100);
}