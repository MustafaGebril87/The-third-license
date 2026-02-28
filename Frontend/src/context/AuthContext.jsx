import React from 'react'; // ✅ Required for JSX
import { createContext, useContext, useState, useEffect } from 'react';
import { jwtDecode } from 'jwt-decode'; // ✅ Correct import

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [authToken, setAuthToken] = useState(localStorage.getItem('token'));
  const [refreshToken, setRefreshToken] = useState(localStorage.getItem('refreshToken'));
  const [user, setUser] = useState(authToken ? jwtDecode(authToken) : null);

  useEffect(() => {
    if (authToken) {
      try {
        const decoded = jwtDecode(authToken);
        setUser(decoded);
      } catch (err) {
        console.error("Invalid token:", err);
        logout();
      }
    }
  }, [authToken]);

  const login = (token, refresh) => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', refresh);
    setAuthToken(token);
    setRefreshToken(refresh);
    setUser(jwtDecode(token));
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    setAuthToken(null);
    setRefreshToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ authToken, refreshToken, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
