import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import ServiceWorkerRegister from "./sw-register";
import BottomNav from "@/components/BottomNav";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "AutoDispatch",
  description: "Auto-rickshaw rides on campus — book in one tap.",
  appleWebApp: {
    capable: true,
    title: "AutoDispatch",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  themeColor: "#106344",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body>
        <ServiceWorkerRegister />
        <main className="pb-16">{children}</main>
        <BottomNav />
      </body>
    </html>
  );
}
