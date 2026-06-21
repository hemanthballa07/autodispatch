'use client';

import { useEffect, useState } from 'react';
import { listRides, AdminRideView, RIDE_STATUSES } from '@/lib/admin-api';
import { Button } from '@/components/ui/button';
import StatusBadge from '@/components/StatusBadge';

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

  const selectClass =
    'h-9 rounded-md border border-input bg-background px-3 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-ring';

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold text-foreground">Rides</h1>

      <div className="mb-4 flex flex-wrap gap-3">
        <select
          value={statusFilter}
          onChange={handleStatusChange}
          aria-label="Status filter"
          className={selectClass}
        >
          <option value="">All statuses</option>
          {RIDE_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <input
          type="date"
          value={dateFilter}
          onChange={handleDateChange}
          aria-label="Date filter"
          className={selectClass}
        />
      </div>

      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}

      <div className="overflow-x-auto rounded-lg border border-border bg-white">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-[#f9fafb]">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Rider</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Pickup</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Drop</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Fare</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Requested</th>
            </tr>
          </thead>
          <tbody>
            {!loading && rides.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                  No rides found
                </td>
              </tr>
            )}
            {rides.map(r => (
              <tr key={r.id} className="border-b border-border last:border-0">
                <td className="px-4 py-3">{r.riderName}</td>
                <td className="px-4 py-3">{r.pickupLabel}</td>
                <td className="px-4 py-3">{r.dropLabel}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={r.status} />
                </td>
                <td className="px-4 py-3">{r.fareAmount != null ? String(r.fareAmount) : '—'}</td>
                <td className="px-4 py-3 text-muted-foreground">{r.requestedAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex items-center gap-4">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
          Prev
        </Button>
        <span className="text-sm text-muted-foreground">Page {page + 1}</span>
        <Button variant="outline" size="sm" disabled={rides.length < 20} onClick={() => setPage(p => p + 1)}>
          Next
        </Button>
      </div>
    </div>
  );
}
