import type { DriverCard as DriverInfo } from "@/lib/api";

export default function DriverCard({ driver }: { driver: DriverInfo }) {
  const initial = driver.name.charAt(0).toUpperCase();

  return (
    <div aria-label="Driver" className="flex items-center gap-3 rounded-xl border border-border bg-card p-4">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#106344] text-sm font-bold text-white">
        {initial}
      </div>
      <div>
        <p className="font-semibold text-foreground">{driver.name}</p>
        <p className="text-sm text-muted-foreground">
          {driver.vehicleNo} · {driver.maskedPhone}
        </p>
      </div>
    </div>
  );
}
