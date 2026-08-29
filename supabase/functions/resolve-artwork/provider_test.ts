import {
  combineProviderResults,
  normalizeFanartPayload,
  normalizeTmdbImagePayload,
  normalizeTmdbMatches,
} from "./provider.ts";

function assertEqual<T>(actual: T, expected: T, label: string): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${label}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

Deno.test("TMDB paths normalize into typed artwork lists", () => {
  const artwork = normalizeTmdbImagePayload({
    posters: [{ file_path: "/poster-a.jpg" }, { file_path: "poster-invalid.jpg" }],
    backdrops: [{ file_path: "/backdrop-a.jpg" }],
    logos: [{ file_path: "/logo-a.png" }],
  });

  assertEqual(artwork.posters, [{ provider: "tmdb", reference: "/poster-a.jpg" }], "TMDB posters");
  assertEqual(artwork.backdrops, [{ provider: "tmdb", reference: "/backdrop-a.jpg" }], "TMDB backdrops");
  assertEqual(artwork.logos, [{ provider: "tmdb", reference: "/logo-a.png" }], "TMDB logos");
});

Deno.test("Fanart movie and series fields map to the right lists", () => {
  const movie = normalizeFanartPayload({
    movieposter: [{ url: "https://fanart.example/movie-poster.jpg" }],
    moviebackground: [{ url: "https://fanart.example/movie-background.jpg" }],
    hdmovielogo: [{ url: "https://fanart.example/movie-logo.png" }],
    tvposter: [{ url: "https://fanart.example/wrong-tv.jpg" }],
  }, "movie");
  const series = normalizeFanartPayload({
    tvposter: [{ url: "https://fanart.example/tv-poster.jpg" }],
    showbackground: [{ url: "https://fanart.example/tv-background.jpg" }],
    hdtvlogo: [{ url: "https://fanart.example/tv-logo.png" }],
    movieposter: [{ url: "https://fanart.example/wrong-movie.jpg" }],
  }, "series");

  assertEqual(movie.posters, [{ provider: "fanart", reference: "https://fanart.example/movie-poster.jpg" }], "movie posters");
  assertEqual(movie.backdrops, [{ provider: "fanart", reference: "https://fanart.example/movie-background.jpg" }], "movie backgrounds");
  assertEqual(movie.logos, [{ provider: "fanart", reference: "https://fanart.example/movie-logo.png" }], "movie logos");
  assertEqual(series.posters, [{ provider: "fanart", reference: "https://fanart.example/tv-poster.jpg" }], "TV posters");
  assertEqual(series.backdrops, [{ provider: "fanart", reference: "https://fanart.example/tv-background.jpg" }], "TV backgrounds");
  assertEqual(series.logos, [{ provider: "fanart", reference: "https://fanart.example/tv-logo.png" }], "TV logos");
});

Deno.test("Fanart rejects malformed and non-HTTPS URLs", () => {
  const artwork = normalizeFanartPayload({
    movieposter: [
      { url: "http://fanart.example/insecure.jpg" },
      { url: "" },
      { url: "   " },
      { url: "https://fanart.example/valid.jpg" },
      {},
    ],
  }, "movie");

  assertEqual(
    artwork.posters,
    [{ provider: "fanart", reference: "https://fanart.example/valid.jpg" }],
    "validated Fanart posters",
  );
});

Deno.test("combined assets dedupe provider-reference pairs without cross-provider collisions", () => {
  const combined = combineProviderResults([
    {
      provider: "fanart",
      status: "success",
      posters: [
        { provider: "fanart", reference: "same-reference" },
        { provider: "fanart", reference: "same-reference" },
      ],
      backdrops: [],
      logos: [],
    },
    {
      provider: "tmdb",
      status: "success",
      posters: [
        { provider: "tmdb", reference: "same-reference" },
        { provider: "tmdb", reference: "tmdb-only" },
      ],
      backdrops: [],
      logos: [],
    },
  ]);

  assertEqual(
    combined.posters,
    [
      { provider: "tmdb", reference: "same-reference" },
      { provider: "tmdb", reference: "tmdb-only" },
      { provider: "fanart", reference: "same-reference" },
    ],
    "combined posters",
  );
  assertEqual(
    combined.provider_results,
    [
      { provider: "tmdb", status: "success" },
      { provider: "fanart", status: "success" },
    ],
    "provider result order",
  );
});

Deno.test("TMDB result fields preserve movie and TV matches", () => {
  assertEqual(
    normalizeTmdbMatches({ results: [
      { media_type: "tv", id: 20, poster_path: "/tv.jpg", backdrop_path: "/tv-bg.jpg" },
      { media_type: "movie", id: 10, poster_path: "/movie.jpg", backdrop_path: "/movie-bg.jpg" },
    ] }, "movie"),
    [{ kind: "movie", id: 10, posterReference: "/movie.jpg", backdropReference: "/movie-bg.jpg" }],
    "movie match fields",
  );
  assertEqual(
    normalizeTmdbMatches({ results: [
      { media_type: "tv", id: 20, poster_path: "/tv.jpg", backdrop_path: "/tv-bg.jpg" },
      { media_type: "movie", id: 10 },
    ] }, "series"),
    [{ kind: "tv", id: 20, posterReference: "/tv.jpg", backdropReference: "/tv-bg.jpg" }],
    "TV match fields",
  );
});
