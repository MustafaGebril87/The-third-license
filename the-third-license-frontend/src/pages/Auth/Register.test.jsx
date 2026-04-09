import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Register from './Register';

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

// Mock axios
vi.mock('../../api/axios', () => ({
  default: { post: vi.fn() },
}));

import axiosMock from '../../api/axios';

describe('Register', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── Scenario: Page renders correctly ──────────────────────────────────────

  it('renders the Register heading', () => {
    render(<Register />);
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument();
  });

  it('renders username, email, password inputs and a submit button', () => {
    render(<Register />);
    expect(screen.getByPlaceholderText('Username')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Email')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Register' })).toBeInTheDocument();
  });

  // ── Scenario: User registers successfully ─────────────────────────────────

  it('shows success message and eventually navigates to /login', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    axiosMock.post.mockResolvedValueOnce({ data: 'User registered successfully' });

    render(<Register />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'alice@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => {
      expect(screen.getByText('User registered successfully!')).toBeInTheDocument();
    });

    expect(axiosMock.post).toHaveBeenCalledWith('/auth/register', {
      username: 'alice',
      email: 'alice@test.com',
      password: 'pass123',
    });

    // advance past the 1500ms navigation delay
    await vi.advanceTimersByTimeAsync(1500);
    expect(mockNavigate).toHaveBeenCalledWith('/login');

    vi.useRealTimers();
  });

  // ── Scenario: Registration does not navigate before timer fires ───────────

  it('does not navigate before the 1500ms delay', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    axiosMock.post.mockResolvedValueOnce({ data: 'User registered successfully' });

    render(<Register />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'alice@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => {
      expect(screen.getByText('User registered successfully!')).toBeInTheDocument();
    });

    // advance only 500ms — navigation should not have fired yet
    await vi.advanceTimersByTimeAsync(500);
    expect(mockNavigate).not.toHaveBeenCalled();

    vi.useRealTimers();
  });

  // ── Scenario: Credentials already taken (generic message to prevent enumeration) ──

  it('shows generic error when credentials are already taken', async () => {
    axiosMock.post.mockRejectedValueOnce({
      response: { data: 'Registration failed. Please try different credentials.' },
    });

    render(<Register />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'alice@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => {
      expect(screen.getByText('Registration failed. Please try different credentials.')).toBeInTheDocument();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ── Scenario: Generic server error ────────────────────────────────────────

  it('shows fallback error message when server error has no response body', async () => {
    axiosMock.post.mockRejectedValueOnce(new Error('Network Error'));

    render(<Register />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'alice@test.com' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => {
      expect(screen.getByText('Registration failed')).toBeInTheDocument();
    });
  });

  // ── Scenario: No error or success shown on initial render ─────────────────

  it('renders no success or error message initially', () => {
    render(<Register />);
    expect(screen.queryByText('User registered successfully!')).not.toBeInTheDocument();
    expect(screen.queryByText('Registration failed')).not.toBeInTheDocument();
  });

  // ── Scenario: Form fields are controlled ─────────────────────────────────

  it('updates all fields on user input', () => {
    render(<Register />);

    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'bob' } });
    fireEvent.change(screen.getByPlaceholderText('Email'), { target: { value: 'bob@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'secret' } });

    expect(screen.getByPlaceholderText('Username').value).toBe('bob');
    expect(screen.getByPlaceholderText('Email').value).toBe('bob@example.com');
    expect(screen.getByPlaceholderText('Password').value).toBe('secret');
  });
});
