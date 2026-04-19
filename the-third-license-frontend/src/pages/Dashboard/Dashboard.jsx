import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../../context/AuthContext';
import Navbar from '../../components/Navbar';
import axios from '../../api/axios';

const Dashboard = () => {
  const { user } = useAuth();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState('');
  const debounceRef = useRef(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }

    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      setMessage('');
      try {
        const res = await axios.get('/companies/search', { params: { q: query } });
        setResults(res.data || []);
        if (res.data.length === 0) setMessage('No companies found.');
      } catch {
        setMessage('Failed to search companies.');
      } finally {
        setSearching(false);
      }
    }, 300);

    return () => clearTimeout(debounceRef.current);
  }, [query]);

  const requestAccess = async (company) => {
    if (!company.repositoryId) {
      setMessage(`No repository found for "${company.name}".`);
      return;
    }
    try {
      await axios.post(`/contributions/${company.repositoryId}/request-access`);
      setMessage(`Access request sent for "${company.name}".`);
    } catch {
      setMessage(`Failed to request access for "${company.name}".`);
    }
  };

  return (
    <div>
      <Navbar />
      <div style={{ padding: '2rem' }}>
        <h2>Welcome, {user?.username}!</h2>

        <div style={{ marginTop: '1.5rem', maxWidth: '480px' }}>
          <h3>Search Companies</h3>
          <input
            type="text"
            placeholder="Search by company name..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: '100%', padding: '0.5rem', fontSize: '1rem', boxSizing: 'border-box' }}
          />
        </div>

        {searching && <p style={{ marginTop: '0.5rem' }}>Searching...</p>}

        {message && (
          <p style={{ marginTop: '0.5rem', color: message.startsWith('Access request sent') ? 'green' : 'red' }}>
            {message}
          </p>
        )}

        {results.length > 0 && (
          <ul style={{ marginTop: '1rem', listStyle: 'none', padding: 0, maxWidth: '600px' }}>
            {results.map((company) => (
              <li
                key={company.id}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '0.6rem 0',
                  borderBottom: '1px solid #ddd',
                }}
              >
                <span>
                  <strong>{company.name}</strong> — {company.owner}
                </span>
                <button onClick={() => requestAccess(company)} style={{ marginLeft: '1rem' }}>
                  Request Access
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
