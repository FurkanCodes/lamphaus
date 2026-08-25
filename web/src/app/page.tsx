import Link from "next/link";
import { LamphausMark } from "@/components/lamphaus-mark";

const steps = [
  {
    n: "01",
    title: "Your TV shows a QR code.",
    body: "Cold start, straight to pairing. Nobody types an account password with a remote.",
  },
  {
    n: "02",
    title: "Scan it with any phone.",
    body: "The code opens this site in the browser — installing the Lamphaus app is optional, not required.",
  },
  {
    n: "03",
    title: "Sign in with Google.",
    body: "The TV inherits your identity the moment you approve, and stays signed in across restarts.",
  },
];

const features = [
  {
    term: "Library follows you",
    detail: "Save something from the couch; it's already in your pocket before you reach the door.",
  },
  {
    term: "Progress follows you",
    detail: "Pause at minute 43 on the TV. Your phone offers minute 43 — not minute one.",
  },
  {
    term: "Profiles per person",
    detail: "Everyone in the house gets their own space, with PIN locks where wanted.",
  },
  {
    term: "Live, not eventual",
    detail: "Changes land on your other devices while they're open — no refresh ritual.",
  },
];

export default function HomePage() {
  return (
    <div className="mx-auto w-full max-w-5xl px-6 md:px-12">
      {/* Hero */}
      <section className="grid items-center gap-14 pt-16 pb-24 md:grid-cols-[1fr_auto] md:pt-24">
        <div className="max-w-xl">
          <h1 className="rise font-display text-5xl leading-[1.04] font-semibold tracking-tight md:text-7xl">
            Start on your phone.
            <br />
            <span className="text-primary">Finish on your TV.</span>
          </h1>
          <p className="rise rise-2 mt-7 max-w-md text-lg leading-relaxed text-fg-muted">
            Lamphaus remembers where you stopped — down to the second — and
            carries it between every screen in the house.
          </p>
          <div className="rise rise-3 mt-9 flex flex-wrap items-center gap-4">
            <Link
              href="/pair/"
              className="rounded-card bg-primary px-6 py-3 font-medium text-on-primary transition duration-[160ms] ease-out hover:brightness-95"
            >
              Pair a TV
            </Link>
            <a
              href="#how"
              className="rounded-card bg-white/[0.06] px-6 py-3 font-medium text-fg transition duration-[160ms] ease-out hover:bg-white/10"
            >
              How pairing works
            </a>
          </div>
        </div>

        {/* The beam: mark plus a hard horizontal light line. Flat, no glow. */}
        <div className="rise rise-2 hidden items-center md:flex" aria-hidden="true">
          <LamphausMark size={180} />
          <div className="h-[2px] w-28 -ml-2 bg-beam lg:w-44" />
        </div>
      </section>

      {/* Pairing steps */}
      <section id="how" className="scroll-mt-16 pb-24">
        <h2 className="rise font-display text-2xl font-semibold tracking-tight md:text-3xl">
          Pairing takes one scan.
        </h2>
        <ol className="mt-10">
          {steps.map((step, i) => (
            <li
              key={step.n}
              className={`rise rise-${i + 2} grid grid-cols-[auto_1fr] items-baseline gap-6 border-t border-white/10 py-8`}
            >
              <span className="font-display text-sm font-medium text-fg-subtle">{step.n}</span>
              <div>
                <h3 className="font-display text-xl font-semibold">{step.title}</h3>
                <p className="mt-2 max-w-prose leading-relaxed text-fg-muted">{step.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* Features */}
      <section className="pb-24">
        <h2 className="rise font-display text-2xl font-semibold tracking-tight md:text-3xl">
          Everything follows you.
        </h2>
        <dl className="mt-10 grid gap-x-20 gap-y-10 sm:grid-cols-2">
          {features.map((f, i) => (
            <div key={f.term} className={`rise rise-${i + 2}`}>
              <dt className="font-display text-lg font-semibold">{f.term}</dt>
              <dd className="mt-2 leading-relaxed text-fg-muted">{f.detail}</dd>
            </div>
          ))}
        </dl>
      </section>

      {/* Closing band */}
      <section className="mb-4">
        <div className="rise flex flex-wrap items-center justify-between gap-6 rounded-hero bg-surface p-10">
          <p className="font-display text-2xl font-semibold tracking-tight">
            Ready when the TV is.
          </p>
          <Link
            href="/pair/"
            className="rounded-card bg-primary px-6 py-3 font-medium text-on-primary transition duration-[160ms] ease-out hover:brightness-95"
          >
            Pair a TV
          </Link>
        </div>
      </section>
    </div>
  );
}
