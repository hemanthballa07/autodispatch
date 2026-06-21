'use client';

import { useState } from 'react';
import { getStats, ADMIN_KEY_STORAGE } from '@/lib/admin-api';
import { ApiError } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';

interface Props {
  onSuccess: () => void;
}

export default function AdminLoginForm({ onSuccess }: Props) {
  const [inputKey, setInputKey] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = inputKey.trim();
    if (!trimmed) return;
    setSubmitting(true);
    setError(null);
    try {
      await getStats(trimmed);
      sessionStorage.setItem(ADMIN_KEY_STORAGE, trimmed);
      onSuccess();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('Invalid key');
      } else {
        setError('Could not connect. Try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-sm shadow-sm">
        <CardHeader>
          <CardTitle className="text-xl">Admin Login</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="admin-key" className="text-sm font-medium text-foreground">
                Admin Key
              </label>
              <Input
                id="admin-key"
                type="password"
                value={inputKey}
                onChange={e => setInputKey(e.target.value)}
                placeholder="Enter admin key"
              />
            </div>
            {error && (
              <p role="alert" className="text-sm text-red-600">
                {error}
              </p>
            )}
            <Button
              type="submit"
              disabled={submitting || inputKey.trim() === ''}
              className="w-full bg-[#106344] text-white hover:bg-[#0d5238]"
            >
              {submitting ? 'Checking…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
