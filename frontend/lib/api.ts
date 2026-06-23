const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
const TOKEN_KEY = "autodispatch_token";

export type Location = { id: string; name: string; zone: string };
export type DriverCard = { name: string; vehicleNo: string; maskedPhone: string };
export type Ride = {
  id: string;
  status: string;
  pickup: string;
  drop: string;
  fare: number | string | null;
  driver: DriverCard | null;
  cancellable: boolean;
  requestedAt: string;
  completedAt: string | null;
  cancelReason: string | null;
  scheduledFor: string | null;
};

export const TERMINAL_STATES = ["COMPLETED", "CANCELLED", "EXPIRED"];

export class ApiError extends Error {
  status: number;
  constructor(status: number, detail: string) {
    super(detail);
    this.status = status;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  window.localStorage.setItem(TOKEN_KEY, token);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const response = await fetch(`${BASE}${path}`, { ...init, headers });
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    try {
      const problem = await response.json();
      if (problem?.detail) detail = problem.detail;
    } catch {
      // non-JSON error body; keep the default detail
    }
    throw new ApiError(response.status, detail);
  }
  return (await response.json()) as T;
}

export async function createSession(name: string, phone: string) {
  return request<{ token: string; riderId: string; name: string }>(
    "/api/v1/sessions",
    { method: "POST", body: JSON.stringify({ name, phone }) },
  );
}

export const getLocations = () => request<Location[]>("/api/v1/locations");

export const estimateFare = (pickupId: string, dropId: string) =>
  request<{ amount: number }>(
    `/api/v1/fares/estimate?pickupId=${pickupId}&dropId=${dropId}`,
  );

export const createRide = (pickupId: string, dropId: string, scheduledFor?: string) =>
  request<Ride>("/api/v1/rides", {
    method: "POST",
    body: JSON.stringify({ pickupId, dropId, ...(scheduledFor ? { scheduledFor } : {}) }),
  });

export const getRide = (rideId: string) => request<Ride>(`/api/v1/rides/${rideId}`);

export const cancelRide = (rideId: string) =>
  request<Ride>(`/api/v1/rides/${rideId}/cancel`, { method: "POST" });

export const listRides = (page: number = 0) =>
  request<Ride[]>(`/api/v1/rides?mine=true&page=${page}`);

export type RatingView = {
  rideId: string;
  stars: number;
  comment: string | null;
  createdAt: string;
};

export const getRating = (rideId: string) =>
  request<RatingView>(`/api/v1/rides/${rideId}/rating`);

export const submitRating = (rideId: string, stars: number, comment?: string) =>
  request<RatingView>(
    `/api/v1/rides/${rideId}/rating?stars=${stars}${comment ? `&comment=${encodeURIComponent(comment)}` : ""}`,
    { method: "POST" },
  );

export const triggerSos = (rideId: string) =>
  request<void>(`/api/v1/rides/${rideId}/safety/sos`, { method: "POST" });

export const reportIncident = (rideId: string, details: string) =>
  request<void>(
    `/api/v1/rides/${rideId}/safety/incident?details=${encodeURIComponent(details)}`,
    { method: "POST" },
  );

export type PaymentTransaction = {
  id: string;
  rideId: string;
  type: string;
  method: string;
  status: string;
  amount: number;
};

export const getPayments = (rideId: string) =>
  request<PaymentTransaction[]>(`/api/v1/rides/${rideId}/payment`);

export const acknowledgePayment = (rideId: string) =>
  request<PaymentTransaction>(`/api/v1/rides/${rideId}/payment/acknowledge`, {
    method: "POST",
  });
