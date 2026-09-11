"use client";

import { useEffect, useState } from "react";
import {
  RELEASES_PAGE,
  formatBytes,
  loadVerifiedFeed,
  renderChangelog,
  type FeedRelease,
  type FeedStatus,
} from "@/lib/updates";

/**
 * Download page (plan §5). Phone and TV buttons resolve to the same universal
 * APK. Handles loading, no release, withdrawal, invalid metadata, offline,
 * and GitHub unavailability explicitly; without JS or successful validation
 * the static fallback links the GitHub Releases page.
 */
export default function DownloadPage() {
  const [status, setStatus] = useState<FeedStatus | null>(null);

  useEffect(() => {
    let live = true;
    loadVerifiedFeed().then((s) => {
      if (live) setStatus(s);
    });
    return () => {
      live = false;
    };
  }, []);

  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-16 md:px-12">
      <h1 className="font-display text-4xl font-semibold tracking-tight">Download Lamphaus</h1>
      <p className="mt-4 leading-relaxed text-fg-muted">
        One universal APK for phones, tablets, and TV. Beta releases arrive here first.
      </p>

      {status === null && (
        <p className="mt-10 text-fg-muted" role="status">
          Checking the signed release feed…
        </p>
      )}

      {status?.kind === "empty" && (
        <div className="mt-10 rounded-hero bg-surface p-8">
          <p className="font-display text-xl font-semibold">No release yet</p>
          <p className="mt-2 text-fg-muted">
            The first beta has not been published. Watch the releases page instead.
          </p>
          <a className="mt-6 inline-block rounded-card bg-primary px-6 py-3 font-medium text-on-primary" href={RELEASES_PAGE}>
            GitHub Releases
          </a>
        </div>
      )}

      {status?.kind === "error" && (
        <div className="mt-10 rounded-hero bg-surface p-8">
          <p className="font-display text-xl font-semibold">
            {status.reason === "offline" ? "You appear to be offline" : "Release metadata unavailable"}
          </p>
          <p className="mt-2 text-fg-muted">
            {status.reason === "invalid"
              ? "The release feed failed verification, so no download is offered. Try the releases page directly."
              : "The signed feed could not be reached. A newly promoted version appears here without a site rebuild once propagation completes."}
          </p>
          <a className="mt-6 inline-block rounded-card bg-primary px-6 py-3 font-medium text-on-primary" href={RELEASES_PAGE}>
            GitHub Releases
          </a>
        </div>
      )}

      {status?.kind === "ok" && <ReleaseCard rel={status.latest} />}
    </div>
  );
}

function ReleaseCard({ rel }: { rel: FeedRelease }) {
  const { paragraphs, items } = renderChangelog(rel.changelog);
  return (
    <article className="mt-10 rounded-hero bg-surface p-8">
      <p className="text-sm text-fg-subtle">
        {rel.channel === "beta" ? "Beta" : "Stable"} · {rel.versionName} ·{" "}
        {new Date(rel.publishedAt).toLocaleDateString()}
      </p>
      <h2 className="mt-2 font-display text-2xl font-semibold">Lamphaus {rel.versionName}</h2>
      {paragraphs.map((p) => (
        <p key={p} className="mt-3 leading-relaxed text-fg-muted">
          {p}
        </p>
      ))}
      {items.length > 0 && (
        <ul className="mt-3 list-disc space-y-1 pl-6 text-fg-muted">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
      <dl className="mt-6 grid grid-cols-2 gap-4 text-sm">
        <div>
          <dt className="text-fg-subtle">Size</dt>
          <dd>{formatBytes(rel.apk.byteLength)}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle">Minimum Android</dt>
          <dd>{rel.minSdk}.0</dd>
        </div>
      </dl>
      <div className="mt-8 flex flex-wrap gap-4">
        <a className="rounded-card bg-primary px-6 py-3 font-medium text-on-primary" href={rel.apk.url}>
          Download for phone
        </a>
        <a className="rounded-card bg-white/[0.06] px-6 py-3 font-medium text-fg" href={rel.apk.url}>
          Download for TV
        </a>
      </div>
      <h3 className="mt-8 font-display text-lg font-semibold">Installing</h3>
      <ol className="mt-2 list-decimal space-y-1 pl-6 text-fg-muted">
        <li>Open the APK and confirm installation when Android asks.</li>
        <li>If blocked, allow installs from your browser, then retry.</li>
        <li>On TV, sideload with the same file — no separate build exists.</li>
      </ol>
      <p className="mt-6 text-sm text-fg-subtle">
        SHA-256 <code className="break-all">{rel.apk.sha256}</code>
      </p>
      <noscript>
        <p>
          <a href={RELEASES_PAGE}>GitHub Releases</a>
        </p>
      </noscript>
    </article>
  );
}
