import { render, screen, waitFor } from '@testing-library/react';
import { vi, expect } from 'vitest';
import AdminDashboard from './AdminDashboard';

vi.mock('@/lib/admin-api', async (importActual) => {
  const real = await importActual<typeof import('@/lib/admin-api')>();
  return {
    ...real,
    getStats: vi.fn(),
  };
});

import { getStats } from '@/lib/admin-api';

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(getStats).mockResolvedValue({ activeRides: 0, completedToday: 0, availableDrivers: 0 });
});

test('shows loading state while getStats is pending', () => {
  vi.mocked(getStats).mockReturnValue(new Promise(() => {}));
  render(<AdminDashboard />);
  expect(screen.getByText('Loading…')).toBeInTheDocument();
});

test('renders all three stat values on success', async () => {
  vi.mocked(getStats).mockResolvedValue({ activeRides: 2, completedToday: 5, availableDrivers: 1 });
  render(<AdminDashboard />);
  await waitFor(() => expect(screen.getByText('2')).toBeInTheDocument());
  expect(screen.getByText('5')).toBeInTheDocument();
  expect(screen.getByText('1')).toBeInTheDocument();
});
