import React, { useEffect, useState } from 'react';
import axios from '../api/axios';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';

const MergeConflictResolver = () => {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [conflicts, setConflicts] = useState([]);
  const [mergedContents, setMergedContents] = useState({});
  const [error, setError] = useState('');
  const [isOwner, setIsOwner] = useState(false);
  const navigate = useNavigate();

  const params = new URLSearchParams(window.location.search);
  const repositoryId = params.get('repositoryId');
  const branch = params.get('branch');

  useEffect(() => {
    verifyOwnerAndLoad();
  }, []);

  const verifyOwnerAndLoad = async () => {
    try {
      setLoading(true);

      if (!user) {
        setError("❌ User not authenticated.");
        return;
      }

      const currentUserId = user.id;

      // Cookies are sent automatically via withCredentials on the axios instance
      const repoRes = await axios.get(`/repositories/${repositoryId}`);
      const repo = repoRes.data;

      if (!repo) {
        setError("❌ Repository not found.");
        return;
      }

      const owner = repo.ownerId === currentUserId;
      setIsOwner(owner);
      const isContributor = repo.contributors?.includes(currentUserId);

      if (!owner && !isContributor) {
        setError("❌ You are not authorized to resolve this merge conflict.");
        return;
      }

      const fileListRes = await axios.get('/contributions/merge/files', {
        params: { repositoryId, branch },
      });

      const fileList = fileListRes.data;
      if (!fileList || fileList.length === 0) {
        setError('ℹ️ No files to merge.');
        return;
      }

      const diffRes = await axios.get('/contributions/merge/diff', {
        params: {
          repositoryId,
          branch,
          filePath: fileList,
        },
        paramsSerializer: params =>
          `repositoryId=${params.repositoryId}&branch=${params.branch}&` +
          params.filePath.map(f => `filePath=${encodeURIComponent(f)}`).join('&'),
      });

      const conflictFiles = diffRes.data.conflicts;
      setConflicts(conflictFiles);

      const initial = {};
      conflictFiles.forEach(file => {
        initial[file.filePath] = file.base || file.branch || '';
      });
      setMergedContents(initial);
    } catch (err) {
      console.error('❌ verifyOwnerAndLoad error:', err);
      setError("❌ Failed to verify repository ownership.");
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (filePath, content) => {
    setMergedContents(prev => ({ ...prev, [filePath]: content }));
  };

  const handleSelect = (filePath, version) => {
    const selected = conflicts.find(f => f.filePath === filePath)[version];
    handleChange(filePath, selected);
  };

  const handleSubmit = async () => {
    try {
      const files = Object.entries(mergedContents).map(([filePath, mergedContent]) => ({
        filePath,
        mergedContent,
      }));

      const mergeType = isOwner ? 'MERGE_REQUEST' : 'PULL_CONFLICT';

      await axios.post('/contributions/merge-branch', {
        repositoryId,
        branch,
        files,
        mergeType,
      });

      alert('✅ Merge submitted successfully');
      navigate('/admin/dashboard');
    } catch (e) {
      console.error(e);
      alert('❌ Merge failed.');
    }
  };

  if (error) return <div className="text-red-500 p-4">{error}</div>;
  if (loading) return <div className="p-4">Loading...</div>;

  return (
    <div>
      <Navbar />
      <div className="p-6">
        <h1 className="text-xl font-semibold mb-4">Resolve Merge Conflicts</h1>
        {conflicts.map((file, idx) => (
          <div key={idx} className="border rounded-xl shadow p-4 mb-6">
            <h2 className="font-bold text-lg mb-2">{file.filePath}</h2>
            <div className="grid grid-cols-3 gap-4 mb-4">
              <textarea
                readOnly
                value={file.base}
                className="p-2 border rounded text-sm bg-gray-100"
                placeholder="Base version"
              />
              <textarea
                readOnly
                value={file.branch}
                className="p-2 border rounded text-sm bg-gray-100"
                placeholder="Branch version"
              />
              <textarea
                value={mergedContents[file.filePath] || ''}
                onChange={e => handleChange(file.filePath, e.target.value)}
                className="p-2 border rounded text-sm"
                placeholder="Merged result"
              />
            </div>
            <div className="flex gap-2">
              <button
                className="bg-blue-500 text-white px-4 py-1 rounded"
                onClick={() => handleSelect(file.filePath, 'base')}
              >
                Use Base
              </button>
              <button
                className="bg-green-500 text-white px-4 py-1 rounded"
                onClick={() => handleSelect(file.filePath, 'branch')}
              >
                Use Branch
              </button>
            </div>
          </div>
        ))}
        <button
          className="bg-purple-600 text-white px-6 py-2 rounded mt-4"
          onClick={handleSubmit}
        >
          Submit Merge
        </button>
      </div>
    </div>
  );
};

export default MergeConflictResolver;
