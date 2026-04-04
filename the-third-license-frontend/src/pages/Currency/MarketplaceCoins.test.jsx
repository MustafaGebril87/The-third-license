import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import MarketplaceCoins from './MarketplaceCoins';

vi.mock('../../components/Navbar', () => ({ default: () => <div data-testid="navbar" /> }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'test-user-id' } }),
}));
vi.mock('../../api/axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import axiosMock from '../../api/axios';

const sampleOffer = {
  id: 'offer-1',
  coinAmount: 100,
  pricePerCoin: 0.5,
  seller: { email: 'seller@test.com' },
  sourceToken: { id: 'token-1' },
};

describe('MarketplaceCoins', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    axiosMock.get.mockResolvedValue({ data: [] });
  });

  it('renders the Marketplace heading', async () => {
    await act(async () => { render(<MarketplaceCoins />); });
    expect(screen.getByText('Marketplace')).toBeInTheDocument();
  });

  it('renders section headings for shares and coin offers', async () => {
    await act(async () => { render(<MarketplaceCoins />); });
    expect(screen.getByText('Shares for Sale')).toBeInTheDocument();
    expect(screen.getByText('Coin Offers')).toBeInTheDocument();
  });

  it('shows empty state message when no coin offers exist', async () => {
    axiosMock.get.mockResolvedValue({ data: [] });
    render(<MarketplaceCoins />);
    await waitFor(() => {
      expect(screen.getByText('No coin offers available right now.')).toBeInTheDocument();
    });
  });

  it('shows empty state message when no shares exist', async () => {
    axiosMock.get.mockResolvedValue({ data: [] });
    render(<MarketplaceCoins />);
    await waitFor(() => {
      expect(screen.getByText('No shares are currently for sale.')).toBeInTheDocument();
    });
  });

  it('renders coin offer rows when offers are returned', async () => {
    axiosMock.get.mockImplementation((url) => {
      if (url.includes('coin-market')) return Promise.resolve({ data: [sampleOffer] });
      return Promise.resolve({ data: [] });
    });

    render(<MarketplaceCoins />);

    await waitFor(() => {
      expect(screen.getByText('100')).toBeInTheDocument(); // coinAmount
      expect(screen.getByText('$0.50')).toBeInTheDocument(); // pricePerCoin
    });
  });

  it('renders the Stripe top-up button for each offer', async () => {
    axiosMock.get.mockImplementation((url) => {
      if (url.includes('coin-market')) return Promise.resolve({ data: [sampleOffer] });
      return Promise.resolve({ data: [] });
    });

    render(<MarketplaceCoins />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Top up wallet with Stripe' })).toBeInTheDocument();
    });
  });

  it('renders a Refresh button', async () => {
    await act(async () => { render(<MarketplaceCoins />); });
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument();
  });

  it('shows error message when stripe checkout returns no checkoutUrl', async () => {
    axiosMock.get.mockImplementation((url) => {
      if (url.includes('coin-market')) return Promise.resolve({ data: [sampleOffer] });
      return Promise.resolve({ data: [] });
    });
    axiosMock.post.mockResolvedValueOnce({ data: {} }); // no checkoutUrl

    render(<MarketplaceCoins />);
    await waitFor(() => screen.getByRole('button', { name: 'Top up wallet with Stripe' }));

    fireEvent.click(screen.getByRole('button', { name: 'Top up wallet with Stripe' }));

    await waitFor(() => {
      expect(screen.getByText(/Failed: backend did not return checkoutUrl/)).toBeInTheDocument();
    });
  });

  it('shows error message when stripe checkout request fails', async () => {
    axiosMock.get.mockImplementation((url) => {
      if (url.includes('coin-market')) return Promise.resolve({ data: [sampleOffer] });
      return Promise.resolve({ data: [] });
    });
    axiosMock.post.mockRejectedValueOnce({ message: 'Network Error' });

    render(<MarketplaceCoins />);
    await waitFor(() => screen.getByRole('button', { name: 'Top up wallet with Stripe' }));

    fireEvent.click(screen.getByRole('button', { name: 'Top up wallet with Stripe' }));

    await waitFor(() => {
      expect(screen.getByText(/Failed to start Stripe checkout/)).toBeInTheDocument();
    });
  });
});
