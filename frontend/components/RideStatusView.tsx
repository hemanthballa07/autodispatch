"use client";

import type { Ride } from "@/lib/api";

const STEPS: { state: string; label: string }[] = [
  { state: "REQUESTED", label: "Requested" },
  { state: "BROADCASTING", label: "Finding auto" },
  { state: "ASSIGNED", label: "Assigned" },
  { state: "ARRIVED", label: "Arrived" },
  { state: "IN_PROGRESS", label: "On trip" },
  { state: "COMPLETED", label: "Completed" },
];

const HEADLINES: Record<string, string> = {
  REQUESTED: "Ride requested",
  BROADCASTING: "Finding an auto near you…",
  ASSIGNED: "Driver assigned",
  ARRIVED: "Your auto has arrived",
  IN_PROGRESS: "On trip",
  COMPLETED: "Trip completed",
  CANCELLED: "Ride cancelled",
  EXPIRED: "No autos right now",
};

/**
 * Pure presentational status renderer — every ride state has a screen.
 */
export default function RideStatusView({
  ride,
  onCancel,
  cancelling = false,
}: {
  ride: Ride;
  onCancel?: () => void;
  cancelling?: boolean;
}) {
  const stepIndex = STEPS.findIndex((s) => s.state === ride.status);

  return (
    <section aria-label="Ride status">
      <h2>{HEADLINES[ride.status] ?? ride.status}</h2>
      <p>
        {ride.pickup} → {ride.drop}
        {ride.fare != null && <> · ₹{ride.fare}</>}
      </p>

      {ride.status === "EXPIRED" && (
        <p role="status">No autos right now, try again in a few minutes.</p>
      )}
      {ride.status === "CANCELLED" && (
        <p role="status">{ride.cancelReason ?? "This ride was cancelled."}</p>
      )}

      {stepIndex >= 0 && (
        <ol aria-label="Progress">
          {STEPS.map((step, i) => (
            <li key={step.state} aria-current={i === stepIndex ? "step" : undefined}>
              {i <= stepIndex ? "● " : "○ "}
              {step.label}
            </li>
          ))}
        </ol>
      )}

      {ride.driver && (
        <div aria-label="Driver">
          <h3>{ride.driver.name}</h3>
          <p>
            {ride.driver.vehicleNo} · {ride.driver.maskedPhone}
          </p>
        </div>
      )}

      {ride.cancellable && onCancel && (
        <button type="button" onClick={onCancel} disabled={cancelling}>
          {cancelling ? "Cancelling…" : "Cancel ride"}
        </button>
      )}
    </section>
  );
}
