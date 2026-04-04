import { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';
import React from 'react';

const MyCompanies = () => {
  const [companies, setCompanies] = useState([]);
  const [reposByCompany, setReposByCompany] = useState({});
  const [expanded, setExpanded] = useState({});
  const [requested, setRequested] = useState({});
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [newCompanyName, setNewCompanyName] = useState('');

  useEffect(() => {
    const fetchCompanies = async () => {
      try {
        const res = await axios.get('/companies/my-companies');
        setCompanies(res.data.companies || []);
      } catch (err) {
        setError('Failed to load companies');
      }
    };

    fetchCompanies();
  }, []);

  const toggleRepos = async (companyId) => {
    if (expanded[companyId]) {
      setExpanded(prev => ({ ...prev, [companyId]: false }));
      return;
    }

    if (!reposByCompany[companyId]) {
      try {
        const res = await axios.get(`/companies/${companyId}/repositories`);
        setReposByCompany(prev => ({ ...prev, [companyId]: res.data }));
      } catch (err) {
        setError('Failed to load repositories');
        return;
      }
    }

    setExpanded(prev => ({ ...prev, [companyId]: true }));
  };

  const handleRequestAccess = async (repoId) => {
    try {
      await axios.post(`/contributions/${repoId}/request-access`);
      setRequested(prev => ({ ...prev, [repoId]: true }));
    } catch (err) {
      alert('Failed to request access: ' + (err.response?.data || ''));
    }
  };

  const handleClone = async (repoId) => {
    try {
      const res = await axios.post(`/companies/${repoId}/clone`);
      alert(res.data || 'Repository cloned successfully!');
    } catch (err) {
      alert('❌ Failed to clone repository: ' + (err.response?.data || ''));
    }
  };
  const handlePull = async (repoId, branch = "main", filePath = "README.md") => {
  try {
    const res = await axios.get('/contributions/pull-file', {
      params: {
        repositoryId: repoId,
        branch,
        filePath,
        mode: 'pull'   // ✅ tell backend this is a pull, not a merge request trigger
      },
    });
    alert(`✅ Pulled content of ${filePath}:\n\n` + res.data.content);
  } catch (err) {
    const status = err.response?.status;
    const message = err.response?.data || '';

    console.log("🔴 Pull error:", status, message);

    if (status === 409 || status === 500) {
      let conflictFile = null;

      if (typeof message === 'string') {
        const match = message.match(/files?:\s*\n?([a-zA-Z0-9_./-]+)/i);
        if (match && match[1]) {
          conflictFile = match[1].trim();
        }
      }

      if (conflictFile) {
        alert(`⚠️ Merge conflict detected in ${conflictFile}. A merge request has been sent to the repository owner for resolution.`);
      } else {
        alert('⚠️ Merge conflict occurred. A merge request has been sent to the repository owner.');
      }
    } else {
      alert('❌ Failed to pull file: ' + message);
    }
  }
};



  const handleCreateCompany = async () => {
    if (!newCompanyName.trim()) return;

    try {
      await axios.post('/companies/open', { name: newCompanyName });
      setNewCompanyName('');
      setShowForm(false);
      const res = await axios.get('/companies/my-companies');
      setCompanies(res.data.companies || []);
    } catch (err) {
      alert('Failed to create company: ' + (err.response?.data || ''));
    }
  };

  return (
    <div className="container">
      <Navbar />
      <h2>My Companies</h2>
      {error && <p style={{ color: 'red' }}>{error}</p>}

      <button
        onClick={() => setShowForm(true)}
        style={{ marginBottom: '1rem' }}
      >
        ➕ Open New Company
      </button>

      {showForm && (
        <div style={{ marginBottom: '1rem' }}>
          <input
            type="text"
            placeholder="Company Name"
            value={newCompanyName}
            onChange={(e) => setNewCompanyName(e.target.value)}
            style={{ marginRight: '0.5rem' }}
          />
          <button onClick={handleCreateCompany}>Create</button>
          <button onClick={() => setShowForm(false)} style={{ marginLeft: '0.5rem' }}>
            Cancel
          </button>
        </div>
      )}

      {companies.length === 0 ? (
        <p>You don't own or contribute to any companies yet.</p>
      ) : (
        <ul>
          {companies.map(company => (
            <li key={company.id} style={{ marginBottom: '1rem' }}>
              <div>
                <strong>{company.name}</strong> (Owner: {company.owner})
                <button
                  onClick={() => toggleRepos(company.id)}
                  style={{ marginLeft: '1rem' }}
                >
                  {expanded[company.id] ? 'Hide Repos' : 'Show Repos'}
                </button>
              </div>

              {expanded[company.id] && (
                <ul style={{ marginTop: '0.5rem', paddingLeft: '1rem' }}>
                  {(reposByCompany[company.id] || []).map(repo => {
                    const accessStatus = repo.accessStatus;
                    const hasAccess = accessStatus === 'APPROVED';
                    const pendingAccess = accessStatus === 'PENDING';
                    const cloned = repo.cloned;

                    return (
                      <li key={repo.id} style={{ marginBottom: '0.5rem' }}>
                        {repo.name} — {repo.gitUrl}
                        <button
                          onClick={() => handleRequestAccess(repo.id)}
                          disabled={hasAccess || pendingAccess}
                          style={{ marginLeft: '1rem' }}
                        >
                          {hasAccess
                            ? 'Access Granted'
                            : pendingAccess
                            ? 'Access Pending'
                            : 'Request Access'}
                        </button>
                        <button
                          onClick={() => handleClone(repo.id)}
                          style={{ marginLeft: '0.5rem' }}
                        >
                          Clone
                        </button>
                      <button
						  onClick={() => handlePull(repo.id, repo.branchName || 'main')}
						  style={{ marginLeft: '0.5rem' }}
						>
						  Pull
						</button>

                      </li>
                    );
                  })}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default MyCompanies;
