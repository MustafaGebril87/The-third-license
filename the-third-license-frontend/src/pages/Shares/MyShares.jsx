import { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';
import React from 'react';
import PayoutsPanel from './PayoutsPanel';

const MyShares = () => {
  const [shares, setShares] = useState([]);
  const [loading, setLoading] = useState(true);
  const [splitPercent, setSplitPercent] = useState({});
  const [salePrice, setSalePrice] = useState({});
  const [message, setMessage] = useState('');
  const [payoutsEnabled, setPayoutsEnabled] = useState(false);

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
      await axios.post(`/shares/${shareId}/split`, null, { params: { percentage: percent } });
      await loadShares();
      setMessage('✅ Share split successfully');
    } catch (err) {
      setMessage('❌ Failed to split share: ' + (err.response?.data || err.message));
    }
  };

  const handleMarkForSale = async (shareId) => {
    try {
      const price = salePrice[shareId];
      await axios.post(`/shares/${shareId}/mark-for-sale`, null, { params: { price } });
      await loadShares();
      setMessage('✅ Share marked for sale');
    } catch (err) {
      setMessage('❌ Failed to mark share for sale: ' + (err.response?.data || err.message));
    }
  };

  const handleUnmark = async (shareId) => {
    try {
      await axios.post(`/shares/${shareId}/unmark-for-sale`);
      await loadShares();
      setMessage('✅ Share unmarked for sale');
    } catch (err) {
      setMessage('❌ Failed to unmark share: ' + (err.response?.data || err.message));
    }
  };

  if (loading) return <p>Loading shares...</p>;

  return (
    <div className="container">
      <Navbar />
      <h2>My Shares</h2>
      <PayoutsPanel onStatus={(s) => setPayoutsEnabled(!!s.payoutsEnabled)} />
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
              <strong>{share.companyName}</strong> — {Number(share.percentage).toFixed(4)}%
              <span style={{ color: '#777' }}> ({Number(share.units).toLocaleString()} units)</span>{' '}
              {share.forSale ? `(For sale at $${Number(share.price).toFixed(2)})` : ''}

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
                  min="0.50"
                  placeholder="Sale price (min $0.50)"
                  onChange={(e) =>
                    setSalePrice((prev) => ({
                      ...prev,
                      [share.id]: e.target.value,
                    }))
                  }
                />
                <button
                  onClick={() => handleMarkForSale(share.id)}
                  disabled={!payoutsEnabled}
                  title={payoutsEnabled ? '' : 'Set up payouts first'}
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
