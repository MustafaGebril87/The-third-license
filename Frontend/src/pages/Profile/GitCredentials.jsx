import { useState, useEffect } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';
import React from 'react';

const GitCredentials = () => {
  const [form, setForm] = useState({ gitUsername: '', gitToken: '' });
  const [message, setMessage] = useState('');

  // Load current user info
  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await axios.get('/users/users/me'); // You might need a `/me` endpoint
        setForm({
          gitUsername: res.data.gitUsername || '',
          gitToken: res.data.gitToken || ''
        });
      } catch (err) {
        console.warn('Failed to fetch user info');
      }
    };
    fetchUser();
  }, []);

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await axios.put('/users/update-git', form);
      setMessage('✅ Git credentials updated successfully.');
    } catch (err) {
      setMessage('❌ Failed to update Git credentials.');
    }
  };

  return (
    <div className="container">
	<Navbar />
      <h2>Update Git Credentials</h2>
      {message && <p style={{ color: message.startsWith('✅') ? 'lightgreen' : 'salmon' }}>{message}</p>}
      <form onSubmit={handleSubmit}>
        <label>Git Username</label>
        <input
          name="gitUsername"
          value={form.gitUsername}
          onChange={handleChange}
          required
        />
        <label>Git Token</label>
        <input
          name="gitToken"
          value={form.gitToken}
          onChange={handleChange}
          required
        />
        <button type="submit">Save</button>
      </form>
    </div>
  );
};

export default GitCredentials;
