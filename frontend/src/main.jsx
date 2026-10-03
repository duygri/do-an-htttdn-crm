import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import AdminApp from './SystemAdminApp';
import ManagerApp from './ManagerApp';
import './styles.css';
import './admin-design.css';
import './storefront.css';
import './storefront-figma.css';

const mode = import.meta.env.MODE;
const isAdmin = mode === 'admin' || (mode !== 'user' && (window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/')));
const isManager = mode === 'manager' || (mode !== 'user' && mode !== 'admin' && /^\/manager(?:\/|$)/.test(window.location.pathname));
createRoot(document.getElementById('root')).render(<React.StrictMode>{isManager ? <ManagerApp /> : isAdmin ? <AdminApp /> : <App />}</React.StrictMode>);
