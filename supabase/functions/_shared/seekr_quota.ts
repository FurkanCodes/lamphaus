// Account-wide Seekr cap memory (PLY-SEEK-01), backed by the deny-all
// seekr_quota_blocks table. Every call is best effort: a failed read means
// "no block known" and a failed write is skipped, so previews never break on
// this bookkeeping.

import type { SeekrLimitScope } from "./seekr.ts";

export type SeekrBlock = { scope: SeekrLimitScope; untilMs: number };

/** Burst back-offs shorter than this are not worth sharing across devices. */
const MIN_SHARED_BLOCK_SECONDS = 60;

/** The caps still in force for [userId]. */
export async function activeSeekrBlocks(
  supabaseUrl: string,
  serviceRole: string,
  userId: string,
  nowMs: number = Date.now(),
): Promise<SeekrBlock[]> {
  try {
    const since = encodeURIComponent(new Date(nowMs).toISOString());
    const response = await fetch(
      `${supabaseUrl}/rest/v1/seekr_quota_blocks?user_id=eq.${userId}&blocked_until=gt.${since}&select=scope,blocked_until`,
      { headers: { apikey: serviceRole, Authorization: `Bearer ${serviceRole}` } },
    );
    if (!response.ok) return [];
    const rows = await response.json() as Array<{ scope?: unknown; blocked_until?: unknown }>;
    return rows.flatMap((row) => {
      const until = typeof row.blocked_until === "string" ? Date.parse(row.blocked_until) : NaN;
      const scope = row.scope;
      if (!Number.isFinite(until) || (scope !== "movie" && scope !== "episode" && scope !== "all")) return [];
      return [{ scope, untilMs: until }];
    });
  } catch {
    return [];
  }
}

/** The block that stops a lookup of [kind], if any (an "all" block stops both). */
export function blockFor(blocks: SeekrBlock[], kind: "movie" | "episode"): SeekrBlock | null {
  return blocks
    .filter((block) => block.scope === kind || block.scope === "all")
    .sort((a, b) => b.untilMs - a.untilMs)[0] ?? null;
}

/** Remembers a cap Seekr reported, so no device asks again until it lifts. */
export async function recordSeekrBlock(
  supabaseUrl: string,
  serviceRole: string,
  userId: string,
  scope: SeekrLimitScope,
  retryAfterSeconds: number,
  nowMs: number = Date.now(),
): Promise<void> {
  if (retryAfterSeconds < MIN_SHARED_BLOCK_SECONDS) return;
  try {
    await fetch(`${supabaseUrl}/rest/v1/seekr_quota_blocks?on_conflict=user_id,scope`, {
      method: "POST",
      headers: {
        apikey: serviceRole,
        Authorization: `Bearer ${serviceRole}`,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=minimal",
      },
      body: JSON.stringify({
        user_id: userId,
        scope,
        blocked_until: new Date(nowMs + retryAfterSeconds * 1000).toISOString(),
      }),
    });
  } catch {
    // Best effort: the device still waits on its own.
  }
}
