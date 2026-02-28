import { useAuth } from '../context/AuthContext';

const OfferCoins = () => {
  const { user } = useAuth();
  const { user } = useAuth();
  const [coinAmount, setCoinAmount] = useState(0);
  const [pricePerCoin, setPricePerCoin] = useState(0);
  const [selectedTokenId, setSelectedTokenId] = useState(null);
  const [message, setMessage] = useState('');

  const handleOffer = async () => {
    try {
      const res = await axios.post('/coin-market/offer', null, {
        params: {
          tokenId: selectedTokenId, 
          coinAmount,
          pricePerCoin
        }
      });
      setMessage('Offer created');
    } catch (err) {
      setMessage('Failed to offer');
    }
  };
  // rest unchanged...
};
