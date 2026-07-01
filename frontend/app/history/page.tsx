"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ApiError, getToken, listRides, type Ride } from "@/lib/api";

const HISTORY_CACHE_KEY = "autodispatch_history_cache";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import StatusBadge from "@/components/StatusBadge";

export default function HistoryPage() {
  const [rides, setRides] = useState<Ride[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [stale, setStale] = useState(false);
  const hasCacheRef = useRef(false);

  // Mount-only: seed UI from localStorage before the first network fetch lands.
  useEffect(() => {
    if (!getToken()) return;
    try {
      const raw = localStorage.getItem(HISTORY_CACHE_KEY);
      if (raw) {
        setRides(JSON.parse(raw) as Ride[]);
        setStale(true);
        hasCacheRef.current = true;
      }
    } catch { /* corrupted data — ignore, fresh fetch will overwrite */ }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      setError("Start a session from the booking page first.");
      setRides([]);
      return;
    }
    setError(null);
    // Don't flash spinner when we already have stale cache data for page 0.
    if (page !== 0 || !hasCacheRef.current) setRides(null);
    listRides(page)
      .then((data) => {
        setRides(data);
        setStale(false);
        hasCacheRef.current = false;
        if (page === 0) {
          try { localStorage.setItem(HISTORY_CACHE_KEY, JSON.stringify(data)); } catch { /* quota */ }
        }
      })
      .catch((e) => {
        setError(e instanceof ApiError ? e.message : "Could not load history.");
        // Keep stale rides visible — do not null them out on network failure.
      });
  }, [page]);

  return (
    <main className="mx-auto max-w-md px-4 py-6">
      <div className="mb-6 flex items-center gap-3">
        <h1 className="text-2xl font-bold text-foreground">Your rides</h1>
        {stale && (
          <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">
            Cached — connect to refresh
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className="mb-4 text-sm text-red-600">
          {error}
        </p>
      )}

      {rides === null && !error && (
        <p className="text-sm text-muted-foreground">Loading…</p>
      )}

      {rides !== null && rides.length === 0 && !error && (
        <div className="flex flex-col items-center gap-4 py-12 text-center">
          <p className="text-muted-foreground">No rides yet.</p>
          <Button asChild className="bg-[#106344] text-white hover:bg-[#0d5238]">
            <Link href="/book">Book your first one →</Link>
          </Button>
        </div>
      )}

      <div className="flex flex-col gap-3">
        {rides?.map((ride) => (
          <Link key={ride.id} href={`/ride/${ride.id}`}>
            <Card className="transition hover:shadow-md">
              <CardContent className="flex items-center justify-between py-4">
                <div className="flex flex-col gap-1">
                  <p className="text-sm font-medium text-foreground">
                    {ride.pickup} → {ride.drop}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {new Date(ride.requestedAt).toLocaleString("en-IN", {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                    {ride.fare != null && ` · ₹${ride.fare}`}
                  </p>
                </div>
                <StatusBadge status={ride.status} />
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      {rides !== null && (rides.length > 0 || page > 0) && (
        <div className="mt-6 flex items-center justify-between gap-4">
          <Button
            variant="outline"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Prev
          </Button>
          <span className="text-sm text-muted-foreground">Page {page + 1}</span>
          <Button
            variant="outline"
            disabled={rides.length < 20}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </main>
  );
}
