import React, { createContext, useContext, useMemo, useState } from 'react';
import { authApi } from '../../api';
const AuthContext = createContext(null);
export function AuthProvider({ children }) { const [user,setUser]=useState(()=>JSON.parse(localStorage.getItem('crm_admin_user')||'null')); const login=async(email,password)=>{const result=await authApi.login({email,password});localStorage.setItem('crm_access_token',result.accessToken||'');const next=result.user||{email,role:'ADMIN'};localStorage.setItem('crm_admin_user',JSON.stringify(next));setUser(next)}; const logout=async()=>{await authApi.logout();localStorage.removeItem('crm_access_token');localStorage.removeItem('crm_admin_user');setUser(null)}; return <AuthContext.Provider value={useMemo(()=>({user,login,logout}),[user])}>{children}</AuthContext.Provider> }
export const useAuth=()=>useContext(AuthContext);
