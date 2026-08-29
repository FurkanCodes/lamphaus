import {
  createProviderRegistry,
  executeProviderAdapters,
  type ConfiguredArtworkProvider,
} from "./providers/registry.ts";
import {
  emptyArtworkLists,
  type ArtworkProviderAdapter,
  type ArtworkRequest,
} from "./provider.ts";

const request: ArtworkRequest = {
  mediaKey: "imdb:tt1234567",
  name: "Example",
  releaseYear: 2024,
  mediaType: "series",
};

function adapter(
  id: string,
  after: readonly string[] = [],
  resolve: ArtworkProviderAdapter["resolve"] = async () => ({
    provider: id,
    status: "success",
    ...emptyArtworkLists(),
    identifiers: {},
  }),
): ArtworkProviderAdapter {
  return {
    id,
    after,
    allowedImageHosts: ["fixture.example"],
    requestedIdentifiers: () => [],
    resolve,
  };
}

Deno.test("registry accepts fixture provider and isolates thrown adapters", async () => {
  const fixture = adapter("fixture_art", [], async () => ({
    provider: "fixture_art",
    status: "success",
    posters: [{ provider: "fixture_art", reference: "https://fixture.example/poster.jpg" }],
    backdrops: [],
    logos: [],
    identifiers: {},
  }));
  const broken = adapter("broken_art", [], async () => {
    throw new Error("upstream failure");
  });
  const results = await executeProviderAdapters(
    createProviderRegistry([fixture, broken]),
    request,
    [{ id: "fixture_art", apiKey: "fixture" }, { id: "broken_art", apiKey: "broken" }],
  );
  if (results.find((result) => result.provider === "fixture_art")?.status !== "success") throw new Error("fixture failed");
  if (results.find((result) => result.provider === "broken_art")?.status !== "lookup_failed") throw new Error("broken adapter escaped");
});

Deno.test("registry waits for dependencies and propagates identifiers", async () => {
  let sawIdentifier = false;
  const upstream = adapter("tmdb", [], async () => ({
    provider: "tmdb",
    status: "success",
    ...emptyArtworkLists(),
    identifiers: { tvdb_id: "42" },
  }));
  const downstream = adapter("fanart", ["tmdb"], async ({ identifiers }) => {
    sawIdentifier = identifiers.tvdb_id === "42";
    return {
      provider: "fanart",
      status: sawIdentifier ? "success" : "missing_external_id",
      ...emptyArtworkLists(),
      identifiers: {},
    };
  });
  await executeProviderAdapters(
    createProviderRegistry([downstream, upstream]),
    request,
    [{ id: "fanart", apiKey: "fanart" }, { id: "tmdb", apiKey: "tmdb" }],
  );
  if (!sawIdentifier) throw new Error("dependency identifier was not published");
});

Deno.test("registry rejects duplicate IDs and dependency cycles", () => {
  try {
    createProviderRegistry([adapter("fixture_art"), adapter("fixture_art")]);
    throw new Error("duplicate ID accepted");
  } catch (error) {
    if (!(error instanceof Error) || !error.message.startsWith("duplicate_provider_id:")) throw error;
  }
  try {
    createProviderRegistry([adapter("a", ["b"]), adapter("b", ["a"])]);
    throw new Error("cycle accepted");
  } catch (error) {
    if (!(error instanceof Error) || !error.message.startsWith("provider_dependency_cycle:")) throw error;
  }
});

Deno.test("provider reference sanitization drops unsafe and same-provider duplicates", async () => {
  const fixture = adapter("fixture_art", [], async () => ({
    provider: "fixture_art",
    status: "success",
    posters: [
      { provider: "fixture_art", reference: "https://fixture.example/ok.jpg" },
      { provider: "fixture_art", reference: "https://fixture.example/ok.jpg" },
      { provider: "fixture_art", reference: "http://fixture.example/http.jpg" },
      { provider: "fixture_art", reference: "https://wrong.example/wrong.jpg" },
      { provider: "fixture_art", reference: "not a URL" },
    ],
    backdrops: [],
    logos: [],
    identifiers: {},
  }));
  const [result] = await executeProviderAdapters(createProviderRegistry([fixture]), request, [{ id: "fixture_art", apiKey: "fixture" }]);
  if (result.posters.length !== 1 || result.posters[0].reference !== "https://fixture.example/ok.jpg") {
    throw new Error("unsafe or duplicate references survived");
  }
});
