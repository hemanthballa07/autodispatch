import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import RatingModal from "../RatingModal";

vi.mock("@/lib/api", () => ({
  submitRating: vi.fn().mockResolvedValue({ rideId: "r1", stars: 4, comment: null, createdAt: "" }),
}));

describe("RatingModal", () => {
  const onRated = vi.fn();

  beforeEach(() => { onRated.mockClear(); });

  it("renders 5 star buttons", () => {
    render(<RatingModal rideId="r1" onRated={onRated} />);
    expect(screen.getAllByRole("button", { name: /stars/i })).toHaveLength(5);
  });

  it("highlights selected stars", () => {
    render(<RatingModal rideId="r1" onRated={onRated} />);
    fireEvent.click(screen.getByRole("button", { name: "3 stars" }));
    expect(screen.getByRole("button", { name: "3 stars" })).toHaveAttribute("aria-pressed", "true");
  });

  it("submits and calls onRated", async () => {
    render(<RatingModal rideId="r1" onRated={onRated} />);
    fireEvent.click(screen.getByRole("button", { name: "4 stars" }));
    fireEvent.click(screen.getByRole("button", { name: /submit/i }));
    await waitFor(() => expect(onRated).toHaveBeenCalled());
  });

  it("shows thank you after submission", async () => {
    render(<RatingModal rideId="r1" onRated={onRated} />);
    fireEvent.click(screen.getByRole("button", { name: "5 stars" }));
    fireEvent.click(screen.getByRole("button", { name: /submit/i }));
    await waitFor(() => expect(screen.getByText(/thank you/i)).toBeInTheDocument());
  });
});
