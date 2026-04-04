import { useEffect, useState } from 'react';
import axios from '../../api/axios';
import React from 'react';
import { useNavigate } from 'react-router-dom';

const PushFiles = () => {
  const [repositories, setRepositories] = useState([]);
  const [selectedRepo, setSelectedRepo] = useState('');
  const [branchName, setBranchName] = useState('');
  const [files, setFiles] = useState([]);
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const loadAllCompanies = async () => {
      try {
        const res = await axios.get('/companies/my-companies');
        const companies = res.data.companies || [];

        const repoResults = await Promise.all(
          companies.map(c => axios.get(`/companies/${c.id}/repositories`))
        );

        const allRepos = repoResults.flatMap(r => r.data);
        setRepositories(allRepos);
      } catch (err) {
        setStatus('❌ Failed to load repositories');
      }
    };

    loadAllCompanies();
  }, []);
const handleSubmit = async (e) => {
  e.preventDefault();
  setStatus('');

  if (!selectedRepo || !branchName.trim() || files.length === 0 || !message.trim()) {
    setStatus('⚠️ Please fill all fields');
    return;
  }

  const formData = new FormData();
  formData.append('repositoryId', selectedRepo);
  formData.append('branch', branchName);
  formData.append('message', message);
  for (const file of files) {
    formData.append('files', file);
  }

  try {
    await axios.post('/contributions/push-files', formData);
    setStatus('✅ Files pushed successfully!');
    setFiles([]);
    setMessage('');
    setBranchName('');
  } catch (err) {
    const statusCode = err.response?.status || 0;
    const errorData = err.response?.data;

    console.log('Push error status:', statusCode);
    console.log('Push error data:', errorData);

    let errorText = '';
    if (typeof errorData === 'string') {
      errorText = errorData;
    } else if (typeof errorData === 'object' && errorData.message) {
      errorText = errorData.message;
    } else {
      errorText = 'Unknown error';
    }

    setStatus('❌ Failed to push files: ' + errorText);

    if (
      statusCode === 409 &&
      errorText.toLowerCase().includes('merge conflict')
    ) {
      console.log('🔁 Detected merge conflict, redirecting to merge-resolver...');
		navigate(`/merge-conflict?repositoryId=${selectedRepo}&branch=${branchName}`);
    }
  }
};

  return (
    <div className="container">
      <h2>Push Files to Repository</h2>
      {status && <p style={{ color: status.startsWith('✅') ? 'green' : 'red' }}>{status}</p>}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '1rem' }}>
          <label>Repository:</label>
          <select
            value={selectedRepo}
            onChange={e => setSelectedRepo(e.target.value)}
            required
            style={{ width: '100%', padding: '0.5rem' }}
          >
            <option value="">-- Select a repository --</option>
            {repositories.map(repo => (
              <option key={repo.id} value={repo.id}>
                {repo.name}
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginBottom: '1rem' }}>
          <label>Branch Name:</label>
          <input
            type="text"
            value={branchName}
            onChange={e => setBranchName(e.target.value)}
            required
            style={{ width: '100%', padding: '0.5rem' }}
          />
        </div>

        <div style={{ marginBottom: '1rem' }}>
          <label>Files:</label>
          <input
            type="file"
            multiple
            onChange={e => setFiles(Array.from(e.target.files))}
          />
        </div>

        <div style={{ marginBottom: '1rem' }}>
          <label>Commit Message:</label>
          <textarea
            value={message}
            onChange={e => setMessage(e.target.value)}
            rows="3"
            style={{ width: '100%', padding: '0.5rem' }}
            required
          />
        </div>

        <button type="submit" style={{ padding: '0.5rem 1rem' }}>
          Push Files
        </button>
      </form>
    </div>
  );
};

export default PushFiles;
