import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from '../../api/axios';
import Navbar from '../../components/Navbar';

/** Stripe sends sellers here after onboarding. */
export const PayoutsReturn = () => {
  const [message, setMessage] = useState('Checking your payout setup…');

  useEffect(() => {
    axios.get('/payouts/status')
      .then((res) => setMessage(res.data.payoutsEnabled
        ? '✅ Payouts are set up. You can now list shares for sale.'
        : 'Stripe is still reviewing your details, or some information is missing. You can continue the setup from My Shares.'))
      .catch(() => setMessage('Could not check your payout setup. Try again from My Shares.'));
  }, []);

  return (
    <div className="container">
      <Navbar />
      <h2>Payout setup</h2>
      <p>{message}</p>
      <Link to="/shares">Go to My Shares</Link>
    </div>
  );
};

/** Stripe sends sellers here when an onboarding link expired; get a fresh one. */
export const PayoutsRefresh = () => {
  const [message, setMessage] = useState('Reopening Stripe setup…');

  useEffect(() => {
    axios.post('/payouts/onboard')
      .then((res) => { window.location.href = res.data.url; })
      .catch(() => setMessage('Could not reopen Stripe setup. Try again from My Shares.'));
  }, []);

  return (
    <div className="container">
      <Navbar />
      <p>{message}</p>
      <Link to="/shares">Go to My Shares</Link>
    </div>
  );
};
