import { cn } from "@/lib/utils";

const STEPS = [
  { state: "SCHEDULED", label: "Scheduled" },
  { state: "REQUESTED", label: "Requested" },
  { state: "BROADCASTING", label: "Finding driver" },
  { state: "ASSIGNED", label: "Driver assigned" },
  { state: "ARRIVED", label: "Driver arrived" },
  { state: "IN_PROGRESS", label: "In progress" },
  { state: "COMPLETED", label: "Done" },
];

export default function RideProgressStepper({ status }: { status: string }) {
  const currentIndex = STEPS.findIndex((s) => s.state === status);

  return (
    <ol aria-label="Progress" className="flex flex-col gap-3">
      {STEPS.map((step, i) => {
        const done = i < currentIndex;
        const active = i === currentIndex;
        return (
          <li
            key={step.state}
            aria-current={active ? "step" : undefined}
            className="flex items-center gap-3"
          >
            <span
              className={cn(
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold",
                done && "bg-[#106344] text-white",
                active && "animate-pulse bg-[#106344] text-white",
                !done && !active && "bg-gray-100 text-gray-400",
              )}
            >
              {done ? "✓" : i + 1}
            </span>
            <span
              className={cn(
                "text-sm",
                done && "text-gray-500 line-through",
                active && "font-semibold text-[#106344]",
                !done && !active && "text-gray-400",
              )}
            >
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
