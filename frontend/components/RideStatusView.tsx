"use client";

import type { Ride } from "@/lib/api";
import { TERMINAL_STATES } from "@/lib/api";
import RideProgressStepper from "./RideProgressStepper";
import DriverCard from "./DriverCard";
import RideReceipt from "./RideReceipt";
import { Button } from "@/components/ui/button";

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

const DRIVER_VISIBLE = new Set(["ASSIGNED", "ARRIVED", "IN_PROGRESS", "COMPLETED"]);

export default function RideStatusView({
  ride,
  onCancel,
  cancelling = false,
}: {
  ride: Ride;
  onCancel?: () => void;
  cancelling?: boolean;
}) {
  const isTerminal = TERMINAL_STATES.includes(ride.status);

  return (
    <section
      aria-label="Ride status"
      className="mx-auto flex w-full max-w-md flex-col gap-5 px-4 py-6"
    >
      <h2 className="text-xl font-bold text-foreground">{HEADLINES[ride.status] ?? ride.status}</h2>

      <p className="text-sm text-muted-foreground">
        {ride.pickup} → {ride.drop}
        {ride.fare != null && (
          <span className="ml-2 rounded-full bg-[#e8f5ef] px-2 py-0.5 text-xs font-medium text-[#106344]">
            ₹{ride.fare}
          </span>
        )}
      </p>

      {ride.status === "EXPIRED" && (
        <p role="status" className="text-sm text-muted-foreground">
          No autos right now, try again in a few minutes.
        </p>
      )}
      {ride.status === "CANCELLED" && (
        <p role="status" className="text-sm text-muted-foreground">
          {ride.cancelReason ?? "This ride was cancelled."}
        </p>
      )}

      {isTerminal ? (
        <RideReceipt ride={ride} />
      ) : (
        <RideProgressStepper status={ride.status} />
      )}

      {DRIVER_VISIBLE.has(ride.status) && ride.driver && (
        <div className="animate-in fade-in duration-300">
          <DriverCard driver={ride.driver} />
        </div>
      )}

      {ride.cancellable && onCancel && (
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={cancelling}
          className="w-full border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700"
        >
          {cancelling ? "Cancelling…" : "Cancel ride"}
        </Button>
      )}
    </section>
  );
}
