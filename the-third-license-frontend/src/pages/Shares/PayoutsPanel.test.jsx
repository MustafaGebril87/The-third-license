import '@testing-library/jest-dom';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import PayoutsPanel from './PayoutsPanel';

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

import axiosMock from '../../api/axios';

describe('PayoutsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('offers payout setup when the user has no connected account', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { connected: false, payoutsEnabled: false } });
    const onStatus = vi.fn();

    render(<PayoutsPanel onStatus={onStatus} />);

    expect(await screen.findByRole('button', { name: 'Set up payouts' })).toBeInTheDocument();
    expect(onStatus).toHaveBeenCalledWith({ connected: false, payoutsEnabled: false });
  });

  it('redirects to the Stripe onboarding URL from the server', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { connected: false, payoutsEnabled: false } });
    axiosMock.post.mockResolvedValueOnce({ data: { url: 'https://connect.stripe.com/setup/abc' } });
    const location = { href: '' };
    vi.stubGlobal('location', location);

    render(<PayoutsPanel />);
    fireEvent.click(await screen.findByRole('button', { name: 'Set up payouts' }));

    await waitFor(() => expect(location.href).toBe('https://connect.stripe.com/setup/abc'));
    expect(axiosMock.post).toHaveBeenCalledWith('/payouts/onboard');
  });

  it('shows continue-setup when onboarding is unfinished', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { connected: true, payoutsEnabled: false } });

    render(<PayoutsPanel />);

    expect(await screen.findByRole('button', { name: 'Continue setup' })).toBeInTheDocument();
  });

  it('shows active payouts and the dashboard button when enabled', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { connected: true, payoutsEnabled: true } });

    render(<PayoutsPanel />);

    expect(await screen.findByText(/Payouts are active/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open Stripe dashboard' })).toBeInTheDocument();
  });
});
