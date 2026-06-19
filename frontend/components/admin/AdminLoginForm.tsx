'use client';

import { useState } from 'react';
import { getStats, ADMIN_KEY_STORAGE } from '@/lib/admin-api';
import { ApiError } from '@/lib/api';

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
    <div style={{ maxWidth: 360, margin: '4rem auto', padding: '2rem' }}>
      <h1>Admin Login</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="admin-key">Admin Key</label>
          <input
            id="admin-key"
            type="password"
            value={inputKey}
            onChange={e => setInputKey(e.target.value)}
            style={{ display: 'block', width: '100%', margin: '0.5rem 0' }}
          />
        </div>
        {error && <p role="alert" style={{ color: 'red' }}>{error}</p>}
        <button type="submit" disabled={submitting || inputKey.trim() === ''}>
          {submitting ? 'Checking…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
