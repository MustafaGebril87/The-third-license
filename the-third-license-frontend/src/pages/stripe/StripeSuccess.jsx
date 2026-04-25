import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

const StripeSuccess = () => {
  const [message, setMessage] = useState('Confirming your purchase...');
  const [done, setDone] = useState(false);

  useEffect(() => {
    const confirm = async () => {
      try {
        const params = new URLSearchParams(window.location.search);
        const sessionId = params.get('session_id');

        if (!sessionId) {
          setMessage('Missing session_id in the URL.');
          setDone(true);
          return;
        }

        await axios.post('/shares/buy/stripe/confirm', null, {
          params: { sessionId },
        });

        setMessage('Payment confirmed. Share ownership has been transferred to you.');
        setDone(true);
      } catch (err) {
        setMessage('Failed to confirm purchase: ' + (err.response?.data || err.message || ''));
        setDone(true);
      }
    };

    confirm();
  }, []);

  return (
    <div className="container p-4">
      <Navbar />
      <h2 className="text-2xl font-bold mb-4">Payment Successful</h2>
      <p style={{ color: message.startsWith('Failed') ? 'red' : 'green' }}>{message}</p>

      {done && (
        <div className="mt-4">
          <a href="/shares" className="text-blue-600 underline">Go to My Shares</a>
          <span style={{ margin: '0 10px' }}>|</span>
          <a href="/marketplace" className="text-blue-600 underline">Back to Marketplace</a>
        </div>
      )}
    </div>
  );
};

export default StripeSuccess;
