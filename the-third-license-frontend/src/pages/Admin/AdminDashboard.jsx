import { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';
import { Link } from 'react-router-dom';
import React from 'react';

const AdminDashboard = () => {
  const [pendingContributions, setPendingContributions] = useState([]);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [contribRes, accessRes] = await Promise.all([
        axios.get('/contributions/pending'),
        axios.get('/contributions/pending_requests'),
      ]);
      setPendingContributions(contribRes.data || []);
      setPendingRequests(accessRes.data || []);
    } catch (err) {
      setMessage('Failed to load admin data');
    }
  };

  const handleApprove = async (type, id) => {
    try {
      const url = type === 'contribution'
        ? `/contributions/${id}/approve`
        : `/contributions/requests/${id}/approve`;

      await axios.post(url);
      setMessage('✅ Approved');
      loadData();
    } catch (err) {
      setMessage('❌ Approval failed');
    }
  };

  const handleDecline = async (type, id) => {
    try {
      const url = type === 'contribution'
        ? `/contributions/${id}/decline`
        : `/contributions/requests/${id}/decline`;

      await axios.post(url);
      setMessage('✅ Declined');
      loadData();
    } catch (err) {
      setMessage('❌ Decline failed');
    }
  };

  return (
    <div className="container">
      <Navbar />
      <h2>Admin Dashboard</h2>
      {message && <p style={{ color: message.startsWith('✅') ? 'green' : 'red' }}>{message}</p>}

      <h3>Pending Contributions</h3>
      {pendingContributions.length === 0 ? (
        <p>No contributions pending approval.</p>
      ) : (
        <ul>
          {pendingContributions.map(contrib => (
            <li key={contrib.id} style={{ marginBottom: '0.5rem' }}>
              <strong>{contrib.filename}</strong> by {contrib.username}
              <button onClick={() => handleApprove('contribution', contrib.id)} style={{ marginLeft: '1rem' }}>
                Approve
              </button>
              <button onClick={() => handleDecline('contribution', contrib.id)} style={{ marginLeft: '0.5rem' }}>
                Decline
              </button>
              <Link
				  to={`/merge-conflict?repositoryId=${contrib.repositoryId}&branch=${contrib.branch}`}
				  style={{ marginLeft: '0.5rem' }}
				>
				  <button>Merge</button>
				</Link>


            </li>
          ))}
        </ul>
      )}

      <h3>Pending Access Requests</h3>
      {pendingRequests.length === 0 ? (
        <p>No access requests pending.</p>
      ) : (
        <ul>
          {pendingRequests.map(req => (
            <li key={req.id} style={{ marginBottom: '0.5rem' }}>
              <strong>{req.username}</strong> requests access to <em>{req.repositoryName}</em>
              <button onClick={() => handleApprove('access', req.id)} style={{ marginLeft: '1rem' }}>
                Approve
              </button>
              <button onClick={() => handleDecline('access', req.id)} style={{ marginLeft: '0.5rem' }}>
                Decline
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default AdminDashboard;
