"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ApiError,
  createRide,
  createSession,
  estimateFare,
  getLocations,
  getToken,
  setToken,
  type Location,
} from "@/lib/api";

/**
 * First-run session capture + booking form. `onBooked` receives the new ride
 * id (the page navigates to the status screen).
 */
export default function BookingFlow({ onBooked }: { onBooked: (rideId: string) => void }) {
  const [hasSession, setHasSession] = useState<boolean | null>(null);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");

  const [locations, setLocations] = useState<Location[]>([]);
  const [pickupId, setPickupId] = useState("");
  const [dropId, setDropId] = useState("");
  const [fare, setFare] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setHasSession(getToken() !== null);
  }, []);

  const loadLocations = useCallback(async () => {
    try {
      setLocations(await getLocations());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load stops.");
    }
  }, []);

  useEffect(() => {
    if (hasSession) void loadLocations();
  }, [hasSession, loadLocations]);

  useEffect(() => {
    setFare(null);
    if (!pickupId || !dropId || pickupId === dropId) return;
    let stale = false;
    estimateFare(pickupId, dropId)
      .then((r) => {
        if (!stale) setFare(r.amount);
      })
      .catch((e) => {
        if (!stale) setError(e instanceof ApiError ? e.message : "No fare available.");
      });
    return () => {
      stale = true;
    };
  }, [pickupId, dropId]);

  async function startSession(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const session = await createSession(name.trim(), phone.trim());
      setToken(session.token);
      setHasSession(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not start session.");
    }
  }

  async function confirmBooking() {
    if (submitting) return; // duplicate taps must not create two rides
    setSubmitting(true);
    setError(null);
    try {
      const ride = await createRide(pickupId, dropId);
      onBooked(ride.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Booking failed.");
      setSubmitting(false);
    }
  }

  if (hasSession === null) {
    return <p>Loading…</p>;
  }

  if (!hasSession) {
    return (
      <form onSubmit={startSession} aria-label="Start session">
        <h2>Tell us who you are</h2>
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        </label>
        <label>
          Phone
          <input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            required
            inputMode="tel"
            placeholder="+91…"
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit">Continue</button>
      </form>
    );
  }

  const ready = pickupId !== "" && dropId !== "" && pickupId !== dropId && fare !== null;

  return (
    <div>
      <h2>Book a ride</h2>
      <label>
        Pickup
        <select
          aria-label="Pickup"
          value={pickupId}
          onChange={(e) => setPickupId(e.target.value)}
        >
          <option value="">Select pickup…</option>
          {locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Drop
        <select aria-label="Drop" value={dropId} onChange={(e) => setDropId(e.target.value)}>
          <option value="">Select drop…</option>
          {locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
      </label>
      {fare !== null && <p aria-label="Fare estimate">Fare: ₹{fare}</p>}
      {error && <p role="alert">{error}</p>}
      <button type="button" onClick={confirmBooking} disabled={!ready || submitting}>
        {submitting ? "Booking…" : "Confirm ride"}
      </button>
    </div>
  );
}
