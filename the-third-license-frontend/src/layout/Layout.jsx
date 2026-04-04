import { useAuth } from '../context/AuthContext';
import { Navigate, Link } from 'react-router-dom';
import React from 'react';

const Landing = () => {
  const { user } = useAuth();
  if (user) return <Navigate to="/dashboard" />;

  return (
    <div className="container" style={{ textAlign: 'center', paddingTop: '5rem' }}>
      <h1 style={{ fontSize: '3rem', marginBottom: '1rem' }}>The Third License</h1>
      <p>A modern platform for equity-based open source contributions.</p>
      <Link to="/login"><button>Login</button></Link>
      <Link to="/register" style={{ marginLeft: '1rem' }}><button>Register</button></Link>
    </div>
  );
};

export default Landing;
