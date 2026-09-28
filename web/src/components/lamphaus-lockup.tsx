import { LamphausMark } from "@/components/lamphaus-mark";

/**
 * The Lamphaus lockup: the mark beside the tracked wordmark. The mark sits
 * at about 1.6× the cap height; the gap is half the mark's width.
 */
export function LamphausLockup({ size = 28, className = "" }: { size?: number; className?: string }) {
  return (
    <span className={`inline-flex items-center ${className}`} style={{ gap: size * 0.45 }}>
      <LamphausMark size={size} />
      <span className="wordmark text-fg" style={{ fontSize: size * 0.52 }} aria-hidden="true">
        Lamphaus
      </span>
    </span>
  );
}
