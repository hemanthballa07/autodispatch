"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ApiError, getToken, listRides, type Ride } from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import StatusBadge from "@/components/StatusBadge";

export default function HistoryPage() {
  const [rides, setRides] = useState<Ride[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (!getToken()) {
      setError("Start a session from the booking page first.");
      setRides([]);
      return;
    }
    setRides(null);
    listRides(page)
      .then(setRides)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load history."));
  }, [page]);

  return (
    <main className="mx-auto max-w-md px-4 py-6">
      <h1 className="mb-6 text-2xl font-bold text-foreground">Your rides</h1>

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
