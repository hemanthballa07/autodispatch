import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, expect } from 'vitest';
import AdminLoginForm from './AdminLoginForm';
import { ApiError } from '@/lib/api';

vi.mock('@/lib/admin-api', async (importActual) => {
  const real = await importActual<typeof import('@/lib/admin-api')>();
  return {
    ...real,
    getStats: vi.fn(),
  };
});

import { getStats, ADMIN_KEY_STORAGE } from '@/lib/admin-api';

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
});

test('submit button is disabled when input is empty', () => {
  render(<AdminLoginForm onSuccess={vi.fn()} />);
  expect(screen.getByRole('button', { name: /sign in/i })).toBeDisabled();
});

test('valid key: stores key in sessionStorage and calls onSuccess', async () => {
  vi.mocked(getStats).mockResolvedValue({ activeRides: 0, completedToday: 0, availableDrivers: 0 });
  const onSuccess = vi.fn();
  render(<AdminLoginForm onSuccess={onSuccess} />);

  fireEvent.change(screen.getByLabelText(/admin key/i), { target: { value: 'good-key' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

  await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
  expect(sessionStorage.getItem(ADMIN_KEY_STORAGE)).toBe('good-key');
});

test('rejected getStats: sessionStorage NOT written and onSuccess NOT called', async () => {
  vi.mocked(getStats).mockRejectedValue(new ApiError(401, 'Invalid key'));
  const onSuccess = vi.fn();
  render(<AdminLoginForm onSuccess={onSuccess} />);

  fireEvent.change(screen.getByLabelText(/admin key/i), { target: { value: 'bad-key' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

  await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  expect(sessionStorage.getItem(ADMIN_KEY_STORAGE)).toBeNull();
  expect(onSuccess).not.toHaveBeenCalled();
});

test('401 error renders "Invalid key"', async () => {
  vi.mocked(getStats).mockRejectedValue(new ApiError(401, 'x'));
  render(<AdminLoginForm onSuccess={vi.fn()} />);

  fireEvent.change(screen.getByLabelText(/admin key/i), { target: { value: 'bad' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Invalid key'));
});

test('non-401 error renders generic message', async () => {
  vi.mocked(getStats).mockRejectedValue(new Error('network failure'));
  render(<AdminLoginForm onSuccess={vi.fn()} />);

  fireEvent.change(screen.getByLabelText(/admin key/i), { target: { value: 'any' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

  await waitFor(() =>
    expect(screen.getByRole('alert')).toHaveTextContent('Could not connect. Try again.'),
  );
});
