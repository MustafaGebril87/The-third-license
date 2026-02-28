import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import React from 'react';

const Landing = () => {
  const { user } = useAuth();

  // If logged in, redirect to dashboard
  if (user) return <Navigate to="/dashboard" />;
console.log("App rendered");

  return (
    <div className="container" style={{ textAlign: 'center', paddingTop: '5rem' }}>
      <h1 style={{ fontSize: '3rem', marginBottom: '1rem' }}>The Third License</h1>
      <p style={{ fontSize: '1.2rem', marginBottom: '2rem' }}>
       Turn every commit into ownership. Contribute. Buy. Sell shares!.
      </p>
	  <p style={{ fontSize: '1.2rem', marginBottom: '2rem' }}>
       Let's save software developers from AI!
      </p>
      <div>
        <Link to="/login">
          <button style={{ marginRight: '1rem' }}>Login</button>
        </Link>
        <Link to="/register">
          <button>Register</button>
        </Link>
      </div>
    </div>
  );
};

export default Landing;
