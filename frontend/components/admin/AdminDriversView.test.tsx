import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, expect } from 'vitest';
import AdminDriversView from './AdminDriversView';
import { ApiError } from '@/lib/api';

vi.mock('@/lib/admin-api', async (importActual) => {
  const real = await importActual<typeof import('@/lib/admin-api')>();
  return {
    ...real,
    listDrivers: vi.fn(),
    verifyDriver: vi.fn(),
    suspendDriver: vi.fn(),
    unsuspendDriver: vi.fn(),
    registerDriver: vi.fn(),
  };
});

import { listDrivers, verifyDriver, suspendDriver, registerDriver } from '@/lib/admin-api';

const makeDriver = (overrides = {}) => ({
  id: 'd1',
  name: 'Ravi Kumar',
  whatsappId: '+91900000001',
  vehicleNo: 'KA01AB1234',
  state: 'OFFLINE' as const,
  verified: false,
  suspended: false,
  createdAt: '2026-06-01T10:00:00Z',
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  vi.mocked(listDrivers).mockResolvedValue([]);
});

test('renders one row per driver', async () => {
  vi.mocked(listDrivers).mockResolvedValue([
    makeDriver({ id: 'd1', name: 'Ravi Kumar' }),
    makeDriver({ id: 'd2', name: 'Priya Sharma', verified: true }),
  ]);
  render(<AdminDriversView />);
  await waitFor(() => expect(screen.getByText('Ravi Kumar')).toBeInTheDocument());
  expect(screen.getByText('Priya Sharma')).toBeInTheDocument();
});

test('unverified driver shows Verify button; verified driver does not', async () => {
  vi.mocked(listDrivers).mockResolvedValue([
    makeDriver({ id: 'd1', verified: false }),
    makeDriver({ id: 'd2', name: 'Priya', verified: true }),
  ]);
  render(<AdminDriversView />);
  await waitFor(() => expect(screen.getByText('Ravi Kumar')).toBeInTheDocument());
  expect(screen.getAllByRole('button', { name: /verify/i })).toHaveLength(1);
});

test('Verify click calls verifyDriver then re-fetches list', async () => {
  vi.mocked(listDrivers).mockResolvedValue([makeDriver({ verified: false })]);
  vi.mocked(verifyDriver).mockResolvedValue(makeDriver({ verified: true }));
  render(<AdminDriversView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /verify/i })).toBeInTheDocument());

  fireEvent.click(screen.getByRole('button', { name: /verify/i }));

  await waitFor(() => expect(verifyDriver).toHaveBeenCalledWith('d1'));
  await waitFor(() => expect(listDrivers).toHaveBeenCalledTimes(2));
});

test('suspendDriver 422 shows inline error for that driver', async () => {
  vi.mocked(listDrivers).mockResolvedValue([makeDriver({ verified: true })]);
  vi.mocked(suspendDriver).mockRejectedValue(new ApiError(422, 'Driver is ON_RIDE'));
  render(<AdminDriversView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /suspend/i })).toBeInTheDocument());

  fireEvent.click(screen.getByRole('button', { name: /suspend/i }));

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Driver is ON_RIDE'));
});

test('register 409 shows "WhatsApp ID already registered"', async () => {
  vi.mocked(registerDriver).mockRejectedValue(new ApiError(409, 'conflict'));
  render(<AdminDriversView />);
  await waitFor(() => expect(screen.getByRole('button', { name: /register driver/i })).toBeInTheDocument());

  fireEvent.click(screen.getByRole('button', { name: /register driver/i }));
  fireEvent.change(screen.getByPlaceholderText('Name'), { target: { value: 'Test' } });
  fireEvent.change(screen.getByPlaceholderText('WhatsApp ID'), { target: { value: '+91900000001' } });
  fireEvent.change(screen.getByPlaceholderText('Vehicle No'), { target: { value: 'KA01' } });
  fireEvent.click(screen.getByRole('button', { name: /submit/i }));

  await waitFor(() =>
    expect(screen.getByRole('alert')).toHaveTextContent('WhatsApp ID already registered'),
  );
});

test('register 400 shows error detail', async () => {
  vi.mocked(registerDriver).mockRejectedValue(new ApiError(400, 'vehicleNo is required'));
  render(<AdminDriversView />);
  await waitFor(() => screen.getByRole('button', { name: /register driver/i }));

  fireEvent.click(screen.getByRole('button', { name: /register driver/i }));
  fireEvent.change(screen.getByPlaceholderText('Name'), { target: { value: 'Test' } });
  fireEvent.change(screen.getByPlaceholderText('WhatsApp ID'), { target: { value: '+91900000001' } });
  fireEvent.change(screen.getByPlaceholderText('Vehicle No'), { target: { value: 'KA01' } });
  fireEvent.click(screen.getByRole('button', { name: /submit/i }));

  await waitFor(() =>
    expect(screen.getByRole('alert')).toHaveTextContent('vehicleNo is required'),
  );
});

test('register 201 success: re-fetches drivers and hides form', async () => {
  vi.mocked(registerDriver).mockResolvedValue(makeDriver({ id: 'd2', name: 'New Driver' }));
  render(<AdminDriversView />);
  await waitFor(() => screen.getByRole('button', { name: /register driver/i }));

  fireEvent.click(screen.getByRole('button', { name: /register driver/i }));
  fireEvent.change(screen.getByPlaceholderText('Name'), { target: { value: 'New Driver' } });
  fireEvent.change(screen.getByPlaceholderText('WhatsApp ID'), { target: { value: '+91900000002' } });
  fireEvent.change(screen.getByPlaceholderText('Vehicle No'), { target: { value: 'KA01XY9999' } });
  fireEvent.click(screen.getByRole('button', { name: /submit/i }));

  await waitFor(() => expect(listDrivers).toHaveBeenCalledTimes(2));
  expect(screen.queryByPlaceholderText('Name')).not.toBeInTheDocument();
});
