import React from 'react';
import { createContext, useContext, useEffect, useState } from 'react';
import api, { setSessionExpiredHandler } from '../api/axios';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  // User info is kept in memory only — tokens live in HttpOnly cookies set by the server
  const [user, setUser] = useState(null);
  // True until we've asked the server whether the cookies still hold a valid session
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));

    // Restore the session after a page reload or a redirect back from Stripe
    api.get('/users/me')
      .then((res) => setUser(res.data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

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
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
