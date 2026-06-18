"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import BookingFlow from "@/components/BookingFlow";

export default function BookRidePage() {
  const router = useRouter();
  return (
    <main style={{ padding: "1.5rem", maxWidth: 480, margin: "0 auto" }}>
      <h1>AutoDispatch</h1>
      <BookingFlow onBooked={(rideId) => router.push(`/ride/${rideId}`)} />
      <p>
        <Link href="/history">Ride history</Link>
      </p>
    </main>
  );
}
