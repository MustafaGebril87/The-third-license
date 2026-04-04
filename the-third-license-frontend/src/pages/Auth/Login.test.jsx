import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Login from './Login';

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

// Mock AuthContext
const mockLogin = vi.fn();
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin }),
}));

// Mock axios
vi.mock('../../api/axios', () => ({
  default: { post: vi.fn() },
}));

import axiosMock from '../../api/axios';

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Scenario: Page renders correctly ──────────────────────────────────────

  it('renders the Login heading', () => {
    render(<Login />);
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument();
  });

  it('renders username and password inputs and a submit button', () => {
    render(<Login />);
    expect(screen.getByPlaceholderText('Username')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Login' })).toBeInTheDocument();
  });

  // ── Scenario: User logs in successfully ───────────────────────────────────

  it('calls login and navigates to /dashboard on success', async () => {
    axiosMock.post.mockResolvedValueOnce({
      data: { accessToken: 'jwt-token', refreshToken: 'refresh-token' },
    });

    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(axiosMock.post).toHaveBeenCalledWith('/auth/login', {
        username: 'alice',
        password: 'pass123',
      });
      expect(mockLogin).toHaveBeenCalledWith('jwt-token', 'refresh-token');
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    });
  });

  // ── Scenario: Server returns response without accessToken ─────────────────

  it('shows error when accessToken is missing from response', async () => {
    axiosMock.post.mockResolvedValueOnce({ data: { refreshToken: 'refresh-only' } });

    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ── Scenario: User enters wrong credentials ───────────────────────────────

  it('shows error message when login request fails', async () => {
    axiosMock.post.mockRejectedValueOnce({ message: 'Unauthorized' });

    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'wrongpass' } });
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument();
    });
    expect(mockLogin).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ── Scenario: No error message on initial render ──────────────────────────

  it('does not show error message before form is submitted', () => {
    render(<Login />);
    expect(screen.queryByText('Invalid username or password')).not.toBeInTheDocument();
  });

  // ── Scenario: Form fields are controlled ─────────────────────────────────

  it('updates username and password fields on user input', () => {
    render(<Login />);
    const usernameInput = screen.getByPlaceholderText('Username');
    const passwordInput = screen.getByPlaceholderText('Password');

    fireEvent.change(usernameInput, { target: { value: 'testuser' } });
    fireEvent.change(passwordInput, { target: { value: 'secret' } });

    expect(usernameInput.value).toBe('testuser');
    expect(passwordInput.value).toBe('secret');
  });

  // ── Scenario: Network error ───────────────────────────────────────────────

  it('shows error message on network failure', async () => {
    axiosMock.post.mockRejectedValueOnce(new Error('Network Error'));

    render(<Login />);
    fireEvent.change(screen.getByPlaceholderText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'pass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid username or password')).toBeInTheDocument();
    });
  });
});
