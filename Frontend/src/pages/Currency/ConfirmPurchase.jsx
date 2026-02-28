import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import axios from '../../api/axios';

const ConfirmPurchase = () => {
  const [params] = useSearchParams();
  const [status, setStatus] = useState('Processing...');

  useEffect(() => {
    const confirm = async () => {
      try {
        const orderId = params.get('token');
        const offerId = params.get('offerId');
        const buyerId = params.get('buyerId');

        const res = await axios.post('/coin-market/confirm', null, {
          params: { orderId, offerId, buyerId }
        });
        setStatus(res.data.message);
      } catch (err) {
        setStatus('Payment confirmation failed');
      }
    };

    confirm();
  }, [params]);

  return (
    <div className="p-4">
      <h2 className="text-lg font-semibold">{status}</h2>
    </div>
  );
};

export default ConfirmPurchase;
