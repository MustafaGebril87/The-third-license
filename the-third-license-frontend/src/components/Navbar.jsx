import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import React from 'react';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav>
      <div>
        <ul style={{ display: 'flex', listStyle: 'none', margin: 0, padding: 0 }}>
          <li><Link to="/dashboard">Dashboard</Link></li>
		  <li><Link to="/profile/git" style={{ marginRight: '1rem' }}>Git Settings</Link></li>
		  <li><Link to="/companies/all">All Companies</Link></li>
          <li><Link to="/companies">My Companies</Link></li>
          <li><Link to="/admin">Admin</Link></li>
          <li><Link to="/push">Push Files</Link></li>
          <li><Link to="/shares">My Shares</Link></li>
          <li><Link to="/tokens">My Tokens</Link></li>
		  <li><Link to="/pages/Currency/MarketplaceCoins">Marketplace</Link></li>

        </ul>
      </div>
      <div>
        {user ? (
          <>
            <span style={{ marginRight: '1rem' }}>{user.username}</span>
            <button onClick={handleLogout}>Logout</button>
          </>
        ) : (
          <Link to="/login">Login</Link>
        )}
      </div>
    </nav>
  );
};

export default Navbar;
