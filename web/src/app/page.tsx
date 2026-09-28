import Link from "next/link";
import { LamphausMark } from "@/components/lamphaus-mark";
import { LatestRelease } from "@/components/latest-release";

// Every title and image on this page is invented for illustration. Lamphaus
// supplies no content (SHR-PROD-05).
const base = process.env.NEXT_PUBLIC_BASE_PATH ?? "";
const art = (kind: "poster" | "backdrop", id: string) => `${base}/art/${kind}/${id}.jpg`;
// Real captures from Android TV (SEI Box R 4K Plus) and a Xiaomi 15T Pro running Lamphaus.
const shot = (name: string) => `${base}/shots/${name}.jpg`;

type Title = { id: string; name: string; meta: string; runtime?: number };

const shelf: Title[] = [
  { id: "measure-of-tide", name: "A Measure of Tide", meta: "Adventure · 2024" },
  { id: "glass-district", name: "Glass District", meta: "Mystery · Series" },
  { id: "deep-field", name: "Deep Field", meta: "Science fiction · 2025" },
  { id: "paper-lanterns", name: "Paper Lanterns", meta: "Romance · 2026" },
  { id: "northbound", name: "Northbound", meta: "Crime · Series" },
  { id: "kite-weather", name: "Kite Weather", meta: "Family · 2025" },
  { id: "iron-meadow", name: "Iron Meadow", meta: "Western · Series" },
];

const tonight: Title[] = [
  { id: "midnight-laundromat", name: "Midnight Laundromat", meta: "1h 34m", runtime: 94 },
  { id: "kite-weather", name: "Kite Weather", meta: "1h 38m", runtime: 98 },
  { id: "paper-lanterns", name: "Paper Lanterns", meta: "1h 41m", runtime: 101 },
  { id: "measure-of-tide", name: "A Measure of Tide", meta: "1h 48m", runtime: 108 },
];

function Screen({ src, alt, className = "" }: { src: string; alt: string; className?: string }) {
  return (
    <div className={`rounded-[10px] bg-[#050507] p-[1.2%] shadow-[0_40px_80px_-30px_rgba(0,0,0,0.8)] ring-1 ring-white/10 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={alt} className="block aspect-video w-full rounded-[6px] object-cover" />
    </div>
  );
}

function Phone({ src, alt, className = "" }: { src: string; alt: string; className?: string }) {
  return (
    <div className={`rounded-[22px] bg-[#050507] p-[1.8%] shadow-[0_30px_60px_-20px_rgba(0,0,0,0.85)] ring-1 ring-white/15 ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={alt} className="block aspect-[1280/2772] w-full rounded-[16px] object-cover" />
    </div>
  );
}

function Poster({ title, focused = false }: { title: Title; focused?: boolean }) {
  return (
    <figure
      className={`group relative shrink-0 transition-transform duration-150 ease-out ${focused ? "z-10 scale-[1.04]" : "hover:z-10 hover:scale-[1.04]"}`}
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={art("poster", title.id)}
        alt=""
        loading="lazy"
        className={`block aspect-[2/3] w-full rounded-card object-cover ring-offset-2 ring-offset-bg transition duration-150 ${
          focused ? "shadow-[0_0_28px_rgba(168,200,255,0.28)] ring-[3px] ring-beam" : "group-hover:shadow-[0_0_28px_rgba(168,200,255,0.28)] group-hover:ring-[3px] group-hover:ring-beam"
        }`}
      />
      <figcaption
        className={`mt-3 text-sm transition-opacity duration-150 ${focused ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
      >
        <span className="block font-medium text-fg">{title.name}</span>
        <span className="block text-fg-subtle">{title.meta}</span>
      </figcaption>
    </figure>
  );
}

function SourceRow({ quality, detail, fit }: { quality: string; detail: string; fit?: string }) {
  return (
    <li className="flex items-start gap-4 rounded-card bg-surface-card px-5 py-4">
      <span className="mt-0.5 w-14 shrink-0 font-display text-lg font-medium text-fg">{quality}</span>
      <span className="min-w-0">
        <span className="block text-sm text-fg">{detail}</span>
        {fit && <span className="mt-1 block text-sm text-lamp">{fit}</span>}
      </span>
    </li>
  );
}

export default function HomePage() {
  return (
    <>
      {/* Hero: the lamp switches on over the screen. */}
      <section className="relative mx-auto grid w-full max-w-6xl items-center gap-14 px-6 pt-10 pb-28 md:px-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:pt-16">
        <div>
          <h1 className="rise font-display text-[clamp(2.75rem,5.6vw,4.75rem)] leading-[1.02] font-medium tracking-[-0.02em]">
            <span className="block whitespace-nowrap">Lights down.</span>
            <span className="block whitespace-nowrap">Lamphaus on.</span>
          </h1>
          <p className="rise rise-2 mt-7 max-w-[34rem] text-lg leading-[1.7] text-fg-muted">
            A calm media home for Android TV, Google TV, and your phone. Add the sources you trust; Lamphaus takes
            care of the picture, the sound, and exactly where you left off.
          </p>
          <div className="rise rise-3 mt-10 flex flex-wrap items-center gap-4">
            <Link
              href="/download/"
              className="rounded-card bg-focus px-6 py-3.5 font-medium text-on-focus transition duration-150 hover:bg-white"
            >
              Download the beta
            </Link>
            <Link
              href="/pair/"
              className="rounded-card px-6 py-3.5 font-medium text-fg ring-1 ring-white/20 transition duration-150 hover:bg-white/[0.06]"
            >
              Pair a TV
            </Link>
          </div>
          <p className="rise rise-4 mt-6 text-sm text-fg-subtle">
            <LatestRelease /> · Android 8.0 and later · one app for phone, tablet, and TV
          </p>
        </div>

        <div className="relative" aria-hidden="true">
          <div className="absolute -top-2 left-1/2 z-10 -translate-x-1/2 lg:-top-6">
            <LamphausMark size={96} />
          </div>
          {/* The lamp's light, falling from the mark onto the screen. */}
          <div
            className="lamp-on lamp-bloom pointer-events-none absolute top-[62px] left-1/2 z-10 h-[62%] w-[92%] -translate-x-1/2 lg:top-[42px]"
            style={{
              clipPath: "polygon(44% 0, 56% 0, 100% 100%, 0 100%)",
              background: "linear-gradient(to bottom, rgba(104,212,232,0.38), rgba(104,212,232,0.10) 55%, rgba(104,212,232,0) 100%)",
              mixBlendMode: "screen",
            }}
          />
          <Screen src={shot("tv-spotlight")} alt="" className="mt-20 lg:mt-24" />
        </div>
      </section>

      {/* Picture and sound */}
      <section id="picture" className="scroll-mt-10 border-t border-white/[0.06] bg-surface/60">
        <div className="mx-auto grid w-full max-w-6xl gap-16 px-6 py-28 md:px-10 lg:grid-cols-2">
          <div>
            <h2 className="font-display text-[clamp(2rem,4vw,3.25rem)] leading-[1.08] font-medium tracking-[-0.01em]">
              Every source, shown the way it was made.
            </h2>
            <p className="mt-6 max-w-[34rem] leading-[1.75] text-fg-muted">
              Before a frame plays, Lamphaus matches your TV to the film&apos;s frame rate and resolution, keeps HDR10,
              HLG, and Dolby Vision when your screen supports them, and sends Dolby and DTS straight to your receiver.
            </p>
            <dl className="mt-10 space-y-7">
              <div>
                <dt className="font-medium text-fg">Know before you press play</dt>
                <dd className="mt-1 max-w-[32rem] text-fg-muted">
                  Each source says whether it plays natively on your device, or what will change.
                </dd>
              </div>
              <div>
                <dt className="font-medium text-fg">Night listening</dt>
                <dd className="mt-1 max-w-[32rem] text-fg-muted">
                  Evens out loud and quiet moments and lifts dialogue, so the late film doesn&apos;t wake the house.
                </dd>
              </div>
              <div>
                <dt className="font-medium text-fg">Subtitles that keep their style</dt>
                <dd className="mt-1 max-w-[32rem] text-fg-muted">
                  Styled anime subtitles keep their fonts and positions; right-to-left languages read correctly.
                </dd>
              </div>
            </dl>
          </div>

          <figure className="self-center">
            <ul className="space-y-3" aria-label="Example source list">
              <SourceRow quality="4K" detail="REMUX · Dolby Vision · TrueHD Atmos 7.1" fit="Dolby Vision plays as HDR10" />
              <SourceRow quality="4K" detail="WEB-DL · Dolby Vision · Dolby Digital Plus Atmos" fit="Plays natively" />
              <SourceRow quality="1080p" detail="WEB-DL · AVC · 5.1" />
            </ul>
            <figcaption className="mt-5 text-sm text-fg-subtle">
              How the same title&apos;s sources read on a TV that takes Dolby Vision streaming profiles but not disc
              remuxes.
            </figcaption>
          </figure>
        </div>
      </section>

      {/* Browsing */}
      <section className="overflow-hidden py-28">
        <div className="mx-auto w-full max-w-6xl px-6 md:px-10">
          <h2 className="max-w-3xl font-display text-[clamp(2rem,4vw,3.25rem)] leading-[1.08] font-medium tracking-[-0.01em]">
            Made to be read from the sofa.
          </h2>
          <p className="mt-6 max-w-[38rem] leading-[1.75] text-fg-muted">
            Artwork leads and the interface stays out of the way. The title you land on tells you just enough, trailers
            play only if you ask them to, and nothing moves while you read.
          </p>
        </div>
        <div className="mx-auto mt-14 w-full max-w-6xl px-6 md:px-10">
          <div className="grid grid-cols-3 gap-5 sm:grid-cols-5 lg:grid-cols-7">
            {shelf.map((title, i) => (
              <div key={title.id} className={i >= 5 ? "hidden lg:block" : i >= 3 ? "hidden sm:block" : ""}>
                <Poster title={title} focused={i === 2} />
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Evenings */}
      <section id="evenings" className="scroll-mt-10 border-t border-white/[0.06]">
        <div className="mx-auto w-full max-w-6xl px-6 py-28 md:px-10">
          <h2 className="max-w-3xl font-display text-[clamp(2rem,4vw,3.25rem)] leading-[1.08] font-medium tracking-[-0.01em]">
            Evenings, planned around you.
          </h2>

          <div className="mt-16 grid gap-16 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
            <figure>
              <p className="font-display text-2xl font-medium">Ends before 11:30 PM</p>
              <p className="text-sm text-fg-subtle">Movies you can finish tonight</p>
              <ul className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4" aria-label="Example Fits tonight row">
                {tonight.map((title) => (
                  <li key={title.id}>
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={art("poster", title.id)} alt="" loading="lazy" className="aspect-[2/3] w-full rounded-card object-cover" />
                    <p className="mt-2 truncate text-sm text-fg">{title.name}</p>
                    <p className="text-xs text-fg-subtle">{title.meta}</p>
                  </li>
                ))}
              </ul>
              <figcaption className="mt-6 max-w-[34rem] leading-[1.75] text-fg-muted">
                In the evening, Home leads with the movies you can still finish before the time you choose.
              </figcaption>
            </figure>

            <div className="space-y-12">
              <figure className="rounded-hero bg-surface p-7">
                <p className="text-sm font-medium text-primary">Previously</p>
                <p className="mt-2 font-display text-xl font-medium">S1 · E2  Refraction</p>
                <p className="mt-2 text-fg-muted">A reflection points Mara toward an abandoned plan for the district.</p>
                <figcaption className="mt-6 border-t border-white/10 pt-5 text-sm leading-[1.7] text-fg-subtle">
                  Back after a few weeks? A series recaps the last episode you finished, never one you haven&apos;t seen.
                </figcaption>
              </figure>
              <div>
                <p className="font-medium text-fg">The next episode, when you want it</p>
                <p className="mt-1 text-fg-muted">
                  It starts after a short countdown, skips the intro if you like, and after three in a row Lamphaus
                  asks whether you&apos;re still watching.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Phone and TV */}
      <section id="together" className="scroll-mt-10 bg-surface/60">
        <div className="mx-auto grid w-full max-w-6xl gap-16 px-6 py-28 md:px-10 lg:grid-cols-2">
          <div>
            <h2 className="font-display text-[clamp(2rem,4vw,3.25rem)] leading-[1.08] font-medium tracking-[-0.01em]">
              Phone and TV, one library.
            </h2>
            <p className="mt-6 max-w-[34rem] leading-[1.75] text-fg-muted">
              Pause on the TV and your phone picks up at the same second. Your library, progress, and profiles follow
              you, and on Google TV what you&apos;re watching waits in the home screen&apos;s Continue watching row.
            </p>
          </div>
          <div>
            <h3 className="font-display text-xl font-medium">Pairing takes one scan</h3>
            <ol className="mt-6 space-y-6">
              {[
                ["Your TV shows a code.", "No passwords typed with a remote."],
                ["Scan it with your phone.", "It opens in the browser; the app is optional."],
                ["Sign in with Google.", "The TV is yours and stays signed in."],
              ].map(([title, body], i) => (
                <li key={title} className="grid grid-cols-[2.5rem_1fr] gap-2">
                  <span className="font-display text-2xl leading-none font-medium text-primary">{i + 1}</span>
                  <span>
                    <span className="block font-medium text-fg">{title}</span>
                    <span className="block text-fg-muted">{body}</span>
                  </span>
                </li>
              ))}
            </ol>
          </div>
          <div className="relative lg:col-span-2" aria-hidden="true">
            <Screen src={shot("tv-details")} alt="" className="w-[84%] lg:w-[68%]" />
            <Phone
              src={shot("phone-home")}
              alt=""
              className="absolute right-0 bottom-[-8%] w-[26%] lg:right-[22%] lg:w-[16%]"
            />
          </div>
        </div>
      </section>

      {/* Honest by design */}
      <section className="mx-auto w-full max-w-6xl px-6 py-28 md:px-10">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)]">
          <h2 className="font-display text-[clamp(2rem,4vw,3.25rem)] leading-[1.08] font-medium tracking-[-0.01em]">
            It comes with nothing to watch, on purpose.
          </h2>
          <div className="space-y-5 leading-[1.75] text-fg-muted">
            <p>
              Lamphaus is a player and a library, not a service. You add compatible add-ons you trust, and your
              sources stay yours: their addresses are hidden once added and never appear in diagnostics.
            </p>
            <p>
              No ads, no selling data, no tracking scripts. Crash reports are sent only if you turn them on.{" "}
              <Link href="/privacy/" className="text-primary underline decoration-primary/40 underline-offset-4 hover:decoration-primary">
                Read the privacy notes
              </Link>
            </p>
          </div>
        </div>
      </section>

      {/* Download: the house lit up. */}
      <section className="bg-house text-on-house">
        <div className="mx-auto grid w-full max-w-6xl items-center gap-10 px-6 py-24 md:px-10 lg:grid-cols-[1fr_auto]">
          <div>
            <h2 className="font-display text-[clamp(2.25rem,5vw,4rem)] leading-[1.04] font-medium tracking-[-0.015em]">
              Ready when the TV is.
            </h2>
            <p className="mt-5 max-w-[36rem] text-lg text-on-house-muted">
              One app for Android phones, tablets, Android TV, and Google TV. Updates arrive inside the app, signed and verified.
            </p>
            <p className="mt-3 text-sm text-on-house-muted">
              <LatestRelease />
            </p>
          </div>
          <div className="flex flex-wrap gap-4">
            <Link
              href="/download/"
              className="rounded-card bg-white px-7 py-4 font-medium text-[#1a2a6c] transition duration-150 hover:bg-focus"
            >
              Download the beta
            </Link>
            <Link
              href="/pair/"
              className="rounded-card px-7 py-4 font-medium text-on-house ring-1 ring-white/50 transition duration-150 hover:bg-white/10"
            >
              Pair a TV
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
