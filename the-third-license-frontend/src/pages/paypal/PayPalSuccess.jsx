// src/pages/paypal/PayPalSuccess.jsx
import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

const PayPalSuccess = () => {
  const [message, setMessage] = useState('Capturing your PayPal payment...');
  const [done, setDone] = useState(false);

  useEffect(() => {
    const capture = async () => {
      try {
        const params = new URLSearchParams(window.location.search);

        // PayPal returns token=<ORDER_ID>
        const orderId = params.get('token') || params.get('orderId') || params.get('order_id');

        if (!orderId) {
          setMessage('❌ Missing PayPal orderId in the URL.');
          setDone(true);
          return;
        }

        // Capture TOP-UP (Option A)
        // This hits: POST /api/paypal/topup/capture?orderId=...
        await axios.post('/paypal/topup/capture', null, {
          params: { orderId },
        });

        const pendingUsd = localStorage.getItem('pendingTopUpUsd');
        const offerId = localStorage.getItem('pendingTopUpOfferId');

        localStorage.removeItem('pendingTopUpUsd');
        localStorage.removeItem('pendingTopUpOfferId');

        if (pendingUsd && offerId) {
          setMessage(
            `✅ Payment captured. Wallet topped up successfully ($${pendingUsd}). You can now buy offer #${offerId} using tokens.`
          );
        } else {
          setMessage('✅ Payment captured. Wallet topped up successfully!');
        }

        setDone(true);
      } catch (err) {
        setMessage('❌ Failed to capture PayPal order: ' + (err.response?.data || err.message || ''));
        setDone(true);
      }
    };

    capture();
  }, []);

  return (
    <div className="container p-4">
      <Navbar />
      <h2 className="text-2xl font-bold mb-4">PayPal Success</h2>
      <p style={{ color: message.startsWith('✅') ? 'green' : 'red' }}>{message}</p>

      {done && (
        <div className="mt-4">
          <a href="/my-tokens" className="text-blue-600 underline">
            Go to My Tokens
          </a>
          <span style={{ margin: '0 10px' }}>|</span>
          <a href="/marketplace" className="text-blue-600 underline">
            Back to Marketplace
          </a>
        </div>
      )}
    </div>
  );
};

export default PayPalSuccess;
