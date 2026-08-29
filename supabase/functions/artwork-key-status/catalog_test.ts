import {
  isValidArtworkCatalogRow,
  projectArtworkKeyStatus,
  type ArtworkCatalogRow,
} from "./catalog.ts";

const tmdb: ArtworkCatalogRow = {
  id: "tmdb",
  display_name: "TMDB",
  purpose: "Match titles.",
  help_text: "Use a TMDB v3 key.",
  key_page_url: "https://www.themoviedb.org/settings/api",
  sort_order: 10,
  enabled: true,
};
const fixture: ArtworkCatalogRow = {
  id: "fixture_art",
  display_name: "Fixture Art",
  purpose: "Test artwork.",
  help_text: "Use a fixture key.",
  key_page_url: "https://example.test/key",
  sort_order: 5,
  enabled: true,
};

Deno.test("catalog accepts opaque fixture IDs and orders by server sort", () => {
  if (!isValidArtworkCatalogRow(fixture)) throw new Error("fixture catalog row rejected");
  const response = projectArtworkKeyStatus([tmdb, fixture], new Set(["artwork.fixture_art"]), 2);
  if (response.contract_version !== 2) throw new Error("missing v2 contract");
  if (response.providers.map((provider) => provider.id).join(",") !== "fixture_art,tmdb") {
    throw new Error("catalog order is not deterministic");
  }
});

Deno.test("disabled configured catalog entries remain removable", () => {
  const retired = { ...fixture, enabled: false };
  const response = projectArtworkKeyStatus([retired], new Set(["artwork.fixture_art"]), 2);
  if (response.providers.length !== 1 || response.providers[0].enabled !== false || !response.providers[0].configured) {
    throw new Error("retired configured provider was hidden");
  }
});

Deno.test("invalid catalog URLs are rejected", () => {
  if (isValidArtworkCatalogRow({ ...tmdb, key_page_url: "http://unsafe.test/key" })) {
    throw new Error("HTTP catalog URL accepted");
  }
});
