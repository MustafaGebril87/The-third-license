import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import StripeCancel from './StripeCancel';

vi.mock('../../components/Navbar', () => ({ default: () => <div data-testid="navbar" /> }));

describe('StripeCancel', () => {
  it('renders the cancellation heading', () => {
    render(<StripeCancel />);
    expect(screen.getByText('Payment Cancelled')).toBeInTheDocument();
  });

  it('renders the cancellation message', () => {
    render(<StripeCancel />);
    expect(screen.getByText('You cancelled the checkout.')).toBeInTheDocument();
  });

  it('renders a Try again link pointing to /buy-coins', () => {
    render(<StripeCancel />);
    const link = screen.getByRole('link', { name: 'Try again' });
    expect(link).toHaveAttribute('href', '/buy-coins');
  });

  it('renders a Back to Marketplace link pointing to /marketplace', () => {
    render(<StripeCancel />);
    const link = screen.getByRole('link', { name: 'Back to Marketplace' });
    expect(link).toHaveAttribute('href', '/marketplace');
  });

  it('renders the navbar', () => {
    render(<StripeCancel />);
    expect(screen.getByTestId('navbar')).toBeInTheDocument();
  });
});
