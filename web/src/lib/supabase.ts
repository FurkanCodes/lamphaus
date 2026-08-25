"use client";

import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { cloudConfigured, SUPABASE_PUBLISHABLE_KEY, SUPABASE_URL } from "./config";

let client: SupabaseClient | null = null;

/** Browser singleton; PKCE + automatic ?code= exchange are the v2 defaults. */
export function getSupabase(): SupabaseClient {
  if (!cloudConfigured) {
    throw new Error("Supabase env vars missing — copy web/.env.example to .env.local");
  }
  client ??= createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY);
  return client;
}

export class PairingUnavailableError extends Error {}

/**
 * Nukes every trace of Supabase state from this browser: the stored
 * session, PKCE leftovers, cached user metadata. Needed because the
 * pairing page must never present a token belonging to an account that
 * was deleted elsewhere — signOut alone proved too gentle with zombies.
 * The pairing code itself travels in the URL, so wiping is safe mid-flow.
 */
export async function purgeLocalAuth(): Promise<void> {
  const supabase = getSupabase();
  await supabase.auth.signOut().catch(() => {});
  try {
    const doomed: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && (key.startsWith("sb-") || key.startsWith("lamphaus."))) {
        doomed.push(key);
      }
    }
    doomed.forEach((key) => localStorage.removeItem(key));
  } catch {
    // Storage unavailable (private mode quirks) — signOut already ran.
  }
}

export type ClaimResult =
  | { ok: true }
  | {
      ok: false;
      kind: "expired" | "unavailable" | "stale-session" | "network";
      detail?: string;
    };

/**
 * Claims the QR code shown on the TV with the caller's session
 * (plan flow F2). Every failure mode maps to an explicit outcome so the
 * page can show an honest, actionable message — never a generic one.
 */
export async function claimPairingSession(code: string): Promise<ClaimResult> {
  const supabase = getSupabase();
  let message: string;
  let status: number;
  try {
    const { error } = await supabase.functions.invoke("claim-pairing-session", {
      body: { code },
    });
    if (!error) return { ok: true };
    message = error.message;
    status = (error as { status?: number }).status ?? 0;
  } catch (e) {
    // invoke() itself can throw (blocked CORS, offline, redirect-cancelled
    // fetch). Without this catch the page silently swallowed the reason.
    return {
      ok: false,
      kind: "network",
      detail: e instanceof Error ? e.message : String(e),
    };
  }

  if (status === 400 || status === 404 || status === 410) {
    return { ok: false, kind: "expired", detail: message };
  }
  if (status === 401 || status === 403) {
    // The bearer was rejected: the locally cached session points at an
    // auth user that no longer exists (deleted account, revoked session).
    // Purge EVERYTHING this browser holds so the next attempt starts
    // from zero instead of looping on dead credentials.
    await purgeLocalAuth();
    return { ok: false, kind: "stale-session" };
  }
  if (status === 0 || status >= 500) {
    return { ok: false, kind: "network", detail: message };
  }
  return { ok: false, kind: "unavailable", detail: message };
}
