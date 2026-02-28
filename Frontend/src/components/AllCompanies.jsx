import { useEffect, useState } from 'react';
import axios from '../api/axios';
import Navbar from '../components/Navbar';
import React from 'react';

const AllCompanies = () => {
  const [companies, setCompanies] = useState([]);
  const [reposByCompany, setReposByCompany] = useState({});
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadCompanies();
  }, []);

  const loadCompanies = async () => {
    try {
      const res = await axios.get('/companies/all');
      setCompanies(res.data || []);
      setMessage('');

      // For each company, load its repository
      for (const company of res.data) {
        const repoRes = await axios.get(`/companies/${company.id}/repositories`);
        if (repoRes.data.length > 0) {
          setReposByCompany(prev => ({ ...prev, [company.id]: repoRes.data[0] })); // assume first repo
        }
      }
    } catch (err) {
      setMessage('❌ Failed to load companies or repositories.');
    }
  };

  const requestAccess = async (companyId) => {
    const repo = reposByCompany[companyId];
    if (!repo) {
      setMessage('❌ Repository not found for this company.');
      return;
    }

    try {
      await axios.post(`/contributions/${repo.id}/request-access`);
      setMessage('✅ Access request sent.');
    } catch (err) {
      setMessage('❌ Failed to send access request.');
    }
  };

  return (
    <div className="container">
      <Navbar />
      <h2>All Companies</h2>
      {message && <p style={{ color: message.startsWith('✅') ? 'green' : 'red' }}>{message}</p>}
      {companies.length === 0 ? (
        <p>No companies found.</p>
      ) : (
        <ul>
          {companies.map((company) => (
            <li key={company.id} style={{ marginBottom: '1rem' }}>
              <strong>{company.name}</strong> — Owner: {company.owner}
              <button
                onClick={() => requestAccess(company.id)}
                style={{ marginLeft: '1rem' }}
              >
                Request Access
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default AllCompanies;
