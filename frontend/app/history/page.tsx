"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError, getToken, listRides, type Ride } from "@/lib/api";

export default function HistoryPage() {
  const [rides, setRides] = useState<Ride[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getToken()) {
      setError("Start a session from the booking page first.");
      setRides([]);
      return;
    }
    listRides()
      .then(setRides)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load history."));
  }, []);

  return (
    <main style={{ padding: "1.5rem", maxWidth: 480, margin: "0 auto" }}>
      <h1>Your rides</h1>
      {error && <p role="alert">{error}</p>}
      {rides === null && <p>Loading…</p>}
      {rides !== null && rides.length === 0 && !error && <p>No rides yet.</p>}
      <ul>
        {rides?.map((ride) => (
          <li key={ride.id}>
            <Link href={`/ride/${ride.id}`}>
              {ride.pickup} → {ride.drop} · {ride.status}
              {ride.fare != null && <> · ₹{ride.fare}</>}
            </Link>
          </li>
        ))}
      </ul>
      <p>
        <Link href="/book">Book a ride</Link>
      </p>
    </main>
  );
}
