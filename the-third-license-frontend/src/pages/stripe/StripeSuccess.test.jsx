import { render, screen, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import StripeSuccess from './StripeSuccess';

vi.mock('../../components/Navbar', () => ({ default: () => <div data-testid="navbar" /> }));
vi.mock('../../api/axios', () => ({
  default: { post: vi.fn() },
}));

import axiosMock from '../../api/axios';

describe('StripeSuccess', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders the Payment Successful heading', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test' });
    axiosMock.post.mockResolvedValueOnce({});
    await act(async () => { render(<StripeSuccess />); });
    expect(screen.getByRole('heading', { name: 'Payment Successful' })).toBeInTheDocument();
  });

  it('shows success message after capture completes', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test_123' });
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText('Payment captured. Wallet topped up successfully!')).toBeInTheDocument();
    });
  });

  it('includes pending offer info in success message when localStorage is set', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test_456' });
    localStorage.setItem('pendingTopUpUsd', '20.00');
    localStorage.setItem('pendingTopUpOfferId', 'offer-99');
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText(/offer #offer-99/)).toBeInTheDocument();
    });
    expect(localStorage.getItem('pendingTopUpUsd')).toBeNull();
    expect(localStorage.getItem('pendingTopUpOfferId')).toBeNull();
  });

  it('shows error message when session_id is missing', async () => {
    vi.stubGlobal('location', { search: '' });

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText('Missing session_id in the URL.')).toBeInTheDocument();
    });
  });

  it('shows error message when capture request fails', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_fail' });
    axiosMock.post.mockRejectedValueOnce({ message: 'Network Error' });

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText(/Failed to capture payment/)).toBeInTheDocument();
    });
  });

  it('shows navigation links after operation completes', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_nav_test' });
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Go to My Tokens' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Back to Marketplace' })).toBeInTheDocument();
    });
  });

  it('calls capture endpoint with correct sessionId', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_verify_call' });
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(axiosMock.post).toHaveBeenCalledWith(
        '/stripe/topup/capture',
        null,
        { params: { sessionId: 'cs_verify_call' } }
      );
    });
  });
});
