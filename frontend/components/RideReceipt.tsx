import Link from "next/link";
import type { Ride } from "@/lib/api";
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

  return (
    <Card className="w-full">
      <CardContent className="flex flex-col gap-3 pt-4">
        <p className={`text-lg font-bold ${header.color}`}>✓ {header.label}</p>

        <div className="flex flex-col gap-1 text-sm text-foreground">
          <p>
            {ride.pickup} → {ride.drop}
          </p>
          {ride.fare != null && (
            <p className="font-medium">
              Fare: ₹{ride.fare}
            </p>
          )}
          {ride.driver && (
            <p className="text-muted-foreground">
              Driver: {ride.driver.name} · {ride.driver.vehicleNo}
            </p>
          )}
          {duration && (
            <p className="text-muted-foreground">Duration: {duration}</p>
          )}
        </div>

        <Button
          asChild
          className="mt-2 w-full bg-[#106344] text-white hover:bg-[#0d5238]"
        >
          <Link href="/book">Book another ride</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
