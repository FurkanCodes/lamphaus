/**
 * Lamphaus house-and-beam mark.
 *
 * Path data is ported VERBATIM from
 * app/src/main/res/drawable/ic_lamphaus_foreground.xml (plan §8.2 — the
 * Android drawable stays canonical). Never redraw these paths by hand.
 */
export function LamphausMark({
  size = 40,
  className,
}: {
  size?: number;
  className?: string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 108 108"
      role="img"
      aria-label="Lamphaus"
      className={className}
    >
      <path fill="#4058D8" d="M18,48 L54,18 L90,48 L90,88 L18,88 Z" />
      <path fill="#090A0D" d="M50,31 L58,31 L58,48 L67,48 C67,48 73,50 76,58 L32,58 C35,50 41,48 50,48 Z" />
      <path fill="#68D4E8" d="M43,58 L65,58 L76,84 L32,84 Z" />
    </svg>
  );
}
