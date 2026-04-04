import { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';
import React from 'react';

const MyShares = () => {
  const [shares, setShares] = useState([]);
  const [loading, setLoading] = useState(true);
  const [splitPercent, setSplitPercent] = useState({});
  const [salePrice, setSalePrice] = useState({});
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadShares();
  }, []);

  const loadShares = async () => {
    try {
      const res = await axios.get('/shares/my');
      setShares(res.data);
      setMessage('');
    } catch (err) {
      setMessage('Failed to load shares');
    } finally {
      setLoading(false);
    }
  };

  const handleSplit = async (shareId) => {
    try {
      const percent = splitPercent[shareId];
      await axios.post(`/shares/${shareId}/split?percentage=${percent}`);
      await loadShares();
      setMessage('✅ Share split successfully');
    } catch (err) {
      setMessage('❌ Failed to split share');
    }
  };

  const handleMarkForSale = async (shareId) => {
    try {
      const price = salePrice[shareId];
      await axios.post(`/shares/${shareId}/mark-for-sale?price=${price}`);
      await loadShares();
      setMessage('✅ Share marked for sale');
    } catch (err) {
      setMessage('❌ Failed to mark share for sale');
    }
  };

  const handleUnmark = async (shareId) => {
    try {
      await axios.post(`/shares/${shareId}/unmark-for-sale`);
      await loadShares();
      setMessage('✅ Share unmarked for sale');
    } catch (err) {
      setMessage('❌ Failed to unmark share');
    }
  };

  if (loading) return <p>Loading shares...</p>;

  return (
    <div className="container">
      <Navbar />
      <h2>My Shares</h2>
      {message && (
        <p style={{ color: message.startsWith('✅') ? 'green' : 'red' }}>
          {message}
        </p>
      )}
      {shares.length === 0 ? (
        <p>You don't own any shares.</p>
      ) : (
        <ul>
          {shares.map((share) => (
            <li key={share.id} style={{ marginBottom: '1rem' }}>
              <strong>{share.repositoryName}</strong> — {share.percentage}%{' '}
              {share.forSale ? '(For Sale)' : ''}
              <br />
              <span style={{ fontStyle: 'italic', color: '#777' }}>
                Company: {share.companyName}
              </span>

              <div style={{ marginTop: '0.5rem' }}>
                <input
                  type="number"
                  step="0.01"
                  placeholder="Split %"
                  onChange={(e) =>
                    setSplitPercent((prev) => ({
                      ...prev,
                      [share.id]: e.target.value,
                    }))
                  }
                />
                <button
                  onClick={() => handleSplit(share.id)}
                  style={{ marginLeft: '0.5rem' }}
                >
                  Split
                </button>
              </div>

              <div style={{ marginTop: '0.5rem' }}>
                <input
                  type="number"
                  step="0.01"
                  placeholder="Sale price"
                  onChange={(e) =>
                    setSalePrice((prev) => ({
                      ...prev,
                      [share.id]: e.target.value,
                    }))
                  }
                />
                <button
                  onClick={() => handleMarkForSale(share.id)}
                  style={{ marginLeft: '0.5rem' }}
                >
                  Mark For Sale
                </button>
                {share.forSale && (
                  <button
                    onClick={() => handleUnmark(share.id)}
                    style={{ marginLeft: '0.5rem' }}
                  >
                    Unmark
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default MyShares;
