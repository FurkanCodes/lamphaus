import type { Metadata } from "next";
import Link from "next/link";
import { Inter, Jost } from "next/font/google";
import { LamphausLockup } from "@/components/lamphaus-lockup";
import "./globals.css";

// Jost carries the display voice and the wordmark: geometric capitals, the
// "haus" in Lamphaus. Inter is the app's own text face.
const jost = Jost({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-jost",
  display: "swap",
});

const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Lamphaus: a calm home for what you watch",
    template: "%s · Lamphaus",
  },
  description:
    "Lamphaus is a media home for Android TV, Google TV, and Android phones. Add the sources you trust; it takes care of the picture, the sound, and where you left off.",
};

const nav = [
  { href: "/#picture", label: "Picture & sound" },
  { href: "/#evenings", label: "Evenings" },
  { href: "/#together", label: "Phone & TV" },
];

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${jost.variable} ${inter.variable}`}>
      <body className="flex min-h-screen flex-col">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-card focus:bg-focus focus:px-4 focus:py-2 focus:text-on-focus"
        >
          Skip to content
        </a>
        <header className="relative z-20 mx-auto flex w-full max-w-6xl items-center justify-between gap-6 px-6 py-6 md:px-10">
          <Link href="/" aria-label="Lamphaus home">
            <LamphausLockup size={30} />
          </Link>
          <nav aria-label="Primary" className="flex items-center gap-7 text-sm font-medium">
            <ul className="hidden items-center gap-7 md:flex">
              {nav.map((item) => (
                <li key={item.href}>
                  <Link href={item.href} className="text-fg-muted transition-colors duration-150 hover:text-fg">
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
            <Link
              href="/download/"
              className="rounded-card bg-focus px-4 py-2 text-on-focus transition duration-150 hover:bg-white"
            >
              Download
            </Link>
          </nav>
        </header>
        <main id="main" className="flex-1">
          {children}
        </main>
        <footer className="border-t border-white/10">
          <div className="mx-auto grid w-full max-w-6xl gap-8 px-6 py-12 text-sm md:grid-cols-[1fr_auto] md:px-10">
            <div className="max-w-md">
              <LamphausLockup size={24} />
              <p className="mt-4 text-fg-subtle">
                Lamphaus supplies no movies, shows, or streams, and is not affiliated with any media service.
                You choose the sources it uses.
              </p>
            </div>
            <ul className="flex flex-wrap items-start gap-x-8 gap-y-3 text-fg-muted">
              <li><Link href="/download/" className="hover:text-fg">Download</Link></li>
              <li><Link href="/pair/" className="hover:text-fg">Pair a TV</Link></li>
              <li><Link href="/privacy/" className="hover:text-fg">Privacy</Link></li>
              <li>
                <a href="https://github.com/furkancodes/lamphaus/releases" className="hover:text-fg">
                  Releases
                </a>
              </li>
            </ul>
            <p className="text-xs text-fg-subtle md:col-span-2">© 2026 Lamphaus</p>
          </div>
        </footer>
      </body>
    </html>
  );
}
