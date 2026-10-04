import { parseSeekrLimit, secondsUntilUtcMidnight } from "../functions/_shared/seekr.ts";
import { blockFor } from "../functions/_shared/seekr_quota.ts";

function assertEquals<T>(actual: T, expected: T, message = ""): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

// 2026-10-04 21:00:00 UTC: three hours before Seekr's daily reset.
const NINE_PM = Date.UTC(2026, 9, 4, 21, 0, 0);

Deno.test("daily counts reset at midnight UTC", () => {
  assertEquals(secondsUntilUtcMidnight(NINE_PM), 3 * 3600);
});

Deno.test("each cap is told apart and lasts as long as Seekr says", () => {
  assertEquals(parseSeekrLimit("distinct movie cap reached", "10800", NINE_PM), { scope: "movie", retryAfterSeconds: 10800 });
  assertEquals(parseSeekrLimit("distinct episode cap reached", "10800", NINE_PM), { scope: "episode", retryAfterSeconds: 10800 });
  assertEquals(parseSeekrLimit("daily quota exceeded", "10800", NINE_PM), { scope: "all", retryAfterSeconds: 10800 });
});

Deno.test("a daily cap without a usable Retry-After lasts until midnight UTC", () => {
  assertEquals(parseSeekrLimit("distinct movie cap reached", null, NINE_PM), { scope: "movie", retryAfterSeconds: 3 * 3600 });
  assertEquals(parseSeekrLimit("distinct movie cap reached", "soon", NINE_PM), { scope: "movie", retryAfterSeconds: 3 * 3600 });
});

Deno.test("an unnamed 429 is the short burst limit", () => {
  assertEquals(parseSeekrLimit(undefined, "2", NINE_PM), { scope: "all", retryAfterSeconds: 2 });
  assertEquals(parseSeekrLimit("slow down", null, NINE_PM), { scope: "all", retryAfterSeconds: 60 });
});

Deno.test("a movie cap never stops episodes, an all cap stops both", () => {
  const movie = { scope: "movie" as const, untilMs: NINE_PM + 1000 };
  const all = { scope: "all" as const, untilMs: NINE_PM + 500 };
  assertEquals(blockFor([movie], "episode"), null);
  assertEquals(blockFor([movie], "movie"), movie);
  assertEquals(blockFor([all], "episode"), all);
  assertEquals(blockFor([movie, all], "movie"), movie, "the longest block wins");
});
