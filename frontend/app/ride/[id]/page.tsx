"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import RideStatusView from "@/components/RideStatusView";
import { ApiError, cancelRide, getRide, TERMINAL_STATES, type Ride } from "@/lib/api";

const POLL_INTERVAL_MS = 4000;

export default function RideStatusPage() {
  const params = useParams<{ id: string }>();
  const rideId = params.id;
  const [ride, setRide] = useState<Ride | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setRide(await getRide(rideId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load ride.");
    }
  }, [rideId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Poll every 4s while the ride is non-terminal.
  useEffect(() => {
    if (!ride || TERMINAL_STATES.includes(ride.status)) return;
    const timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [ride, refresh]);

  async function onCancel() {
    setCancelling(true);
    try {
      setRide(await cancelRide(rideId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not cancel.");
    } finally {
      setCancelling(false);
    }
  }

  return (
    <main style={{ padding: "1.5rem", maxWidth: 480, margin: "0 auto" }}>
      {error && <p role="alert">{error}</p>}
      {ride ? <RideStatusView ride={ride} onCancel={onCancel} cancelling={cancelling} /> : <p>Loading…</p>}
      {ride && TERMINAL_STATES.includes(ride.status) && (
        <p>
          <Link href="/book">Book another ride</Link>
        </p>
      )}
    </main>
  );
}
