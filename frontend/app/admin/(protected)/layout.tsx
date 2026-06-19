'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { ADMIN_KEY_STORAGE } from '@/lib/admin-api';

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!sessionStorage.getItem(ADMIN_KEY_STORAGE)) {
      router.replace('/admin/login');
    } else {
      setChecked(true);
    }
  }, [router]);

  if (!checked) return null;

  function signOut() {
    sessionStorage.removeItem(ADMIN_KEY_STORAGE);
    router.push('/admin/login');
  }

  return (
    <div>
      <nav style={{ display: 'flex', gap: '1rem', padding: '1rem', borderBottom: '1px solid #eee' }}>
        <Link href="/admin">Dashboard</Link>
        <Link href="/admin/drivers">Drivers</Link>
        <Link href="/admin/rides">Rides</Link>
        <button onClick={signOut} style={{ marginLeft: 'auto' }}>Sign out</button>
      </nav>
      <main style={{ padding: '1rem' }}>{children}</main>
    </div>
  );
}
