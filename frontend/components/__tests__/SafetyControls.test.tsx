import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect } from "vitest";
import SafetyControls from "../SafetyControls";

vi.mock("@/lib/api", () => ({
  triggerSos: vi.fn().mockResolvedValue(undefined),
  reportIncident: vi.fn().mockResolvedValue(undefined),
}));

describe("SafetyControls", () => {
  it("renders SOS button", () => {
    render(<SafetyControls rideId="r1" />);
    expect(screen.getByRole("button", { name: /sos/i })).toBeInTheDocument();
  });

  it("shows confirm dialog before sending SOS", () => {
    render(<SafetyControls rideId="r1" />);
    fireEvent.click(screen.getByRole("button", { name: /sos/i }));
    expect(screen.getByText(/confirm/i)).toBeInTheDocument();
  });

  it("sends SOS on confirmation", async () => {
    const { triggerSos } = await import("@/lib/api");
    render(<SafetyControls rideId="r1" />);
    fireEvent.click(screen.getByRole("button", { name: /sos/i }));
    fireEvent.click(screen.getByRole("button", { name: /yes/i }));
    await waitFor(() => expect(triggerSos).toHaveBeenCalledWith("r1"));
  });

  it("shows sent confirmation after SOS", async () => {
    render(<SafetyControls rideId="r1" />);
    fireEvent.click(screen.getByRole("button", { name: /sos/i }));
    fireEvent.click(screen.getByRole("button", { name: /yes/i }));
    await waitFor(() => expect(screen.getByText(/help is on the way/i)).toBeInTheDocument());
  });
});
