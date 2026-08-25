"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { LamphausMark } from "@/components/lamphaus-mark";
import { claimPairingSession, getSupabase } from "@/lib/supabase";
import { cloudConfigured, withBasePath } from "@/lib/config";

const PENDING_CODE_KEY = "lamphaus.pairCode";

type Phase =
  | { kind: "checking" }
  | { kind: "no-code" }
  | { kind: "not-configured" }
  | { kind: "signed-out"; code: string }
  | { kind: "claiming"; code: string }
  | { kind: "success" }
  | { kind: "expired" }
  | { kind: "unavailable"; detail?: string }
  | { kind: "network"; detail?: string };

export default function PairClient() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code");
  const [phase, setPhase] = useState<Phase>({ kind: "checking" });
  const attemptedFor = useRef<string | null>(null);

  const attemptClaim = useCallback(async (claimCode: string) => {
    setPhase({ kind: "claiming", code: claimCode });
    const result = await claimPairingSession(claimCode);
    if (result.ok) {
      setPhase({ kind: "success" });
    } else if (result.kind === "expired") {
      setPhase({ kind: "expired" });
    } else if (result.kind === "stale-session") {
      // Cached credentials were rejected (deleted account / revoked
      // session) and have been cleared — ask for a fresh sign-in.
      setPhase({ kind: "signed-out", code: claimCode });
    } else if (result.kind === "network") {
      setPhase({ kind: "network", detail: result.detail });
    } else {
      setPhase({ kind: "unavailable", detail: result.detail });
    }
  }, []);

  useEffect(() => {
    if (!cloudConfigured) {
      setPhase({ kind: "not-configured" });
      return;
    }
    if (!code) {
      setPhase({ kind: "no-code" });
      return;
    }
    let active = true;
    getSupabase()
      .auth.getSession()
      .then(({ data }) => {
        if (!active) return;
        if (data.session) {
          // One claim attempt per code, even if React re-runs this effect.
          if (attemptedFor.current !== code) {
            attemptedFor.current = code;
            void attemptClaim(code);
          }
        } else {
          setPhase({ kind: "signed-out", code });
        }
      })
      .catch(() => {
        if (active) setPhase({ kind: "not-configured" });
      });
    return () => {
      active = false;
    };
  }, [code, attemptClaim]);

  async function signInWithGoogle(pairCode: string) {
    sessionStorage.setItem(PENDING_CODE_KEY, pairCode);
    await getSupabase().auth.signInWithOAuth({
      provider: "google",
      options: {
        redirectTo: `${window.location.origin}${withBasePath("/auth/callback/")}`,
      },
    });
  }

  return (
    <div className="mx-auto flex min-h-[70vh] w-full max-w-md flex-col items-center justify-center px-6 text-center">
      {phase.kind === "checking" && (
        <>
          <LamphausMark size={56} />
          <p className="mt-8 animate-pulse text-fg-muted">Checking session…</p>
        </>
      )}

      {phase.kind === "no-code" && (
        <>
          <LamphausMark size={56} />
          <h1 className="mt-8 font-display text-2xl font-semibold">Waiting for a code</h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Scan the QR code shown on your TV — it opens this page with the
            pairing code attached.
          </p>
        </>
      )}

      {phase.kind === "not-configured" && (
        <>
          <h1 className="font-display text-2xl font-semibold">Setup needed</h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Supabase credentials are missing from this deployment. Copy{" "}
            <code className="rounded-card bg-white/[0.06] px-1.5 py-0.5 text-sm">web/.env.example</code>{" "}
            to <code className="rounded-card bg-white/[0.06] px-1.5 py-0.5 text-sm">.env.local</code>{" "}
            and rebuild.
          </p>
        </>
      )}

      {phase.kind === "signed-out" && (
        <>
          <LamphausMark size={56} />
          <h1 className="mt-8 font-display text-3xl font-semibold tracking-tight">
            Pair this TV?
          </h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Sign in to link the TV showing code{" "}
            <span className="font-display font-semibold tracking-[0.18em] text-beam">
              {phase.code}
            </span>{" "}
            to your account.
          </p>
          <button
            onClick={() => void signInWithGoogle(phase.code)}
            className="mt-8 rounded-card bg-primary px-6 py-3 font-medium text-on-primary transition duration-[160ms] ease-out hover:brightness-95"
          >
            Continue with Google
          </button>
          <p className="mt-4 text-xs text-fg-subtle">
            You'll come straight back here afterwards.
          </p>
        </>
      )}

      {phase.kind === "claiming" && (
        <>
          <LamphausMark size={56} />
          <p className="mt-8 animate-pulse text-fg-muted">Linking your TV…</p>
        </>
      )}

      {phase.kind === "success" && (
        <>
          <div className="flex size-14 items-center justify-center rounded-full bg-secondary-container font-display text-2xl text-on-secondary-container">
            ✓
          </div>
          <h1 className="mt-8 font-display text-3xl font-semibold tracking-tight">Paired.</h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Head back to the couch — the TV is already yours.
          </p>
        </>
      )}

      {phase.kind === "expired" && (
        <>
          <h1 className="font-display text-2xl font-semibold">That code expired.</h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Show a fresh QR on the TV, then scan it again. Codes are single-use
            and only live for a few minutes.
          </p>
        </>
      )}

      {phase.kind === "network" && (
        <>
          <h1 className="font-display text-2xl font-semibold">
            Can&apos;t reach the pairing service.
          </h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Check your connection, then show a fresh QR on the TV and try
            again.
          </p>
          {phase.detail && (
            <p className="mt-4 text-xs text-fg-muted">{phase.detail}</p>
          )}
        </>
      )}

      {phase.kind === "unavailable" && (
        <>
          <h1 className="font-display text-2xl font-semibold">
            Something went wrong while linking.
          </h1>
          <p className="mt-3 leading-relaxed text-fg-muted">
            Your sign-in worked, but the TV could not be linked just now.
            Please try again in a minute.
          </p>
          {phase.detail && (
            <p className="mt-4 text-xs text-fg-muted">{phase.detail}</p>
          )}
        </>
      )}
    </div>
  );
}
