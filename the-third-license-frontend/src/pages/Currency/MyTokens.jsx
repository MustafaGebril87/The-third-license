import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

const MyTokens = () => {
  const [tokens, setTokens] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [offers, setOffers] = useState({});
  const [priceInputs, setPriceInputs] = useState({});
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('info'); // 'info' | 'error' | 'success'

  useEffect(() => {
    const fetchTokens = async () => {
      try {
        const res = await axios.get('/tokens/my'); // -> /api/tokens/my
        console.log('Loaded tokens:', res.data);
        setTokens(res.data || []);
      } catch (err) {
        console.error('Failed to load tokens:', err);
        setError('Failed to load tokens');
      } finally {
        setLoading(false);
      }
    };

    fetchTokens();
  }, []);

  const handleOfferChange = (tokenKey, value) => {
    setOffers(prev => ({ ...prev, [tokenKey]: value }));
  };

  const handlePriceChange = (tokenKey, value) => {
    setPriceInputs(prev => ({ ...prev, [tokenKey]: value }));
  };

  const handleOfferSubmit = async (tokenId, maxAvailable) => {
    const amount = parseInt(offers[tokenId]);
    const price = parseFloat(priceInputs[tokenId]);

    if (isNaN(amount) || amount <= 0 || amount > maxAvailable) {
      setMessageType('error');
      setMessage('Invalid amount');
      return;
    }
    if (isNaN(price) || price <= 0) {
      setMessageType('error');
      setMessage('Invalid price');
      return;
    }

    console.log('Submitting offer with:', { tokenId, amount, price });

    try {
      // baseURL assumed: http://localhost:8080/api
      // final URL: POST /api/coin-market/offer?tokenId=...&coinAmount=...&pricePerCoin=...
      await axios.post('/coin-market/offer', null, {
        params: {
          tokenId,
          coinAmount: amount,
          pricePerCoin: price,
        },
      });

      setMessageType('success');
      setMessage(`Offered ${amount} coins from token ${tokenId}`);
    } catch (err) {
      console.error('Offer error:', err.response || err);
      setMessageType('error');

      const backendMsg =
        err?.response?.data && typeof err.response.data === 'string'
          ? err.response.data
          : 'Failed to create offer';

      setMessage(backendMsg);
    }
  };

  if (loading) {
    return (
      <div className="container">
        <Navbar />
        <p>Loading tokens...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <Navbar />
        <p style={{ color: 'red' }}>{error}</p>
      </div>
    );
  }

  return (
    <div className="container">
      <Navbar />
      <h2>My Tokens</h2>

      {tokens.length === 0 ? (
        <p>You have no currency tokens.</p>
      ) : (
        <ul>
          {tokens.map(token => {
            const tokenId = token.id;           // 👈 requires backend to send id
            const tokenValue = token.tokenValue; // nice to show to user
            const amount = token.amount;

            console.log('Rendering token:', token);

            if (!tokenId) {
              return (
                <li
                  key={tokenValue || Math.random()}
                  style={{ marginBottom: '1rem', color: 'red' }}
                >
                  Invalid token object (no id): {JSON.stringify(token)}
                </li>
              );
            }

            return (
              <li key={tokenId} style={{ marginBottom: '1rem' }}>
                <div>
                  <strong>Token:</strong> {tokenValue || tokenId}
                </div>
                <div>
                  <strong>Amount:</strong> {amount}
                </div>

                <input
                  type="number"
                  placeholder="Coins to offer"
                  value={offers[tokenId] || ''}
                  onChange={e => handleOfferChange(tokenId, e.target.value)}
                  style={{ marginRight: '0.5rem' }}
                />

                <input
                  type="number"
                  placeholder="Price per coin"
                  value={priceInputs[tokenId] || ''}
                  onChange={e => handlePriceChange(tokenId, e.target.value)}
                  style={{ marginRight: '0.5rem' }}
                />

                <button onClick={() => handleOfferSubmit(tokenId, amount)}>
                  Offer
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {message && (
        <div
          style={{
            marginTop: '1rem',
            color: messageType === 'error' ? 'red' : 'green',
          }}
        >
          {message}
        </div>
      )}
    </div>
  );
};

export default MyTokens;
