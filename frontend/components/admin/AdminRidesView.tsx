'use client';

import { useEffect, useState } from 'react';
import { listRides, AdminRideView, RIDE_STATUSES } from '@/lib/admin-api';

export default function AdminRidesView() {
  const [rides, setRides] = useState<AdminRideView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [dateFilter, setDateFilter] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listRides(statusFilter || undefined, dateFilter || undefined, page)
      .then(setRides)
      .catch(() => setError('Failed to load rides.'))
      .finally(() => setLoading(false));
  }, [statusFilter, dateFilter, page]);

  function handleStatusChange(e: React.ChangeEvent<HTMLSelectElement>) {
    setStatusFilter(e.target.value);
    setPage(0);
  }

  function handleDateChange(e: React.ChangeEvent<HTMLInputElement>) {
    setDateFilter(e.target.value);
    setPage(0);
  }

  return (
    <div>
      <h1>Rides</h1>
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
        <select value={statusFilter} onChange={handleStatusChange} aria-label="Status filter">
          <option value="">All statuses</option>
          {RIDE_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <input
          type="date"
          value={dateFilter}
          onChange={handleDateChange}
          aria-label="Date filter"
        />
      </div>

      {error && <p role="alert">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Rider</th><th>Pickup</th><th>Drop</th>
            <th>Status</th><th>Fare</th><th>Requested</th>
          </tr>
        </thead>
        <tbody>
          {!loading && rides.length === 0 && (
            <tr><td colSpan={6}>No rides found</td></tr>
          )}
          {rides.map(r => (
            <tr key={r.id}>
              <td>{r.riderName}</td>
              <td>{r.pickupLabel}</td>
              <td>{r.dropLabel}</td>
              <td>{r.status}</td>
              <td>{r.fareAmount != null ? String(r.fareAmount) : '—'}</td>
              <td>{r.requestedAt}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
        <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Prev</button>
        <span>Page {page + 1}</span>
        <button disabled={rides.length < 20} onClick={() => setPage(p => p + 1)}>Next</button>
      </div>
    </div>
  );
}
