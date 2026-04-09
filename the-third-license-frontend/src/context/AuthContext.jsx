import React from 'react';
import { createContext, useContext, useState } from 'react';
import api from '../api/axios';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  // User info is kept in memory only — tokens live in HttpOnly cookies set by the server
  const [user, setUser] = useState(null);

  const login = (userData) => {
    // userData is the response body from POST /api/auth/login
    // { id, username, email, roles }
    setUser(userData);
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } catch (_) {
      // Proceed regardless — cookies will expire naturally
    }
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
