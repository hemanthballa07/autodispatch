import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, expect } from 'vitest';
import AdminRidesView from './AdminRidesView';

vi.mock('@/lib/admin-api', async (importActual) => {
  const real = await importActual<typeof import('@/lib/admin-api')>();
  return {
    ...real,
    listRides: vi.fn(),
  };
});

import { listRides } from '@/lib/admin-api';

const makeRide = (overrides = {}) => ({
  id: 'r1',
  riderId: 'rider1',
  riderName: 'Alice',
  riderPhone: '+91900000001',
  driverId: null,
  driverName: null,
  driverWhatsappId: null,
  driverVehicleNo: null,
  pickupLabel: 'Gate 1',
  dropLabel: 'Hostel B',
  status: 'REQUESTED' as const,
  fareAmount: 50,
  requestedAt: '2026-06-19T08:00:00Z',
  assignedAt: null,
  completedAt: null,
  cancelReason: null,
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(listRides).mockResolvedValue([]);
});

test('renders rows with pickupLabel, dropLabel, fareAmount', async () => {
  vi.mocked(listRides).mockResolvedValue([
    makeRide({ id: 'r1', pickupLabel: 'Gate 1', dropLabel: 'Hostel B', fareAmount: 50 }),
    makeRide({ id: 'r2', pickupLabel: 'Library', dropLabel: 'Admin Block', fareAmount: 75 }),
  ]);
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByText('Gate 1')).toBeInTheDocument());
  expect(screen.getByText('Hostel B')).toBeInTheDocument();
  expect(screen.getByText('50')).toBeInTheDocument();
});

test('null fareAmount renders "—"', async () => {
  vi.mocked(listRides).mockResolvedValue([makeRide({ fareAmount: null })]);
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByText('—')).toBeInTheDocument());
});

test('Next not disabled at exactly 20 rows; click advances page', async () => {
  const twentyRides = Array.from({ length: 20 }, (_, i) => makeRide({ id: `r${i}` }));
  vi.mocked(listRides).mockResolvedValue(twentyRides);
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled());

  fireEvent.click(screen.getByRole('button', { name: /next/i }));

  await waitFor(() =>
    expect(listRides).toHaveBeenLastCalledWith(undefined, undefined, 1),
  );
});

test('Prev enabled after advancing; click returns to page 0', async () => {
  const twentyRides = Array.from({ length: 20 }, (_, i) => makeRide({ id: `r${i}` }));
  vi.mocked(listRides)
    .mockResolvedValueOnce(twentyRides)
    .mockResolvedValue(Array.from({ length: 5 }, (_, i) => makeRide({ id: `r${i + 20}` })));
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled());

  fireEvent.click(screen.getByRole('button', { name: /next/i }));
  await waitFor(() => expect(screen.getByRole('button', { name: /prev/i })).not.toBeDisabled());

  fireEvent.click(screen.getByRole('button', { name: /prev/i }));
  await waitFor(() =>
    expect(listRides).toHaveBeenLastCalledWith(undefined, undefined, 0),
  );
});

test('page resets to 0 when status filter changes', async () => {
  const twentyRides = Array.from({ length: 20 }, (_, i) => makeRide({ id: `r${i}` }));
  vi.mocked(listRides).mockResolvedValue(twentyRides);
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled());

  fireEvent.click(screen.getByRole('button', { name: /next/i }));
  await waitFor(() => expect(listRides).toHaveBeenLastCalledWith(undefined, undefined, 1));

  fireEvent.change(screen.getByRole('combobox', { name: /status filter/i }), {
    target: { value: 'COMPLETED' },
  });
  await waitFor(() =>
    expect(listRides).toHaveBeenLastCalledWith('COMPLETED', undefined, 0),
  );
});

test('Next disabled when fewer than 20 rows returned', async () => {
  vi.mocked(listRides).mockResolvedValue([makeRide(), makeRide({ id: 'r2' }), makeRide({ id: 'r3' })]);
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeDisabled());
});

test('Prev disabled at page 0', async () => {
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /prev/i })).toBeDisabled());
});

test('date filter calls listRides with date string', async () => {
  render(<AdminRidesView />);
  await waitFor(() => screen.getByRole('button', { name: /prev/i }));

  fireEvent.change(screen.getByLabelText(/date filter/i), {
    target: { value: '2026-06-19' },
  });
  await waitFor(() =>
    expect(listRides).toHaveBeenLastCalledWith(undefined, '2026-06-19', 0),
  );
});

test('error state renders role=alert', async () => {
  vi.mocked(listRides).mockRejectedValue(new Error('network'));
  render(<AdminRidesView />);
  await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
});
