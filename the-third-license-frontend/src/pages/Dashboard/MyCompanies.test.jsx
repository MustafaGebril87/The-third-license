import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import MyCompanies from './MyCompanies';

vi.mock('../../components/Navbar', () => ({ default: () => <div data-testid="navbar" /> }));

vi.mock('../../api/axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import axiosMock from '../../api/axios';

const sampleCompany = {
  id: 'company-1',
  name: 'Acme Inc',
  owner: 'alice',
};

const sampleRepo = {
  id: 'repo-1',
  name: 'acme-repo',
  gitUrl: 'http://localhost:9090/alice/acme.git',
  accessStatus: 'NONE',
  cloned: false,
};

describe('MyCompanies', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    axiosMock.get.mockResolvedValue({ data: { companies: [] } });
  });

  // ── Scenario: Page renders correctly ──────────────────────────────────────

  it('renders the My Companies heading', async () => {
    await act(async () => { render(<MyCompanies />); });
    expect(screen.getByText('My Companies')).toBeInTheDocument();
  });

  it('renders the Open New Company button', async () => {
    await act(async () => { render(<MyCompanies />); });
    expect(screen.getByRole('button', { name: '➕ Open New Company' })).toBeInTheDocument();
  });

  // ── Scenario: User has no companies ───────────────────────────────────────

  it('shows empty state message when user has no companies', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { companies: [] } });
    render(<MyCompanies />);
    await waitFor(() => {
      expect(screen.getByText("You don't own or contribute to any companies yet.")).toBeInTheDocument();
    });
  });

  // ── Scenario: User loads their companies ──────────────────────────────────

  it('renders company name and owner when companies are returned', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { companies: [sampleCompany] } });
    render(<MyCompanies />);
    await waitFor(() => {
      expect(screen.getByText('Acme Inc')).toBeInTheDocument();
      expect(screen.getByText(/alice/)).toBeInTheDocument();
    });
  });

  it('renders Show Repos button for each company', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { companies: [sampleCompany] } });
    render(<MyCompanies />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Show Repos' })).toBeInTheDocument();
    });
  });

  // ── Scenario: Loading companies fails ─────────────────────────────────────

  it('shows error message when companies cannot be loaded', async () => {
    axiosMock.get.mockRejectedValueOnce(new Error('Network Error'));
    render(<MyCompanies />);
    await waitFor(() => {
      expect(screen.getByText('Failed to load companies')).toBeInTheDocument();
    });
  });

  // ── Scenario: User opens the new company form ─────────────────────────────

  it('shows company name input and Create/Cancel buttons when form is opened', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { companies: [] } });
    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: '➕ Open New Company' }));

    fireEvent.click(screen.getByRole('button', { name: '➕ Open New Company' }));

    expect(screen.getByPlaceholderText('Company Name')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
  });

  // ── Scenario: User cancels company creation ───────────────────────────────

  it('hides the company form when Cancel is clicked', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: { companies: [] } });
    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: '➕ Open New Company' }));

    fireEvent.click(screen.getByRole('button', { name: '➕ Open New Company' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByPlaceholderText('Company Name')).not.toBeInTheDocument();
  });

  // ── Scenario: User creates a new company ─────────────────────────────────

  it('creates a company and refreshes the list on success', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [] } })              // initial load
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } }); // after create

    axiosMock.post.mockResolvedValueOnce({ data: 'Company created! Repository URL: http://localhost:9090/alice/acme.git' });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: '➕ Open New Company' }));

    fireEvent.click(screen.getByRole('button', { name: '➕ Open New Company' }));
    fireEvent.change(screen.getByPlaceholderText('Company Name'), { target: { value: 'Acme Inc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      expect(axiosMock.post).toHaveBeenCalledWith('/companies/open', { name: 'Acme Inc' });
    });

    await waitFor(() => {
      expect(screen.getByText('Acme Inc')).toBeInTheDocument();
    });
  });

  // ── Scenario: User expands company repos ─────────────────────────────────

  it('fetches and shows repositories when Show Repos is clicked', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } })
      .mockResolvedValueOnce({ data: [sampleRepo] });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: 'Show Repos' }));

    fireEvent.click(screen.getByRole('button', { name: 'Show Repos' }));

    await waitFor(() => {
      expect(screen.getByText(/acme-repo/)).toBeInTheDocument();
      expect(screen.getByText(/http:\/\/localhost:9090\/alice\/acme\.git/)).toBeInTheDocument();
    });

    // Button label switches to Hide Repos
    expect(screen.getByRole('button', { name: 'Hide Repos' })).toBeInTheDocument();
  });

  // ── Scenario: Request Access button for a repo with no access ─────────────

  it('shows Request Access button when user has no access', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } })
      .mockResolvedValueOnce({ data: [{ ...sampleRepo, accessStatus: 'NONE' }] });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: 'Show Repos' }));
    fireEvent.click(screen.getByRole('button', { name: 'Show Repos' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Request Access' })).toBeInTheDocument();
    });
  });

  // ── Scenario: Access Granted state shown for approved repos ──────────────

  it('shows disabled "Access Granted" button when access is APPROVED', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } })
      .mockResolvedValueOnce({ data: [{ ...sampleRepo, accessStatus: 'APPROVED' }] });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: 'Show Repos' }));
    fireEvent.click(screen.getByRole('button', { name: 'Show Repos' }));

    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'Access Granted' });
      expect(btn).toBeInTheDocument();
      expect(btn).toBeDisabled();
    });
  });

  // ── Scenario: Access Pending state shown for pending repos ────────────────

  it('shows disabled "Access Pending" button when access request is pending', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } })
      .mockResolvedValueOnce({ data: [{ ...sampleRepo, accessStatus: 'PENDING' }] });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: 'Show Repos' }));
    fireEvent.click(screen.getByRole('button', { name: 'Show Repos' }));

    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'Access Pending' });
      expect(btn).toBeInTheDocument();
      expect(btn).toBeDisabled();
    });
  });

  // ── Scenario: User hides repos after expanding ────────────────────────────

  it('hides repositories when Hide Repos is clicked', async () => {
    axiosMock.get
      .mockResolvedValueOnce({ data: { companies: [sampleCompany] } })
      .mockResolvedValueOnce({ data: [sampleRepo] });

    render(<MyCompanies />);
    await waitFor(() => screen.getByRole('button', { name: 'Show Repos' }));
    fireEvent.click(screen.getByRole('button', { name: 'Show Repos' }));

    await waitFor(() => screen.getByText(/acme-repo/));

    fireEvent.click(screen.getByRole('button', { name: 'Hide Repos' }));
    expect(screen.queryByText(/acme-repo/)).not.toBeInTheDocument();
  });

  // ── Scenario: Multiple companies shown ────────────────────────────────────

  it('renders multiple companies from the API', async () => {
    axiosMock.get.mockResolvedValueOnce({
      data: {
        companies: [
          { id: 'c1', name: 'Alpha Corp', owner: 'alice' },
          { id: 'c2', name: 'Beta Ltd', owner: 'bob' },
        ],
      },
    });

    render(<MyCompanies />);
    await waitFor(() => {
      expect(screen.getByText('Alpha Corp')).toBeInTheDocument();
      expect(screen.getByText('Beta Ltd')).toBeInTheDocument();
    });
  });
});
