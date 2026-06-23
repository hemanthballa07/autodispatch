"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { Ride, PaymentTransaction } from "@/lib/api";
import { getPayments, acknowledgePayment, ApiError } from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

function formatDuration(requestedAt: string, completedAt: string): string {
  const start = new Date(requestedAt).getTime();
  const end = new Date(completedAt).getTime();
  const mins = Math.round((end - start) / 60000);
  return `~${mins} min`;
}

const HEADER: Record<string, { label: string; color: string }> = {
  COMPLETED: { label: "Ride Complete", color: "text-[#106344]" },
  CANCELLED: { label: "Ride Cancelled", color: "text-red-600" },
  EXPIRED: { label: "No Drivers Found", color: "text-amber-600" },
};

export default function RideReceipt({ ride }: { ride: Ride }) {
  const header = HEADER[ride.status] ?? { label: ride.status, color: "text-foreground" };
  const duration =
    ride.requestedAt && ride.completedAt
      ? formatDuration(ride.requestedAt, ride.completedAt)
      : null;

  const [payment, setPayment] = useState<PaymentTransaction | null>(null);
  const [acking, setAcking] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);

  useEffect(() => {
    if (ride.status !== "COMPLETED") return;
    getPayments(ride.id)
      .then((txs) => {
        const fare = txs.find((t) => t.type === "RIDE_FARE");
        if (fare) setPayment(fare);
      })
      .catch(() => { /* payment not yet available — ignore */ });
  }, [ride.id, ride.status]);

  async function handleAcknowledge() {
    setAcking(true);
    setPayError(null);
    try {
      const updated = await acknowledgePayment(ride.id);
      setPayment(updated);
    } catch (e) {
      setPayError(e instanceof ApiError ? e.message : "Could not confirm payment.");
    } finally {
      setAcking(false);
    }
  }

  return (
    <Card className="w-full">
      <CardContent className="flex flex-col gap-3 pt-4">
        <p className={`text-lg font-bold ${header.color}`}>✓ {header.label}</p>

        <div className="flex flex-col gap-1 text-sm text-foreground">
          <p>{ride.pickup} → {ride.drop}</p>
          {ride.fare != null && <p className="font-medium">Fare: ₹{ride.fare}</p>}
          {ride.driver && (
            <p className="text-muted-foreground">
              Driver: {ride.driver.name} · {ride.driver.vehicleNo}
            </p>
          )}
          {duration && <p className="text-muted-foreground">Duration: {duration}</p>}
        </div>

        {payment && (
          <div className="rounded-md bg-muted px-3 py-2 text-sm">
            {payment.status === "COLLECTED" ? (
              <p className="text-[#106344] font-medium">✓ Cash paid (₹{payment.amount})</p>
            ) : (
              <div className="flex flex-col gap-2">
                <p className="text-muted-foreground">Cash payment pending: ₹{payment.amount}</p>
                {payError && <p role="alert" className="text-xs text-red-600">{payError}</p>}
                <Button
                  size="sm"
                  onClick={handleAcknowledge}
                  disabled={acking}
                  className="bg-[#106344] text-white hover:bg-[#0d5238]"
                >
                  {acking ? "Confirming…" : "Mark as paid"}
                </Button>
              </div>
            )}
          </div>
        )}

        <Button asChild className="mt-2 w-full bg-[#106344] text-white hover:bg-[#0d5238]">
          <Link href="/book">Book another ride</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
