import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy",
};

/*
 * TODO(owner, launch blocker per plan §8/§11): legal review of this copy and
 * a working contact address before public pairing goes live.
 */

export default function PrivacyPage() {
  return (
    <article className="mx-auto w-full max-w-2xl px-6 py-16 md:px-0">
      <h1 className="font-display text-3xl font-semibold tracking-tight">Privacy</h1>
      <p className="mt-2 text-sm text-fg-subtle">Effective August 25, 2026</p>

      <div className="mt-10 space-y-8 leading-relaxed text-fg-muted [&_h2]:font-display [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:text-fg [&_h2]:mb-2">
        <section>
          <h2>What we collect</h2>
          <p>
            When you sign in with Google we receive your name, email address
            and a Google account identifier. That's the whole list. If you pair
            a television, we also remember that device so it can stay signed in.
          </p>
        </section>
        <section>
          <h2>Why we collect it</h2>
          <p>
            Your account links your devices together: it's what makes your
            library, playback progress and profiles follow you between screens.
            Without it, every device would be a stranger.
          </p>
        </section>
        <section>
          <h2>What we never do</h2>
          <p>
            We don't sell data. We don't run ads. We don't embed analytics or
            tracking scripts on this site.
          </p>
        </section>
        <section>
          <h2>Deleting your data</h2>
          <p>
            Deleting your account removes your profile, library, progress,
            settings and paired devices from our systems. Unpairing a single TV
            revokes only that device.
          </p>
        </section>
        <section>
          <h2>Contact</h2>
          <p>
            Questions or deletion requests: <span className="text-fg">privacy@lamphaus.dev</span>{" "}
            <em>(address to be activated before launch)</em>.
          </p>
        </section>
      </div>
    </article>
  );
}
