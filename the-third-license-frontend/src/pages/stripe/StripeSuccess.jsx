import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

const StripeSuccess = () => {
  const [message, setMessage] = useState('Capturing your payment...');
  const [done, setDone] = useState(false);

  useEffect(() => {
    const capture = async () => {
      try {
        const params = new URLSearchParams(window.location.search);
        const sessionId = params.get('session_id');

        if (!sessionId) {
          setMessage('Missing session_id in the URL.');
          setDone(true);
          return;
        }

        await axios.post('/stripe/topup/capture', null, {
          params: { sessionId },
        });

        const pendingUsd = localStorage.getItem('pendingTopUpUsd');
        const offerId = localStorage.getItem('pendingTopUpOfferId');

        localStorage.removeItem('pendingTopUpUsd');
        localStorage.removeItem('pendingTopUpOfferId');

        if (pendingUsd && offerId) {
          setMessage(
            `Payment captured. Wallet topped up successfully ($${pendingUsd}). You can now buy offer #${offerId} using tokens.`
          );
        } else {
          setMessage('Payment captured. Wallet topped up successfully!');
        }

        setDone(true);
      } catch (err) {
        setMessage('Failed to capture payment: ' + (err.response?.data || err.message || ''));
        setDone(true);
      }
    };

    capture();
  }, []);

  return (
    <div className="container p-4">
      <Navbar />
      <h2 className="text-2xl font-bold mb-4">Payment Successful</h2>
      <p style={{ color: message.startsWith('Failed') ? 'red' : 'green' }}>{message}</p>

      {done && (
        <div className="mt-4">
          <a href="/my-tokens" className="text-blue-600 underline">Go to My Tokens</a>
          <span style={{ margin: '0 10px' }}>|</span>
          <a href="/marketplace" className="text-blue-600 underline">Back to Marketplace</a>
        </div>
      )}
    </div>
  );
};

export default StripeSuccess;
