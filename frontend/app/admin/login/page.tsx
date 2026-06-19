'use client';

import { useRouter } from 'next/navigation';
import AdminLoginForm from '@/components/admin/AdminLoginForm';

export default function LoginPage() {
  const router = useRouter();
  return <AdminLoginForm onSuccess={() => router.push('/admin')} />;
}
