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
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export default function BookingFlow({ onBooked }: { onBooked: (rideId: string) => void }) {
  const [hasSession, setHasSession] = useState<boolean | null>(null);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");

  const [locations, setLocations] = useState<Location[]>([]);
  const [pickupId, setPickupId] = useState("");
  const [dropId, setDropId] = useState("");
  const [fare, setFare] = useState<number | null>(null);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduledFor, setScheduledFor] = useState("");

  useEffect(() => {
    setHasSession(getToken() !== null);
  }, []);

  const loadLocations = useCallback(async () => {
    setLoadingLocations(true);
    try {
      setLocations(await getLocations());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load stops.");
    } finally {
      setLoadingLocations(false);
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
      localStorage.setItem("autodispatch_name", name.trim());
      localStorage.setItem("autodispatch_phone", phone.trim());
      setHasSession(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not start session.");
    }
  }

  async function confirmBooking() {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const isoScheduledFor = scheduleEnabled && scheduledFor
        ? new Date(scheduledFor).toISOString()
        : undefined;
      const ride = await createRide(pickupId, dropId, isoScheduledFor);
      onBooked(ride.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Booking failed.");
      setSubmitting(false);
    }
  }

  const selectClass = cn(
    "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2",
    "text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
    "disabled:cursor-not-allowed disabled:opacity-50",
  );

  if (hasSession === null) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <Skeleton className="h-64 w-full max-w-md rounded-2xl" />
      </div>
    );
  }

  if (!hasSession) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <Card className="w-full max-w-md shadow-sm">
          <CardHeader>
            <CardTitle className="text-xl">Tell us who you are</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={startSession} aria-label="Start session" className="flex flex-col gap-4">
              <div className="flex flex-col gap-1">
                <label htmlFor="name" className="text-sm font-medium text-foreground">
                  Name
                </label>
                <Input
                  id="name"
                  aria-label="Name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  maxLength={120}
                  placeholder="Your name"
                />
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor="phone" className="text-sm font-medium text-foreground">
                  Phone
                </label>
                <Input
                  id="phone"
                  aria-label="Phone"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  required
                  inputMode="tel"
                  placeholder="+91…"
                />
              </div>
              {error && (
                <p role="alert" className="text-sm text-red-600">
                  {error}
                </p>
              )}
              <Button
                type="submit"
                className="w-full bg-[#106344] text-white hover:bg-[#0d5238]"
              >
                Continue
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    );
  }

  const minScheduledFor = new Date(Date.now() + 10 * 60 * 1000).toISOString().slice(0, 16);
  const ready =
    pickupId !== "" && dropId !== "" && pickupId !== dropId && fare !== null &&
    (!scheduleEnabled || scheduledFor !== "");

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <Card className="w-full max-w-md shadow-sm">
        <CardHeader>
          <CardTitle className="text-xl">Book a ride</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {loadingLocations ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-10 w-full rounded-md" />
              <Skeleton className="h-10 w-full rounded-md" />
            </div>
          ) : (
            <>
              <div className="flex flex-col gap-1">
                <label htmlFor="pickup" className="text-sm font-medium text-foreground">
                  Pickup
                </label>
                <select
                  id="pickup"
                  aria-label="Pickup"
                  value={pickupId}
                  onChange={(e) => setPickupId(e.target.value)}
                  className={selectClass}
                >
                  <option value="">Select pickup…</option>
                  {locations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor="drop" className="text-sm font-medium text-foreground">
                  Drop
                </label>
                <select
                  id="drop"
                  aria-label="Drop"
                  value={dropId}
                  onChange={(e) => setDropId(e.target.value)}
                  className={selectClass}
                >
                  <option value="">Select drop…</option>
                  {locations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-2">
                <input
                  id="schedule-toggle"
                  type="checkbox"
                  aria-label="Schedule for later"
                  checked={scheduleEnabled}
                  onChange={(e) => {
                    setScheduleEnabled(e.target.checked);
                    if (!e.target.checked) setScheduledFor("");
                  }}
                  className="h-4 w-4 accent-[#106344]"
                />
                <label htmlFor="schedule-toggle" className="text-sm text-foreground cursor-pointer">
                  Schedule for later
                </label>
              </div>
              {scheduleEnabled && (
                <div className="flex flex-col gap-1">
                  <label htmlFor="scheduled-for" className="text-sm font-medium text-foreground">
                    Date &amp; time
                  </label>
                  <input
                    id="scheduled-for"
                    type="datetime-local"
                    aria-label="Scheduled for date and time"
                    min={minScheduledFor}
                    value={scheduledFor}
                    onChange={(e) => setScheduledFor(e.target.value)}
                    className={selectClass}
                  />
                </div>
              )}

              {fare !== null && (
                <p
                  aria-label="Fare estimate"
                  className="text-center text-3xl font-bold text-[#106344]"
                >
                  ₹{fare}
                </p>
              )}
            </>
          )}

          {error && (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          )}

          <Button
            type="button"
            onClick={confirmBooking}
            disabled={!ready || submitting}
            className="w-full bg-[#106344] text-white hover:bg-[#0d5238] disabled:opacity-50"
          >
            {submitting ? "Booking…" : "Confirm ride"}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
