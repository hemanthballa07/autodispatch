import { ApiError } from '@/lib/api';

const BASE = process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080';

export const ADMIN_KEY_STORAGE = 'admin-key';

export const RIDE_STATUSES = [
  'REQUESTED', 'BROADCASTING', 'ASSIGNED', 'ARRIVED',
  'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED',
] as const;
export type RideStatus = typeof RIDE_STATUSES[number];

export interface StatsResponse {
  activeRides: number;
  completedToday: number;
  availableDrivers: number;
}

export interface DriverAdminResponse {
  id: string;
  name: string;
  whatsappId: string;
  vehicleNo: string;
  state: 'OFFLINE' | 'AVAILABLE' | 'ON_RIDE';
  verified: boolean;
  suspended: boolean;
  createdAt: string;
}

export interface AdminRideView {
  id: string;
  riderId: string;
  riderName: string;
  riderPhone: string;
  driverId: string | null;
  driverName: string | null;
  driverWhatsappId: string | null;
  driverVehicleNo: string | null;
  pickupLabel: string;
  dropLabel: string;
  status: RideStatus;
  fareAmount: number | null;
  requestedAt: string;
  assignedAt: string | null;
  completedAt: string | null;
  cancelReason: string | null;
}

export interface RegisterDriverPayload {
  name: string;
  whatsappId: string;
  vehicleNo: string;
}

async function adminRequest<T>(path: string, init?: RequestInit, keyOverride?: string): Promise<T> {
  const key = keyOverride ?? sessionStorage.getItem(ADMIN_KEY_STORAGE) ?? '';
  const res = await fetch(BASE + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
      'X-Admin-Key': key,
    },
  });
  if (res.status === 401) {
    if (keyOverride !== undefined) throw new ApiError(401, 'Invalid key');
    sessionStorage.removeItem(ADMIN_KEY_STORAGE);
    window.location.href = '/admin/login';
    throw new ApiError(401, 'Unauthorized');
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(res.status, body.detail ?? 'Request failed');
  }
  return res.json() as Promise<T>;
}

export const getStats = (keyOverride?: string) =>
  adminRequest<StatsResponse>('/api/admin/v1/stats', undefined, keyOverride);

export const listDrivers = () =>
  adminRequest<DriverAdminResponse[]>('/api/admin/v1/drivers/');

export const registerDriver = (payload: RegisterDriverPayload) =>
  adminRequest<DriverAdminResponse>('/api/admin/v1/drivers/', {
    method: 'POST',
    body: JSON.stringify(payload),
  });

export const verifyDriver = (id: string) =>
  adminRequest<DriverAdminResponse>(`/api/admin/v1/drivers/${id}/verify`, { method: 'POST' });

export const suspendDriver = (id: string) =>
  adminRequest<DriverAdminResponse>(`/api/admin/v1/drivers/${id}/suspend`, { method: 'POST' });

export const unsuspendDriver = (id: string) =>
  adminRequest<DriverAdminResponse>(`/api/admin/v1/drivers/${id}/unsuspend`, { method: 'POST' });

export const listRides = (status?: string, date?: string, page?: number) => {
  const p = new URLSearchParams({ page: String(page ?? 0), size: '20' });
  if (status) p.set('status', status);
  if (date) p.set('date', date);
  return adminRequest<AdminRideView[]>(`/api/admin/v1/rides/?${p}`);
};
