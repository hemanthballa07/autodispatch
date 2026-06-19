'use client';

import { useEffect, useState } from 'react';
import { getStats, StatsResponse } from '@/lib/admin-api';
import StatsCard from './StatsCard';

export default function AdminDashboard() {
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getStats()
      .then(setStats)
      .catch(() => setError('Failed to load stats.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading…</p>;
  if (error) return <p role="alert">{error}</p>;
  if (!stats) return null;

  return (
    <div>
      <h1>Dashboard</h1>
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '1rem' }}>
        <StatsCard label="Active Rides" value={stats.activeRides} />
        <StatsCard label="Completed Today" value={stats.completedToday} />
        <StatsCard label="Available Drivers" value={stats.availableDrivers} />
      </div>
    </div>
  );
}
