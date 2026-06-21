import { cn } from "@/lib/utils";

const STATUS_STYLES: Record<string, string> = {
  REQUESTED: "bg-gray-100 text-gray-700",
  BROADCASTING: "bg-amber-100 text-amber-700",
  ASSIGNED: "bg-green-100 text-green-700",
  ARRIVED: "bg-green-100 text-green-700",
  IN_PROGRESS: "bg-green-100 text-green-700",
  COMPLETED: "bg-blue-100 text-blue-700",
  CANCELLED: "bg-red-100 text-red-700",
  EXPIRED: "bg-red-100 text-red-700",
};

const STATUS_LABELS: Record<string, string> = {
  REQUESTED: "Requested",
  BROADCASTING: "Finding auto",
  ASSIGNED: "Assigned",
  ARRIVED: "Arrived",
  IN_PROGRESS: "On trip",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

export default function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
        STATUS_STYLES[status] ?? "bg-gray-100 text-gray-600",
      )}
    >
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}
