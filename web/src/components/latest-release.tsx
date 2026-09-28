"use client";

import { useEffect, useState } from "react";
import { formatBytes, loadVerifiedFeed, type FeedRelease } from "@/lib/updates";

/**
 * The newest release from the signed feed, or a plain fallback while it
 * loads, offline, or when verification fails. Never shows unverified data.
 */
export function LatestRelease({ className = "" }: { className?: string }) {
  const [release, setRelease] = useState<FeedRelease | null>(null);

  useEffect(() => {
    let live = true;
    loadVerifiedFeed().then((status) => {
      if (live && status.kind === "ok") setRelease(status.latest);
    });
    return () => {
      live = false;
    };
  }, []);

  return (
    <span className={className}>
      {release ? `Version ${release.versionName} · ${formatBytes(release.apk.byteLength)}` : "Public beta"}
    </span>
  );
}
