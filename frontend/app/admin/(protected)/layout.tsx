'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { ADMIN_KEY_STORAGE } from '@/lib/admin-api';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';

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
    <div className="min-h-screen bg-[#f9fafb]">
      <nav className="flex items-center gap-4 bg-white px-6 py-3 shadow-sm">
        <Link href="/admin" className="text-sm font-medium text-foreground hover:text-[#106344]">
          Dashboard
        </Link>
        <Link href="/admin/drivers" className="text-sm font-medium text-foreground hover:text-[#106344]">
          Drivers
        </Link>
        <Link href="/admin/rides" className="text-sm font-medium text-foreground hover:text-[#106344]">
          Rides
        </Link>
        <div className="ml-auto">
          <Button variant="outline" size="sm" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </nav>
      <Separator />
      <main className="px-6 py-6">{children}</main>
    </div>
  );
}
