import type { Metadata } from "next";
import Link from "next/link";
import { Instrument_Sans, Sora } from "next/font/google";
import { LamphausMark } from "@/components/lamphaus-mark";
import "./globals.css";

const sora = Sora({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-sora",
  display: "swap",
});

const instrument = Instrument_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-instrument",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Lamphaus — Start on your phone. Finish on your TV.",
    template: "%s · Lamphaus",
  },
  description:
    "Watch together with yourself: Lamphaus carries your library, progress and profiles between every screen in the house.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${sora.variable} ${instrument.variable}`}>
      <body className="flex min-h-screen flex-col">
        <header className="rise flex items-center justify-between px-6 py-5 md:px-12">
          <Link href="/" className="flex items-center gap-3">
            <LamphausMark size={28} />
            <span className="font-display text-sm font-semibold tracking-[0.22em] text-fg">
              LAMPHAUS
            </span>
          </Link>
          <nav className="text-sm font-medium">
            <Link href="/pair/" className="text-primary transition-colors duration-[160ms] hover:text-beam">
              Pair a TV
            </Link>
          </nav>
        </header>
        <main className="flex-1">{children}</main>
        <footer className="mt-24 border-t border-white/10 px-6 py-6 md:px-12">
          <div className="mx-auto flex w-full max-w-5xl items-center justify-between text-xs text-fg-subtle">
            <span>© 2026 Lamphaus</span>
            <Link href="/privacy/" className="transition-colors duration-[160ms] hover:text-fg-muted">
              Privacy
            </Link>
          </div>
        </footer>
      </body>
    </html>
  );
}
