import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import AdminApp from './AdminApp';
import './styles.css';

const mode = import.meta.env.MODE;
const isAdmin = mode === 'admin' || (mode !== 'user' && (window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/')));
createRoot(document.getElementById('root')).render(<React.StrictMode>{isAdmin ? <AdminApp /> : <App />}</React.StrictMode>);
