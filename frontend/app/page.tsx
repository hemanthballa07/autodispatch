import Link from "next/link";

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col bg-[#f9fafb]">
      {/* Hero */}
      <main className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-[#111827] sm:text-5xl">
          Campus rides, instant.
        </h1>
        <p className="mt-4 max-w-sm text-base text-[#6b7280]">
          Auto-rickshaw dispatch via WhatsApp — book in one tap, no app for drivers.
        </p>
        <Link
          href="/book"
          className="mt-8 inline-flex items-center justify-center rounded-lg bg-[#106344] px-6 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-[#0d5238]"
        >
          Book a ride →
        </Link>

        {/* How it works */}
        <div className="mt-14 w-full max-w-md">
          <h2 className="mb-6 text-sm font-semibold uppercase tracking-wider text-[#6b7280]">
            How it works
          </h2>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div className="flex flex-col items-center gap-2">
              <span className="text-2xl">📱</span>
              <p className="text-xs text-[#111827]">Enter your name &amp; phone</p>
            </div>
            <div className="flex flex-col items-center gap-2">
              <span className="text-2xl">📍</span>
              <p className="text-xs text-[#111827]">Pick your stop, see the fare</p>
            </div>
            <div className="flex flex-col items-center gap-2">
              <span className="text-2xl">🛺</span>
              <p className="text-xs text-[#111827]">Driver confirms on WhatsApp</p>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="py-4 text-center text-xs text-[#6b7280]">
        WhatsApp-powered · Campus only · No app for drivers
      </footer>
    </div>
  );
}
