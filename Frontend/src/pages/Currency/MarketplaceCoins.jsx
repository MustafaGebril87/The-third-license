// src/pages/Currency/MarketplaceCoins.jsx
import React, { useEffect, useState } from 'react';
import axios from '../../api/axios';
import { useAuth } from '../../context/AuthContext';
import Navbar from '../../components/Navbar';

const ENDPOINTS = {
  // ✅ change these 2 strings if your backend paths are different
  SHARES_FOR_SALE: '/shares/for-sale',          // e.g. GET returns shares listed by others
  BUY_SHARE_WITH_TOKENS: '/shares/buy',         // e.g. POST params: shareId, price
  COIN_OFFERS: '/coin-market/offers',           // GET coin offers
  PAYPAL_TOPUP_CREATE: '/paypal/topup/create',  // POST params: coinAmount, returnUrl, cancelUrl
};

const MarketplaceCoins = () => {
  useAuth(); // ensures JWT exists

  // Shares
  const [shares, setShares] = useState([]);
  const [buyingShare, setBuyingShare] = useState({}); // shareId -> boolean

  // Coin offers
  const [offers, setOffers] = useState([]);
  const [buyingOffer, setBuyingOffer] = useState({}); // offerId -> boolean

  // UI
  const [message, setMessage] = useState('');

  useEffect(() => {
    refreshAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refreshAll = async () => {
    setMessage('');
    await Promise.allSettled([fetchShares(), fetchOffers()]);
  };

  const fetchShares = async () => {
    try {
      const res = await axios.get(ENDPOINTS.SHARES_FOR_SALE);
      setShares(res.data || []);
    } catch (err) {
      // don't block page if shares fail
      setShares([]);
    }
  };

  const fetchOffers = async () => {
    try {
      const res = await axios.get(ENDPOINTS.COIN_OFFERS);
      setOffers(res.data || []);
    } catch (err) {
      setOffers([]);
      setMessage('Failed to load coin offers');
    }
  };

  // ----------------------------
  // Shares: buy with tokens/coins
  // ----------------------------
  const buyShareWithTokens = async (share) => {
    if (!share?.id) return;
    if (buyingShare[share.id]) return;

    try {
      setMessage('');
      setBuyingShare((prev) => ({ ...prev, [share.id]: true }));

      // Price: try common fields, fallback to 0
      const price =
        Number(share.price) ||
        Number(share.priceUsd) ||
        Number(share.listingPrice) ||
        0;

      if (!Number.isFinite(price) || price <= 0) {
        setMessage('This share has no valid price.');
        return;
      }

      // Calls your backend which already spends tokens and transfers ownership
      // based on your earlier TokenService.purchaseShare(buyerId, shareId, price)
      await axios.post(ENDPOINTS.BUY_SHARE_WITH_TOKENS, null, {
        params: { shareId: share.id, price },
      });

      setMessage('✅ Share purchased successfully.');
      await fetchShares(); // refresh
    } catch (err) {
      setMessage('❌ Failed to buy share: ' + (err.response?.data || err.message || ''));
    } finally {
      setBuyingShare((prev) => ({ ...prev, [share.id]: false }));
    }
  };

  // ------------------------------------
  // Offers: Option A PayPal top-up only
  // ------------------------------------
  const topUpWithPayPalForOffer = async (offer) => {
    if (!offer?.id) return;
    if (buyingOffer[offer.id]) return;

    try {
      setMessage('');
      setBuyingOffer((prev) => ({ ...prev, [offer.id]: true }));

      const coins = Number(offer.coinAmount || 0);
      const ppc = Number(offer.pricePerCoin || 0);
      const totalUsd = coins * ppc;

      if (!Number.isFinite(totalUsd) || totalUsd <= 0) {
        setMessage('Invalid offer total.');
        return;
      }

      localStorage.setItem('pendingTopUpOfferId', String(offer.id));
      localStorage.setItem('pendingTopUpUsd', totalUsd.toFixed(2));

      const returnUrl = `${window.location.origin}/paypal/success`;
      const cancelUrl = `${window.location.origin}/paypal/cancel`;

      const res = await axios.post(ENDPOINTS.PAYPAL_TOPUP_CREATE, null, {
        params: { coinAmount: totalUsd, returnUrl, cancelUrl },
      });

      const approvalUrl = res?.data?.approvalUrl;
      if (!approvalUrl) {
        localStorage.removeItem('pendingTopUpOfferId');
        localStorage.removeItem('pendingTopUpUsd');
        setMessage('Failed: backend did not return approvalUrl');
        return;
      }

      window.location.href = approvalUrl;
    } catch (err) {
      localStorage.removeItem('pendingTopUpOfferId');
      localStorage.removeItem('pendingTopUpUsd');
      setMessage('❌ Failed to start PayPal top-up: ' + (err.response?.data || err.message || ''));
    } finally {
      setBuyingOffer((prev) => ({ ...prev, [offer.id]: false }));
    }
  };

  return (
    <div className="p-4">
      <Navbar />

      <div className="flex items-center justify-between mt-4">
        <h2 className="text-2xl font-bold">Marketplace</h2>
        <button
          onClick={refreshAll}
          className="border px-3 py-1 rounded hover:bg-gray-100"
        >
          Refresh
        </button>
      </div>

      {message && (
        <p className="mt-3" style={{ color: message.startsWith('✅') ? 'green' : 'red' }}>
          {message}
        </p>
      )}

      {/* ------------------ */}
      {/* Shares for Sale */}
      {/* ------------------ */}
      <div className="mt-6">
        <h3 className="text-xl font-bold mb-3">Shares for Sale</h3>

        {shares.length === 0 ? (
          <p>No shares are currently for sale.</p>
        ) : (
          <ul className="space-y-4">
            {shares.map((s) => {
              const price =
                Number(s.price) ||
                Number(s.priceUsd) ||
                Number(s.listingPrice) ||
                0;

              return (
                <li key={s.id} className="border p-4 rounded shadow">
                  <div><strong>Company:</strong> {s.company?.name || s.companyName || '—'}</div>
                  <div><strong>Share %:</strong> {s.percentage ?? s.percent ?? '—'}</div>
                  <div><strong>Price:</strong> {price > 0 ? `$${price.toFixed(2)}` : '—'}</div>
                  <div><strong>Seller:</strong> {s.owner?.email || s.seller?.email || s.ownerId || '—'}</div>

                  <button
                    onClick={() => buyShareWithTokens(s)}
                    disabled={!!buyingShare[s.id]}
                    className="mt-2 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-60"
                  >
                    {buyingShare[s.id] ? 'Buying...' : 'Buy Share with Tokens'}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* ------------------ */}
      {/* Coin Offers */}
      {/* ------------------ */}
      <div className="mt-10">
        <h3 className="text-xl font-bold mb-3">Coin Offers</h3>

        {offers.length === 0 ? (
          <p>No coin offers available right now.</p>
        ) : (
          <ul className="space-y-4">
            {offers.map((offer) => {
              const coins = Number(offer.coinAmount || 0);
              const ppc = Number(offer.pricePerCoin || 0);
              const total = coins * ppc;

              return (
                <li key={offer.id} className="border p-4 rounded shadow">
                  <div><strong>Coins:</strong> {coins}</div>
                  <div><strong>Price per coin:</strong> ${ppc.toFixed(2)}</div>
                  <div><strong>Total:</strong> ${total.toFixed(2)} + 1% PayPal fee</div>
                  <div><strong>From Token:</strong> {offer.sourceToken?.id || offer.sourceTokenId || '—'}</div>
                  <div><strong>Seller:</strong> {offer.seller?.email || offer.sellerId || '—'}</div>

                  <button
                    onClick={() => topUpWithPayPalForOffer(offer)}
                    disabled={!!buyingOffer[offer.id]}
                    className="mt-2 bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 disabled:opacity-60"
                  >
                    {buyingOffer[offer.id] ? 'Redirecting...' : 'Top up wallet with PayPal (Sandbox)'}
                  </button>

                  <div className="mt-2 text-sm text-gray-600">
                    After top-up completes, buy the offer using tokens (wallet balance).
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
};

export default MarketplaceCoins;
