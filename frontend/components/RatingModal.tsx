"use client";

import { useState } from "react";
import { submitRating } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

const STARS = [1, 2, 3, 4, 5];

export default function RatingModal({
  rideId,
  onRated,
}: {
  rideId: string;
  onRated: () => void;
}) {
  const [selected, setSelected] = useState(0);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (done) {
    return (
      <Card className="w-full">
        <CardContent className="pt-4 text-center text-sm text-[#106344] font-medium">
          Thank you for your rating!
        </CardContent>
      </Card>
    );
  }

  async function handleSubmit() {
    if (selected === 0 || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitRating(rideId, selected, comment.trim() || undefined);
      setDone(true);
      onRated();
    } catch {
      setError("Could not submit rating. Try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card className="w-full">
      <CardContent className="flex flex-col gap-3 pt-4">
        <p className="text-sm font-medium text-foreground">Rate your driver</p>
        <div className="flex gap-2">
          {STARS.map((n) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} stars`}
              aria-pressed={selected === n}
              onClick={() => setSelected(n)}
              className={`text-2xl transition-transform ${
                selected >= n ? "text-amber-400 scale-110" : "text-muted-foreground/40"
              }`}
            >
              ★
            </button>
          ))}
        </div>
        <textarea
          aria-label="Comment (optional)"
          placeholder="Leave a comment (optional)"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={500}
          rows={2}
          className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-ring"
        />
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        <Button
          type="button"
          onClick={handleSubmit}
          disabled={selected === 0 || submitting}
          className="w-full bg-[#106344] text-white hover:bg-[#0d5238] disabled:opacity-50"
        >
          {submitting ? "Submitting…" : "Submit rating"}
        </Button>
      </CardContent>
    </Card>
  );
}
