import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, expect } from 'vitest';

const mockReplace = vi.fn();
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace: mockReplace, push: mockPush }) }));
vi.mock('next/link', () => ({ default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a> }));

vi.mock('@/lib/admin-api', async (importActual) => {
  const real = await importActual<typeof import('@/lib/admin-api')>();
  return { ...real };
});

import { ADMIN_KEY_STORAGE } from '@/lib/admin-api';
import ProtectedLayout from '@/app/admin/(protected)/layout';

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
});

test('no key: redirects to /admin/login and child not rendered', async () => {
  render(<ProtectedLayout><div>child</div></ProtectedLayout>);
  await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/admin/login'));
  expect(screen.queryByText('child')).not.toBeInTheDocument();
});

test('key present: children rendered and no redirect', async () => {
  sessionStorage.setItem(ADMIN_KEY_STORAGE, 'test-key');
  render(<ProtectedLayout><div>child</div></ProtectedLayout>);
  await waitFor(() => expect(screen.getByText('child')).toBeInTheDocument());
  expect(mockReplace).not.toHaveBeenCalled();
});

test('sign out: clears sessionStorage and navigates to /admin/login', async () => {
  sessionStorage.setItem(ADMIN_KEY_STORAGE, 'test-key');
  render(<ProtectedLayout><div>child</div></ProtectedLayout>);
  await waitFor(() => expect(screen.getByText('child')).toBeInTheDocument());

  fireEvent.click(screen.getByRole('button', { name: /sign out/i }));

  expect(sessionStorage.getItem(ADMIN_KEY_STORAGE)).toBeNull();
  expect(mockPush).toHaveBeenCalledWith('/admin/login');
});
