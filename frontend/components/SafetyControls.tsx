"use client";

import { useState } from "react";
import { triggerSos, reportIncident } from "@/lib/api";
import { Button } from "@/components/ui/button";

type Phase = "idle" | "sos-confirm" | "sos-sent" | "incident-form" | "incident-sent";

export default function SafetyControls({ rideId }: { rideId: string }) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [details, setDetails] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function sendSos() {
    setError(null);
    try {
      await triggerSos(rideId);
      setPhase("sos-sent");
    } catch {
      setError("SOS could not be sent. Call 112 immediately.");
    }
  }

  async function sendIncident() {
    setError(null);
    try {
      await reportIncident(rideId, details.trim());
      setPhase("incident-sent");
    } catch {
      setError("Could not submit report. Try again.");
    }
  }

  if (phase === "sos-sent") {
    return (
      <div role="status" className="rounded-lg bg-red-50 p-3 text-center text-sm font-medium text-red-700">
        SOS sent — help is on the way. Call 112 if needed.
      </div>
    );
  }

  if (phase === "incident-sent") {
    return (
      <div role="status" className="rounded-lg bg-amber-50 p-3 text-center text-sm text-amber-700">
        Incident reported. Thank you.
      </div>
    );
  }

  if (phase === "sos-confirm") {
    return (
      <div className="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 p-3">
        <p className="text-sm font-medium text-red-700">Confirm SOS alert?</p>
        <div className="flex gap-2">
          <Button size="sm" variant="destructive" aria-label="Yes, send SOS" onClick={sendSos}>
            Yes
          </Button>
          <Button size="sm" variant="outline" onClick={() => setPhase("idle")}>
            Cancel
          </Button>
        </div>
      </div>
    );
  }

  if (phase === "incident-form") {
    return (
      <div className="flex flex-col gap-2">
        <textarea
          aria-label="Incident details"
          placeholder="Describe the incident…"
          value={details}
          onChange={(e) => setDetails(e.target.value)}
          rows={3}
          maxLength={1000}
          className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-ring"
        />
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-2">
          <Button size="sm" onClick={sendIncident} disabled={!details.trim()}
                  className="bg-amber-600 text-white hover:bg-amber-700">
            Report
          </Button>
          <Button size="sm" variant="outline" onClick={() => setPhase("idle")}>Cancel</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex gap-2">
      <Button
        type="button"
        variant="destructive"
        size="sm"
        aria-label="SOS emergency"
        onClick={() => setPhase("sos-confirm")}
        className="flex-1"
      >
        🆘 SOS
      </Button>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setPhase("incident-form")}
        className="flex-1 border-amber-300 text-amber-700 hover:bg-amber-50"
      >
        Report incident
      </Button>
    </div>
  );
}
