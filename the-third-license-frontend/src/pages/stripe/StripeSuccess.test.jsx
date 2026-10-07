import '@testing-library/jest-dom';
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
  });

  it('renders the Payment Successful heading', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test' });
    axiosMock.post.mockResolvedValueOnce({});
    await act(async () => { render(<StripeSuccess />); });
    expect(screen.getByRole('heading', { name: 'Payment Successful' })).toBeInTheDocument();
  });

  it('confirms the share purchase with the session id from the URL', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test_123' });
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(axiosMock.post).toHaveBeenCalledWith(
        '/shares/buy/stripe/confirm',
        null,
        { params: { sessionId: 'cs_test_123' } }
      );
    });
    expect(
      await screen.findByText('Payment confirmed. Share ownership has been transferred to you.')
    ).toBeInTheDocument();
  });

  it('reads session_id even when other query params come first', async () => {
    vi.stubGlobal('location', { search: '?foo=bar&session_id=cs_test_789' });
    axiosMock.post.mockResolvedValueOnce({});

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(axiosMock.post).toHaveBeenCalledWith(
        '/shares/buy/stripe/confirm',
        null,
        { params: { sessionId: 'cs_test_789' } }
      );
    });
  });

  it('shows error message when session_id is missing', async () => {
    vi.stubGlobal('location', { search: '' });

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText('Missing session_id in the URL.')).toBeInTheDocument();
    });
    expect(axiosMock.post).not.toHaveBeenCalled();
  });

  it('shows the server message when the purchase was refunded', async () => {
    vi.stubGlobal('location', { search: '?session_id=cs_test_999' });
    axiosMock.post.mockRejectedValueOnce({
      response: { status: 409, data: 'This share was sold or changed before your payment completed. Your payment has been refunded.' },
    });

    render(<StripeSuccess />);

    await waitFor(() => {
      expect(screen.getByText(/Your payment has been refunded/)).toBeInTheDocument();
    });
  });
});
