import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import RideStatusView from "./RideStatusView";
import type { Ride } from "@/lib/api";

function ride(overrides: Partial<Ride>): Ride {
  return {
    id: "ride-1",
    status: "REQUESTED",
    pickup: "Main gate",
    drop: "Library",
    fare: 30,
    driver: null,
    cancellable: false,
    requestedAt: "2026-06-11T00:00:00Z",
    completedAt: null,
    cancelReason: null,
    ...overrides,
  };
}

const EXPECTED_HEADLINES: Record<string, string> = {
  REQUESTED: "Ride requested",
  BROADCASTING: "Finding an auto near you…",
  ASSIGNED: "Driver assigned",
  ARRIVED: "Your auto has arrived",
  IN_PROGRESS: "On trip",
  COMPLETED: "Trip completed",
  CANCELLED: "Ride cancelled",
  EXPIRED: "No autos right now",
};

describe("RideStatusView", () => {
  it.each(Object.entries(EXPECTED_HEADLINES))(
    "renders the %s state",
    (status, headline) => {
      render(<RideStatusView ride={ride({ status })} />);
      expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent(headline);
    },
  );

  it("shows the driver card when assigned", () => {
    render(
      <RideStatusView
        ride={ride({
          status: "ASSIGNED",
          driver: { name: "Ravi", vehicleNo: "KA-01-1234", maskedPhone: "+91••••••4455" },
        })}
      />,
    );
    expect(screen.getByText("Ravi")).toBeInTheDocument();
    expect(screen.getByText(/KA-01-1234/)).toBeInTheDocument();
    expect(screen.getByText(/\+91••••••4455/)).toBeInTheDocument();
  });

  it("shows cancel only in cancellable states and wires the handler", () => {
    const onCancel = vi.fn();
    const { rerender } = render(
      <RideStatusView ride={ride({ status: "BROADCASTING", cancellable: true })} onCancel={onCancel} />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Cancel ride" }));
    expect(onCancel).toHaveBeenCalledTimes(1);

    rerender(<RideStatusView ride={ride({ status: "IN_PROGRESS", cancellable: false })} onCancel={onCancel} />);
    expect(screen.queryByRole("button", { name: "Cancel ride" })).not.toBeInTheDocument();
  });

  it("renders friendly terminal screens", () => {
    const { rerender } = render(<RideStatusView ride={ride({ status: "EXPIRED" })} />);
    expect(screen.getByRole("status")).toHaveTextContent("No autos right now, try again");

    rerender(<RideStatusView ride={ride({ status: "CANCELLED", cancelReason: "Cancelled by rider" })} />);
    expect(screen.getByRole("status")).toHaveTextContent("Cancelled by rider");
  });
});
