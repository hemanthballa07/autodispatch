import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import BookingFlow from "./BookingFlow";
import * as api from "@/lib/api";

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    getToken: vi.fn(),
    setToken: vi.fn(),
    createSession: vi.fn(),
    getLocations: vi.fn(),
    estimateFare: vi.fn(),
    createRide: vi.fn(),
  };
});

const LOCATIONS = [
  { id: "loc-1", name: "Main gate", zone: "CORE" },
  { id: "loc-2", name: "Library", zone: "ACADEMIC" },
];

describe("BookingFlow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getToken).mockReturnValue("token-123");
    vi.mocked(api.getLocations).mockResolvedValue(LOCATIONS);
    vi.mocked(api.estimateFare).mockResolvedValue({ amount: 30 });
  });

  it("select → estimate → confirm books a ride", async () => {
    const onBooked = vi.fn();
    vi.mocked(api.createRide).mockResolvedValue({ id: "ride-1", status: "BROADCASTING" } as never);

    render(<BookingFlow onBooked={onBooked} />);

    await screen.findByLabelText("Pickup");
    fireEvent.change(screen.getByLabelText("Pickup"), { target: { value: "loc-1" } });
    fireEvent.change(screen.getByLabelText("Drop"), { target: { value: "loc-2" } });

    // live fare estimate appears after both selections
    await waitFor(() => expect(screen.getByLabelText("Fare estimate")).toHaveTextContent("₹30"));
    expect(api.estimateFare).toHaveBeenCalledWith("loc-1", "loc-2");

    fireEvent.click(screen.getByRole("button", { name: "Confirm ride" }));

    await waitFor(() => expect(onBooked).toHaveBeenCalledWith("ride-1"));
    expect(api.createRide).toHaveBeenCalledTimes(1);
    expect(api.createRide).toHaveBeenCalledWith("loc-1", "loc-2");
  });

  it("duplicate taps do not create two rides", async () => {
    const onBooked = vi.fn();
    let resolveRide!: (ride: unknown) => void;
    vi.mocked(api.createRide).mockReturnValue(
      new Promise((resolve) => {
        resolveRide = resolve;
      }) as never,
    );

    render(<BookingFlow onBooked={onBooked} />);
    await screen.findByLabelText("Pickup");
    fireEvent.change(screen.getByLabelText("Pickup"), { target: { value: "loc-1" } });
    fireEvent.change(screen.getByLabelText("Drop"), { target: { value: "loc-2" } });
    await screen.findByLabelText("Fare estimate");

    const confirm = screen.getByRole("button", { name: "Confirm ride" });
    fireEvent.click(confirm);
    fireEvent.click(confirm); // second tap while submitting
    fireEvent.click(confirm);

    expect(api.createRide).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Booking…" })).toBeDisabled();

    resolveRide({ id: "ride-2", status: "BROADCASTING" });
    await waitFor(() => expect(onBooked).toHaveBeenCalledWith("ride-2"));
  });

  it("captures name and phone on first run", async () => {
    vi.mocked(api.getToken).mockReturnValue(null);
    vi.mocked(api.createSession).mockResolvedValue({
      token: "fresh-token",
      riderId: "rider-1",
      name: "Asha",
    });

    render(<BookingFlow onBooked={vi.fn()} />);

    await screen.findByText("Tell us who you are");
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Asha" } });
    fireEvent.change(screen.getByLabelText("Phone"), { target: { value: "+919876543210" } });
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() =>
      expect(api.createSession).toHaveBeenCalledWith("Asha", "+919876543210"),
    );
    expect(api.setToken).toHaveBeenCalledWith("fresh-token");
    await screen.findByLabelText("Pickup");
  });
});
