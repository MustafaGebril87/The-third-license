import React from 'react';
import Navbar from '../../components/Navbar';

const StripeCancel = () => {
  return (
    <div className="container p-4">
      <Navbar />
      <h2 className="text-2xl font-bold mb-4">Payment Cancelled</h2>
      <p style={{ color: 'red' }}>You cancelled the checkout.</p>

      <div className="mt-4">
        <a href="/buy-coins" className="text-blue-600 underline">Try again</a>
        <span style={{ margin: '0 10px' }}>|</span>
        <a href="/marketplace" className="text-blue-600 underline">Back to Marketplace</a>
      </div>
    </div>
  );
};

export default StripeCancel;
