'use client';

import { useEffect, useState } from 'react';
import {
  listDrivers,
  verifyDriver,
  suspendDriver,
  unsuspendDriver,
  registerDriver,
  DriverAdminResponse,
} from '@/lib/admin-api';
import { ApiError } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import StatusBadge from '@/components/StatusBadge';

export default function AdminDriversView() {
  const [drivers, setDrivers] = useState<DriverAdminResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({});
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);
  const [formName, setFormName] = useState('');
  const [formWhatsapp, setFormWhatsapp] = useState('');
  const [formVehicle, setFormVehicle] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  function fetchDrivers() {
    return listDrivers()
      .then(setDrivers)
      .catch(() => setError('Failed to load drivers.'))
      .finally(() => setLoading(false));
  }

  useEffect(() => { fetchDrivers(); }, []);

  async function handleAction(id: string, action: () => Promise<unknown>) {
    setSubmittingId(id);
    setActionErrors(prev => { const next = { ...prev }; delete next[id]; return next; });
    try {
      await action();
      await fetchDrivers();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Action failed.';
      setActionErrors(prev => ({ ...prev, [id]: msg }));
    } finally {
      setSubmittingId(null);
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setRegistering(true);
    setFormError(null);
    try {
      await registerDriver({ name: formName, whatsappId: formWhatsapp, vehicleNo: formVehicle });
      setShowForm(false);
      setFormName(''); setFormWhatsapp(''); setFormVehicle('');
      await fetchDrivers();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setFormError('WhatsApp ID already registered');
      } else if (err instanceof ApiError) {
        setFormError(err.message);
      } else {
        setFormError('Registration failed.');
      }
    } finally {
      setRegistering(false);
    }
  }

  if (loading) return <p>Loading…</p>;
  if (error) return <p role="alert">{error}</p>;

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">Drivers</h1>
        <Button
          variant="outline"
          onClick={() => setShowForm(v => !v)}
        >
          {showForm ? 'Cancel' : 'Register Driver'}
        </Button>
      </div>

      {showForm && (
        <Card className="mb-4 max-w-md">
          <CardHeader>
            <CardTitle className="text-base">Register Driver</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleRegister} className="flex flex-col gap-3">
              <Input placeholder="Name" value={formName} onChange={e => setFormName(e.target.value)} required />
              <Input placeholder="WhatsApp ID" value={formWhatsapp} onChange={e => setFormWhatsapp(e.target.value)} required />
              <Input placeholder="Vehicle No" value={formVehicle} onChange={e => setFormVehicle(e.target.value)} required />
              {formError && <p role="alert" className="text-sm text-red-600">{formError}</p>}
              <Button type="submit" disabled={registering} className="bg-[#106344] text-white hover:bg-[#0d5238]">
                {registering ? 'Registering…' : 'Submit'}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="overflow-x-auto rounded-lg border border-border bg-white">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-[#f9fafb]">
            <tr>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Name</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Vehicle</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">State</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Verified</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Suspended</th>
              <th className="px-4 py-3 text-left font-medium text-muted-foreground">Actions</th>
            </tr>
          </thead>
          <tbody>
            {drivers.map(d => (
              <tr key={d.id} className="border-b border-border last:border-0">
                <td className="px-4 py-3 font-medium">{d.name}</td>
                <td className="px-4 py-3 text-muted-foreground">{d.vehicleNo}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={d.state} />
                </td>
                <td className="px-4 py-3">{d.verified ? 'Yes' : 'No'}</td>
                <td className="px-4 py-3">{d.suspended ? 'Yes' : 'No'}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-2">
                    {!d.verified && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={submittingId === d.id}
                        onClick={() => handleAction(d.id, () => verifyDriver(d.id))}
                      >
                        Verify
                      </Button>
                    )}
                    {!d.suspended && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={submittingId === d.id}
                        onClick={() => handleAction(d.id, () => suspendDriver(d.id))}
                      >
                        Suspend
                      </Button>
                    )}
                    {d.suspended && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={submittingId === d.id}
                        onClick={() => handleAction(d.id, () => unsuspendDriver(d.id))}
                      >
                        Unsuspend
                      </Button>
                    )}
                    {actionErrors[d.id] && (
                      <span role="alert" className="text-sm text-red-600">
                        {actionErrors[d.id]}
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
