import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';

/**
 * Seller payout setup (Stripe Connect). Sellers must finish Stripe's hosted onboarding
 * before they can list shares; sale proceeds go straight to their own Stripe account.
 */
const PayoutsPanel = ({ onStatus }) => {
  const [status, setStatus] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    axios.get('/payouts/status')
      .then((res) => {
        setStatus(res.data);
        onStatus?.(res.data);
      })
      .catch(() => setError('Could not load payout status.'));
  }, []);

  const startOnboarding = async () => {
    setBusy(true);
    setError('');
    try {
      const res = await axios.post('/payouts/onboard');
      window.location.href = res.data.url;
    } catch (err) {
      setError('Could not start payout setup: ' + (err.response?.data || err.message));
      setBusy(false);
    }
  };

  const openDashboard = async () => {
    setBusy(true);
    try {
      const res = await axios.post('/payouts/dashboard');
      window.open(res.data.url, '_blank', 'noopener');
    } catch (err) {
      setError('Could not open the Stripe dashboard: ' + (err.response?.data || err.message));
    } finally {
      setBusy(false);
    }
  };

  if (!status && !error) return <p>Checking payout setup…</p>;

  return (
    <div style={{ border: '1px solid #ddd', borderRadius: 6, padding: '0.75rem', margin: '1rem 0' }}>
      <strong>Payouts</strong>
      {error && <p style={{ color: 'red' }}>{error}</p>}

      {status?.payoutsEnabled ? (
        <p>
          ✅ Payouts are active — money from share sales goes to your Stripe account.{' '}
          <button onClick={openDashboard} disabled={busy}>Open Stripe dashboard</button>
        </p>
      ) : (
        <p>
          {status?.connected
            ? 'Your payout setup is not finished yet.'
            : 'To sell shares you need to set up payouts with Stripe.'}{' '}
          <button onClick={startOnboarding} disabled={busy}>
            {status?.connected ? 'Continue setup' : 'Set up payouts'}
          </button>
        </p>
      )}
    </div>
  );
};

export default PayoutsPanel;
