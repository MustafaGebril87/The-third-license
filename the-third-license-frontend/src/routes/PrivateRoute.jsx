// src/routes/PrivateRoute.jsx

import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Navigate } from 'react-router-dom';

const PrivateRoute = ({ children }) => {
  const { authToken } = useAuth();

  // If the user is authenticated, show the child route component
  // Otherwise, redirect to the login page
  return authToken ? children : <Navigate to="/login" replace />;
};

export default PrivateRoute;
