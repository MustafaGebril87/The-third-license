import React from 'react'; // This must come first
import { useAuth } from '../../context/AuthContext';
import Navbar from '../../components/Navbar';

const Dashboard = () => {
  const { user } = useAuth();

  return (
    <div>
      <Navbar />
      <div style={{ padding: '2rem' }}>
        <h2>Welcome, {user?.username}!</h2>
        <p>Select a section from the navbar:</p>
      </div>
    </div>
  );
};

export default Dashboard;
