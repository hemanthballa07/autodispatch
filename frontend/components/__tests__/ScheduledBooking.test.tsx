import { render, screen, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect } from "vitest";
import BookingFlow from "../BookingFlow";

vi.mock("@/lib/api", () => ({
  getToken: vi.fn().mockReturnValue("tok"),
  getLocations: vi.fn().mockResolvedValue([
    { id: "loc1", name: "Gate A", zone: "Z1" },
    { id: "loc2", name: "Hostel B", zone: "Z2" },
  ]),
  estimateFare: vi.fn().mockResolvedValue({ amount: 35 }),
  createRide: vi.fn().mockResolvedValue({ id: "ride1", status: "SCHEDULED", scheduledFor: null }),
  setToken: vi.fn(),
}));

describe("BookingFlow — scheduled ride", () => {
  it("shows schedule toggle in booking form", async () => {
    render(<BookingFlow onBooked={vi.fn()} />);
    expect(await screen.findByLabelText(/schedule for later/i)).toBeInTheDocument();
  });

  it("shows datetime input when schedule toggle is enabled", async () => {
    render(<BookingFlow onBooked={vi.fn()} />);
    const toggle = await screen.findByLabelText(/schedule for later/i);
    fireEvent.click(toggle);
    expect(screen.getByLabelText(/date.*time|scheduled.*for/i)).toBeInTheDocument();
  });
});
