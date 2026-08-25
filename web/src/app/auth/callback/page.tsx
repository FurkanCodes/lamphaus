"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getSupabase } from "@/lib/supabase";
import { withBasePath } from "@/lib/config";

const PENDING_CODE_KEY = "lamphaus.pairCode";

/**
 * OAuth return leg (plan §8): supabase-js exchanges the PKCE ?code=
 * automatically on load; we just wait for SIGNED_IN and bounce back to
 * /pair carrying the pending pairing code.
 */
export default function AuthCallbackPage() {
  const router = useRouter();

  useEffect(() => {
    const pendingCode = sessionStorage.getItem(PENDING_CODE_KEY);
    const target = withBasePath(
      `/pair${pendingCode ? `?code=${encodeURIComponent(pendingCode)}` : "/"}`,
    );

    const { data } = getSupabase().auth.onAuthStateChange((event) => {
      if (event === "SIGNED_IN") router.replace(target);
    });

    // The exchange usually completes before this mounts; don't strand anyone.
    const fallback = setTimeout(() => router.replace(target), 4000);

    return () => {
      data.subscription.unsubscribe();
      clearTimeout(fallback);
    };
  }, [router]);

  return (
    <div className="mx-auto flex min-h-[70vh] w-full max-w-md flex-col items-center justify-center px-6 text-center">
      <p className="animate-pulse text-fg-muted">Signing you in…</p>
    </div>
  );
}
