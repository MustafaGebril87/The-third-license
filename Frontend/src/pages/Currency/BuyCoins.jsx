import React, { useState } from 'react';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

const BuyCoins = () => {
  const [coinAmount, setCoinAmount] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const startPayPalTopUp = async () => {
    setMessage('');

    const amount = parseFloat(coinAmount);
    if (isNaN(amount) || amount <= 0) {
      setMessage('❌ Enter a valid coin amount');
      return;
    }

    try {
      setLoading(true);

      const returnUrl = `${window.location.origin}/paypal/success`;
      const cancelUrl = `${window.location.origin}/paypal/cancel`;

      const res = await axios.post('/paypal/topup/create', null, {
        params: { coinAmount: amount, returnUrl, cancelUrl }
      });

      if (res?.data?.approvalUrl) {
        window.location.href = res.data.approvalUrl;
        return;
      }

      setMessage('❌ PayPal approvalUrl not returned by backend.');
    } catch (err) {
      setMessage('❌ Failed to start PayPal checkout: ' + (err.response?.data || ''));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container p-4">
      <Navbar />
      <h2 className="text-2xl font-bold mb-4">Buy Coins with PayPal</h2>

      {message && <p style={{ color: message.startsWith('❌') ? 'red' : 'green' }}>{message}</p>}

      <div className="border p-4 rounded" style={{ maxWidth: 420 }}>
        <label className="block mb-2">
          Amount of coins (1 coin = $1 for now):
        </label>

        <input
          type="number"
          value={coinAmount}
          onChange={(e) => setCoinAmount(e.target.value)}
          placeholder="e.g. 10"
          className="border p-2 rounded w-full"
          min="0"
          step="0.01"
        />

        <button
          onClick={startPayPalTopUp}
          disabled={loading}
          className="mt-3 bg-blue-600 text-white px-4 py-2 rounded w-full"
        >
          {loading ? 'Redirecting to PayPal...' : 'Buy with PayPal'}
        </button>

        <p className="mt-2 text-sm" style={{ opacity: 0.8 }}>
          A 1% fee will be added at checkout.
        </p>
      </div>
    </div>
  );
};

export default BuyCoins;
