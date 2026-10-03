import {
  createResolvePersonCreditsHandler,
  rankCredits,
} from "../functions/resolve-person-credits/index.ts";

function assert(condition: boolean, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function request(body: unknown, authorized = true): Request {
  return new Request("https://functions.test/resolve-person-credits", {
    method: "POST",
    headers: {
      ...(authorized ? { Authorization: "Bearer user-token" } : {}),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
}

function handler(fetchImpl: (url: string) => Promise<Response>, envTmdbKey = "env-key") {
  return createResolvePersonCreditsHandler({
    supabaseUrl: "https://project.test",
    anonKey: "anon-key",
    serviceRole: "service-role",
    envTmdbKey,
    decrypt: () => Promise.resolve({ config: { api_key: "user-key" } }),
    fetchImpl: (input) => fetchImpl(input.toString()),
  });
}

const person = {
  id: 7,
  name: "Ada Example",
  profile_path: "/ada.jpg",
  known_for_department: "Acting",
  combined_credits: {
    cast: [
      { id: 1, media_type: "movie", title: "Quiet Film", popularity: 5, vote_count: 40, release_date: "2019-05-01" },
      { id: 2, media_type: "tv", name: "Big Series", popularity: 50, vote_count: 900, first_air_date: "2021-01-01" },
      { id: 3, media_type: "tv", name: "Late Talk", popularity: 99, vote_count: 900, genre_ids: [10767] },
      { id: 4, media_type: "movie", title: "Unseen", popularity: 80, vote_count: 1 },
    ],
    crew: [
      { id: 1, media_type: "movie", title: "Quiet Film", job: "Producer", popularity: 5, vote_count: 40 },
    ],
  },
};

Deno.test("credits rank by popularity, merge cast and crew, and drop talk shows and obscure titles", () => {
  const ranked = rankCredits(person);
  assert(ranked.length === 2, `expected 2 credits, got ${ranked.length}`);
  assert(ranked[0].id === 2 && ranked[1].id === 1, "most popular first");
});

Deno.test("titles come back keyed by IMDb id with the client's series spelling", async () => {
  const urls: string[] = [];
  const response = await handler(async (url) => {
    urls.push(url);
    if (url.endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
    if (url.includes("/rest/v1/provider_configs")) return Response.json([{ encrypted_config: "blob" }]);
    if (url.includes("/person/7?")) return Response.json(person);
    if (url.includes("/tv/2/external_ids")) return Response.json({ imdb_id: "tt0000002" });
    if (url.includes("/movie/1/external_ids")) return Response.json({ imdb_id: null });
    return new Response(null, { status: 404 });
  })(request({ personId: "7" }));

  assert(response.status === 200, `expected 200, got ${response.status}`);
  const body = await response.json();
  assert(body.name === "Ada Example", "person name");
  assert(body.credits.length === 1, "titles without an IMDb id are left out");
  assert(body.credits[0].id === "tt0000002" && body.credits[0].type === "series", "series spelled for the client");
  assert(urls.some((url) => url.includes("api_key=user-key")), "the caller's own key is used");
  assert(!JSON.stringify(body).includes("user-key"), "the key never reaches the client");
});

Deno.test("unauthenticated callers and malformed ids are refused", async () => {
  const fetchImpl = async (url: string) =>
    url.endsWith("/auth/v1/user") ? Response.json({ id: "user-a" }) : new Response(null, { status: 404 });
  assert((await handler(fetchImpl)(request({ personId: "7" }, false))).status === 401, "needs auth");
  assert((await handler(fetchImpl)(request({ personId: "../x" }))).status === 400, "rejects non-numeric ids");
});

Deno.test("a missing TMDB key is a clean 404, not an error", async () => {
  const response = await handler(async (url) => {
    if (url.endsWith("/auth/v1/user")) return Response.json({ id: "user-a" });
    return Response.json([]);
  }, "")(request({ personId: "7" }));
  assert(response.status === 404, `expected 404, got ${response.status}`);
});
