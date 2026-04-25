import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import Navbar from '../../components/Navbar';

const ShareMarketplace = () => {
  useAuth();

  const [shares, setShares] = useState([]);
  const [buying, setBuying] = useState({});
  const [message, setMessage] = useState('');

  useEffect(() => {
    fetchShares();
  }, []);

  const fetchShares = async () => {
    try {
      const res = await axios.get('/shares/marketplace');
      setShares(res.data || []);
    } catch {
      setShares([]);
    }
  };

  const buyShareWithStripe = async (share) => {
    if (!share?.id || buying[share.id]) return;

    const price = Number(share.price);
    if (!Number.isFinite(price) || price <= 0) {
      setMessage('This share has no valid price.');
      return;
    }

    setBuying((prev) => ({ ...prev, [share.id]: true }));
    setMessage('');

    try {
      const successUrl = `${window.location.origin}/stripe/success?shareId=${share.id}`;
      const cancelUrl  = `${window.location.origin}/stripe/cancel`;

      const res = await axios.post(
        `/shares/buy/${share.id}/stripe/create`,
        null,
        { params: { successUrl, cancelUrl } }
      );

      const checkoutUrl = res?.data?.checkoutUrl;
      if (!checkoutUrl) {
        setMessage('Error: no checkout URL returned.');
        return;
      }

      window.location.href = checkoutUrl;
    } catch (err) {
      setMessage('Failed to start checkout: ' + (err.response?.data || err.message || ''));
    } finally {
      setBuying((prev) => ({ ...prev, [share.id]: false }));
    }
  };

  return (
    <div className="p-4">
      <Navbar />

      <div className="flex items-center justify-between mt-4">
        <h2 className="text-2xl font-bold">Share Marketplace</h2>
        <button
          onClick={fetchShares}
          className="border px-3 py-1 rounded hover:bg-gray-100"
        >
          Refresh
        </button>
      </div>

      {message && (
        <p className="mt-3" style={{ color: message.startsWith('Failed') || message.startsWith('Error') ? 'red' : 'green' }}>
          {message}
        </p>
      )}

      <div className="mt-6">
        {shares.length === 0 ? (
          <p>No shares are currently for sale.</p>
        ) : (
          <ul className="space-y-4">
            {shares.map((s) => {
              const price = Number(s.price);
              return (
                <li key={s.id} className="border p-4 rounded shadow">
                  <div><strong>Company:</strong> {s.companyName || '—'}</div>
                  <div><strong>Ownership:</strong> {s.percentage ?? '—'}%</div>
                  <div><strong>Price:</strong> {price > 0 ? `$${price.toFixed(2)}` : '—'}</div>
                  <div><strong>Seller:</strong> {s.ownerUsername || s.ownerId || '—'}</div>

                  <button
                    onClick={() => buyShareWithStripe(s)}
                    disabled={!!buying[s.id]}
                    className="mt-3 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-60"
                  >
                    {buying[s.id] ? 'Redirecting to Stripe...' : 'Buy with Stripe'}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
};

export default ShareMarketplace;
