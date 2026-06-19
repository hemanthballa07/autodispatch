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
      <h1>Drivers</h1>
      <button onClick={() => setShowForm(v => !v)} style={{ marginBottom: '1rem' }}>
        {showForm ? 'Cancel' : 'Register Driver'}
      </button>

      {showForm && (
        <form onSubmit={handleRegister} style={{ marginBottom: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', maxWidth: 400 }}>
          <input placeholder="Name" value={formName} onChange={e => setFormName(e.target.value)} required />
          <input placeholder="WhatsApp ID" value={formWhatsapp} onChange={e => setFormWhatsapp(e.target.value)} required />
          <input placeholder="Vehicle No" value={formVehicle} onChange={e => setFormVehicle(e.target.value)} required />
          {formError && <p role="alert">{formError}</p>}
          <button type="submit" disabled={registering}>
            {registering ? 'Registering…' : 'Submit'}
          </button>
        </form>
      )}

      <table>
        <thead>
          <tr>
            <th>Name</th><th>Vehicle</th><th>State</th>
            <th>Verified</th><th>Suspended</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {drivers.map(d => (
            <tr key={d.id}>
              <td>{d.name}</td>
              <td>{d.vehicleNo}</td>
              <td>{d.state}</td>
              <td>{d.verified ? 'Yes' : 'No'}</td>
              <td>{d.suspended ? 'Yes' : 'No'}</td>
              <td>
                {!d.verified && (
                  <button
                    disabled={submittingId === d.id}
                    onClick={() => handleAction(d.id, () => verifyDriver(d.id))}
                  >
                    Verify
                  </button>
                )}
                {!d.suspended && (
                  <button
                    disabled={submittingId === d.id}
                    onClick={() => handleAction(d.id, () => suspendDriver(d.id))}
                  >
                    Suspend
                  </button>
                )}
                {d.suspended && (
                  <button
                    disabled={submittingId === d.id}
                    onClick={() => handleAction(d.id, () => unsuspendDriver(d.id))}
                  >
                    Unsuspend
                  </button>
                )}
                {actionErrors[d.id] && (
                  <span role="alert" style={{ color: 'red', marginLeft: '0.5rem' }}>
                    {actionErrors[d.id]}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
