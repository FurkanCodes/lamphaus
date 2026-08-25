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

export type ClaimResult =
  | { ok: true }
  | { ok: false; kind: "expired" | "unavailable" | "stale-session"; detail?: string };

/**
 * Claims the QR code shown on the TV with the caller's session
 * (plan flow F2). The Edge Function ships in M4; until then every claim
 * surfaces as `unavailable` instead of pretending to succeed.
 */
export async function claimPairingSession(code: string): Promise<ClaimResult> {
  const supabase = getSupabase();
  const { error } = await supabase.functions.invoke("claim-pairing-session", {
    body: { code },
  });
  if (!error) return { ok: true };

  const status = (error as { status?: number }).status ?? 0;
  if (status === 400 || status === 404 || status === 410) {
    return { ok: false, kind: "expired", detail: error.message };
  }
  if (status === 401) {
    // The bearer was rejected: the locally cached session points at an
    // auth user that no longer exists (deleted account, revoked session).
    // Clear it so the visitor goes through Google sign-in again instead
    // of looping on dead credentials forever.
    await supabase.auth.signOut().catch(() => {});
    return { ok: false, kind: "stale-session" };
  }
  return { ok: false, kind: "unavailable", detail: error.message };
}
